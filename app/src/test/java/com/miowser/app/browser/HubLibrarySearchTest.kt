package com.miowser.app.browser

import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibrarySourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HubLibrarySearchTest {
    @Test
    fun blankQueryKeepsEveryEntryInItsCurrentOrder() {
        val entries = listOf(entry("First"), entry("Second"))

        assertEquals(entries, filterLibraryEntries(entries, "   "))
    }

    @Test
    fun searchMatchesTitleSourceAndMediaAddressIgnoringCase() {
        val byTitle = entry("Ocean Documentary")
        val bySource = entry("Episode 4", source = "Example Network")
        val byAddress = entry("Untitled", uri = "https://media.example/special-stream.m3u8")
        val entries = listOf(byTitle, bySource, byAddress)

        assertEquals(listOf(byTitle), filterLibraryEntries(entries, "OCEAN"))
        assertEquals(listOf(bySource), filterLibraryEntries(entries, "network"))
        assertEquals(listOf(byAddress), filterLibraryEntries(entries, "SPECIAL-STREAM"))
    }

    @Test
    fun unmatchedQueryReturnsAnEmptyList() {
        assertEquals(emptyList<LibraryEntry>(), filterLibraryEntries(listOf(entry("One")), "missing"))
    }

    private fun entry(
        title: String,
        source: String = "example.com",
        uri: String = "https://example.com/$title"
    ) = LibraryEntry(
        mediaUri = uri,
        pageUrl = null,
        title = title,
        sourceLabel = source,
        sourceKind = LibrarySourceKind.REMOTE_STREAM,
        mediaType = null,
        userAgent = null,
        thumbnailUrl = null,
        lastPlayedAt = 1L,
        positionMs = 0L,
        durationMs = 100L
    )
}
