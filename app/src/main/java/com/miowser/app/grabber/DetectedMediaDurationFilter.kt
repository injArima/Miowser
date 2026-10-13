package com.miowser.app.grabber

/** Unknown durations cannot pass a non-zero minimum; they remain visible when filtering is off. */
fun DetectedMedia.meetsMinimumDuration(minimumSeconds: Int): Boolean {
    if (type == MediaType.SUBTITLE) return true
    if (minimumSeconds <= 0) return true
    val duration = durationSeconds ?: return false
    return duration.isFinite() && duration >= minimumSeconds
}
