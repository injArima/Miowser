package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionTabCleanupTest {
    @Test
    fun `unused tabs close at twenty five seconds and never before`() {
        val cleanup = cleanup(1)
        assertEquals(25_000L, cleanup.update(setOf(1), emptySet(), 0).nextDelayMs)
        assertTrue(cleanup.update(setOf(1), emptySet(), 24_999).expiredIds.isEmpty())
        assertEquals(setOf(1L), cleanup.update(setOf(1), emptySet(), 25_000).expiredIds)
    }

    @Test
    fun `returning to a tab cancels its deadline and leaving starts a fresh grace period`() {
        val cleanup = cleanup(1)
        cleanup.update(setOf(1), emptySet(), 0)
        assertEquals(null, cleanup.update(setOf(1), setOf(1), 24_000).nextDelayMs)
        assertTrue(cleanup.update(setOf(1), setOf(1), 100_000).expiredIds.isEmpty())
        assertEquals(25_000L, cleanup.update(setOf(1), emptySet(), 101_000).nextDelayMs)
        assertTrue(cleanup.update(setOf(1), emptySet(), 125_999).expiredIds.isEmpty())
        assertEquals(setOf(1L), cleanup.update(setOf(1), emptySet(), 126_000).expiredIds)
    }

    @Test
    fun `new abandoned tabs do not extend the deadline of older tabs`() {
        val cleanup = cleanup(1, 2)
        cleanup.update(setOf(1), emptySet(), 0)
        assertEquals(15_000L, cleanup.update(setOf(1, 2), emptySet(), 10_000).nextDelayMs)
        val first = cleanup.update(setOf(1, 2), emptySet(), 25_000)
        assertEquals(setOf(1L), first.expiredIds)
        assertEquals(10_000L, first.nextDelayMs)
        assertEquals(setOf(2L), cleanup.update(setOf(2), emptySet(), 35_000).expiredIds)
    }

    @Test
    fun `player and pane tabs stay protected while other abandoned tabs expire`() {
        val cleanup = cleanup(1, 2, 3)
        cleanup.update(setOf(1, 2, 3), setOf(1, 2), 0)
        assertEquals(setOf(3L), cleanup.update(setOf(1, 2, 3), setOf(1, 2), 30_000).expiredIds)
    }

    @Test
    fun `removed tabs are forgotten and unrelated browser tabs are never scheduled`() {
        val cleanup = cleanup(1)
        cleanup.update(setOf(1), emptySet(), 0)
        assertEquals(null, cleanup.update(emptySet(), setOf(9), 10_000).nextDelayMs)
        assertTrue(cleanup.update(emptySet(), emptySet(), 30_000).expiredIds.isEmpty())
        assertEquals(25_000L, cleanup.update(setOf(1), emptySet(), 40_000).nextDelayMs)
    }

    @Test
    fun `ordinary source tab is excluded even when a handoff nominates it for cleanup`() {
        val cleanup = cleanup(2)
        assertEquals(setOf(2L), cleanup.ownedCandidates(setOf(1, 2)))
        cleanup.update(setOf(1, 2), emptySet(), 0)
        assertEquals(setOf(2L), cleanup.update(setOf(1, 2), emptySet(), 25_000).expiredIds)
        assertEquals(null, cleanup.update(setOf(1), emptySet(), 100_000).nextDelayMs)
    }

    @Test
    fun `only side pane tabs expire across repeated playback handoffs`() {
        val cleanup = cleanup(2, 3)
        cleanup.update(setOf(1, 2), setOf(2), 0)
        assertTrue(cleanup.update(setOf(1, 2), setOf(2), 30_000).expiredIds.isEmpty())
        cleanup.update(setOf(1, 2, 3), setOf(3), 40_000)
        assertEquals(setOf(2L), cleanup.update(setOf(1, 2, 3), setOf(3), 65_000).expiredIds)
    }

    @Test
    fun `closing a side pane tab forgets its ownership and timer`() {
        val cleanup = cleanup(2)
        cleanup.update(setOf(2), emptySet(), 0)
        cleanup.retainTabs(setOf(1))
        assertTrue(cleanup.ownedCandidates(setOf(1, 2)).isEmpty())
        assertEquals(null, cleanup.update(setOf(1, 2), emptySet(), 30_000).nextDelayMs)
    }

    private fun cleanup(vararg ids: Long) = CompanionTabCleanup().apply { ids.forEach(::register) }
}
