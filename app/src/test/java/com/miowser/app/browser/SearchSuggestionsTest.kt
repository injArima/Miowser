package com.miowser.app.browser

import com.miowser.app.settings.SearchEngine
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
}
