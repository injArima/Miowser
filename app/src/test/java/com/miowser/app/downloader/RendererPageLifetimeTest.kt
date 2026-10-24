package com.miowser.app.downloader

import org.junit.Assert.*
import org.junit.Test

class RendererPageLifetimeTest {
    @Test fun queuedDownloadCannotStartOnANewPage() {
        val page = RendererPageLifetime()
        val queuedOnOldPage = page.capture()
        page.invalidate()
        val queuedOnNewPage = page.capture()

        assertNotNull(page.failureFor(queuedOnOldPage))
        assertNull(page.failureFor(queuedOnNewPage))
    }

    @Test fun rendererLossRejectsTokensEvenWhenWorkerRegistersAfterCleanup() {
        val page = RendererPageLifetime()
        val queuedBeforeCrash = page.capture()
        page.retire("Renderer stopped. Reload the page.")

        assertEquals("Renderer stopped. Reload the page.", page.failureFor(queuedBeforeCrash))
        assertEquals("Renderer stopped. Reload the page.", page.failureFor(page.capture()))
    }

    @Test fun ReloadDoesNotReviveTheOldBridgeOrItsTransfers() {
        val failedBridge = RendererPageLifetime()
        val oldTransfer = failedBridge.capture()
        failedBridge.retire("Renderer stopped")
        failedBridge.invalidate() // tab cleanup can invalidate more than once
        val replacementBridge = RendererPageLifetime()

        assertEquals("Renderer stopped", failedBridge.failureFor(oldTransfer))
        assertEquals("Renderer stopped", failedBridge.failureFor(failedBridge.capture()))
        assertNull(replacementBridge.failureFor(replacementBridge.capture()))
    }

    @Test fun duplicateCleanupKeepsTheOriginalFailureAndDoesNotAffectOtherTabs() {
        val affected = RendererPageLifetime()
        val unaffected = RendererPageLifetime()
        val unaffectedTransfer = unaffected.capture()
        affected.retire("Renderer stopped")
        affected.retire("Tab closed")

        assertEquals("Renderer stopped", affected.failureFor(affected.capture()))
        assertNull(unaffected.failureFor(unaffectedTransfer))
    }
}
