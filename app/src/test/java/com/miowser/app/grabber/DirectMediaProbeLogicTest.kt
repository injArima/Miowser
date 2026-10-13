package com.miowser.app.grabber

import java.io.ByteArrayInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DirectMediaProbeLogicTest {
    @Test
    fun `successful matching HEAD is accepted without body sampling`() {
        assertEquals(HeadProbeAction.ACCEPT, decideHeadProbe(200, "video/mp4", MediaType.MP4))
        assertEquals(HeadProbeAction.ACCEPT, decideHeadProbe(200, "application/mp4", MediaType.MP4))
        assertEquals(HeadProbeAction.ACCEPT, decideHeadProbe(200, "text/plain", MediaType.SUBTITLE))
    }

    @Test
    fun `generic missing and rejected HEAD responses fall back to bounded GET`() {
        assertEquals(HeadProbeAction.SAMPLE_WITH_GET, decideHeadProbe(200, "application/octet-stream", MediaType.MP4))
        assertEquals(HeadProbeAction.SAMPLE_WITH_GET, decideHeadProbe(200, null, MediaType.MP4))
        assertEquals(HeadProbeAction.SAMPLE_WITH_GET, decideHeadProbe(405, "text/html", MediaType.MP4))
    }

    @Test
    fun `definitive missing or contradictory HEAD is rejected`() {
        assertEquals(HeadProbeAction.REJECT, decideHeadProbe(404, null, MediaType.MP4))
        assertEquals(HeadProbeAction.REJECT, decideHeadProbe(200, "text/html", MediaType.MP4))
        assertEquals(HeadProbeAction.REJECT, decideHeadProbe(200, "audio/mpeg", MediaType.MP4))
    }

    @Test
    fun `sample signature confirms generic direct media`() {
        assertEquals(MediaType.MP4, resolveSampledMediaType(MediaType.MP4, "application/octet-stream", "mp4"))
        assertEquals(MediaType.WEBM, resolveSampledMediaType(MediaType.WEBM, null, "webm"))
    }

    @Test
    fun `contradictory sampled response is rejected`() {
        assertNull(resolveSampledMediaType(MediaType.MP4, "text/html", null))
        assertNull(resolveSampledMediaType(MediaType.MP4, "application/octet-stream", "webm"))
    }

    @Test
    fun `bounded reader never consumes beyond its cap`() {
        val input = ByteArrayInputStream(ByteArray(100) { it.toByte() })
        val result = input.readAtMost(16)

        assertArrayEquals(ByteArray(16) { it.toByte() }, result)
        assertEquals(16, input.available().let { 100 - it })
    }

    @Test
    fun `size logic distinguishes full representation from sampled range`() {
        assertEquals(5_000L, exactProbeSize(200, true, null, 5_000L))
        assertEquals(5_000L, exactProbeSize(206, false, "bytes 0-4999/5000", 5_000L))
        assertEquals(5_000L, exactProbeSize(200, false, null, 5_000L))
        assertNull(exactProbeSize(206, false, null, 65_536L))
        assertNull(exactProbeSize(405, true, null, 123L))
    }

    @Test
    fun `content range parser validates boundaries and preserves unknown totals`() {
        assertEquals(ProbeContentRange(0, 99, 1_000), parseProbeContentRange("bytes 0-99/1000"))
        assertEquals(ProbeContentRange(50, 99, null), parseProbeContentRange("BYTES 50-99/*"))
        assertNull(parseProbeContentRange("bytes 100-99/1000"))
        assertNull(parseProbeContentRange("bytes 0-1000/1000"))
        assertNull(parseProbeContentRange("items 0-9/10"))
    }

    @Test
    fun `manifest size prefers exact declarations then weighted samples then bitrate`() {
        assertEquals(
            ProbeSizeEstimate(12_345L, false),
            estimateManifestSize(100.0, 12_345L, 9_000_000L, listOf(1_000L to 2.0))
        )
        assertEquals(
            ProbeSizeEstimate(52_500L, true),
            estimateManifestSize(100.0, null, null, listOf(1_000L to 2.0, 500L to 1.0))
        )
        assertEquals(
            ProbeSizeEstimate(12_875_000L, true),
            estimateManifestSize(100.0, null, 1_000_000L, emptyList())
        )
        assertEquals(ProbeSizeEstimate(null, false), estimateManifestSize(null, null, 1_000_000L, emptyList()))
    }
}
