package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectedMediaMergeTest {
    @Test
    fun `network and element facts merge without losing exact metadata`() {
        val network = media(
            isConfirmed = true,
            sizeBytes = 5_000,
            durationSeconds = null,
            resolution = null,
            responseFileName = "movie.mp4",
            mimeType = "video/mp4"
        )
        val element = media(
            isConfirmed = false,
            sizeBytes = null,
            durationSeconds = 123.5,
            resolution = "1920x1080"
        )

        val merged = mergeDetectedMedia(network, element)

        assertTrue(merged.isConfirmed)
        assertEquals(5_000L, merged.sizeBytes)
        assertFalse(merged.isSizeApproximate)
        assertEquals(123.5, merged.durationSeconds!!, 0.001)
        assertEquals("1920x1080", merged.resolution)
        assertEquals("movie.mp4", merged.responseFileName)
        assertEquals("video/mp4", merged.mimeType)
    }

    @Test
    fun `exact size wins over a later estimate and larger resolution is retained`() {
        val estimate = media(sizeBytes = 9_000, approximate = true, resolution = "1920x1080")
        val exact = media(sizeBytes = 8_000, approximate = false, resolution = "640x360")

        val merged = mergeDetectedMedia(estimate, exact)

        assertEquals(8_000L, merged.sizeBytes)
        assertFalse(merged.isSizeApproximate)
        assertEquals("1920x1080", merged.resolution)
    }

    private fun media(
        isConfirmed: Boolean = true,
        sizeBytes: Long? = null,
        approximate: Boolean = false,
        durationSeconds: Double? = null,
        resolution: String? = null,
        responseFileName: String? = null,
        mimeType: String? = null
    ) = DetectedMedia(
        url = "https://example.com/movie.mp4",
        pageUrl = "https://example.com/watch",
        type = MediaType.MP4,
        isConfirmed = isConfirmed,
        sizeBytes = sizeBytes,
        isSizeApproximate = approximate,
        durationSeconds = durationSeconds,
        variantCount = null,
        resolution = resolution,
        isEncrypted = false,
        pageTitle = "Movie",
        responseFileName = responseFileName,
        mimeType = mimeType
    )
}
