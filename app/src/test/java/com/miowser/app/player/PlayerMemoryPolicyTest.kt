package com.miowser.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMemoryPolicyTest {
    private val mib = 1024L * 1024

    @Test fun `small heap no longer budgets sixty four megabytes for samples`() {
        val bytes = playerSampleBufferBytes(192 * mib, lowRam = false, availableBytes = 120 * mib)
        assertEquals(16 * mib, bytes.toLong())
    }

    @Test fun `low ram and crowded heaps reserve space for other app work`() {
        assertEquals(16 * mib, playerSampleBufferBytes(512 * mib, true, 400 * mib).toLong())
        assertEquals(6 * mib, playerSampleBufferBytes(192 * mib, false, 36 * mib).toLong())
        assertEquals(4 * mib, playerSampleBufferBytes(192 * mib, true, 10 * mib).toLong())
    }

    @Test fun `large heaps do not expand player sample memory without bound`() {
        assertEquals(32 * mib, playerSampleBufferBytes(2_048 * mib, false, 1_000 * mib).toLong())
        assertTrue(playerSampleBufferBytes(128 * mib, true, 80 * mib) < 16 * mib)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    @Test fun `full sample budget starts playback and released samples permit loading again`() {
        val control = playerLoadControl(8 * 1024 * 1024)
        val player = androidx.media3.exoplayer.analytics.PlayerId.UNSET
        control.onPrepared(player)
        assertEquals(0L, control.getBackBufferDurationUs(player))
        assertFalse(control.retainBackBufferFromKeyframe(player))
        val parameters = androidx.media3.exoplayer.LoadControl.Parameters(player,
            androidx.media3.common.Timeline.EMPTY,
            androidx.media3.exoplayer.source.MediaSource.MediaPeriodId("video"),
            30_000_000, 1_000_000, 1f, true, true, androidx.media3.common.C.TIME_UNSET)
        assertTrue(control.shouldContinueLoading(parameters))
        val allocator = control.allocator
        val allocations = (0 until 128).map { allocator.allocate() }
        try {
            assertFalse(control.shouldContinueLoading(parameters))
            // A high-bitrate stream can hit its byte budget before the five-second rebuffer
            // duration. It must resume playing rather than wait for bytes it cannot load.
            assertTrue(control.shouldStartPlayback(parameters))
            allocations.forEach { allocator.release(it) }
            assertTrue(control.shouldContinueLoading(parameters))
        } finally { control.onReleased(player) }
    }
}
