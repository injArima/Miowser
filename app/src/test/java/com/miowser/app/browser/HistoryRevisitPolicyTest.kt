package com.miowser.app.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRevisitPolicyTest {
    private val url = "https://example.com/article"

    @Test fun completedTabMissingFromHistoryIsRecordedWhenRevisited() {
        assertTrue(shouldRecordRevisitedPage(url, url, false, false, false, false))
    }

    @Test fun existingEntryIsNotCountedTwice() {
        assertFalse(shouldRecordRevisitedPage(url, url, false, false, false, true))
    }

    @Test fun privateHomeFailedAndInFlightPagesStayOutOfHistory() {
        assertFalse(shouldRecordRevisitedPage(url, url, true, false, false, false))
        assertFalse(shouldRecordRevisitedPage(url, url, false, true, false, false))
        assertFalse(shouldRecordRevisitedPage(url, url, false, false, true, false))
        assertFalse(shouldRecordRevisitedPage(url, null, false, false, false, false))
        assertFalse(shouldRecordRevisitedPage(url, "https://example.com/old", false, false, false, false))
    }
}
