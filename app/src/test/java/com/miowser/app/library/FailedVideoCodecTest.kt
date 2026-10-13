package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedVideoCodecTest {
    @Test fun `log preserves all copyable details`() {
        val record = FailedVideoRecord(
            "one", "clip.m3u8", "Episode 1 日本語", "example.com",
            "https://example.com/watch?x=1&y=2", 1_000L
        )
        assertEquals(listOf(record), FailedVideoCodec.decode(FailedVideoCodec.encode(listOf(record))))
    }

    @Test fun `copy action timestamp extends the ninety day lifetime`() {
        val day = 24L * 60L * 60L * 1_000L
        val record = FailedVideoRecord("one", "clip", "Page", "example.com", "https://example.com", 0L)
        assertTrue(FailedVideoCodec.retained(listOf(record), 91L * day).isEmpty())
        assertEquals(listOf(record.copy(lastActionAt = 90L * day)),
            FailedVideoCodec.retained(listOf(record.copy(lastActionAt = 90L * day)), 91L * day))
    }

    @Test fun `moving a recent record preserves its text and removes it from the expiring list`() {
        val record = FailedVideoRecord("one", "clip", "Episode", "example.com", "https://example.com/watch", 1L)
        val moved = moveVideoRecord(listOf(record), emptyList(), "one", 200L)!!
        assertTrue(moved.first.isEmpty())
        assertEquals(listOf(record.copy(lastActionAt = 200L)), moved.second)
        assertNull(moveVideoRecord(moved.first, moved.second, "one", 300L))
    }

    @Test fun `moving an already saved source does not create another copy`() {
        val recent = FailedVideoRecord("recent", "clip", "Episode", "example.com", "https://example.com/watch", 1L)
        val saved = recent.copy(id = "saved", lastActionAt = 100L)
        assertEquals(emptyList<FailedVideoRecord>() to listOf(saved),
            moveVideoRecord(listOf(recent), listOf(saved), "recent", 200L))
    }

    @Test fun `library reference keeps only the usual textual details`() {
        val entry = LibraryEntry(
            mediaUri = "https://cdn.example.com/video.m3u8",
            pageUrl = "https://www.example.com/watch/1",
            title = "Library title",
            sourceLabel = "example.com",
            sourceKind = LibrarySourceKind.REMOTE_STREAM,
            mediaType = null,
            userAgent = null,
            thumbnailUrl = "thumbnail",
            lastPlayedAt = 1L,
            positionMs = 2L,
            durationMs = 3L,
            pageTitle = "Episode page",
            videoName = "clip.m3u8"
        )
        val record = savedReferenceFromLibrary(entry, 100L)
        assertEquals("clip.m3u8", record.videoName)
        assertEquals("Episode page", record.pageTitle)
        assertEquals("example.com", record.domain)
        assertEquals(entry.pageUrl, record.pageUrl)
        assertEquals(100L, record.lastActionAt)
    }
}
