package com.miowser.app.browser.media

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteMediaCompatibilityTest {
    @Test fun `only explicit HTTPS media hosts receive visibility compatibility`() {
        listOf("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com", "open.spotify.com")
            .forEach { host ->
                assertTrue(host, SiteMediaCompatibility.supports("https://$host/watch?v=track"))
                assertFalse(host, SiteMediaCompatibility.supports("http://$host/"))
                assertFalse(host, SiteMediaCompatibility.supports("https://$host.evil.test/"))
            }
        assertTrue(SiteMediaCompatibility.supports("HTTPS://MUSIC.YOUTUBE.COM/"))
        listOf("https://sub.youtube.com", "https://youtube.com.", "https://spotify.com",
            "https://www.open.spotify.com", "https://youtube.com@evil.test", "https://user@youtube.com",
            "javascript:youtube.com", "file:///youtube.com", "https://youtube.com%2eevil.test", "not a URL")
            .forEach { assertFalse(it, SiteMediaCompatibility.supports(it)) }
    }
}
