package com.miowser.app.browser

import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectedMediaGroupingTest {
    private fun media(url: String, type: MediaType) = DetectedMedia(
        url = url,
        pageUrl = "https://site.example/watch",
        type = type,
        isConfirmed = true,
        sizeBytes = null,
        isSizeApproximate = false,
        durationSeconds = null,
        variantCount = null,
        resolution = null,
        isEncrypted = false,
        pageTitle = null
    )

    @Test fun onlyPresentCategoriesAppearAndUnknownBlobStaysWithVideos() {
        val video = media("https://cdn.example/video.m3u8", MediaType.HLS)
        val blob = media("blob:https://site.example/123", MediaType.UNKNOWN)
        val subtitle = media("https://cdn.example/captions.vtt", MediaType.SUBTITLE)
        val groups = detectorGroups(listOf(video, blob, subtitle))

        assertEquals(listOf(DetectorCategory.VIDEOS, DetectorCategory.SUBTITLES), groups.keys.toList())
        assertEquals(listOf(video, blob), groups[DetectorCategory.VIDEOS])
        assertEquals(listOf(subtitle), groups[DetectorCategory.SUBTITLES])
    }
}
