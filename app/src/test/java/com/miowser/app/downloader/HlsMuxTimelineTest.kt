package com.miowser.app.downloader

import org.junit.Assert.assertEquals
import org.junit.Test

class HlsMuxTimelineTest {
    @Test
    fun `preserves audio lead and segment spacing`() {
        val clock = HlsMuxTimeline()
        val origin = 900_000L
        assertEquals(0L, clock.timestamp(origin, video = true, segmentOriginUs = origin))
        assertEquals(80_000L, clock.timestamp(origin + 80_000L, video = false, segmentOriginUs = origin))
        assertEquals(9_960_000L, clock.timestamp(origin + 9_960_000L, video = true, segmentOriginUs = origin))
        clock.finishSegment(10.0)
        assertEquals(10_000_000L, clock.timestamp(50_000L, video = true, segmentOriginUs = 50_000L))
        assertEquals(10_080_000L, clock.timestamp(130_000L, video = false, segmentOriginUs = 50_000L))
    }

    @Test
    fun `keeps each track monotonic when segment duration is slightly short`() {
        val clock = HlsMuxTimeline()
        assertEquals(1_050_000L, clock.timestamp(1_050_000L, 0L, video = false))
        clock.finishSegment(1.0)
        assertEquals(1_050_001L, clock.timestamp(0L, 0L, video = false))
    }

    @Test
    fun `preserves out of order video presentation timestamps for B frames`() {
        val clock = HlsMuxTimeline()
        assertEquals(0L, clock.timestamp(0L, 0L, video = true))
        assertEquals(80_000L, clock.timestamp(80_000L, 0L, video = true))
        assertEquals(40_000L, clock.timestamp(40_000L, 0L, video = true))
        clock.finishSegment(0.1)
        assertEquals(100_000L, clock.timestamp(900_000L, 900_000L, video = true))
    }
}
