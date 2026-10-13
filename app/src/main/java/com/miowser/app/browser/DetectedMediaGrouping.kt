package com.miowser.app.browser

import com.miowser.app.downloader.rendererResourceKind
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType

internal enum class DetectorCategory(val label: String, val heading: String) {
    VIDEOS("Videos", "Videos"),
    AUDIO("Audio", "Audio"),
    SUBTITLES("Subtitles", "Subtitles & captions"),
    OTHER("Other", "Other files")
}

internal fun detectorCategory(item: DetectedMedia): DetectorCategory = when (item.type) {
    MediaType.AUDIO -> DetectorCategory.AUDIO
    MediaType.SUBTITLE -> DetectorCategory.SUBTITLES
    MediaType.UNKNOWN -> if (rendererResourceKind(item.url) != null) DetectorCategory.VIDEOS
        else DetectorCategory.OTHER
    else -> DetectorCategory.VIDEOS
}

internal fun detectorGroups(items: List<DetectedMedia>): Map<DetectorCategory, List<DetectedMedia>> =
    DetectorCategory.entries.mapNotNull { category ->
        items.filter { detectorCategory(it) == category }.takeIf { it.isNotEmpty() }
            ?.let { category to it }
    }.toMap()
