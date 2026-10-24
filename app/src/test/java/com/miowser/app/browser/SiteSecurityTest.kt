package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteSecurityTest {
    @Test fun onlyFinishedHttpsPageGetsLockedStatus() {
        val url = "https://example.com/page"
        assertEquals(SiteSecurity.CHECKING, siteSecurity(url, null, null, false))
        assertEquals(SiteSecurity.SECURE, siteSecurity(url, url, null, false))
        assertEquals(SiteSecurity.SECURE, siteSecurity("$url#section", url, null, false))
        assertEquals(SiteSecurity.CHECKING, siteSecurity(url, "https://other.example/page", null, false))
    }

    @Test fun errorsAndHttpNeverShowSecureStatus() {
        assertEquals(SiteSecurity.CERTIFICATE_ERROR,
            siteSecurity("https://example.com", null, "https://example.com", false))
        assertEquals(SiteSecurity.CERTIFICATE_BYPASSED,
            siteSecurity("https://example.com/path", null, "https://example.com", true))
        assertEquals(SiteSecurity.INSECURE, siteSecurity("http://example.com", "http://example.com", null, false))
        assertEquals(SiteSecurity.OTHER, siteSecurity("about:blank", null, null, false))
    }

    @Test fun httpApprovalIsPerHostAndPrivateModeForThisSession() {
        val url = "http://approval-test.example/path"
        assertTrue(HttpNavigationApproval.requiresWarning(url, false))
        HttpNavigationApproval.accept(url, false)
        assertFalse(HttpNavigationApproval.requiresWarning("http://approval-test.example/other", false))
        assertTrue(HttpNavigationApproval.requiresWarning(url, true))
        HttpNavigationApproval.accept(url, true)
        HttpNavigationApproval.clearPrivate()
        assertTrue(HttpNavigationApproval.requiresWarning(url, true))
        assertFalse(HttpNavigationApproval.requiresWarning(url, false))
        assertFalse(HttpNavigationApproval.requiresWarning("https://approval-test.example", false))
    }
}
