package com.miowser.app.downloader

import java.io.IOException
import java.net.HttpURLConnection
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

enum class TransferFailureKind { NETWORK, THROTTLED, AUTHORIZATION, PERMANENT, STORAGE, INTEGRITY }

data class TransferFailure(
    val kind: TransferFailureKind,
    val message: String,
    val retryAfterMillis: Long = 0,
    val httpStatus: Int? = null
) {
    val retryable: Boolean get() = kind == TransferFailureKind.NETWORK || kind == TransferFailureKind.THROTTLED
}

class TransferException(val failure: TransferFailure, cause: Throwable? = null) : IOException(failure.message, cause)

object TransferPolicy {
    fun from(error: Throwable): TransferFailure {
        val chain = generateSequence(error) { it.cause }.take(12).toList()
        chain.filterIsInstance<TransferException>().firstOrNull()?.let { return it.failure }
        val message = error.message?.takeIf(String::isNotBlank) ?: "Download failed"
        if (chain.any { it is SecurityException }) return TransferFailure(TransferFailureKind.STORAGE, "Storage access is required. Choose an accessible download folder.")
        if (chain.any { it is SSLHandshakeException || it is SSLPeerUnverifiedException }) {
            return TransferFailure(TransferFailureKind.PERMANENT, "The server's secure connection could not be verified.")
        }
        if (chain.any { it is java.io.FileNotFoundException || it is java.nio.file.FileSystemException } ||
            listOf("no space left", "enospc", "read-only file system", "permission denied").any { message.contains(it, true) }) {
            return TransferFailure(TransferFailureKind.STORAGE, message)
        }
        return TransferFailure(if (chain.any { it is IOException }) TransferFailureKind.NETWORK else TransferFailureKind.PERMANENT, message)
    }

    fun http(status: Int, retryAfter: String? = null, nowMillis: Long = System.currentTimeMillis()): TransferFailure =
        TransferFailure(
            when (status) {
                401, 403 -> TransferFailureKind.AUTHORIZATION
                408, 425, 429, 500, 502, 503, 504 -> TransferFailureKind.THROTTLED
                else -> TransferFailureKind.PERMANENT
            },
            when (status) {
                401, 403 -> "HTTP $status — return to the page to sign in or refresh this download link."
                404, 410 -> "HTTP $status — this file is no longer available. Refresh the download link."
                else -> "HTTP $status"
            },
            retryAfterMillis(retryAfter, nowMillis), status
        )

    fun retryAfterMillis(value: String?, nowMillis: Long): Long {
        val text = value?.trim() ?: return 0
        text.toLongOrNull()?.let { seconds ->
            return if (seconds <= 0) 0 else seconds.coerceAtMost(Long.MAX_VALUE / 1000) * 1000
        }
        return runCatching {
            (ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - nowMillis).coerceAtLeast(0)
        }.getOrDefault(0)
    }

    fun retryDelay(failure: TransferFailure, retryNumber: Int): Long =
        maxOf(failure.retryAfterMillis, (1000L shl (retryNumber - 1).coerceIn(0, 5)).coerceAtMost(30_000))

    fun requireComplete(expected: Long, received: Long) {
        if (expected >= 0 && expected != received) throw TransferException(
            TransferFailure(TransferFailureKind.NETWORK, "The file ended early or had an unexpected length ($received of $expected bytes)."))
    }
}

internal fun HttpURLConnection.requireTransferSuccess() {
    if (responseCode !in 200..299) throw TransferException(TransferPolicy.http(responseCode, getHeaderField("Retry-After")))
}

internal fun effectiveDownloadConnections(override: Int, default: Int, perHost: Int): Int =
    (override.takeIf { it > 0 } ?: default).coerceIn(1, minOf(6, perHost.coerceIn(1, 6)))
