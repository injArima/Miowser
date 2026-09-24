package com.miowser.app.browser

import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibrarySourceKind
import org.junit.Assert.assertEquals
import org.junit.Test

class HubLibrarySortTest {
    @Test
    fun recentSortUsesNewestPlaybackFirst() {
        val entries = listOf(
            entry("older", lastPlayedAt = 10L),
            entry("newest", lastPlayedAt = 30L),
            entry("middle", lastPlayedAt = 20L)
        )

        assertEquals(
            listOf("newest", "middle", "older"),
            sortLibraryEntries(entries, HubLibrarySort.RECENT).map { it.title }
        )
    }

    @Test
    fun titleSortIsCaseInsensitiveAndUsesRecencyForEqualTitles() {
        val entries = listOf(
            entry("beta", lastPlayedAt = 10L),
            entry("Alpha", lastPlayedAt = 20L, uri = "alpha-old"),
            entry("alpha", lastPlayedAt = 30L, uri = "alpha-new")
        )

        assertEquals(
            listOf("alpha-new", "alpha-old", "beta"),
            sortLibraryEntries(entries, HubLibrarySort.TITLE).map { it.mediaUri }
        )
    }

    @Test
    fun progressSortUsesWatchedRatioThenRecency() {
        val entries = listOf(
            entry("quarter", lastPlayedAt = 40L, positionMs = 25L, durationMs = 100L),
            entry("half-old", lastPlayedAt = 20L, positionMs = 50L, durationMs = 100L),
            entry("half-new", lastPlayedAt = 30L, positionMs = 100L, durationMs = 200L),
            entry("unknown", lastPlayedAt = 50L, positionMs = 90L, durationMs = 0L)
        )

        assertEquals(
            listOf("half-new", "half-old", "quarter", "unknown"),
            sortLibraryEntries(entries, HubLibrarySort.PROGRESS).map { it.title }
        )
    }

    private fun entry(
        title: String,
        lastPlayedAt: Long,
        uri: String = title,
        positionMs: Long = 0L,
        durationMs: Long = 100L
    ) = LibraryEntry(
        mediaUri = uri,
        pageUrl = null,
        title = title,
        sourceLabel = "example.com",
        sourceKind = LibrarySourceKind.REMOTE_STREAM,
        mediaType = null,
        userAgent = null,
        thumbnailUrl = null,
        lastPlayedAt = lastPlayedAt,
        positionMs = positionMs,
        durationMs = durationMs
    )
}
