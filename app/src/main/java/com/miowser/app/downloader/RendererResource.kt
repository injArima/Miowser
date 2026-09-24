package com.miowser.app.downloader

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class RendererResourceKind { BLOB, DATA }

fun rendererResourceKind(url: String): RendererResourceKind? = when {
    url.startsWith("blob:", ignoreCase = true) -> RendererResourceKind.BLOB
    url.startsWith("data:", ignoreCase = true) -> RendererResourceKind.DATA
    else -> null
}

data class DecodedDataUrl(val mimeType: String, val bytes: ByteArray)

sealed interface DataUrlDecodeResult {
    data class Success(val value: DecodedDataUrl) : DataUrlDecodeResult
    data class Failure(val message: String) : DataUrlDecodeResult
}

/**
 * Strict, bounded decoder for renderer-only data: URLs. It deliberately works at byte level for
 * percent escapes: URLDecoder would apply HTML form rules (`+` becomes a space) and decode into a
 * String first, corrupting arbitrary binary payloads.
 */
object DataUrlDecoder {
    fun decode(url: String, maxBytes: Int): DataUrlDecodeResult {
        if (maxBytes <= 0) return DataUrlDecodeResult.Failure("Invalid data URL size limit")
        if (!url.startsWith("data:", ignoreCase = true)) {
            return DataUrlDecodeResult.Failure("Not a data URL")
        }
        val comma = url.indexOf(',')
        if (comma < 0) return DataUrlDecodeResult.Failure("Malformed data URL")

        val metadata = url.substring(5, comma)
        if (metadata.length > 512 || metadata.any { it.code < 0x20 || it.code == 0x7f }) {
            return DataUrlDecodeResult.Failure("Invalid data URL metadata")
        }
        val parts = metadata.split(';')
        val mimeType = parts.firstOrNull()
            ?.takeIf { it.contains('/') }
            ?.trim()
            ?.lowercase()
            ?: "text/plain"
        if (!mimeType.matches(Regex("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+"))) {
            return DataUrlDecodeResult.Failure("Invalid data URL MIME type")
        }
        val isBase64 = parts.drop(1).any { it.equals("base64", ignoreCase = true) }
        val payload = url.substring(comma + 1)

        return try {
            val bytes = if (isBase64) decodeBase64(payload, maxBytes) else decodePercentBytes(payload, maxBytes)
            DataUrlDecodeResult.Success(DecodedDataUrl(mimeType, bytes))
        } catch (_: SizeLimitExceededException) {
            DataUrlDecodeResult.Failure("Page-generated download exceeds the ${formatLimit(maxBytes)} limit")
        } catch (_: IllegalArgumentException) {
            DataUrlDecodeResult.Failure("Malformed data URL payload")
        }
    }

    private fun decodeBase64(payload: String, maxBytes: Int): ByteArray {
        val maxEncodedChars = ((maxBytes.toLong() + 2L) / 3L * 4L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        // A small allowance covers legal ASCII whitespace without allowing a whitespace-only URL
        // several times larger than the configured decoded-data cap.
        if (payload.length > maxEncodedChars + 1024) throw SizeLimitExceededException()
        val compact = payload.filterNot(Char::isWhitespace)
        if (compact.length > maxEncodedChars) throw SizeLimitExceededException()
        return Base64.getDecoder().decode(compact).also {
            if (it.size > maxBytes) throw SizeLimitExceededException()
        }
    }

    private fun decodePercentBytes(payload: String, maxBytes: Int): ByteArray {
        if (payload.length > maxBytes.toLong() * 3L) throw SizeLimitExceededException()
        val output = ByteArrayOutputStream(minOf(payload.length, maxBytes))
        var index = 0
        while (index < payload.length) {
            if (payload[index] == '%') {
                if (index + 2 >= payload.length) throw IllegalArgumentException()
                val high = payload[index + 1].digitToIntOrNull(16) ?: throw IllegalArgumentException()
                val low = payload[index + 2].digitToIntOrNull(16) ?: throw IllegalArgumentException()
                output.write((high shl 4) or low)
                index += 3
            } else {
                val codePoint = payload.codePointAt(index)
                val encoded = String(Character.toChars(codePoint)).toByteArray(StandardCharsets.UTF_8)
                output.write(encoded)
                index += Character.charCount(codePoint)
            }
            if (output.size() > maxBytes) throw SizeLimitExceededException()
        }
        return output.toByteArray()
    }

    private fun formatLimit(bytes: Int): String = if (bytes >= 1024 * 1024) {
        "${bytes / (1024 * 1024)} MiB"
    } else {
        "${bytes / 1024} KiB"
    }

    private class SizeLimitExceededException : RuntimeException()
}

/** Pure sequence/size gate used by the JavaScript bridge before any chunk reaches storage. */
internal class BlobTransferLedger(
    private val maxTotalBytes: Long,
    private val maxChunkBytes: Int
) {
    var expectedBytes: Long = -1L
        private set
    var receivedBytes: Long = 0L
        private set
    private var nextChunkIndex = 0

    fun begin(totalBytes: Long): Boolean {
        if (expectedBytes >= 0L || totalBytes <= 0L || totalBytes > maxTotalBytes) return false
        expectedBytes = totalBytes
        return true
    }

    fun acceptChunk(index: Int, byteCount: Int): Boolean {
        if (expectedBytes < 0L || index != nextChunkIndex || byteCount !in 1..maxChunkBytes) return false
        if (receivedBytes + byteCount > expectedBytes || receivedBytes + byteCount > maxTotalBytes) return false
        receivedBytes += byteCount
        nextChunkIndex++
        return true
    }

    fun isComplete(): Boolean = expectedBytes > 0L && receivedBytes == expectedBytes
}
