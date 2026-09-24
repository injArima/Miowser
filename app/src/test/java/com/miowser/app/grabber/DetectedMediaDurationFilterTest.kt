package com.miowser.app.grabber

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedMediaDurationFilterTest {
    @Test fun `disabled filter keeps known and unknown media`() {
        assertTrue(media(null).meetsMinimumDuration(0))
        assertTrue(media(2.0).meetsMinimumDuration(0))
    }

    @Test fun `enabled filter requires known duration meeting threshold`() {
        assertFalse(media(null).meetsMinimumDuration(60))
        assertFalse(media(59.9).meetsMinimumDuration(60))
        assertTrue(media(60.0).meetsMinimumDuration(60))
    }

    @Test fun `subtitle remains visible because it has no intrinsic video length`() {
        assertTrue(media(null, MediaType.SUBTITLE).meetsMinimumDuration(1_800))
    }

    private fun media(duration: Double?, type: MediaType = MediaType.MP4) = DetectedMedia(
        url = "https://example.com/video.mp4", pageUrl = "https://example.com", type = type,
        isConfirmed = true, sizeBytes = null, isSizeApproximate = false,
        durationSeconds = duration, variantCount = null, resolution = null,
        isEncrypted = false, pageTitle = null
    )
}
