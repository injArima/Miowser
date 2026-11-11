package com.miowser.app.player

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleInspectionPolicyTest {
    private val timeline = listOf(
        CuesWithTiming(listOf(Cue.Builder().setText("First").build()), 1_000_000, 2_000_000),
        CuesWithTiming(listOf(Cue.Builder().setText("Second").build()), 5_000_000, 1_000_000),
        CuesWithTiming(listOf(Cue.Builder().setText("Third").build()), 8_000_000, 1_000_000)
    )

    @Test
    fun `gaps reveal the next cue without falsely marking it as playing`() {
        assertEquals(SubtitleInspectionFocus(-1, 0), subtitleInspectionFocus(timeline, 0))
        assertEquals(SubtitleInspectionFocus(0, 0), subtitleInspectionFocus(timeline, 1_500))
        assertEquals(SubtitleInspectionFocus(-1, 1), subtitleInspectionFocus(timeline, 3_000))
        assertEquals(SubtitleInspectionFocus(1, 1), subtitleInspectionFocus(timeline, 5_000))
        assertEquals(SubtitleInspectionFocus(-1, 2), subtitleInspectionFocus(timeline, 20_000))
        val subMillisecond = listOf(CuesWithTiming(timeline[0].cues, 500, 2_000_000), timeline[1])
        assertEquals(SubtitleInspectionFocus(-1, 0), subtitleInspectionFocus(subMillisecond, 0))
    }

    @Test
    fun `backward seeks and delayed playback focus the matching subtitle interval`() {
        val style = PlayerSubtitleStyle(delayMs = 500)
        assertEquals(SubtitleInspectionFocus(2, 2), subtitleInspectionFocus(timeline, style.vttPositionMs(8_700)))
        assertEquals(SubtitleInspectionFocus(0, 0), subtitleInspectionFocus(timeline, style.vttPositionMs(1_500)))
        assertEquals(SubtitleInspectionFocus(-1, 1), subtitleInspectionFocus(timeline, style.vttPositionMs(5_000)))
        assertEquals(SubtitleInspectionFocus(-1, -1), subtitleInspectionFocus(emptyList(), 0))
        assertEquals(SubtitleInspectionFocus(-1, 0), subtitleInspectionFocus(timeline, null))
    }

    @Test
    fun `focus offset leaves the entire line visible in a short viewport`() {
        assertEquals(-84, subtitleInspectionScrollOffset(300, 48, 0.28f))
        assertEquals(-20, subtitleInspectionScrollOffset(100, 80, 0.28f))
        assertEquals(0, subtitleInspectionScrollOffset(100, 150, 0.28f))
        assertEquals(0, subtitleInspectionScrollOffset(0, 48, 0.28f))
    }

    @Test
    fun `delay draft accepts negative values clamps bounds and does not apply incomplete input`() {
        assertEquals(-50L, parseSubtitleDelay("-50"))
        assertEquals(0L, parseSubtitleDelay("0"))
        assertEquals(MAX_SUBTITLE_DELAY_MS, parseSubtitleDelay("99999999"))
        assertEquals(-MAX_SUBTITLE_DELAY_MS, parseSubtitleDelay("-9999999"))
        assertNull(parseSubtitleDelay(""))
        assertNull(parseSubtitleDelay("-"))
        assertNull(parseSubtitleDelay("1.5"))
        assertEquals("+50 ms", subtitleDelayLabel(50))
        assertEquals("-50 ms", subtitleDelayLabel(-50))
    }

    @Test
    fun `assisted selections replace the proposed delay without changing the saved draft`() {
        val original = SubtitleDelayDraft("250")
        val first = original.selectCue(5_000, 4_000_000)
        val second = first.selectCue(5_000, 6_000_000)
        assertEquals(250L, original.valueMs)
        assertEquals(1_000L, first.valueMs)
        assertEquals(-1_000L, second.valueMs)
        assertEquals(6_000_000L, second.cueStartUs)
    }

    @Test
    fun `held cue preview leaves the pending selection unchanged on release`() {
        val pending = SubtitleDelayDraft("0").selectCue(8_000, 7_000_000)
        val preview = pending.selectCue(8_000, 9_000_000)
        assertEquals(-1_000L, preview.valueMs)
        assertEquals(1_000L, pending.valueMs)
        assertEquals(7_000_000L, pending.cueStartUs)
    }

    @Test
    fun `manual edits and steps replace cue selection and remain bounded`() {
        val selected = SubtitleDelayDraft("0").selectCue(5_000, 4_000_000)
        assertEquals(950L, selected.step(-50).valueMs)
        assertNull(selected.step(-50).cueStartUs)
        assertEquals(-125L, selected.edit("-125").valueMs)
        assertNull(selected.edit("-125").cueStartUs)
        assertNull(selected.edit("-").valueMs)
        assertEquals(MAX_SUBTITLE_DELAY_MS, SubtitleDelayDraft(MAX_SUBTITLE_DELAY_MS.toString()).step(50).valueMs)
        assertEquals(-MAX_SUBTITLE_DELAY_MS, SubtitleDelayDraft((-MAX_SUBTITLE_DELAY_MS).toString()).step(-50).valueMs)
    }
}
