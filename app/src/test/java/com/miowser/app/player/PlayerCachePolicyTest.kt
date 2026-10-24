package com.miowser.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCachePolicyTest {
    @Test fun `network windows follow transport budget`() {
        assertEquals(PlayerBufferWindow(900_000L, 300_000L), playerBufferWindow(PlayerConnection.WIFI))
        assertEquals(PlayerBufferWindow(300_000L, 300_000L), playerBufferWindow(PlayerConnection.MOBILE))
        assertEquals(0L, playerBufferWindow(PlayerConnection.OFFLINE).aheadMs)
    }

    @Test fun `cache key separates credentials without exposing them or varying by byte range`() {
        val a = playerCacheKey("video", "https://example.com/seg.ts",
            mapOf("Cookie" to "session=one", "Range" to "bytes=0-99"))
        val b = playerCacheKey("video", "https://example.com/seg.ts",
            mapOf("Cookie" to "session=two", "Range" to "bytes=0-99"))
        val sameResource = playerCacheKey("video", "https://example.com/seg.ts",
            mapOf("Cookie" to "session=one", "Range" to "bytes=100-199"))
        assertNotEquals(a, b)
        assertEquals(a, sameResource)
        assertFalse(a.contains("session="))
        // Existing disk spans must keep the same key after removing per-byte String.format.
        assertEquals("video:f70a2877257ba187b3deeb246ee9a5c4c19994ff0ca91e5f11875ac760a6b2d8", a)
    }

    @Test fun `long active playback protects cache during an immediate reopen`() {
        assertFalse(playerCacheExpired(1_000L, 3_601_000L, activeSessions = 1))
        assertTrue(playerCacheExpired(1_000L, 3_601_000L, activeSessions = 0))
        // Closing renews last-use time before subsequent idle expiry decisions.
        assertFalse(playerCacheExpired(3_601_000L, 3_601_001L, activeSessions = 0))
    }

    @Test fun `cache expires after ten minutes without a reopen`() {
        assertFalse(playerCacheExpired(1_000L, 600_999L))
        assertTrue(playerCacheExpired(1_000L, 601_000L))
        assertFalse(playerCacheExpired(0L, 601_000L))
    }
}
