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

    @Test fun backgroundAudioPermissionIsScopedToItsPageSession() {
        assertTrue(PageMediaPolicy.keepRenderer(true, false, 17L, 17L))
        assertFalse(PageMediaPolicy.keepRenderer(true, false, 17L, 18L))
        assertFalse(PageMediaPolicy.keepRenderer(true, false, null, 17L))
        assertFalse(PageMediaPolicy.keepRenderer(false, false, 17L, 17L))
    }

    @Test fun switchingTabsExcludesThePreviousOwnerAndUnrelatedFrames() {
        assertTrue(PageMediaPolicy.eligibleTab(18L, 18L, null))
        assertFalse(PageMediaPolicy.eligibleTab(17L, 18L, null))
        assertFalse(PageMediaPolicy.eligibleTab(17L, null, null))
        assertTrue(PageMediaPolicy.eligibleTab(17L, 18L, 17L))
    }

    @Test fun explicitBackgroundGrantRetainsOnlyTheExistingOwnerAcrossTabs() {
        assertTrue(PageMediaPolicy.eligibleTab(17L, 18L, null, 17L))
        assertFalse(PageMediaPolicy.eligibleTab(19L, 18L, null, 17L))
        assertFalse(PageMediaPolicy.eligibleTab(17L, 18L, null, null))
    }

    @Test fun siteChoiceAndMasterSwitchMustBothAllowTheRetainedTab() {
        assertTrue(PageMediaPolicy.backgroundAllowed(true, 17L, setOf(17L)))
        assertFalse(PageMediaPolicy.backgroundAllowed(false, 17L, setOf(17L)))
        assertFalse(PageMediaPolicy.backgroundAllowed(true, 18L, setOf(17L)))
        assertFalse(PageMediaPolicy.backgroundAllowed(true, null, setOf(17L)))
        assertFalse(PageMediaPolicy.backgroundAllowed(true, 17L, emptySet()))
    }

    @Test fun foregroundPlaybackRequiresAVisibleBrowserOrPlayer() {
        assertTrue(PageMediaPolicy.allowPlayback(true, true, false, false, false, false))
        assertTrue(PageMediaPolicy.allowPlayback(true, false, true, false, false, false))
        assertTrue(PageMediaPolicy.allowPlayback(true, false, false, true, false, false))
        assertFalse(PageMediaPolicy.allowPlayback(true, false, false, false, false, false))
    }

    @Test fun minimizingRequiresExplicitBackgroundSessionOrPipPermission() {
        assertFalse(PageMediaPolicy.allowPlayback(false, true, true, true, false, false))
        assertTrue(PageMediaPolicy.allowPlayback(false, false, false, false, true, false))
        assertTrue(PageMediaPolicy.allowPlayback(false, false, false, false, false, true))
    }
}
