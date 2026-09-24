package com.miowser.app.library

import com.miowser.app.grabber.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryCodecTest {
    private fun remote(
        mediaUri: String = "https://cdn.example.com/master.m3u8",
        title: String = "Example video",
        positionMs: Long = 42_000L,
        durationMs: Long = 600_000L
    ) = LibraryEntry(
        mediaUri = mediaUri,
        pageUrl = "https://example.com/watch/1",
        title = title,
        sourceLabel = "example.com",
        sourceKind = LibrarySourceKind.REMOTE_STREAM,
        mediaType = MediaType.HLS,
        userAgent = "Miowser test agent",
        thumbnailUrl = "https://example.com/poster.jpg",
        lastPlayedAt = 1_700_000_000_000L,
        positionMs = positionMs,
        durationMs = durationMs
    )

    @Test
    fun `round-trips remote and local entries`() {
        val entries = listOf(
            remote(),
            LibraryEntry(
                mediaUri = "content://media/external/video/42",
                pageUrl = null,
                title = "Downloaded video",
                sourceLabel = "Downloaded",
                sourceKind = LibrarySourceKind.LOCAL_FILE,
                mediaType = null,
                userAgent = null,
                thumbnailUrl = null,
                lastPlayedAt = 1_700_000_100_000L,
                positionMs = 0L,
                durationMs = 0L
            )
        )

        assertEquals(entries, LibraryCodec.decode(LibraryCodec.encode(entries)))
    }

    @Test
    fun `sanitizes record delimiters`() {
        val decoded = LibraryCodec.decode(LibraryCodec.encode(listOf(remote(title = "Title\twith\nlines"))))
        assertEquals(1, decoded.size)
        assertTrue(decoded.single().title.none { it == '\t' || it == '\n' })
    }

    @Test
    fun `empty input yields no entries`() {
        assertTrue(LibraryCodec.decode("").isEmpty())
    }

    @Test
    fun `malformed records are skipped`() {
        val good = remote()
        val decoded = LibraryCodec.decode("broken\n${LibraryCodec.encode(listOf(good))}")
        assertEquals(listOf(good), decoded)
    }

    @Test
    fun `reopening uses the actual new start point while preserving optional metadata`() {
        val existing = remote()
        val reopened = existing.copy(
            title = "Updated title",
            mediaType = null,
            userAgent = null,
            thumbnailUrl = null,
            lastPlayedAt = existing.lastPlayedAt + 1000,
            positionMs = 0L,
            durationMs = 0L
        )

        val merged = mergeOpened(existing, reopened)

        assertEquals("Updated title", merged.title)
        assertEquals(0L, merged.positionMs)
        assertEquals(existing.durationMs, merged.durationMs)
        assertEquals(MediaType.HLS, merged.mediaType)
        assertEquals(existing.thumbnailUrl, merged.thumbnailUrl)
    }

    @Test
    fun `local records do not invent remote metadata`() {
        val local = LibraryEntry(
            mediaUri = "content://media/1",
            pageUrl = null,
            title = "Local",
            sourceLabel = "Downloaded",
            sourceKind = LibrarySourceKind.LOCAL_FILE,
            mediaType = null,
            userAgent = null,
            thumbnailUrl = null,
            lastPlayedAt = 1L,
            positionMs = 0L,
            durationMs = 0L
        )
        val decoded = LibraryCodec.decode(LibraryCodec.encode(listOf(local))).single()
        assertNull(decoded.pageUrl)
        assertNull(decoded.mediaType)
        assertNull(decoded.userAgent)
    }

    @Test
    fun `old eleven field Library records still load`() {
        val old = LibraryCodec.encode(listOf(remote())).split('\t').take(11).joinToString("\t")
        val decoded = LibraryCodec.decode(old).single()
        assertEquals("Example video", decoded.title)
        assertNull(decoded.pageTitle)
        assertNull(decoded.folderId)
    }

    @Test
    fun `folder and page metadata survive restart encoding`() {
        val entry = remote().copy(
            pageTitle = "Watch page",
            videoName = "clip.mp4",
            folderId = "folder-1"
        )
        assertEquals(entry, LibraryCodec.decode(LibraryCodec.encode(listOf(entry))).single())
    }

    @Test
    fun `reopening a video preserves its manual folder and captured names`() {
        val existing = remote().copy(folderId = "manual-1", pageTitle = "Episode page", videoName = "episode.mp4")
        val reopened = remote().copy(folderId = null, pageTitle = null, videoName = null)
        val merged = mergeOpened(existing, reopened)
        assertEquals("manual-1", merged.folderId)
        assertEquals("Episode page", merged.pageTitle)
        assertEquals("episode.mp4", merged.videoName)
    }
}
