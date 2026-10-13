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
}
