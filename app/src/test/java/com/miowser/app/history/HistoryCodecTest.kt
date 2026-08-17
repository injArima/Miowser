package com.miowser.app.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryCodecTest {

    private fun sample(
        url: String = "https://example.com",
        title: String = "Example",
        lastVisitedAt: Long = 1_700_000_000_000L,
        visitCount: Int = 1
    ) = HistoryEntry(url = url, title = title, lastVisitedAt = lastVisitedAt, visitCount = visitCount)

    @Test
    fun `round-trips a single entry`() {
        val original = listOf(sample())
        val decoded = HistoryCodec.decode(HistoryCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `round-trips multiple entries preserving visit counts`() {
        val original = listOf(
            sample(url = "https://example.com", visitCount = 1),
            sample(url = "https://example.com/child", title = "Child page", visitCount = 5),
            sample(url = "https://other.com", visitCount = 2)
        )
        val decoded = HistoryCodec.decode(HistoryCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `sanitizes delimiter characters that would otherwise corrupt the record shape`() {
        val original = listOf(sample(title = "Title\twith\ndelimiters"))
        val decoded = HistoryCodec.decode(HistoryCodec.encode(original))
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].title.none { it == '\t' || it == '\n' })
    }

    @Test
    fun `decode of empty string yields no entries`() {
        assertTrue(HistoryCodec.decode("").isEmpty())
    }

    @Test
    fun `decode skips malformed lines rather than throwing`() {
        val good = sample(url = "https://example.com/good")
        val decoded = HistoryCodec.decode("not-a-valid-record\n${HistoryCodec.encode(listOf(good))}")
        assertEquals(1, decoded.size)
        assertEquals(good, decoded[0])
    }
}
