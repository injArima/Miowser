package com.miowser.app.library

/** The player validates parsed cues before saving; this also rejects unrelated cached documents. */
internal fun durableSubtitleFormat(text: String): String? {
    val normalized = text.removePrefix("\uFEFF").trimStart()
    if (normalized.startsWith("WEBVTT") &&
        normalized.drop(6).firstOrNull()?.let { it.isWhitespace() } != false) return "vtt"
    val timing = Regex("(?m)^\\s*\\d{1,3}:\\d{2}:\\d{2}[,.]\\d{3}\\s+-->\\s+\\d{1,3}:\\d{2}:\\d{2}[,.]\\d{3}")
    return "srt".takeIf { timing.containsMatchIn(normalized) }
}
