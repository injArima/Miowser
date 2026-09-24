package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedVideoCodecTest {
    @Test fun `log preserves all copyable details`() {
        val record = FailedVideoRecord(
            "one", "clip.m3u8", "Episode 1 日本語", "example.com",
            "https://example.com/watch?x=1&y=2", 1_000L
        )
        assertEquals(listOf(record), FailedVideoCodec.decode(FailedVideoCodec.encode(listOf(record))))
    }

    @Test fun `copy action timestamp extends the ninety day lifetime`() {
        val day = 24L * 60L * 60L * 1_000L
        val record = FailedVideoRecord("one", "clip", "Page", "example.com", "https://example.com", 0L)
        assertTrue(FailedVideoCodec.retained(listOf(record), 91L * day).isEmpty())
        assertEquals(listOf(record.copy(lastActionAt = 90L * day)),
            FailedVideoCodec.retained(listOf(record.copy(lastActionAt = 90L * day)), 91L * day))
    }
}
