package com.miowser.app.player

import com.miowser.app.grabber.MediaType
import com.miowser.app.library.LibrarySourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackLibraryTest {
    @Test
    fun `remote playback maps to a persistent library entry without cookies`() {
        val request = PlaybackRequest.Remote(
            url = "https://cdn.example.com/master.m3u8",
            pageUrl = "https://example.com/watch/1",
            userAgent = "Test agent",
            cookies = "session=secret",
            mediaType = MediaType.HLS,
            title = "Example",
            sourceLabel = "example.com",
            pageTitle = "Example watch page",
            videoName = "clip.m3u8",
            startPositionMs = 12_000L
        )

        val entry = request.toLibraryEntry(playedAt = 123L, durationMs = 456L)

        assertEquals(LibrarySourceKind.REMOTE_STREAM, entry.sourceKind)
        assertEquals(request.url, entry.mediaUri)
        assertEquals(request.pageUrl, entry.pageUrl)
        assertEquals(MediaType.HLS, entry.mediaType)
        assertEquals("Test agent", entry.userAgent)
        assertEquals(123L, entry.lastPlayedAt)
        assertEquals(456L, entry.durationMs)
        assertEquals(12_000L, entry.positionMs)
        assertEquals("Example watch page", entry.pageTitle)
        assertEquals("clip.m3u8", entry.videoName)
        assertNull(entry.thumbnailUrl)
    }

    @Test
    fun `library remote playback uses fresh cookies and resumes unfinished media`() {
        val entry = PlaybackRequest.Remote(
            url = "https://cdn.example.com/master.m3u8",
            pageUrl = "https://example.com/watch/1",
            userAgent = "Stored agent",
            cookies = null,
            mediaType = MediaType.HLS,
            title = "Example",
            sourceLabel = "example.com"
        ).toLibraryEntry(durationMs = 600_000L).copy(positionMs = 120_000L)

        val request = entry.toPlaybackRequest(
            freshCookies = "fresh=session",
            fallbackUserAgent = "Fallback agent"
        ) as PlaybackRequest.Remote

        assertEquals("fresh=session", request.cookies)
        assertEquals("Stored agent", request.userAgent)
        assertEquals(120_000L, request.startPositionMs)
    }

    @Test
    fun `completed media restarts while unfinished media resumes`() {
        assertEquals(120_000L, libraryResumePosition(120_000L, 600_000L))
        assertEquals(0L, libraryResumePosition(580_000L, 600_000L))
        assertEquals(15_000L, libraryResumePosition(15_000L, 0L))
    }
}
