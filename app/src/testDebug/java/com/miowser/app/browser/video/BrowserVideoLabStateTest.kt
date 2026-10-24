package com.miowser.app.browser.video

import org.junit.Assert.*
import org.junit.Test

class BrowserVideoLabStateTest {
    @Test fun oldDocumentCannotTakeOwnershipAfterNavigation() {
        val lease = BrowserVideoLabLease()
        val old = lease.reset()
        assertTrue(lease.accept(old, "old", 0))
        val current = lease.reset()
        assertFalse(lease.accept(old, "old", 100))
        assertTrue(lease.accept(current, "new", 0))
        assertEquals("new", lease.documentId)
    }

    @Test fun messagesCannotSwitchDocumentsWithinOneFixture() {
        val lease = BrowserVideoLabLease()
        val generation = lease.reset()
        assertTrue(lease.accept(generation, "frame-one", 0))
        assertFalse(lease.accept(generation, "frame-two", 1))
        assertTrue(lease.accept(generation, "frame-one", 1))
    }

    @Test fun ignoresReplayedOutOfOrderAndMalformedMessages() {
        val lease = BrowserVideoLabLease()
        val generation = lease.reset()
        assertFalse(lease.accept(generation, "", 0))
        assertFalse(lease.accept(generation, "x".repeat(81), 0))
        assertFalse(lease.accept(generation, "video", -1))
        assertTrue(lease.accept(generation, "video", 3))
        assertFalse(lease.accept(generation, "video", 3))
        assertFalse(lease.accept(generation, "video", 2))
        assertTrue(lease.accept(generation, "video", 4))
    }

    @Test fun seeksAreClampedToTheActualSeekWindow() {
        val sample = sample(5_000, 40_000)
        assertEquals(5_000L, sample.seekTarget(-10_000))
        assertEquals(40_000L, sample.seekTarget(60_000))
        assertEquals(25_000L, sample.seekTarget(25_000))
    }

    @Test fun unseekableMediaNeverGetsASeekCommand() {
        assertNull(sample(null, null).seekTarget(10_000))
        assertNull(sample(0, 0).seekTarget(10_000))
        assertNull(sample(10_000, 5_000).seekTarget(10_000))
    }

    private fun sample(start: Long?, end: Long?) = BrowserVideoLabSample("video", 1, 10_000,
        45_000, 40_000, true, false, 1.0, 320, 180, start, end, "MediaSource / blob", 1, 1)
}
