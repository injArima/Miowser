package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserBackNavigationTest {
    @Test
    fun playerAlwaysClosesBeforeItsUnderlyingHubOrPage() {
        assertEquals(
            BrowserBackAction.CLOSE_PLAYER,
            resolveBrowserBackAction(
                showingHub = true,
                playerOpen = true,
                internalPageOpen = false,
                webViewCanGoBack = false,
                activeTabHasOpener = false
            )
        )
        assertEquals(
            BrowserBackAction.CLOSE_PLAYER,
            resolveBrowserBackAction(
                showingHub = false,
                playerOpen = true,
                internalPageOpen = true,
                webViewCanGoBack = true,
                activeTabHasOpener = true
            )
        )
    }

    @Test
    fun internalPageClosesBeforeWebHistory() {
        assertEquals(
            BrowserBackAction.CLOSE_INTERNAL_PAGE,
            resolveBrowserBackAction(
                showingHub = false,
                playerOpen = false,
                internalPageOpen = true,
                webViewCanGoBack = true,
                activeTabHasOpener = true
            )
        )
    }

    @Test
    fun webpageHistoryPrecedesClosingChildTab() {
        assertEquals(
            BrowserBackAction.WEB_HISTORY,
            resolveBrowserBackAction(
                showingHub = false,
                playerOpen = false,
                internalPageOpen = false,
                webViewCanGoBack = true,
                activeTabHasOpener = true
            )
        )
    }

    @Test
    fun exhaustedChildTabReturnsToItsOpener() {
        assertEquals(
            BrowserBackAction.CLOSE_CHILD_TAB,
            resolveBrowserBackAction(
                showingHub = false,
                playerOpen = false,
                internalPageOpen = false,
                webViewCanGoBack = false,
                activeTabHasOpener = true
            )
        )
    }

    @Test
    fun exhaustedManualTabReturnsToHub() {
        assertEquals(
            BrowserBackAction.SHOW_HUB,
            resolveBrowserBackAction(
                showingHub = false,
                playerOpen = false,
                internalPageOpen = false,
                webViewCanGoBack = false,
                activeTabHasOpener = false
            )
        )
    }

    @Test
    fun hubIsTheOnlyLayerThatFallsThroughToAppExit() {
        assertEquals(
            BrowserBackAction.EXIT_APP,
            resolveBrowserBackAction(
                showingHub = true,
                playerOpen = false,
                internalPageOpen = false,
                webViewCanGoBack = true,
                activeTabHasOpener = true
            )
        )
    }

    @Test
    fun hubDownloadsAndMoreReturnToLibraryBeforeExit() {
        assertEquals(
            BrowserBackAction.SHOW_HUB_LIBRARY,
            resolveBrowserBackAction(
                showingHub = true,
                playerOpen = false,
                internalPageOpen = false,
                webViewCanGoBack = true,
                activeTabHasOpener = true,
                hubOnLibrary = false
            )
        )
    }
}
