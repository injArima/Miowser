package com.miowser.app.adblock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterSubscriptionCodecTest {
    private val custom = FilterSubscription(
        id = "custom-1",
        name = "My filters",
        url = "https://example.com/filters.txt",
        isBuiltIn = false,
        enabled = true,
        lastUpdatedAt = 1234L,
        lastError = null,
        networkRuleCount = 12,
        cosmeticRuleCount = 3
    )

    @Test
    fun `round trips subscription metadata`() {
        assertEquals(listOf(custom), FilterSubscriptionCodec.decode(FilterSubscriptionCodec.encode(listOf(custom))))
    }

    @Test
    fun `preserves disabled and failed state`() {
        val failed = custom.copy(enabled = false, lastError = "HTTP 404")
        val decoded = FilterSubscriptionCodec.decode(FilterSubscriptionCodec.encode(listOf(failed))).single()
        assertFalse(decoded.enabled)
        assertEquals("HTTP 404", decoded.lastError)
    }

    @Test
    fun `sanitizes record delimiters in user controlled fields`() {
        val unsafe = custom.copy(name = "My\tlist\nname", lastError = "bad\nresponse")
        val decoded = FilterSubscriptionCodec.decode(FilterSubscriptionCodec.encode(listOf(unsafe))).single()
        assertTrue(decoded.name.none { it == '\t' || it == '\n' })
        assertTrue(decoded.lastError.orEmpty().none { it == '\t' || it == '\n' })
    }

    @Test
    fun `decode skips malformed records`() {
        val valid = FilterSubscriptionCodec.encode(listOf(custom))
        val decoded = FilterSubscriptionCodec.decode("broken\n$valid\nwrong\tfield\tcount")
        assertEquals(listOf(custom), decoded)
    }

    @Test
    fun `decode of empty string is empty`() {
        assertTrue(FilterSubscriptionCodec.decode("").isEmpty())
    }

    @Test
    fun `renamed built in list keeps its required source and enabled state`() {
        val renamed = FilterSubscription(EASYLIST_ID, "My ads list", EASYLIST_URL,
            isBuiltIn = true, enabled = true, lastUpdatedAt = 1234L)
        val loaded = FilterSubscriptionStore.defaultSubscriptions(
            FilterSubscriptionCodec.decode(FilterSubscriptionCodec.encode(listOf(renamed))))
        val easyList = loaded.first { it.id == EASYLIST_ID }

        assertEquals("My ads list", easyList.name)
        assertEquals(EASYLIST_URL, easyList.url)
        assertTrue(easyList.enabled)
        assertEquals(1234L, easyList.lastUpdatedAt)
    }
}
