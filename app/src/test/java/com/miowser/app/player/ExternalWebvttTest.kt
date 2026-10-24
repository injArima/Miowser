package com.miowser.app.player

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalWebvttTest {
    @Test
    fun `position setting overrides a VTT cue line and preserves a stack`() {
        val first = Cue.Builder().setText("First")
            .setLine(0.15f, Cue.LINE_TYPE_FRACTION).build()
        val second = Cue.Builder().setText("Second")
            .setLine(0.25f, Cue.LINE_TYPE_FRACTION).build()

        val low = positionSubtitleCues(listOf(first, second), 0.06f)
        val high = positionSubtitleCues(listOf(first, second), 0.25f)

        assertEquals(0.87f, low[0].line, 0.001f)
        assertEquals(0.94f, low[1].line, 0.001f)
        assertEquals(0.68f, high[0].line, 0.001f)
        assertEquals(0.75f, high[1].line, 0.001f)
        assertEquals(Cue.ANCHOR_TYPE_END, high[0].lineAnchor)
    }

    @Test
    fun `selected cue follows playback position including seeks and gaps`() {
        val first = Cue.Builder().setText("First").build()
        val second = Cue.Builder().setText("Second").build()
        val timeline = listOf(
            CuesWithTiming(listOf(first), 1_000_000, 2_000_000),
            CuesWithTiming(listOf(second), 4_000_000, 2_000_000)
        )

        assertEquals(emptyList<Cue>(), externalWebvttCuesAt(timeline, 999))
        assertEquals(listOf(first), externalWebvttCuesAt(timeline, 1_000))
        assertEquals(emptyList<Cue>(), externalWebvttCuesAt(timeline, 3_000))
        assertEquals(listOf(second), externalWebvttCuesAt(timeline, 4_000))
        assertEquals(listOf(first), externalWebvttCuesAt(timeline, 1_500))
        assertEquals(emptyList<Cue>(), externalWebvttCuesAt(timeline, 6_000))
    }
}
