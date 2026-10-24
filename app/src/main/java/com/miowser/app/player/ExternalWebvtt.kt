package com.miowser.app.player

import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import androidx.media3.extractor.text.webvtt.WebvttParser

/** Media3's WebVTT parser preserves cue timing, overlap, placement and text markup. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun parseExternalWebvtt(text: String): List<CuesWithTiming> {
    val cues = mutableListOf<CuesWithTiming>()
    WebvttParser().parse(text.toByteArray(Charsets.UTF_8), SubtitleParser.OutputOptions.allCues()) {
        cues += it
    }
    return cues.sortedBy { it.startTimeUs }
}

/** Uses the same timed-cue representation for page-detected WebVTT and SubRip files. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun parseExternalSubtitle(text: String): List<CuesWithTiming> {
    val normalized = text.removePrefix("\uFEFF").trimStart()
    if (normalized.startsWith("WEBVTT")) return parseExternalWebvtt(normalized)
    val cues = mutableListOf<CuesWithTiming>()
    SubripParser().parse(normalized.toByteArray(Charsets.UTF_8), SubtitleParser.OutputOptions.allCues()) {
        cues += it
    }
    return cues.sortedBy { it.startTimeUs }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun externalWebvttCuesAt(timeline: List<CuesWithTiming>, positionMs: Long): List<Cue> {
    if (timeline.isEmpty()) return emptyList()
    val timeUs = positionMs * 1_000L
    var left = 0
    var right = timeline.size
    while (left < right) {
        val middle = (left + right) ushr 1
        if (timeline[middle].startTimeUs <= timeUs) left = middle + 1 else right = middle
    }
    val interval = timeline.getOrNull(left - 1) ?: return emptyList()
    return interval.cues.takeIf { timeUs < interval.endTimeUs }.orEmpty()
}

/** Repositions text cues even when a WebVTT file sets its own line, as many sites do. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun positionSubtitleCues(cues: List<Cue>, bottomPadding: Float): List<Cue> {
    val textCues = cues.count { it.bitmap == null }
    var textIndex = 0
    return cues.map { cue ->
        if (cue.bitmap != null) return@map cue
        val fromBottom = bottomPadding.coerceIn(0.03f, 0.35f) +
            (textCues - ++textIndex) * 0.07f
        cue.buildUpon()
            .setLine((1f - fromBottom).coerceIn(0f, 1f), Cue.LINE_TYPE_FRACTION)
            .setLineAnchor(Cue.ANCHOR_TYPE_END)
            .build()
    }
}
