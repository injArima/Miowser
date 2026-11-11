package com.miowser.app.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import java.io.File
import java.io.FileInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(UnstableApi::class)
class EmbeddedSubtitleTimelineTest {
    private fun cue(text: String) = listOf(Cue.Builder().setText(text).build())

    @Test fun skippingVideoSamplesPreservesPeekedBytesAndSegmentOffsets() {
        val file = File.createTempFile("subtitle-input", ".bin")
        val data = ByteArray(256 * 1024) { (it * 31).toByte() }
        try {
            file.outputStream().use { it.write(ByteArray(100)); it.write(data) }
            FileInputStream(file).use { stream ->
                val input = SubtitleFileInput(stream, 100, data.size.toLong()) {}
                input.seek(0)
                val peek = ByteArray(20_000)
                input.peekFully(peek, 0, peek.size)
                input.skipFully(10_000)
                val sample = ByteArray(32)
                input.readFully(sample, 0, sample.size)
                assertArrayEquals(data.copyOfRange(10_000, 10_032), sample)
                input.skipFully(100_000)
                input.readFully(sample, 0, sample.size)
                assertArrayEquals(data.copyOfRange(110_032, 110_064), sample)
                input.seek(64)
                input.readFully(sample, 0, sample.size)
                assertArrayEquals(data.copyOfRange(64, 96), sample)
                input.skipFully(data.size - input.position.toInt())
                assertEquals(C.RESULT_END_OF_INPUT, input.read(sample, 0, sample.size))
            }
        } finally {
            file.delete()
        }
    }

    @Test fun overlappingDialogueAndSignsRemainVisibleTogetherAfterDelay() {
        val timeline = normalizeEmbeddedSubtitleCues(listOf(
            CuesWithTiming(cue("Dialogue"), 0, 3_000_000),
            CuesWithTiming(cue("Sign"), 1_000_000, 4_000_000)
        ), Format.CUE_REPLACEMENT_BEHAVIOR_MERGE)
        assertEquals(listOf("Dialogue"), externalWebvttCuesAt(timeline, 500).map { it.text.toString() })
        assertEquals(listOf("Dialogue", "Sign"), externalWebvttCuesAt(timeline, 2_000).map { it.text.toString() })
        assertEquals(listOf("Sign"), externalWebvttCuesAt(timeline, 4_000).map { it.text.toString() })
        assertTrue(externalWebvttCuesAt(timeline, 5_000).isEmpty())
        val delayed = PlayerSubtitleStyle(delayMs = 2_000)
        assertTrue(externalWebvttCuesAt(timeline, delayed.vttPositionMs(1_000)).isEmpty())
        assertEquals(listOf("Dialogue", "Sign"),
            externalWebvttCuesAt(timeline, delayed.vttPositionMs(4_000)).map { it.text.toString() })
    }

    @Test fun replacementTracksClearAtTheNextEmptyCue() {
        val timeline = normalizeEmbeddedSubtitleCues(listOf(
            CuesWithTiming(cue("First"), 0, C.TIME_UNSET),
            CuesWithTiming(cue("Second"), 1_000_000, C.TIME_UNSET),
            CuesWithTiming(emptyList(), 2_000_000, C.TIME_UNSET)
        ), Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE)
        assertEquals(listOf("First"), externalWebvttCuesAt(timeline, 500).map { it.text.toString() })
        assertEquals(listOf("Second"), externalWebvttCuesAt(timeline, 1_500).map { it.text.toString() })
        assertTrue(externalWebvttCuesAt(timeline, 2_000).isEmpty())
    }
}
