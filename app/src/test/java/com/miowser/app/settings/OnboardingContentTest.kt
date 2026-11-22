package com.miowser.app.settings

import org.junit.Assert.*
import org.junit.Test

class OnboardingContentTest {
    @Test fun completeExperienceCoversAllCurrentGuideTopics() {
        val pages = onboardingPages(Settings())
        assertEquals(GuideTopic.entries.toList(), pages.map { it.topic })
        assertTrue(pages.all { it.title.isNotBlank() && it.description.isNotBlank() && it.points.isNotEmpty() })
    }

    @Test fun everyModeAndHistoryCombinationHasOnlyItsAvailableTopics() {
        for (mode in AppExperience.entries) for (priority in ExperiencePriority.entries) for (history in LibraryBehavior.entries) {
            val settings = Settings().copy(experience = mode, experiencePriority = priority, libraryBehavior = history)
            val pages = onboardingPages(settings)
            val topics = pages.map { it.topic }
            assertEquals(topics.size, topics.distinct().size)
            assertEquals(settings.deviceLibraryAvailable, GuideTopic.DEVICE in topics)
            assertEquals(settings.browserAvailable, GuideTopic.WEB_VIDEO in topics)
            assertEquals(settings.watchCatalogAvailable, GuideTopic.WATCH_CATALOG in topics)
            assertTrue(listOf(GuideTopic.WELCOME, GuideTopic.SUBTITLES, GuideTopic.DOWNLOADS,
                GuideTopic.CUSTOMIZE, GuideTopic.SETTINGS).all { it in topics })
        }
    }

    @Test fun browserOnlyExplainsStandaloneNavigationAndKeepsPlayerEnhancements() {
        val pages = onboardingPages(Settings(experience = AppExperience.BROWSER_ONLY))
        val welcome = pages.single { it.topic == GuideTopic.WELCOME }.points.joinToString()
        assertTrue(welcome.contains("Rich player") && welcome.contains("WebView player"))
        assertTrue(pages.single { it.topic == GuideTopic.DOWNLOADS }.description.contains("without the hub bar"))
        assertTrue(pages.single { it.topic == GuideTopic.WATCH_CATALOG }.points.first().contains("browser menu"))
    }

    @Test fun historyCopyReflectsManualOrDisabledWithoutChangingThePreference() {
        val manual = Settings(libraryBehavior = LibraryBehavior.MANUAL_ONLY)
        val catalog = onboardingPages(manual).single { it.topic == GuideTopic.WATCH_CATALOG }
        assertTrue(catalog.description.startsWith("Manual"))
        assertEquals(LibraryBehavior.MANUAL_ONLY, manual.libraryBehavior)
        val disabled = onboardingPages(manual.copy(libraryBehavior = LibraryBehavior.DISABLED))
        assertFalse(disabled.any { it.topic == GuideTopic.WATCH_CATALOG })
        assertTrue(disabled.single { it.topic == GuideTopic.CUSTOMIZE }.points.any { it.contains("Disabled") })
    }

    @Test fun startupInstructionsRespectPriorityAndHiddenNetwork() {
        val settings = Settings(experiencePriority = ExperiencePriority.BROWSER_FIRST, libraryBehavior = LibraryBehavior.DISABLED)
        val points = onboardingPages(settings).first().points
        assertTrue(points.last().contains("opens in the browser"))
        assertFalse(points.first().contains("Network"))
        assertTrue(onboardingPages(settings.copy(experience = AppExperience.PLAYER_ONLY)).first().points.first().contains("More"))
    }

    @Test fun guideRemainsSearchableWithoutTheBrowser() {
        val results = SettingsSearchIndex.search("getting started", SettingsSearchScope.MEDIA,
            SettingsSearchAvailability(browser = false, library = false))
        assertEquals(SettingsSearchPage.MASTER, results.single { it.id == "master.guide" }.page)
    }
}
