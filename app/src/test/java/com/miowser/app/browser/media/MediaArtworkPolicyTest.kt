package com.miowser.app.browser.media

import org.junit.Assert.*
import org.junit.Test

class MediaArtworkPolicyTest {
    @Test fun referrerContainsOnlyThePageOrigin() {
        assertEquals("https://music.youtube.com/", MediaArtworkPolicy.referrer(
            "https://music.youtube.com/watch?v=private-history", "https://lh3.googleusercontent.com/cover"))
        assertEquals("https://example.com:8443/", MediaArtworkPolicy.referrer(
            "https://example.com:8443/private?token=secret", "https://images.example.com/art"))
    }
    @Test fun credentialsAndHttpsDowngradeAreNotSent() {
        assertNull(MediaArtworkPolicy.referrer("https://user:password@example.com/", "https://images.example.com/art"))
        assertNull(MediaArtworkPolicy.referrer("https://example.com/", "https://user@images.example.com/art"))
        assertNull(MediaArtworkPolicy.referrer("https://example.com/", "http://images.example.com/art"))
        assertNull(MediaArtworkPolicy.referrer("", "https://images.example.com/art"))
        assertNull(MediaArtworkPolicy.referrer("file:///secret", "https://images.example.com/art"))
    }
    @Test fun decodedBitmapsStayBoundedWithoutUpscalingSmallSources() {
        for ((width, height) in listOf(48 to 48, 512 to 512, 1024 to 1024, 8192 to 4096)) {
            val sample = MediaArtworkPolicy.sampleSize(width, height)
            assertTrue(sample >= 1 && sample and (sample - 1) == 0)
            assertTrue(maxOf(width, height) / sample <= MediaArtworkPolicy.targetSize)
        }
        assertEquals(1, MediaArtworkPolicy.sampleSize(48, 48))
    }
}
