package com.miowser.app.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FailedVideoCodecTest {
    @Test fun `log preserves all copyable details`() {
        val record = FailedVideoRecord(
            "one", "clip.m3u8", "Episode 1 日本語", "example.com",
            "https://example.com/watch?x=1&y=2", 1_000L,
            folderId = "anime", manualFolder = true, explicitlySaved = true
        )
        assertEquals(listOf(record), FailedVideoCodec.decode(FailedVideoCodec.encode(listOf(record))))
    }

    @Test fun `old six field records remain readable without placement`() {
        val record = FailedVideoRecord("one", "clip", "Page", "example.com", "https://example.com", 1L)
        val legacy = FailedVideoCodec.encode(listOf(record)).split('\t').take(6).joinToString("\t")
        assertEquals(record, FailedVideoCodec.decode(legacy).single())
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

    @Test fun `replacement selection uses the same page and strongest media identity`() {
        val first = FailedVideoRecord("one", "episode-1.m3u8", "Episode 1", "example.com",
            "https://example.com/watch/1", 1L, folderId = "anime")
        val other = FailedVideoRecord("two", "trailer.m3u8", "Trailer", "example.com",
            "https://example.com/watch/1", 2L, folderId = "trailers")
        val incoming = LibraryEntry(
            mediaUri = "https://cdn.example.com/renewed.m3u8",
            pageUrl = "https://example.com/watch/1#player",
            title = "Episode 1",
            sourceLabel = "example.com",
            sourceKind = LibrarySourceKind.REMOTE_STREAM,
            mediaType = null,
            userAgent = null,
            thumbnailUrl = null,
            lastPlayedAt = 3L,
            positionMs = 0L,
            durationMs = 0L,
            pageTitle = "Episode 1",
            videoName = "episode-1.m3u8"
        )
        assertEquals(first, selectFailedVideoReplacement(listOf(other, first), incoming))
        assertNull(selectFailedVideoReplacement(listOf(first, first.copy(id = "duplicate")), incoming))
    }

    @Test fun `replacement inherits valid manual folder and saved state`() {
        val incoming = LibraryEntry(
            mediaUri = "https://cdn.example.com/new.m3u8",
            pageUrl = "https://example.com/watch/1",
            title = "Episode",
            sourceLabel = "example.com",
            sourceKind = LibrarySourceKind.REMOTE_STREAM,
            mediaType = null,
            userAgent = null,
            thumbnailUrl = null,
            lastPlayedAt = 1L,
            positionMs = 0L,
            durationMs = 0L,
            explicitlySaved = false
        )
        val placement = FailedVideoPlacement("anime", manualFolder = true, explicitlySaved = true)
        val (restored, preserved) = applyFailedVideoPlacement(incoming, placement, setOf("anime"))
        assertTrue(preserved)
        assertEquals("anime", restored.folderId)
        assertTrue(restored.manualFolder)
        assertTrue(restored.explicitlySaved)

        val (fallback, missingPreserved) = applyFailedVideoPlacement(incoming, placement, emptySet())
        assertTrue(!missingPreserved)
        assertNull(fallback.folderId)
    }
}
