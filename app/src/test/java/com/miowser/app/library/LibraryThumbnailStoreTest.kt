package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LibraryThumbnailStoreTest {
    @Test fun visibleFrameCaptureLeavesRoomForTheUi() {
        assertFalse(thumbnailCaptureHasHeadroom(1_280, 720, 10L * 1024 * 1024))
        assertTrue(thumbnailCaptureHasHeadroom(640, 360, 16L * 1024 * 1024))
        assertFalse(thumbnailCaptureHasHeadroom(Int.MAX_VALUE, Int.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun legacyFallbackAllowsHdAndPortraitWithinDecodeBudget() {
        assertTrue(legacyThumbnailFrameIsSafe(1_920, 1_080))
        assertTrue(legacyThumbnailFrameIsSafe(1_080, 1_920))
        assertTrue(legacyThumbnailFrameIsSafe(2_048, 2_048))
    }

    @Test fun legacyFallbackRefusesUnknownOrInvalidDimensions() {
        assertFalse(legacyThumbnailFrameIsSafe(null, 1_080))
        assertFalse(legacyThumbnailFrameIsSafe(1_920, null))
        assertFalse(legacyThumbnailFrameIsSafe(0, 1_080))
        assertFalse(legacyThumbnailFrameIsSafe(1_920, -1))
    }

    @Test fun legacyFallbackRejectsOversizedAndOverflowingFramesBeforeDecode() {
        assertFalse(legacyThumbnailFrameIsSafe(3_840, 2_160))
        assertFalse(legacyThumbnailFrameIsSafe(2_049, 2_048))
        assertFalse(legacyThumbnailFrameIsSafe(8_192, 100))
        assertFalse(legacyThumbnailFrameIsSafe(Int.MAX_VALUE, Int.MAX_VALUE))
    }

    @Test
    fun cacheNameIsStableAndFileSafe() {
        val first = thumbnailCacheFileName("REMOTE_STREAM:https://example.com/video.m3u8?a=1")
        val second = thumbnailCacheFileName("REMOTE_STREAM:https://example.com/video.m3u8?a=1")

        assertEquals(first, second)
        assertTrue(first.matches(Regex("v3_middle_[0-9a-f]{64}\\.jpg")))
        assertNotEquals(first, thumbnailCacheFileName("REMOTE_STREAM:https://example.com/video.m3u8?a=1", middle = false))
    }

    @Test
    fun differentLibraryEntriesDoNotShareCacheNames() {
        assertNotEquals(
            thumbnailCacheFileName("REMOTE_STREAM:https://example.com/one.m3u8"),
            thumbnailCacheFileName("LOCAL_FILE:content://downloads/two.mp4")
        )
    }

    @Test fun generatedArtworkKindsHaveDeterministicPriority() {
        val key = "REMOTE_STREAM:https://example.com/video.m3u8"
        assertEquals(LibraryThumbnailKind.FALLBACK,
            thumbnailKindFromFileName(thumbnailCacheFileName(key, LibraryThumbnailKind.FALLBACK)))
        assertEquals(LibraryThumbnailKind.POSTER,
            thumbnailKindFromFileName(thumbnailCacheFileName(key, LibraryThumbnailKind.POSTER)))
        assertEquals(LibraryThumbnailKind.MIDDLE,
            thumbnailKindFromFileName(thumbnailCacheFileName(key, LibraryThumbnailKind.MIDDLE)))
        assertTrue(LibraryThumbnailKind.MIDDLE.priority > LibraryThumbnailKind.POSTER.priority)
        assertTrue(LibraryThumbnailKind.POSTER.priority > LibraryThumbnailKind.FALLBACK.priority)
    }

    @Test fun blankAndTransparentCapturesAreRejected() {
        assertFalse(thumbnailSamplesLookUsable(IntArray(96) { 0xFF000000.toInt() }))
        assertFalse(thumbnailSamplesLookUsable(IntArray(96) { 0x00000000 }))
    }

    @Test fun aRealFrameWithDarkBordersIsAccepted() {
        val samples = IntArray(96) { index ->
            if (index % 5 == 0) 0xFF805020.toInt() else 0xFF050505.toInt()
        }
        assertTrue(thumbnailSamplesLookUsable(samples))
    }
}
