package com.miowser.app.browser.video

import org.junit.Assert.*
import org.junit.Test

class BrowserVideoRecoveryTest {
    private val owner = BrowserVideoPipOwner(LiveVideoId(1, 2, 3, "doc", "video"), 4)

    @Test fun foregroundCallbacksDoNotStartUnrequestedRecovery() {
        val recovery = BrowserVideoRecovery()
        assertNull(recovery.resume(owner)); assertFalse(recovery.accepts(owner, recovery.requestId))
    }

    @Test fun onlyTheCurrentRequestAndExactEntryCanResume() {
        val recovery = BrowserVideoRecovery(); recovery.pause(); val ticket = recovery.resume(owner)!!
        assertTrue(recovery.accepts(owner, ticket))
        assertNull(recovery.resume(owner)); assertTrue(recovery.accepts(owner, ticket))
        assertFalse(recovery.accepts(owner, ticket - 1))
        assertFalse(recovery.accepts(owner.copy(attempt = 5), ticket))
        assertFalse(recovery.accepts(owner.copy(id = owner.id.copy(navigation = 9)), ticket))
        assertFalse(recovery.accepts(owner.copy(id = owner.id.copy(elementId = "other")), ticket))
        assertFalse(recovery.accepts(owner.copy(id = owner.id.copy(frameId = 9)), ticket))
    }

    @Test fun anotherPauseInvalidatesTheEarlierReplyAndItsDeadline() {
        val recovery = BrowserVideoRecovery(); recovery.pause(); val old = recovery.resume(owner)!!
        recovery.pause(); assertFalse(recovery.accepts(owner, old))
        val next = recovery.resume(owner)!!
        assertNotEquals(old, next); assertFalse(recovery.accepts(owner, old)); assertTrue(recovery.accepts(owner, next))
    }

    @Test fun exitAndNavigationInvalidateRepliesEvenForTheSameElement() {
        val recovery = BrowserVideoRecovery(); recovery.pause(); val old = recovery.resume(owner)!!
        recovery.cancel(); assertFalse(recovery.accepts(owner, old))
        recovery.pause(); val next = recovery.resume(owner.copy(attempt = 5))!!
        assertFalse(recovery.accepts(owner, next)); assertTrue(recovery.accepts(owner.copy(attempt = 5), next))
    }

    @Test fun tabsWithoutAnOwnedFullscreenSessionDoNotAcquireOneOnResume() {
        val recovery = BrowserVideoRecovery(); recovery.pause()
        assertNull(recovery.resume(null)); assertFalse(recovery.paused)
        assertNull(recovery.resume(owner)); assertFalse(recovery.accepts(owner, recovery.requestId))
    }

    @Test fun fullscreenCallbackClosesOnceEvenWhenCloseReenters() {
        var calls = 0
        lateinit var lease: FullscreenCallbackLease
        lease = FullscreenCallbackLease { calls++; lease.close() }
        lease.close(); lease.close()
        assertEquals(1, calls); assertTrue(lease.closed)
    }

    @Test fun callbackFailureDoesNotAllowTheSameViewToCloseAgain() {
        var calls = 0
        val lease = FullscreenCallbackLease { calls++; error("provider callback failed") }
        assertTrue(runCatching { lease.close() }.isFailure)
        lease.close(); assertEquals(1, calls); assertTrue(lease.closed)
    }
}
