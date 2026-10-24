package com.miowser.app.player

import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType

internal const val DETECTED_VTT_TRACK_ID = "miowser-vtt:"

/** The caller supplies one tab's detections; embedded players can report a different frame URL. */
internal fun detectedSubtitleTracks(video: DetectedMedia, detections: List<DetectedMedia>): List<DetectedMedia> {
    val candidates = detections.asSequence()
        .filter { it.url != video.url && it.type == MediaType.SUBTITLE }
        .filter { subtitle ->
            val path = subtitle.url.substringBefore('?').substringBefore('#')
            listOf(".vtt", ".srt").any { path.endsWith(it, ignoreCase = true) ||
                subtitle.responseFileName?.endsWith(it, ignoreCase = true) == true } ||
                subtitle.mimeType?.substringBefore(';')?.trim()?.lowercase() in
                    setOf("text/vtt", "application/x-subrip", "text/srt", "application/srt")
        }
        .filter { it.url.startsWith("https://", true) || it.url.startsWith("http://", true) }
        .distinctBy { it.url }
        .take(24)
        .toList()
    val samePage = candidates.filter { it.pageUrl == video.pageUrl }
    return samePage.ifEmpty { candidates }
}

internal fun detectedSubtitleLabel(item: DetectedMedia): String =
    item.responseFileName?.takeIf(String::isNotBlank)
        ?: item.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
            .takeIf(String::isNotBlank)
        ?: "Subtitle file"
