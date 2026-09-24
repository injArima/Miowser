package com.miowser.app.downloader

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

data class DownloadIdentityInput(
    val originalUrl: String,
    val finalUrl: String?,
    val contentDisposition: String?,
    val declaredMimeType: String?,
    val browserSuggestedName: String?,
    val fallbackBaseName: String,
    val preferredBaseName: String? = null,
    val signature: ByteArray
)

data class ResolvedDownloadIdentity(
    val fileName: String,
    val baseName: String,
    val extension: String,
    val mimeType: String
)

/**
 * Resolves the identity of a direct download from the response that will actually be saved.
 * WebView's initial suggestion is deliberately only a fallback: redirects, response headers and
 * the payload itself commonly reveal more than the clicked URL (especially for download.php and
 * application/octet-stream responses).
 */
object DownloadIdentityResolver {
    private val genericExtensions = setOf("bin", "tmp", "download", "file")
    private val endpointExtensions = setOf("php", "asp", "aspx", "jsp", "cgi", "do", "action")
    private val genericMimeTypes = setOf(
        "application/octet-stream",
        "binary/octet-stream",
        "application/force-download",
        "application/download",
        "application/x-download"
    )

    fun resolve(input: DownloadIdentityInput): ResolvedDownloadIdentity {
        val dispositionName = fileNameFromContentDisposition(input.contentDisposition)
        val finalUrlName = fileNameFromUrl(input.finalUrl)
        val originalUrlName = fileNameFromUrl(input.originalUrl)
        val suggestedName = sanitizeFileName(input.browserSuggestedName)

        val namedCandidates = listOfNotNull(
            dispositionName,
            finalUrlName,
            originalUrlName,
            suggestedName
        )
        val namedExtension = namedCandidates
            .mapNotNull(::extensionFromName)
            .firstOrNull(::isSpecificExtension)
        val mimeExtension = extensionForMimeType(input.declaredMimeType)
        val signatureExtension = sniffExtension(input.signature)
        val genericExtension = namedCandidates
            .mapNotNull(::extensionFromName)
            .firstOrNull { it !in endpointExtensions }

        val extension = namedExtension
            ?: mimeExtension
            ?: signatureExtension
            ?: genericExtension
            ?: "bin"

        // Keep the best server-provided basename even when its provisional extension was generic.
        // Example: filename="movie.bin" plus an MP4 signature becomes movie.mp4, not download.mp4.
        val baseCandidates = listOfNotNull(
            sanitizeBaseName(input.preferredBaseName),
            dispositionName,
            finalUrlName?.takeIf(::isMeaningfulUrlName),
            originalUrlName?.takeIf(::isMeaningfulUrlName),
            suggestedName,
            sanitizeBaseName(input.fallbackBaseName)
        )
        val baseName = baseCandidates
            .firstNotNullOfOrNull(::baseNameFromFileName)
            ?: "download"
        val fileName = "$baseName.$extension"
        val normalizedDeclaredMime = normalizeMimeType(input.declaredMimeType)
        val mimeType = normalizedDeclaredMime
            ?.takeUnless { it in genericMimeTypes }
            ?.takeIf { declaredMimeMatchesExtension(it, extension) }
            ?: mimeTypeForExtension(extension)

        return ResolvedDownloadIdentity(
            fileName = fileName,
            baseName = baseName,
            extension = extension,
            mimeType = mimeType
        )
    }

    internal fun fileNameFromContentDisposition(value: String?): String? {
        if (value.isNullOrBlank()) return null

        // RFC 5987/6266: filename*=UTF-8''percent-encoded-name takes precedence over filename=.
        val encoded = Regex("(?i)(?:^|;)\\s*filename\\*\\s*=\\s*([^;]+)")
            .find(value)?.groupValues?.get(1)?.trim()?.trim('"')
        if (!encoded.isNullOrBlank()) {
            val components = encoded.split('\'', limit = 3)
            val charset = components.getOrNull(0)
                ?.takeIf { components.size == 3 && it.isNotBlank() }
                ?.let { runCatching { Charset.forName(it) }.getOrNull() }
                ?: StandardCharsets.UTF_8
            val encodedValue = components.getOrNull(2) ?: encoded
            runCatching {
                // URLDecoder follows HTML-form rules and normally converts '+' to a space;
                // RFC 5987 treats a literal plus as a plus, so protect it before percent decoding.
                URLDecoder.decode(encodedValue.replace("+", "%2B"), charset.name())
            }.getOrNull()?.let(::sanitizeFileName)?.takeIf(String::isNotBlank)?.let { return it }
        }

        val plain = Regex("(?i)(?:^|;)\\s*filename\\s*=\\s*(?:\"([^\"]*)\"|([^;]*))")
            .find(value)
        val fileName = plain?.groupValues?.let { groups ->
            groups[1].ifBlank { groups[2] }.trim()
        }
        return sanitizeFileName(fileName).takeIf(String::isNotBlank)
    }

    internal fun sniffExtension(bytes: ByteArray): String? {
        fun matches(offset: Int, vararg expected: Int): Boolean =
            offset >= 0 && offset + expected.size <= bytes.size &&
                expected.indices.all { index -> bytes[offset + index].toInt() and 0xff == expected[index] }

        val text = bytes.toString(Charsets.ISO_8859_1)
        val isoBmffOffset = findIsoBmffFileTypeBox(bytes)
        if (isoBmffOffset >= 0) {
            val brandsEnd = minOf(bytes.size, isoBmffOffset + 64)
            val brands = bytes.copyOfRange(isoBmffOffset + 4, brandsEnd).toString(Charsets.ISO_8859_1)
            return when {
                brands.contains("M4A ") || brands.contains("M4B ") -> "m4a"
                brands.contains("M4V ") -> "m4v"
                brands.contains("qt  ") -> "mov"
                else -> "mp4"
            }
        }

        return when {
            matches(0, 0x1a, 0x45, 0xdf, 0xa3) ->
                if (text.contains("webm", ignoreCase = true)) "webm" else "mkv"
            matches(0, 0x4f, 0x67, 0x67, 0x53) -> "ogg"
            matches(0, 0x66, 0x4c, 0x61, 0x43) -> "flac"
            matches(0, 0x52, 0x49, 0x46, 0x46) && matches(8, 0x57, 0x41, 0x56, 0x45) -> "wav"
            matches(0, 0x52, 0x49, 0x46, 0x46) && matches(8, 0x41, 0x56, 0x49, 0x20) -> "avi"
            matches(0, 0x49, 0x44, 0x33) -> "mp3"
            bytes.size >= 2 && (bytes[0].toInt() and 0xff) == 0xff &&
                (bytes[1].toInt() and 0xe0) == 0xe0 -> "mp3"
            matches(0, 0x25, 0x50, 0x44, 0x46) -> "pdf"
            matches(0, 0x50, 0x4b, 0x03, 0x04) -> {
                val isApk = text.contains("AndroidManifest.xml") &&
                    (text.contains("classes.dex") || text.contains("resources.arsc") || text.contains("META-INF/"))
                if (isApk) "apk" else "zip"
            }
            matches(0, 0x89, 0x50, 0x4e, 0x47) -> "png"
            matches(0, 0xff, 0xd8, 0xff) -> "jpg"
            matches(0, 0x47) && bytes.size > 188 && matches(188, 0x47) -> "ts"
            text.startsWith("WEBVTT", ignoreCase = true) -> "vtt"
            else -> null
        }
    }

    private fun findIsoBmffFileTypeBox(bytes: ByteArray): Int {
        // ftyp is normally the first box (type at offset 4), but legal files may place small
        // free/wide boxes before it. Scan the bounded prefix and require a plausible box size so
        // four coincidental letters inside arbitrary data do not classify the whole file as MP4.
        for (typeOffset in 4..bytes.size - 4) {
            if (bytes[typeOffset] != 'f'.code.toByte() ||
                bytes[typeOffset + 1] != 't'.code.toByte() ||
                bytes[typeOffset + 2] != 'y'.code.toByte() ||
                bytes[typeOffset + 3] != 'p'.code.toByte()
            ) continue
            val sizeOffset = typeOffset - 4
            val boxSize = ((bytes[sizeOffset].toLong() and 0xff) shl 24) or
                ((bytes[sizeOffset + 1].toLong() and 0xff) shl 16) or
                ((bytes[sizeOffset + 2].toLong() and 0xff) shl 8) or
                (bytes[sizeOffset + 3].toLong() and 0xff)
            if (boxSize >= 8L) return typeOffset
        }
        return -1
    }

    private fun fileNameFromUrl(value: String?): String? {
        if (value.isNullOrBlank()) return null
        val decoded = runCatching { URI(value).path }.getOrNull()
            ?.substringAfterLast('/')
            ?.takeIf(String::isNotBlank)
            ?: value.substringBefore('?').substringBefore('#').substringAfterLast('/')
        return sanitizeFileName(decoded).takeIf(String::isNotBlank)
    }

    private fun sanitizeFileName(value: String?): String {
        if (value.isNullOrBlank()) return ""
        return value.substringAfterLast('/').substringAfterLast('\\')
            .replace(Regex("[\\u0000-\\u001f<>:\"/\\\\|?*]"), "_")
            .trim().trim('.')
            .take(180)
    }

    private fun sanitizeBaseName(value: String?): String? =
        sanitizeFileName(value).ifBlank { null }?.let { name ->
            extensionFromName(name)?.let { name.substringBeforeLast('.') } ?: name
        }?.trim()?.trim('.')?.takeIf(String::isNotBlank)

    private fun baseNameFromFileName(fileName: String): String? {
        val extension = extensionFromName(fileName)
        val base = if (extension != null) fileName.substringBeforeLast('.') else fileName
        return base.trim().trim('.').takeIf(String::isNotBlank)
    }

    private fun extensionFromName(fileName: String): String? = fileName
        .substringAfterLast('.', missingDelimiterValue = "")
        .lowercase()
        .takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }

    private fun isSpecificExtension(extension: String): Boolean =
        extension !in genericExtensions && extension !in endpointExtensions

    private fun isMeaningfulUrlName(fileName: String): Boolean {
        val extension = extensionFromName(fileName)
        if (extension != null && (extension in genericExtensions || extension in endpointExtensions)) return false
        return fileName.substringBeforeLast('.').lowercase() !in setOf("download", "file", "get", "index")
    }

    private fun normalizeMimeType(value: String?): String? = value
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotBlank)

    private fun extensionForMimeType(value: String?): String? = when (normalizeMimeType(value)) {
        "video/mp4", "application/mp4" -> "mp4"
        "video/x-m4v" -> "m4v"
        "video/quicktime" -> "mov"
        "video/webm" -> "webm"
        "video/x-matroska" -> "mkv"
        "video/mpeg" -> "mpeg"
        "video/mp2t" -> "ts"
        "audio/mpeg" -> "mp3"
        "audio/mp4", "audio/x-m4a" -> "m4a"
        "audio/aac" -> "aac"
        "audio/ogg", "application/ogg" -> "ogg"
        "audio/flac" -> "flac"
        "audio/wav", "audio/x-wav" -> "wav"
        "text/vtt" -> "vtt"
        "application/x-subrip" -> "srt"
        "application/pdf" -> "pdf"
        "application/zip" -> "zip"
        "application/vnd.android.package-archive" -> "apk"
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        else -> null
    }

    private fun mimeTypeForExtension(extension: String): String = when (extension) {
        "apk" -> "application/vnd.android.package-archive"
        "pdf" -> "application/pdf"
        "zip" -> "application/zip"
        "mp4", "m4v" -> "video/mp4"
        "mov" -> "video/quicktime"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mpeg", "mpg" -> "video/mpeg"
        "ts" -> "video/mp2t"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "aac" -> "audio/aac"
        "ogg" -> "audio/ogg"
        "flac" -> "audio/flac"
        "wav" -> "audio/wav"
        "vtt" -> "text/vtt"
        "srt" -> "application/x-subrip"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        else -> "application/octet-stream"
    }

    private fun declaredMimeMatchesExtension(mimeType: String, extension: String): Boolean =
        extensionForMimeType(mimeType)?.let { declaredExtension ->
            declaredExtension == extension ||
                (declaredExtension == "mp4" && extension in setOf("m4v", "m4a")) ||
                (declaredExtension == "jpg" && extension == "jpeg")
        } ?: false
}
