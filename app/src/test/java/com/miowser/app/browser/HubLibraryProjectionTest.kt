package com.miowser.app.browser

import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibrarySourceKind
import org.junit.Assert.*
import org.junit.Test

class HubLibraryProjectionTest {
    private fun entry() = LibraryEntry("file:///video.mp4", null, "Video", "Local",
        LibrarySourceKind.LOCAL_FILE, null, null, null, 1L, 0L, 100L)

    @Test fun stableDestinationCanReuseItsProjectionWithoutKeepingMutableSourceData() {
        val cache = HubLibraryProjection()
        val source = mutableListOf(entry())
        cache.publish(1, "", HubLibrarySort.RECENT, "", source)
        source.clear()
        assertEquals(1, cache.peek(1, "", HubLibrarySort.RECENT, "")?.size)
    }

    @Test fun repositoryChangesFolderSearchAndSortRejectStaleResults() {
        val cache = HubLibraryProjection()
        cache.publish(1, "a", HubLibrarySort.RECENT, "word", listOf(entry()))
        assertNull(cache.peek(2, "a", HubLibrarySort.RECENT, "word"))
        assertNull(cache.peek(1, "b", HubLibrarySort.RECENT, "word"))
        assertNull(cache.peek(1, "a", HubLibrarySort.TITLE, "word"))
        assertNull(cache.peek(1, "a", HubLibrarySort.RECENT, "new"))
    }

    @Test fun unrelatedDataSourcesDoNotReuseTheRepositoryCache() {
        val cache = HubLibraryProjection()
        cache.publish(1, "", HubLibrarySort.RECENT, "", listOf(entry()))
        cache.publish(-1, "", HubLibrarySort.RECENT, "", emptyList())
        assertNull(cache.peek(-1, "", HubLibrarySort.RECENT, ""))
        assertEquals(1, cache.peek(1, "", HubLibrarySort.RECENT, "")?.size)
    }

    @Test fun keepsFourRecentlyUsedViewsAndEvictsTheLeastRecent() {
        val cache = HubLibraryProjection()
        repeat(4) { cache.publish(1, "folder-$it", HubLibrarySort.RECENT, "", listOf(entry())) }
        assertNotNull(cache.peek(1, "folder-0", HubLibrarySort.RECENT, ""))
        cache.publish(1, "folder-4", HubLibrarySort.RECENT, "", listOf(entry()))
        assertNull(cache.peek(1, "folder-1", HubLibrarySort.RECENT, ""))
        assertNotNull(cache.peek(1, "folder-0", HubLibrarySort.RECENT, ""))
    }
}
