package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class ExperienceBackNavigationTest {
    @Test fun browserOnlyReturnsFromStandaloneCatalogToBrowser() {
        assertEquals(BrowserBackAction.SHOW_BROWSER,
            resolveBrowserBackAction(true, false, false, false, false, hubAvailable = false))
    }
    @Test fun browserOnlyDoesNotRevealHubAfterExhaustedHistory() {
        assertEquals(BrowserBackAction.EXIT_APP,
            resolveBrowserBackAction(false, false, false, false, false, hubAvailable = false))
        assertEquals(BrowserBackAction.WEB_HISTORY,
            resolveBrowserBackAction(false, false, false, true, false, hubAvailable = false))
    }
    @Test fun settingsAndPlayersStillCloseBeforeTheirUnderlyingExperience() {
        assertEquals(BrowserBackAction.CLOSE_INTERNAL_PAGE,
            resolveBrowserBackAction(true, false, true, false, false, hubAvailable = false))
        assertEquals(BrowserBackAction.CLOSE_PLAYER,
            resolveBrowserBackAction(true, true, true, false, false, hubAvailable = false))
    }
}
