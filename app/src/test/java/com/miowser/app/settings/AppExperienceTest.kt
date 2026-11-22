package com.miowser.app.settings

import org.junit.Assert.*
import org.junit.Test

class AppExperienceTest {
    @Test fun upgradesKeepTheCompletePlayerFirstExperience() {
        val settings = Settings()
        assertEquals(AppExperience.BOTH, settings.experience)
        assertTrue(settings.browserAvailable && settings.deviceLibraryAvailable && settings.watchCatalogAvailable)
        assertFalse(settings.startsInBrowser)
        assertFalse(settings.experienceCustomized)
    }

    @Test fun everyCombinationHasCorrectCapabilitiesAndStartup() {
        for (mode in AppExperience.entries) for (priority in ExperiencePriority.entries) for (history in LibraryBehavior.entries) {
            val settings = Settings().withExperience(mode, priority, history)
            assertEquals(mode != AppExperience.PLAYER_ONLY, settings.browserAvailable)
            assertEquals(mode != AppExperience.BROWSER_ONLY, settings.deviceLibraryAvailable)
            assertEquals(mode != AppExperience.PLAYER_ONLY && settings.libraryBehavior.isAvailable, settings.watchCatalogAvailable)
            assertEquals(mode == AppExperience.BROWSER_ONLY || (mode == AppExperience.BOTH && priority == ExperiencePriority.BROWSER_FIRST), settings.startsInBrowser)
            assertTrue(settings.experienceCustomized)
        }
    }

    @Test fun modeChangesPreserveIndividualPreferencesAndPrivateChoices() {
        val saved = Settings(libraryBehavior = LibraryBehavior.MANUAL_ONLY, deviceDiscoveryEnabled = false,
            deviceAutoRescanEnabled = false, backgroundPlaybackEnabled = false, companionBrowsingEnabled = true,
            excludedDeviceFolders = setOf("private"), cookiesEnabled = false, resumeSavedVideos = false)
        val player = saved.withExperience(AppExperience.PLAYER_ONLY, ExperiencePriority.BROWSER_FIRST, LibraryBehavior.AUTOMATIC)
        assertEquals(LibraryBehavior.MANUAL_ONLY, player.libraryBehavior)
        assertEquals(saved, player.copy(experience = saved.experience, experiencePriority = saved.experiencePriority,
            experienceCustomized = saved.experienceCustomized))
        val browser = player.withExperience(AppExperience.BROWSER_ONLY, player.experiencePriority, player.libraryBehavior)
        assertFalse(browser.deviceScanningEnabled)
        assertTrue(browser.watchCatalogAvailable)
        assertEquals(saved.excludedDeviceFolders, browser.excludedDeviceFolders)
    }

    @Test fun scanningIsSuspendedInBrowserOnlyWithoutOverwritingDiscovery() {
        val original = Settings()
        val browser = original.withExperience(AppExperience.BROWSER_ONLY, ExperiencePriority.PLAYER_FIRST, LibraryBehavior.DISABLED)
        assertFalse(browser.deviceScanningEnabled)
        assertTrue(browser.deviceDiscoveryEnabled)
        assertTrue(browser.withExperience(AppExperience.BOTH, browser.experiencePriority, browser.libraryBehavior).deviceScanningEnabled)
    }

    @Test fun wizardRequiresHistoryForBrowserAndBothButSkipsItForPlayer() {
        for (mode in AppExperience.entries) {
            assertEquals(ExperienceStep.PRIORITY, ExperienceStep.INTRO.next(mode))
            assertEquals(ExperienceStep.FEATURES, ExperienceStep.PRIORITY.next(mode))
            assertEquals(if (mode == AppExperience.PLAYER_ONLY) ExperienceStep.REVIEW else ExperienceStep.HISTORY,
                ExperienceStep.FEATURES.next(mode))
            assertEquals(if (mode == AppExperience.PLAYER_ONLY) ExperienceStep.FEATURES else ExperienceStep.HISTORY,
                ExperienceStep.REVIEW.previous(mode))
            assertNull(ExperienceStep.INTRO.previous(mode))
        }
    }

    @Test fun searchCannotExposeHiddenDeviceOrBrowserFeatures() {
        val browser = SettingsSearchAvailability(deviceLibrary = false)
        val browserIds = SettingsSearchIndex.search("", SettingsSearchScope.MEDIA, browser).map { it.id }
        assertFalse(browserIds.any { it in listOf("media.folders", "media.discovery", "media.rescan", "media.permissions") })
        assertTrue("subtitles.font" in browserIds)
        val player = SettingsSearchAvailability(browser = false, library = false)
        assertTrue(SettingsSearchIndex.search("", SettingsSearchScope.BROWSER, player).isEmpty())
        val playerIds = SettingsSearchIndex.search("", SettingsSearchScope.MEDIA, player).map { it.id }
        assertFalse(playerIds.any { it in listOf("master.browser", "media.history", "advanced.source", "media.clear") })
        assertTrue(listOf("master.customize", "media.resume", "subtitles.font").all { it in playerIds })
    }
}
