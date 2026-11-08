package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserVideoPageTitleTest {
    @Test fun staleSiteTitleUsesTheCurrentlySelectedEpisode() {
        assertEquals("Watch Example Episode 2 English Sub/Dub", browserVideoPageTitle(
            "https://example.org/watch/show/ep-2", "Watch Example Episode 5 English Sub/Dub"))
    }

    @Test fun seasonAndSeriesNumbersArePreserved() {
        assertEquals("Series 86 Season 2 Episode 12", browserVideoPageTitle(
            "https://example.org/season-2/episode/12", "Series 86 Season 2 Episode 11"))
    }

    @Test fun queryEpisodesAndSpecialsCanSupplyMissingLabels() {
        assertEquals("Example · Episode 2.5", browserVideoPageTitle(
            "https://example.org/watch?episode=02.500", "Example"))
        assertEquals("Example Ep. 0", browserVideoPageTitle(
            "https://example.org/ep-0", "Example Ep. 1"))
    }

    @Test fun ambiguousAndUnrelatedUrlsRetainTheOriginalTitle() {
        val title = "Example Episode 5"
        listOf("https://example.org/watch/2", "https://example.org/ep-2?episode=3",
            "https://example.org/watch?episode=trailer", "not a URL", "file:///ep-2")
            .forEach { assertEquals(title, browserVideoPageTitle(it, title)) }
    }

    @Test fun alreadyCurrentAndPendingPageTitlesStayStable() {
        assertEquals("Example Episode 2", browserVideoPageTitle("https://example.org/ep-2", "Example Episode 2"))
        assertEquals("New Tab", browserVideoPageTitle("https://example.org/ep-2", "New Tab"))
    }
}
