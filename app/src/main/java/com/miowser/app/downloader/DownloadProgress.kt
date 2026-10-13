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
    val checking: Boolean, val percent: Int?, val timing: String?)
fun Download.presentation(elapsedSeconds: Long = elapsedActiveMillis() / 1000): DownloadPresentation =
    DownloadPresentation(displayName, status, recoveryChecking, progressPercent(), timeProgressLabel(elapsedSeconds))
