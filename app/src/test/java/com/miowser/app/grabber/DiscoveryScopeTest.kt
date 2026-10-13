package com.miowser.app.grabber

import java.net.URI
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryScopeTest {
    private val origin = URI("https://videos.example.org/watch/one")

    @Test fun `permits same origin and equivalent default port`() {
        assertTrue(DiscoveryScope.allows(origin, URI("https://videos.example.org:443/watch/two")))
    }

    @Test fun `rejects subdomains alternate ports and protocol changes`() {
        assertFalse(DiscoveryScope.allows(origin, URI("https://cdn.example.org/watch/two")))
        assertFalse(DiscoveryScope.allows(origin, URI("https://videos.example.org:444/watch/two")))
        assertFalse(DiscoveryScope.allows(origin, URI("http://videos.example.org/watch/two")))
    }
}
