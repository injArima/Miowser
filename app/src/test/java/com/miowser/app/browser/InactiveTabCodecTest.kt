package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InactiveTabCodecTest {
    @Test
    fun `round trips lightweight inactive records`() {
        val records = listOf(
            InactiveTabRecord("one", "Example", "https://example.com", 100L, 200L, InactiveTabReason.UNUSED),
            InactiveTabRecord("two", "Copy", "https://example.com/copy", 300L, 400L, InactiveTabReason.DUPLICATE)
        )
        assertEquals(records, InactiveTabCodec.decode(InactiveTabCodec.encode(records)))
    }

    @Test
    fun `malformed records are skipped`() {
        assertTrue(InactiveTabCodec.decode("broken\nrecord").isEmpty())
    }

    @Test
    fun `record text cannot corrupt adjacent rows`() {
        val record = InactiveTabRecord("id", "A\tB\nC", "https://example.com", 1, 2, InactiveTabReason.UNUSED)
        val decoded = InactiveTabCodec.decode(InactiveTabCodec.encode(listOf(record))).single()
        assertTrue(decoded.title.none { it == '\t' || it == '\n' })
    }
}
