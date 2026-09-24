package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class HubVideoCardTest {
    @Test
    fun `watch progress is clamped to the media duration`() {
        assertEquals(0f, mediaWatchProgress(-1_000L, 10_000L))
        assertEquals(0.5f, mediaWatchProgress(5_000L, 10_000L))
        assertEquals(1f, mediaWatchProgress(12_000L, 10_000L))
    }

    @Test
    fun `unknown duration has no watch progress`() {
        assertEquals(0f, mediaWatchProgress(5_000L, 0L))
    }

    @Test
    fun `formats short and long durations`() {
        assertEquals("5:07", formatMediaDuration(307_000L))
        assertEquals("2:03:04", formatMediaDuration(7_384_000L))
    }
}
