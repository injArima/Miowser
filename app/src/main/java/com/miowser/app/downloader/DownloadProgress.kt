package com.miowser.app.downloader

fun Download.progressPercent(): Int? {
    if (totalBytes <= 0L) return null
    return ((bytesDownloaded.coerceIn(0L, totalBytes) * 100L) / totalBytes).toInt()
}

fun formatRemainingTime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(1L)
    return when {
        seconds < 60L -> "${seconds}s remaining"
        seconds < 3_600L -> {
            val minutes = (seconds + 59L) / 60L
            "${minutes} min remaining"
        }
        else -> {
            val hours = seconds / 3_600L
            val minutes = ((seconds % 3_600L) + 59L) / 60L
            if (minutes == 0L || minutes == 60L) {
                "${hours + if (minutes == 60L) 1L else 0L} hr remaining"
            } else {
                "${hours} hr ${minutes} min remaining"
            }
        }
    }
}

fun Download.remainingTimeLabel(): String? = estimatedSecondsRemaining()?.let(::formatRemainingTime)

fun formatSpentTime(totalSeconds: Long): String {
    val seconds = totalSeconds.coerceAtLeast(0L)
    return when {
        seconds < 60L -> "${seconds}s spent"
        seconds < 3_600L -> "${seconds / 60L}m ${seconds % 60L}s spent"
        else -> "${seconds / 3_600L}h ${(seconds % 3_600L) / 60L}m spent"
    }
}

fun Download.timeProgressLabel(elapsedSeconds: Long = elapsedActiveMillis() / 1_000L): String? {
    val remaining = remainingTimeLabel()
    val spent = formatSpentTime(elapsedSeconds)
    return when {
        remaining != null -> "$remaining / $spent"
        status != DownloadStatus.QUEUED -> spent
        else -> null
    }
}


data class DownloadPresentation(val title: String, val status: DownloadStatus,
    val checking: Boolean, val percent: Int?, val timing: String?, val transfer: String? = null)
fun Download.presentation(elapsedSeconds: Long = elapsedActiveMillis() / 1000): DownloadPresentation =
    DownloadPresentation(displayName, status, recoveryChecking, progressPercent(), timeProgressLabel(elapsedSeconds), transferInformation())

fun formatTransferBytes(bytes: Long): String {
    val value = bytes.coerceAtLeast(0)
    return when {
        value < 1024 -> "$value B"
        value < 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f KB", value / 1024.0)
        value < 1024L * 1024 * 1024 -> String.format(java.util.Locale.ROOT, "%.1f MB", value / (1024.0 * 1024))
        else -> String.format(java.util.Locale.ROOT, "%.2f GB", value / (1024.0 * 1024 * 1024))
    }
}

fun Download.transferInformation(): String? {
    retryWaiting?.let { return it }
    if (kind == DownloadKind.WEBVIEW_RESOURCE) return null
    if (status !in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.PAUSED, DownloadStatus.MUXING)) return null
    return buildString {
        if (status == DownloadStatus.DOWNLOADING) { append("${formatTransferBytes(bytesPerSecond)}/s"); append(" • ") }
        if (kind == DownloadKind.DIRECT_FILE) {
            append(formatTransferBytes(bytesDownloaded))
            if (totalBytes >= 0) append(" / ${formatTransferBytes(totalBytes)}")
        } else {
            append("${formatTransferBytes(transferredBytes)} transferred")
            if (totalBytes > 0) append(" • $bytesDownloaded/$totalBytes segments")
        }
        if (status == DownloadStatus.DOWNLOADING) append(" • $activeConnections active connections")
        if (retryCount > 0) append(" • $retryCount retries")
    }
}
