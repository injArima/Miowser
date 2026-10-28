package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsRenditionGroupingTest {
    private fun rendition(
        url: String,
        resolution: String,
        duration: Double = 600.0,
        encrypted: Boolean = false,
        variantCount: Int? = null
    ) = DetectedMedia(
        url = url,
        pageUrl = "https://example.com/watch/episode-1",
        type = MediaType.HLS,
        isConfirmed = true,
        sizeBytes = null,
        isSizeApproximate = false,
        durationSeconds = duration,
        variantCount = variantCount,
        resolution = resolution,
        isEncrypted = encrypted,
        pageTitle = "Episode 1"
    )

    @Test fun `groups matching leaf playlists into one adaptive item`() {
        val grouped = groupHlsRenditions(listOf(
            rendition("https://cdn.example.com/video/1080p/index.m3u8?token=one", "1920x1080"),
            rendition("https://cdn.example.com/video/720p/index.m3u8?token=two", "1280x720")
        ))

        assertEquals(1, grouped.size)
        assertEquals(2, grouped.single().variantCount)
        assertEquals(listOf("1920x1080", "1280x720"),
            grouped.single().hlsRenditions.map(HlsRendition::resolution))
    }

    @Test fun `does not group unrelated durations or encrypted leaves`() {
        val durationMismatch = groupHlsRenditions(listOf(
            rendition("https://cdn.example.com/video/1080p/index.m3u8", "1920x1080", 600.0),
            rendition("https://cdn.example.com/video/720p/index.m3u8", "1280x720", 300.0)
        ))
        assertEquals(2, durationMismatch.size)

        val encrypted = groupHlsRenditions(listOf(
            rendition("https://cdn.example.com/video/1080p/index.m3u8", "1920x1080", encrypted = true),
            rendition("https://cdn.example.com/video/720p/index.m3u8", "1280x720", encrypted = true)
        ))
        assertTrue(encrypted.all { it.hlsRenditions.isEmpty() })
    }

    @Test fun `leaves a real master playlist untouched`() {
        val master = rendition("https://cdn.example.com/video/master.m3u8", "1920x1080", variantCount = 4)
        assertEquals(listOf(master), groupHlsRenditions(listOf(master)))
    }

    @Test fun `does not group media detected on different query-addressed pages`() {
        val first = rendition("https://cdn.example.com/video/1080p/index.m3u8", "1920x1080")
        val second = rendition("https://cdn.example.com/video/720p/index.m3u8", "1280x720")
            .copy(pageUrl = "https://example.com/watch/episode-1?part=2")
        assertEquals(2, groupHlsRenditions(listOf(first, second)).size)
    }
}
