package com.miowser.app.browser.media

import org.junit.Assert.*
import org.junit.Test

class PageMediaSnapshotTest {
    private fun sample() = PageMediaSnapshot(
        tabId = 42, ownerKey = "document:track", title = "Track", artist = "Artist", album = "Album",
        artworkUrl = "https://example.com/art.png", pageUrl = "https://example.com/watch",
        playing = true, buffering = false, positionMs = 10_000, durationMs = 60_000,
        speed = 1f, actions = setOf("play", "pause", "seekto", "nexttrack", "previoustrack")
    )

    @Test fun privatePublicationRemovesEverySiteMetadataField() {
        val private = sample().copy(isPrivate = true).forPublication()
        assertEquals("Private media", private.title)
        assertEquals("", private.artist)
        assertEquals("", private.album)
        assertEquals("", private.pageUrl)
        assertNull(private.artworkUrl)
        assertEquals(42L, private.tabId)
        assertEquals(10_000L, private.positionMs)
        assertTrue(private.playing)
    }

    @Test fun publicationSanitizesUntrustedTimingAndActionValues() {
        val state = sample().copy(title = "", positionMs = -2, durationMs = -20,
            speed = Float.NaN, actions = setOf("play", "unsupported")).forPublication()
        assertEquals("Browser media", state.title)
        assertEquals(0L, state.positionMs)
        assertEquals(-1L, state.durationMs)
        assertEquals(1f, state.speed, 0f)
        assertEquals(setOf("play"), state.actions)
        assertEquals(60_000L, sample().copy(positionMs = Long.MAX_VALUE).forPublication().positionMs)
        for (speed in listOf(0f, -1f, Float.POSITIVE_INFINITY)) {
            assertEquals(1f, sample().copy(speed = speed).forPublication().speed, 0f)
        }
    }

    @Test fun notificationIgnoresClockSamplesAndBufferingButTracksVisibleChanges() {
        val snapshot = sample()
        val key = snapshot.notificationKey()
        assertEquals(key, snapshot.copy(positionMs = 20_000, durationMs = 90_000,
            speed = 2f, buffering = true).notificationKey())
        for (changed in listOf(snapshot.copy(title = "Next"), snapshot.copy(artist = "Other"),
            snapshot.copy(album = "Other"), snapshot.copy(artworkUrl = null),
            snapshot.copy(playing = false), snapshot.copy(actions = setOf("play")),
            snapshot.copy(tabId = 43), snapshot.copy(ownerKey = "new document"))) {
            assertNotEquals(key, changed.notificationKey())
        }
    }

    @Test fun clockExtrapolatesRateAndClampsToDuration() {
        val anchor = PageMediaPositionAnchor(sample().copy(speed = 2f), 1_000)
        assertEquals(10_000L, anchor.positionAt(0))
        assertEquals(14_000L, anchor.positionAt(3_000))
        assertEquals(60_000L, anchor.positionAt(Long.MAX_VALUE))
        val live = PageMediaPositionAnchor(sample().copy(durationMs = -1), 0)
        assertEquals(110_000L, live.positionAt(100_000))
    }

    @Test fun clockSleepsThroughOrdinaryTicksAndReanchorsOnDiscontinuity() {
        val snapshot = sample()
        val anchor = PageMediaPositionAnchor(snapshot, 1_000)
        assertFalse(anchor.needsUpdate(snapshot.copy(positionMs = 12_000), 3_000))
        assertFalse(anchor.needsUpdate(snapshot.copy(positionMs = 13_500), 3_000))
        assertTrue(anchor.needsUpdate(snapshot.copy(positionMs = 13_501), 3_000))
        assertTrue(anchor.needsUpdate(snapshot.copy(positionMs = 1_000), 3_000))
    }

    @Test fun clockReanchorsOnTransportCapabilityAndOwnerTransitions() {
        val snapshot = sample()
        val anchor = PageMediaPositionAnchor(snapshot, 0)
        for (changed in listOf(snapshot.copy(playing = false), snapshot.copy(buffering = true),
            snapshot.copy(speed = 2f), snapshot.copy(durationMs = 120_000),
            snapshot.copy(actions = setOf("play")), snapshot.copy(ownerKey = "new"),
            snapshot.copy(tabId = 43), snapshot.copy(pageUrl = "https://example.com/next"))) {
            assertTrue(anchor.needsUpdate(changed, 0))
        }
        assertFalse(anchor.needsUpdate(snapshot.copy(title = "New title"), 0))
    }

    @Test fun pausedAndBufferingClocksHoldPositionAndPublishEvenSmallSeeks() {
        for (snapshot in listOf(sample().copy(playing = false), sample().copy(buffering = true))) {
            val anchor = PageMediaPositionAnchor(snapshot, 0)
            assertEquals(snapshot.positionMs, anchor.positionAt(50_000))
            assertFalse(anchor.needsUpdate(snapshot, 50_000))
            assertTrue(anchor.needsUpdate(snapshot.copy(positionMs = 10_001), 50_000))
        }
    }

    @Test fun notificationAccessRequiresCurrentProcessTokenAndExactTab() {
        val snapshot = sample()
        val access = PageMediaNotificationAccess(snapshot, "unguessable-process-token")
        assertEquals(42L, access.tabId("unguessable-process-token", 42, snapshot))
        assertNull(access.tabId(null, 42, snapshot))
        assertNull(access.tabId("forged", 42, snapshot))
        assertNull(access.tabId("unguessable-process-token", 43, snapshot))
        assertNull(access.tabId("unguessable-process-token", 42, null))
        assertNull(PageMediaNotificationAccess(snapshot, "").tabId("", 42, snapshot))
    }

    @Test fun notificationAccessRejectsNavigationReplacementAndPrivacyTransitions() {
        val snapshot = sample()
        val access = PageMediaNotificationAccess(snapshot, "token")
        for (replacement in listOf(snapshot.copy(tabId = 43), snapshot.copy(ownerKey = "next"),
            snapshot.copy(pageUrl = "https://example.com/next"), snapshot.copy(isPrivate = true))) {
            assertNull(access.tabId("token", 42, replacement))
        }
        assertEquals(42L, access.tabId("token", 42, snapshot.copy(title = "Retitled", positionMs = 20_000)))
        assertNull(PageMediaNotificationAccess(snapshot, "new-token").tabId("token", 42, snapshot))
    }
}
