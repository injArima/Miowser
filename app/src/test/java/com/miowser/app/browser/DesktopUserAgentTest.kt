package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Test

class DesktopUserAgentTest {
    @Test
    fun `requests desktop layout without changing the WebView browser version`() {
        val mobile = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
            desktopUserAgent(mobile)
        )
    }

    @Test
    fun `removes the WebView mobile version marker from a desktop request`() {
        val mobile = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/ABC123; wv) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 " +
            "Chrome/125.0.6422.0 Mobile Safari/537.36"
        assertEquals(
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/125.0.6422.0 Safari/537.36",
            desktopUserAgent(mobile)
        )
    }
}
