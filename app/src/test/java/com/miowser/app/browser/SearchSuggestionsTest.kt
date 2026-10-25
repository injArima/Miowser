package com.miowser.app.browser

import com.miowser.app.bookmarks.Bookmark
import com.miowser.app.history.HistoryEntry
import com.miowser.app.settings.SearchEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchSuggestionsTest {
    @Test
    fun supportedEnginesHaveHttpsSuggestionFeeds() {
        SearchEngine.entries.filterNot { it == SearchEngine.BRAVE || it == SearchEngine.CUSTOM }.forEach { engine ->
            val url = suggestionUrl(engine, "steel ball")
            assertTrue(url.startsWith("https://"))
            assertTrue(url.contains("steel+ball"))
        }
    }

    @Test
    fun onlySearchTermsAreSentForSuggestions() {
        SearchEngine.entries.filterNot { it == SearchEngine.BRAVE || it == SearchEngine.CUSTOM }.forEach { engine ->
            assertTrue(shouldRequestSuggestions("steel ball", engine))
            assertFalse(shouldRequestSuggestions("s", engine))
            assertFalse(shouldRequestSuggestions("example.com/video", engine))
            assertFalse(shouldRequestSuggestions("example.", engine))
            assertFalse(shouldRequestSuggestions("www.exa", engine))
            assertFalse(shouldRequestSuggestions("https://example.com/video", engine))
            assertFalse(shouldRequestSuggestions("localhost:8080", engine))
        }
    }

    @Test
    fun braveAndCustomNeverRequestRemoteSuggestions() {
        listOf(SearchEngine.BRAVE, SearchEngine.CUSTOM).forEach { engine ->
            assertFalse(shouldRequestSuggestions("steel ball", engine))
            assertTrue(suggestionUrl(engine, "steel ball").isEmpty())
        }
    }

    @Test
    fun localSuggestionsMatchTitlesAndUrlsBeforeProviderResults() {
        val results = localPageSuggestions(
            input = "compose",
            bookmarks = listOf(
                Bookmark("https://developer.android.com/compose", "Compose documentation", 10L)
            ),
            history = listOf(
                HistoryEntry("https://example.com/compose-news", "Weekly Compose news", 20L, 4)
            )
        )

        assertEquals(2, results.size)
        assertEquals(BrowserSuggestionKind.BOOKMARK, results[0].kind)
        assertEquals("https://developer.android.com/compose", results[0].value)
        assertEquals(BrowserSuggestionKind.HISTORY, results[1].kind)
    }

    @Test
    fun exactMatchOutranksSourceAndBookmarkWinsDuplicateUrl() {
        val results = localPageSuggestions(
            input = "example.com",
            bookmarks = listOf(
                Bookmark("https://example.com/", "Saved example", 10L)
            ),
            history = listOf(
                HistoryEntry("https://example.com", "example.com", 30L, 20),
                HistoryEntry("https://example.com/help", "Help", 40L, 2)
            )
        )

        assertEquals(2, results.size)
        assertEquals(BrowserSuggestionKind.BOOKMARK, results[0].kind)
        assertEquals("https://example.com/", results[0].value)
        assertEquals("https://example.com/help", results[1].value)
    }

    @Test
    fun localSuggestionLimitIsRespected() {
        val history = (1..10).map { index ->
            HistoryEntry("https://site$index.example/search", "Search result $index", index.toLong(), index)
        }

        assertEquals(4, localPageSuggestions("search", emptyList(), history, limit = 4).size)
    }
}
