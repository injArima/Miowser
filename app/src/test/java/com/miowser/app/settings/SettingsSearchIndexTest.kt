package com.miowser.app.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsSearchIndexTest {
    @Test fun identifiersAndPageAnchorsAreUniqueAndComplete() {
        val entries = SettingsSearchIndex.entries
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
        assertEquals(entries.size, entries.map { it.page to it.anchor }.toSet().size)
        assertEquals(SettingsSearchPage.entries.toSet(), entries.map { it.page }.toSet())
        entries.forEach {
            assertTrue(it.title.isNotBlank() && it.path.isNotBlank() && it.summary.isNotBlank() && it.anchor.isNotBlank())
            assertEquals(it, SettingsSearchIndex.byId(it.id))
        }
        assertNull(SettingsSearchIndex.byId("missing"))
    }

    @Test fun normalizesCasePunctuationDiacriticsAndMultipleWords() {
        assertEquals("media.orientation", SettingsSearchIndex.search("  SCREEN—orientation! ", SettingsSearchScope.MEDIA).first().id)
        assertEquals("video.brightness", SettingsSearchIndex.search("bríghtnéss", SettingsSearchScope.MEDIA).first().id)
        assertEquals("subtitles.font", SettingsSearchIndex.search("CAPTION typeface", SettingsSearchScope.MEDIA).first().id)
        assertTrue(SettingsSearchIndex.search("caption nonexistentword", SettingsSearchScope.MEDIA).isEmpty())
    }

    @Test fun findsAliasesAndRoutesToTheActualSettingsSubpage() {
        val rotation = SettingsSearchIndex.search("rotation", SettingsSearchScope.MEDIA).first()
        assertEquals(SettingsSearchPage.MEDIA, rotation.page)
        assertEquals("Screen orientation", rotation.anchor)
        val colors = SettingsSearchIndex.search("wallpaper", SettingsSearchScope.MEDIA).first()
        assertEquals(SettingsSearchPage.INTERFACE, colors.page)
        assertEquals("Color theme", colors.anchor)
        val subtitle = SettingsSearchIndex.search("text opacity", SettingsSearchScope.MEDIA).first()
        assertEquals(SettingsSearchPage.SUBTITLES, subtitle.page)
        assertEquals("Text opacity", subtitle.anchor)
        assertTrue(SettingsSearchIndex.search("captions", SettingsSearchScope.MEDIA).any { it.id == "subtitles.font" })
    }

    @Test fun exactTitlesRankBeforePartialAndDescriptionMatches() {
        assertEquals("subtitles.font", SettingsSearchIndex.search("Font", SettingsSearchScope.MEDIA).first().id)
        assertEquals("browser.cookies", SettingsSearchIndex.search("Cookies", SettingsSearchScope.BROWSER).first().id)
        assertEquals("interface.appearance", SettingsSearchIndex.search("Appearance", SettingsSearchScope.MEDIA).first().id)
    }

    @Test fun browserAndMediaScopesStaySeparateButBrowserCanLinkToMediaSettings() {
        val browser = SettingsSearchIndex.search("", SettingsSearchScope.BROWSER)
        assertTrue(browser.all { it.scope == SettingsSearchScope.BROWSER })
        assertTrue(SettingsSearchIndex.search("cookies", SettingsSearchScope.MEDIA).isEmpty())
        assertEquals(SettingsSearchPage.MEDIA, browser.single { it.id == "browser.media" }.page)
        assertTrue(SettingsSearchIndex.search("hardware acceleration", SettingsSearchScope.MEDIA).isEmpty())
    }

    @Test fun omitsHiddenConditionalSettingsAndRestoresThemWhenAvailable() {
        val unavailable = SettingsSearchAvailability(discovery = false, library = false, savedMedia = true, subtitleOutline = false)
        val ids = SettingsSearchIndex.search("", SettingsSearchScope.MEDIA, unavailable).map { it.id }
        assertFalse("media.rescan" in ids)
        assertFalse("media.resume" in ids)
        assertFalse("media.clear" in ids)
        assertFalse("subtitles.outlineColor" in ids)
        val available = SettingsSearchAvailability(savedMedia = true)
        val visible = SettingsSearchIndex.search("", SettingsSearchScope.MEDIA, available).map { it.id }
        assertTrue(listOf("media.rescan", "media.resume", "media.clear", "subtitles.outlineColor").all { it in visible })
        val deviceOnly = unavailable.copy(discovery = true)
        assertTrue(SettingsSearchIndex.search("resume", SettingsSearchScope.MEDIA, deviceOnly).any { it.id == "media.resume" })
    }

    @Test fun blankQueriesListAvailableSettingsAndQueriesDoNotMutateTheCatalog() {
        val original = SettingsSearchIndex.entries.toList()
        assertEquals(SettingsSearchIndex.search("", SettingsSearchScope.BROWSER), SettingsSearchIndex.search(" \n ", SettingsSearchScope.BROWSER))
        repeat(3) { SettingsSearchIndex.search("clear", SettingsSearchScope.MEDIA) }
        assertEquals(original, SettingsSearchIndex.entries)
    }
}
