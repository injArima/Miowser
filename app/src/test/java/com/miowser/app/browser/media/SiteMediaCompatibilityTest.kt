package com.miowser.app.browser.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMediaCompatibilityTest {
    @Test fun webFramesCanReceiveAnExplicitTopSiteGrant() {
        for (url in listOf("https://music.youtube.com/", "https://example.com/player", "http://example.com/",
            "HTTPS://EMBED.EXAMPLE.COM/")) assertTrue(url, SiteMediaCompatibility.supports(url))
    }

    @Test fun invalidOriginsNeverReceiveFrameCompatibility() {
        for (url in listOf("file:///video", "javascript:video", "https://user@example.com", "about:blank",
            "not a URL", "https://example.com%2eevil.test")) assertFalse(url, SiteMediaCompatibility.supports(url))
    }
}
