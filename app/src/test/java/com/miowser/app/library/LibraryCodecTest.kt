package com.miowser.app.library

import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.HlsRendition
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
            folderId = "folder-1",
            qualityHeight = 1080
        )
        assertEquals(entry, LibraryCodec.decode(LibraryCodec.encode(listOf(entry))).single())
    }

    @Test
    fun `embedded stream request context survives restart and older records remain readable`() {
        val entry = remote().copy(
            requestReferer = "https://player.example.org/embed/episode",
            requestOrigin = "https://player.example.org"
        )
        assertEquals(entry, LibraryCodec.decode(LibraryCodec.encode(listOf(entry))).single())

        val old = LibraryCodec.encode(listOf(entry)).split('\t').take(15).joinToString("\t")
        val restored = LibraryCodec.decode(old).single()
        assertNull(restored.requestReferer)
        assertNull(restored.requestOrigin)
    }

    @Test
    fun `previous fourteen field records still load without quality`() {
        val old = LibraryCodec.encode(listOf(remote().copy(folderId = "folder-1")))
            .split('\t').take(14).joinToString("\t")
        val decoded = LibraryCodec.decode(old).single()
        assertEquals("folder-1", decoded.folderId)
        assertNull(decoded.qualityHeight)
        assertTrue(decoded.manualFolder)
    }

    @Test fun `old filed entries stay pinned while new records preserve manual moves`() {
        val filed = remote().copy(folderId = "chosen", manualFolder = true)
        assertEquals(filed, LibraryCodec.decode(LibraryCodec.encode(listOf(filed))).single())
        val old = LibraryCodec.encode(listOf(filed)).split('\t').take(22).joinToString("\t")
        assertTrue(LibraryCodec.decode(old).single().manualFolder)
        assertTrue(mergeOpened(filed, remote()).manualFolder)
    }

    @Test
    fun `quality survives reopening and portrait videos use short edge`() {
        val existing = remote().copy(qualityHeight = 720)
        assertEquals(720, mergeOpened(existing, remote()).qualityHeight)
        assertEquals(1080, videoQualityHeight(1080, 1920))
        assertEquals("1080p", videoQualityLabel(1080))
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

    @Test
    fun `selected subtitle survives restart and reopening`() {
        val subtitle = LibrarySubtitle(
            url = "https://cdn.example.com/eng.vtt",
            pageUrl = "https://example.com/watch/1",
            label = "English",
            requestReferer = "https://player.example.com/embed/1",
            requestOrigin = "https://player.example.com"
        )
        val saved = remote().copy(selectedSubtitle = subtitle)
        assertEquals(saved, LibraryCodec.decode(LibraryCodec.encode(listOf(saved))).single())
        assertEquals(subtitle, mergeOpened(saved, remote()).selectedSubtitle)
    }

    @Test
    fun `adaptive HLS renditions survive restart and reopening`() {
        val renditions = listOf(
            HlsRendition("https://cdn.example.com/1080/index.m3u8", "1920x1080", 5_000_000),
            HlsRendition("https://cdn.example.com/720/index.m3u8", "1280x720", 2_500_000)
        )
        val saved = remote().copy(hlsRenditions = renditions)
        assertEquals(saved, LibraryCodec.decode(LibraryCodec.encode(listOf(saved))).single())
        assertEquals(renditions, mergeOpened(saved, remote()).hlsRenditions)

        val previousRecord = LibraryCodec.encode(listOf(saved)).split('\t').take(24).joinToString("\t")
        assertTrue(LibraryCodec.decode(previousRecord).single().hlsRenditions.isEmpty())
    }

    @Test
    fun `previous seventeen field records remain readable`() {
        val saved = remote().copy(requestOrigin = "https://example.com")
        val old = LibraryCodec.encode(listOf(saved)).split('\t').take(17).joinToString("\t")
        val restored = LibraryCodec.decode(old).single()
        assertEquals(saved.requestOrigin, restored.requestOrigin)
        assertNull(restored.selectedSubtitle)
    }
}
