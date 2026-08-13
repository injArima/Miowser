package com.miowser.app.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdblockPatternCompilerTest {

    @Test
    fun `domain anchor matches the domain and subdomains`() {
        val regex = AdblockPatternCompiler.compile("||doubleclick.net^")
        assertTrue(regex.containsMatchIn("https://doubleclick.net/ad"))
        assertTrue(regex.containsMatchIn("https://ads.doubleclick.net/pixel"))
        assertTrue(regex.containsMatchIn("https://a.b.doubleclick.net/x"))
    }

    @Test
    fun `domain anchor does not match unrelated or suffix-only domains`() {
        val regex = AdblockPatternCompiler.compile("||doubleclick.net^")
        assertFalse(regex.containsMatchIn("https://notdoubleclick.net/ad"))
        assertFalse(regex.containsMatchIn("https://evildoubleclick.net.evil.com/ad"))
        assertFalse(regex.containsMatchIn("https://example.com/doubleclick.net"))
    }

    @Test
    fun `wildcard matches across path segments`() {
        val regex = AdblockPatternCompiler.compile("||example.com/ads/*/track")
        assertTrue(regex.containsMatchIn("https://example.com/ads/banner/123/track"))
        assertFalse(regex.containsMatchIn("https://example.com/other/track"))
    }

    @Test
    fun `end anchor requires exact suffix`() {
        val regex = AdblockPatternCompiler.compile("|https://example.com/exact.js|")
        assertTrue(regex.containsMatchIn("https://example.com/exact.js"))
        assertFalse(regex.containsMatchIn("https://example.com/exact.js.map"))
    }

    @Test
    fun `separator caret matches end of string as well as non-alphanumeric`() {
        val regex = AdblockPatternCompiler.compile("||example.com/track^")
        assertTrue(regex.containsMatchIn("https://example.com/track"))
        assertTrue(regex.containsMatchIn("https://example.com/track?x=1"))
        assertFalse(regex.containsMatchIn("https://example.com/trackers"))
    }

    @Test
    fun `extractAnchorDomain returns the clean anchor domain`() {
        assertEquals("doubleclick.net", AdblockPatternCompiler.extractAnchorDomain("||doubleclick.net^"))
        assertEquals("example.com", AdblockPatternCompiler.extractAnchorDomain("||example.com/path*"))
    }

    @Test
    fun `extractAnchorDomain returns null when a wildcard touches the domain itself`() {
        assertNull(AdblockPatternCompiler.extractAnchorDomain("||cacheserve.*/promodisplay/"))
        assertNull(AdblockPatternCompiler.extractAnchorDomain("/plain-path"))
    }
}
