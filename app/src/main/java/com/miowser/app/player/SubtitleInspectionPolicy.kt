package com.miowser.app.player

import androidx.media3.extractor.text.CuesWithTiming
import kotlin.math.roundToInt

internal data class SubtitleInspectionFocus(val activeIndex: Int, val anchorIndex: Int)

/** Gaps have no selected cue, but seeking into one should still reveal the next line. */
internal fun subtitleInspectionFocus(timeline: List<CuesWithTiming>, positionMs: Long?): SubtitleInspectionFocus {
    if (timeline.isEmpty()) return SubtitleInspectionFocus(-1, -1)
    if (positionMs == null) return SubtitleInspectionFocus(-1, 0)
    val active = subtitleCueIndexAt(timeline, positionMs)
    if (active >= 0) return SubtitleInspectionFocus(active, active)
    val timeUs = positionMs * 1_000L
    var low = 0
    var high = timeline.size
    while (low < high) {
        val middle = (low + high).ushr(1)
        if (timeline[middle].startTimeUs <= timeUs) low = middle + 1 else high = middle
    }
    return SubtitleInspectionFocus(-1, low.coerceAtMost(timeline.lastIndex))
}

/** Negative LazyList offset leaves room above the line without pushing its end out of view. */
internal fun subtitleInspectionScrollOffset(viewportPx: Int, rowPx: Int, anchorFraction: Float): Int =
    -((viewportPx.coerceAtLeast(0) * anchorFraction).roundToInt()
        .coerceIn(0, (viewportPx - rowPx).coerceAtLeast(0)))

internal fun parseSubtitleDelay(input: String): Long? = input.toLongOrNull()
    ?.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)

internal fun subtitleDelayLabel(delayMs: Long): String = "${if (delayMs > 0) "+" else ""}$delayMs ms"

/** Editing and cue previews never change playback or persisted timing until confirmation. */
internal data class SubtitleDelayDraft(val input: String, val cueStartUs: Long? = null) {
    val valueMs: Long? get() = parseSubtitleDelay(input)

    fun selectCue(anchorMs: Long, startUs: Long): SubtitleDelayDraft =
        SubtitleDelayDraft(subtitleDelayForCue(anchorMs, startUs).toString(), startUs)

    fun edit(value: String): SubtitleDelayDraft = SubtitleDelayDraft(value)

    fun step(deltaMs: Long): SubtitleDelayDraft =
        SubtitleDelayDraft(((valueMs ?: 0L) + deltaMs).coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS).toString())
}
