package com.miowser.app.player

import org.junit.Assert.*
import org.junit.Test

class LocalPlaybackProgressTest {
    @Test fun bookmarkRoundTripKeepsOnlyResumeData() {
        val progress = LocalPlaybackProgress(45_000, 100_000, 1234)
        assertEquals(progress, LocalPlaybackProgress.decode(progress.encode()))
        assertEquals(45_000L, libraryResumePosition(progress.positionMs, progress.durationMs))
    }

    @Test fun completedBookmarkStartsAtBeginning() {
        val progress = LocalPlaybackProgress(99_000, 100_000, 1234)
        assertEquals(0L, libraryResumePosition(progress.positionMs, progress.durationMs))
    }

    @Test fun malformedAndNegativeBookmarksAreIgnored() {
        listOf(null, "", "1:2", "1:2:3:4", "-1:2:3", "1:bad:3", "9".repeat(100))
            .forEach { assertNull(LocalPlaybackProgress.decode(it)) }
    }
}
