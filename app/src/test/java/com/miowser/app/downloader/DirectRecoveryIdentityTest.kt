package com.miowser.app.downloader

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectRecoveryIdentityTest {
    private val saved = DownloadRecoveryPlan(
        downloadId = 7L,
        transferKind = RecoveryTransferKind.DIRECT_RANGES,
        sourceFingerprint = "first-64k-sha256",
        etag = "\"stable-file\"",
        lastModified = null,
        totalLength = 10_000L,
        outputFileName = "video.mp4",
        mimeType = "video/mp4",
        itemFingerprint = null,
        itemCount = 2,
        ranges = listOf(RecoveryRange(0, 4_999, 3_000), RecoveryRange(5_000, 9_999, 2_000))
    )

    @Test fun renewedUrlCanReuseVerifiedPartialFile() {
        assertTrue(canReuseDirectRanges(saved, 10_000, "first-64k-sha256",
            "\"stable-file\"", null, "video.mp4", 10_000))
    }

    @Test fun changedRepresentationMustRestart() {
        assertFalse(canReuseDirectRanges(saved, 10_000, "different-content",
            "\"stable-file\"", null, "video.mp4", 10_000))
        assertFalse(canReuseDirectRanges(saved, 10_000, "first-64k-sha256",
            "\"changed-file\"", null, "video.mp4", 10_000))
        assertFalse(canReuseDirectRanges(saved, 10_000, "first-64k-sha256",
            "\"stable-file\"", null, "video.mp4", 2_000))
    }
}
