package com.miowser.app.browser

import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class BlobMediaOptionsTest {
    private fun media(url: String, type: MediaType, confirmed: Boolean, at: Long) = DetectedMedia(
        url = url,
        pageUrl = "https://site.example/watch",
        type = type,
        isConfirmed = confirmed,
        sizeBytes = null,
        isSizeApproximate = false,
        durationSeconds = null,
        variantCount = null,
        resolution = null,
        isEncrypted = false,
        pageTitle = null,
        finalUrl = if (confirmed) url else null,
        detectedAt = at
    )

    @Test fun offersOnlyVerifiedFetchableMediaWithoutAssumingItIsTheBlobSource() {
        val blob = media("blob:https://site.example/id", MediaType.UNKNOWN, false, 100)
        val manifest = media("https://cdn.example/video.m3u8", MediaType.HLS, true, 105)
        val file = media("https://cdn.example/video.mp4", MediaType.MP4, true, 80)
        val guess = media("https://cdn.example/guess.mp4", MediaType.MP4, false, 101)
        val domOnly = media("https://cdn.example/dom.mp4", MediaType.MP4, true, 103).copy(finalUrl = null)
        val segment = media("https://cdn.example/segment.ts", MediaType.UNKNOWN, true, 102)
        assertEquals(listOf(manifest, file), verifiedSourcesForBlob(blob, listOf(blob, file, domOnly, segment, guess, manifest)))
    }
}
