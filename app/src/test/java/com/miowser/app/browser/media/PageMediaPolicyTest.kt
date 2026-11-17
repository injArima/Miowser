package com.miowser.app.browser.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageMediaPolicyTest {
    @Test fun fullscreenPreservesItsOwnEngineButPausesAnotherTabsAudio() {
        assertFalse(PageMediaPolicy.pauseForHandoff(17L, 17L, richPlayer = false))
        assertTrue(PageMediaPolicy.pauseForHandoff(17L, 18L, richPlayer = false))
        assertTrue(PageMediaPolicy.pauseForHandoff(17L, 17L, richPlayer = true))
        assertFalse(PageMediaPolicy.pauseForHandoff(null, 18L, richPlayer = false))
    }

    @Test fun backgroundSwitchDoesNotDisableForegroundSystemControls() {
        assertTrue(PageMediaPolicy.canPublish(foreground = true, backgroundEnabled = false, suppressed = false))
        assertFalse(PageMediaPolicy.canPublish(foreground = false, backgroundEnabled = false, suppressed = false))
        assertTrue(PageMediaPolicy.canPublish(foreground = false, backgroundEnabled = true, suppressed = false))
    }

    @Test fun richAndFullscreenPlayersExcludeThePageSessionEvenWithBackgroundEnabled() {
        for (foreground in listOf(true, false)) {
            assertFalse(PageMediaPolicy.canPublish(foreground, backgroundEnabled = true, suppressed = true))
            assertFalse(PageMediaPolicy.keepRenderer(true, true, 17L, 17L))
        }
    }

    @Test fun retainingOnePageDoesNotProtectUnrelatedTabsFromEviction() {
        assertTrue(PageMediaPolicy.keepRenderer(true, false, 17L, 17L))
        assertFalse(PageMediaPolicy.keepRenderer(true, false, 17L, 18L))
        assertFalse(PageMediaPolicy.keepRenderer(true, false, null, 17L))
        assertFalse(PageMediaPolicy.keepRenderer(false, false, 17L, 17L))
    }
}
