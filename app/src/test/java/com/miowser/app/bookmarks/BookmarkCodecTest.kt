package com.miowser.app.bookmarks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookmarkCodecTest {

    private fun sample(
        url: String = "https://example.com",
        title: String = "Example",
        addedAt: Long = 1_700_000_000_000L
    ) = Bookmark(url = url, title = title, addedAt = addedAt)

    @Test
    fun `round-trips a single bookmark`() {
        val original = listOf(sample())
        val decoded = BookmarkCodec.decode(BookmarkCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `round-trips multiple bookmarks`() {
        val original = listOf(
            sample(url = "https://example.com", title = "Example"),
            sample(url = "https://example.com/child", title = "Child page"),
            sample(url = "https://other.com", title = "Other")
        )
        val decoded = BookmarkCodec.decode(BookmarkCodec.encode(original))
        assertEquals(original, decoded)
    }

    @Test
    fun `sanitizes delimiter characters that would otherwise corrupt the record shape`() {
        val original = listOf(sample(title = "Title\twith\ndelimiters"))
        val decoded = BookmarkCodec.decode(BookmarkCodec.encode(original))
        assertEquals(1, decoded.size)
        assertTrue(decoded[0].title.none { it == '\t' || it == '\n' })
    }

    @Test
    fun `decode of empty string yields no bookmarks`() {
        assertTrue(BookmarkCodec.decode("").isEmpty())
    }

    @Test
    fun `decode skips malformed lines rather than throwing`() {
        val good = sample(url = "https://example.com/good")
        val decoded = BookmarkCodec.decode("not-a-valid-record\n${BookmarkCodec.encode(listOf(good))}")
        assertEquals(1, decoded.size)
        assertEquals(good, decoded[0])
    }
}
