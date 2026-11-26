package com.miowser.app.browser.media

import org.junit.Assert.*
import org.junit.Test

class SiteBackgroundPlaybackPolicyTest {
    @Test fun musicPlayersHaveDefaultsWithoutSavingVisits() {
        for (host in listOf("music.youtube.com", "www.youtube.com", "open.spotify.com", "soundcloud.com",
            "music.apple.com", "music.amazon.in", "www.deezer.com", "listen.tidal.com", "www.pandora.com",
            "www.jiosaavn.com", "gaana.com", "audiomack.com", "play.qobuz.com", "tunein.com", "www.iheart.com",
            "artist.bandcamp.com")) assertTrue(host, SiteBackgroundPlaybackPolicy.enabled("https://$host/play", emptyMap()))
    }

    @Test fun ordinarySitesAndBrandLookalikesStayOff() {
        for (host in listOf("example.com", "apple.com", "amazon.com", "spotify.com", "music.youtube.com.evil.test",
            "notspotify.com", "bandcamp.com.evil.test", "notbandcamp.com"))
            assertFalse(host, SiteBackgroundPlaybackPolicy.enabled("https://$host/", emptyMap()))
    }

    @Test fun explicitOffBeatsMusicDefaultsAndExplicitOnAllowsAnyWebHost() {
        assertFalse(SiteBackgroundPlaybackPolicy.enabled("https://music.youtube.com/", mapOf("music.youtube.com" to false)))
        assertTrue(SiteBackgroundPlaybackPolicy.enabled("https://example.com/video", mapOf("example.com" to true)))
        assertTrue(SiteBackgroundPlaybackPolicy.enabled("http://192.168.1.2/video", mapOf("192.168.1.2" to true)))
    }

    @Test fun overridesAreExactHostAndCanonicalAcrossSchemesAndPorts() {
        val overrides = mapOf("example.com" to true, "artist.bandcamp.com" to false)
        assertTrue(SiteBackgroundPlaybackPolicy.enabled("HTTP://EXAMPLE.COM.:8080/player", overrides))
        assertFalse(SiteBackgroundPlaybackPolicy.enabled("https://sub.example.com/", overrides))
        assertFalse(SiteBackgroundPlaybackPolicy.enabled("https://artist.bandcamp.com/", overrides))
        assertTrue(SiteBackgroundPlaybackPolicy.enabled("https://other.bandcamp.com/", overrides))
    }

    @Test fun nonWebOrCredentialUrlsCannotGainBackgroundPermission() {
        val overrides = mapOf("example.com" to true)
        for (url in listOf("file:///example.com", "javascript:example.com", "about:blank", "not a URL",
            "https://user:password@example.com/", "https://example.com@evil.test/", "https://music.youtube.com%2eevil.test/"))
            assertFalse(url, SiteBackgroundPlaybackPolicy.enabled(url, overrides))
    }

    @Test fun roundTripRetainsExplicitOnAndOffAndRejectsMalformedSavedRows() {
        val overrides = mapOf("example.com" to true, "open.spotify.com" to false)
        assertEquals(overrides, SiteBackgroundPlaybackPolicy.decode(SiteBackgroundPlaybackPolicy.encode(overrides)))
        assertEquals(mapOf("example.com" to false), SiteBackgroundPlaybackPolicy.decode(setOf(
            "EXAMPLE.COM.|false", "bad|maybe", "example.com|", "missing", "invalid/host|true")))
        assertTrue(SiteBackgroundPlaybackPolicy.enabled("https://open.spotify.com/", SiteBackgroundPlaybackPolicy.decode(emptySet())))
    }
}
