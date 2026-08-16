package com.miowser.app.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TabSessionCodecTest {

    @Test
    fun `round-trips a single tab with no opener`() {
        val original = listOf(SavedTab(url = "https://example.com", openerIndex = -1))
        val decoded = TabSessionCodec.decode(TabSessionCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `round-trips multiple tabs preserving opener indices`() {
        val original = listOf(
            SavedTab(url = "https://example.com", openerIndex = -1),
            SavedTab(url = "https://example.com/child", openerIndex = 0),
            SavedTab(url = "https://other.com", openerIndex = -1)
        )
        val decoded = TabSessionCodec.decode(TabSessionCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `sanitizes delimiter characters that would otherwise corrupt the record shape`() {
        // A URL containing a raw tab or newline (essentially never happens in practice, but
        // must not silently corrupt adjacent records if it ever does) gets space-substituted.
        val original = listOf(SavedTab(url = "https://example.com/a\tb\nc", openerIndex = -1))
        val encoded = TabSessionCodec.encode(original)
        val decoded = TabSessionCodec.decode(encoded)
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].url.none { it == '\t' || it == '\n' })
    }

    @Test
    fun `decode of empty string yields no tabs`() {
        assertTrue(TabSessionCodec.decode("").isEmpty())
    }

    @Test
    fun `decode skips malformed lines rather than throwing`() {
        val decoded = TabSessionCodec.decode("not-a-valid-record\n0\thttps://example.com")
        assertEquals(1, decoded.size)
        assertEquals("https://example.com", decoded[0].url)
    }
}
