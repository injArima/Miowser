package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InactiveTabPolicyTest {
    private val now = 2_000_000_000_000L

    @Test
    fun `archives a non-active normal tab at fourteen days`() {
        val tab = tab(1, lastActiveAt = now - INACTIVE_TAB_AGE_MILLIS)
        assertEquals(
            listOf(InactiveTabDecision(1, InactiveTabReason.UNUSED)),
            InactiveTabPolicy.decisions(listOf(tab), now)
        )
    }

    @Test
    fun `protects active private and home tabs from age policy`() {
        val old = now - INACTIVE_TAB_AGE_MILLIS - 1
        val tabs = listOf(
            tab(1, old, isActive = true),
            tab(2, old, isPrivate = true),
            tab(3, old, isHome = true)
        )
        assertTrue(InactiveTabPolicy.decisions(tabs, now).isEmpty())
    }

    @Test
    fun `duplicate policy keeps active tab and archives other copies`() {
        val tabs = listOf(
            tab(1, now - 20, url = "https://EXAMPLE.com:443/page#top"),
            tab(2, now - 10, url = "https://example.com/page", isActive = true),
            tab(3, now, url = "https://other.example/")
        )
        assertEquals(
            listOf(InactiveTabDecision(1, InactiveTabReason.DUPLICATE)),
            InactiveTabPolicy.decisions(tabs, now)
        )
    }

    @Test
    fun `duplicate policy keeps most recently active copy when group is in background`() {
        val tabs = listOf(tab(1, now - 20), tab(2, now - 10))
        assertEquals(listOf(1L), InactiveTabPolicy.decisions(tabs, now).map { it.tabId })
    }

    @Test
    fun `normalization preserves query while removing fragment and default port`() {
        assertEquals(
            "https://example.com/watch?id=7",
            normalizeTabUrl("HTTPS://Example.COM:443/watch?id=7#player")
        )
    }

    @Test
    fun `retention expires at thirty days and not before`() {
        val archived = now - INACTIVE_TAB_RETENTION_MILLIS
        assertTrue(InactiveTabPolicy.isExpired(archived, now))
        assertFalse(InactiveTabPolicy.isExpired(archived + 1, now))
    }

    private fun tab(
        id: Long,
        lastActiveAt: Long,
        url: String = "https://example.com/page",
        isPrivate: Boolean = false,
        isHome: Boolean = false,
        isActive: Boolean = false
    ) = LiveTabSummary(id, url, lastActiveAt, isPrivate, isHome, isActive)
}
