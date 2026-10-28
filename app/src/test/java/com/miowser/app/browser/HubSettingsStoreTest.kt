package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class HubSettingsStoreTest {
    @Test
    fun decodesSavedHubPreferences() {
        assertEquals(
            HubSettings(HubLibrarySort.TITLE, HubDownloadSection.DOWNLOADED),
            decodeHubSettings("TITLE", "DOWNLOADED")
        )
    }

    @Test
    fun invalidValuesFallBackWithoutAffectingBrowserSettings() {
        assertEquals(HubSettings(), decodeHubSettings("unknown", "also-unknown"))
    }

    @Test
    fun folderSortUsesItsSavedValueOrTheLegacyFallback() {
        assertEquals(HubLibrarySort.TITLE, decodeLibrarySort("TITLE", HubLibrarySort.PROGRESS))
        assertEquals(HubLibrarySort.PROGRESS, decodeLibrarySort(null, HubLibrarySort.PROGRESS))
        assertEquals(HubLibrarySort.PROGRESS, decodeLibrarySort("invalid", HubLibrarySort.PROGRESS))
    }

    @Test
    fun everyFolderAndUnfiledHaveDistinctPreferenceKeys() {
        assertEquals("library_sort_folder:", librarySortPreferenceKey(""))
        assertEquals("library_sort_folder:primary", librarySortPreferenceKey("primary"))
        assertEquals("library_sort_folder:child", librarySortPreferenceKey("child"))
    }
}
