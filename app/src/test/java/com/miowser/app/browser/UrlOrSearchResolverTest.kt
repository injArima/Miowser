package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlOrSearchResolverTest {

    @Test
    fun `bare domain is treated as a URL with https prepended`() {
        assertEquals("https://example.com", UrlOrSearchResolver.resolve("example.com"))
    }

    @Test
    fun `full https URL passes through unchanged`() {
        assertEquals("https://example.com/path?x=1", UrlOrSearchResolver.resolve("https://example.com/path?x=1"))
    }

    @Test
    fun `http URL passes through unchanged, not upgraded`() {
        assertEquals("http://example.com", UrlOrSearchResolver.resolve("http://example.com"))
    }

    @Test
    fun `subdomain and path are recognized as a URL`() {
        assertEquals("https://en.wikipedia.org/wiki/Kotlin", UrlOrSearchResolver.resolve("en.wikipedia.org/wiki/Kotlin"))
    }

    @Test
    fun `plain word with no dot is treated as a search query`() {
        val result = UrlOrSearchResolver.resolve("wikipedia")
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
        assertTrue(result.contains("wikipedia"))
    }

    @Test
    fun `multi-word input is treated as a search query with spaces encoded`() {
        val result = UrlOrSearchResolver.resolve("how to bake bread")
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
        assertTrue(result.contains("how+to+bake+bread"))
    }

    @Test
    fun `localhost with port is recognized as a URL`() {
        assertEquals("https://localhost:8080", UrlOrSearchResolver.resolve("localhost:8080"))
    }

    @Test
    fun `bare IP address is recognized as a URL`() {
        assertEquals("https://192.168.1.1", UrlOrSearchResolver.resolve("192.168.1.1"))
    }

    @Test
    fun `a domain-like phrase containing a space is still a search query`() {
        val result = UrlOrSearchResolver.resolve("example.com pricing")
        assertTrue(result.startsWith("https://duckduckgo.com/?q="))
    }

    @Test
    fun `blank input resolves to empty string`() {
        assertEquals("", UrlOrSearchResolver.resolve("   "))
    }
}
