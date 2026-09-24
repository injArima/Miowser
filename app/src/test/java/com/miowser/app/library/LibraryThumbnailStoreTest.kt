package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryThumbnailStoreTest {
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
}
