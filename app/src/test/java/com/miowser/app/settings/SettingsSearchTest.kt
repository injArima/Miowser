package com.miowser.app.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    @Test
    fun customSearchUrlMustBeHttpsAndContainOnePlaceholder() {
        assertTrue(isValidCustomSearchUrl("https://example.com/search?q=%s"))
        assertFalse(isValidCustomSearchUrl("http://example.com/search?q=%s"))
        assertFalse(isValidCustomSearchUrl("https://example.com/search"))
        assertFalse(isValidCustomSearchUrl("https://example.com/search?q=%s&again=%s"))
    }

    @Test
    fun selectedSearchEngineProvidesEncodedNavigationUrl() {
        assertEquals("https://search.brave.com/search?q=steel+ball",
            Settings(searchEngine = SearchEngine.BRAVE).searchUrl("steel ball"))
        assertEquals("https://example.com/find?term=steel+ball&src=app",
            Settings(searchEngine = SearchEngine.CUSTOM,
                customSearchUrl = "https://example.com/find?term=%s&src=app").searchUrl("steel ball"))
    }
}
