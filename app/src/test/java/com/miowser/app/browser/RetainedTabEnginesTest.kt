package com.miowser.app.browser

import org.junit.Assert.*
import org.junit.Test

class RetainedTabEnginesTest {
    @Test fun retirementKeepsSixRecentEnginesAndProtectsVisibleTransfers() {
        val retired = mutableListOf<Long>()
        val owner = RetainedTabEngines<Long>(retired::add)
        (1L..30L).forEach { id -> owner.acquire(id) { id } }
        owner.touch(1)
        owner.retirementCandidates(6) { it != 29L && it != 30L }.forEach(owner::release)
        assertEquals(6, owner.size)
        assertEquals(listOf(26L, 27L, 28L, 29L, 30L, 1L), owner.oldestFirst)
        assertEquals((2L..25L).toList(), retired)
    }
    @Test fun pressureRetiresOnlyEligibleEnginesAndLookupDoesNotChangeLruOrder() {
        val owner = RetainedTabEngines<Long> { }
        (1L..4L).forEach { id -> owner.acquire(id) { id } }
        owner.peek(1)
        assertEquals(listOf(1L, 2L, 3L, 4L), owner.oldestFirst)
        assertEquals(listOf(2L), owner.retirementCandidates(1) { it == 2L })
    }
    @Test fun staleRendererCallbackCannotRetireReloadedEngine() {
        val retired = mutableListOf<Any>()
        val owner = RetainedTabEngines<Any>(retired::add)
        val failed = owner.acquire(1) { Any() }
        assertTrue(owner.release(1, failed))
        val reloaded = owner.acquire(1) { Any() }

        assertFalse(owner.release(1, failed))
        assertSame(reloaded, owner.peek(1))
        assertEquals(listOf(failed), retired)
    }

    @Test fun rendererCallbacksRetireOnlyReportedEnginesAndOnlyOnce() {
        val retired = mutableListOf<Any>()
        val owner = RetainedTabEngines<Any>(retired::add)
        val affectedA = owner.acquire(1) { Any() }
        val affectedB = owner.acquire(2) { Any() }
        val unaffected = owner.acquire(3) { Any() }

        assertTrue(owner.release(1, affectedA))
        assertSame(affectedB, owner.peek(2)) // wait for that view's own callback
        assertTrue(owner.release(2, affectedB))
        assertFalse(owner.release(2, affectedB))
        assertSame(unaffected, owner.peek(3))
        assertEquals(listOf(affectedA, affectedB), retired)
    }

    @Test fun dormantLookupAndCloseCreateNothing() {
        val retired = mutableListOf<Any>()
        val owner = RetainedTabEngines<Any>(retired::add)
        repeat(30) { assertNull(owner.peek(it.toLong())); owner.release(it.toLong()) }
        assertEquals(0, owner.size)
        assertTrue(retired.isEmpty())
    }
    @Test fun switchingKeepsEngineIdentityAndState() {
        val owner = RetainedTabEngines<MutableList<String>> { }
        val first = owner.acquire(1) { mutableListOf("page loaded once") }
        first.add("user input")
        owner.acquire(2) { mutableListOf("second page") }
        assertSame(first, owner.acquire(1) { error("must not reload a warm tab") })
        assertEquals(listOf("page loaded once", "user input"), owner.peek(1))
        assertEquals(2, owner.size)
    }
    @Test fun closingAndDisposalReleaseEachEngineOnce() {
        val retired = mutableListOf<String>()
        val owner = RetainedTabEngines<String>(retired::add)
        owner.acquire(1) { "one" }; owner.acquire(2) { "two" }
        owner.release(1); owner.release(1)
        owner.releaseAll(); owner.releaseAll()
        assertEquals(listOf("one", "two"), retired)
        assertEquals(0, owner.size)
    }
    @Test fun failedCreationLeavesWarmEnginesAndAllowsRetry() {
        val owner = RetainedTabEngines<Any> { }
        val warm = owner.acquire(1) { Any() }
        assertTrue(runCatching { owner.acquire(2) { error("renderer unavailable") } }.isFailure)
        assertEquals(1, owner.size)
        assertSame(warm, owner.peek(1))
        assertNotNull(owner.acquire(2) { Any() })
    }
    @Test fun dormantOpenerRelationshipsSurviveReorderingAndBadIndices() {
        val records = listOf(SavedTab("child", 2), SavedTab("self", 1), SavedTab("parent", -1), SavedTab("bad", 99))
        assertEquals(listOf(30L, null, null, null), restoredOpenerIds(records, listOf(10L, 20L, 30L, 40L)))
    }
}
