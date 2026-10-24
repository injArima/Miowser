package com.miowser.app.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultFilterSourcesTest {
    @Test
    fun `new defaults keep existing EasyList metadata and use requested sources`() {
        val previous = FilterSubscription(EASYLIST_ID, "EasyList", EASYLIST_URL,
            isBuiltIn = true, enabled = true, lastUpdatedAt = 42L, networkRuleCount = 15)
        val defaults = FilterSubscriptionStore.defaultSubscriptions(listOf(previous))

        assertEquals(listOf(PETER_LOWE_ID, EASYLIST_ID, EASYPRIVACY_ID), defaults.map { it.id })
        assertEquals(listOf(PETER_LOWE_URL, EASYLIST_URL, EASYPRIVACY_URL), defaults.map { it.url })
        assertEquals(42L, defaults.single { it.id == EASYLIST_ID }.lastUpdatedAt)
        assertEquals(15, defaults.single { it.id == EASYLIST_ID }.networkRuleCount)
        assertTrue(defaults.all { it.isBuiltIn && it.enabled })
    }

    @Test
    fun `changed PHP source URL is scheduled for refresh`() {
        val old = FilterSubscription(PETER_LOWE_ID, "Peter Lowe's ad and tracking servers",
            "https://pgl.yoyo.org/adservers/serverlist.php", isBuiltIn = true, enabled = true,
            lastUpdatedAt = 42L, networkRuleCount = 500)

        val updated = FilterSubscriptionStore.defaultSubscriptions(listOf(old))
            .single { it.id == PETER_LOWE_ID }

        assertEquals(PETER_LOWE_URL, updated.url)
        assertEquals(0L, updated.lastUpdatedAt)
        assertEquals(0, updated.networkRuleCount)
    }
}
