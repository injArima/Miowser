package com.miowser.app.player

import com.miowser.app.grabber.MediaType
import com.miowser.app.library.LibrarySubtitle
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
            requestReferer = "https://player.example.org/embed/episode",
            requestOrigin = "https://player.example.org",
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
        assertEquals(request.requestReferer, entry.requestReferer)
        assertEquals(request.requestOrigin, entry.requestOrigin)
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
            requestReferer = "https://player.example.org/embed/episode",
            requestOrigin = "https://player.example.org",
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
        assertEquals(entry.requestReferer, request.requestReferer)
        assertEquals(entry.requestOrigin, request.requestOrigin)
    }

    @Test
    fun `completed media restarts while unfinished media resumes`() {
        assertEquals(120_000L, libraryResumePosition(120_000L, 600_000L))
        assertEquals(0L, libraryResumePosition(580_000L, 600_000L))
        assertEquals(15_000L, libraryResumePosition(15_000L, 0L))
    }

    @Test
    fun `saved VTT is offered and selected when reopening remote media`() {
        val subtitle = LibrarySubtitle(
            url = "https://cdn.example.com/english.vtt",
            pageUrl = "https://example.com/watch/1",
            label = "English",
            requestReferer = "https://player.example.com/embed/1",
            requestOrigin = "https://player.example.com"
        )
        val entry = PlaybackRequest.Remote(
            url = "https://cdn.example.com/video.m3u8",
            pageUrl = subtitle.pageUrl,
            userAgent = "Test agent",
            cookies = null,
            mediaType = MediaType.HLS,
            title = "Example",
            sourceLabel = "example.com"
        ).toLibraryEntry().copy(selectedSubtitle = subtitle)

        val reopened = entry.toPlaybackRequest(null, "Fallback") as PlaybackRequest.Remote
        assertEquals(subtitle.url, reopened.preferredSubtitleUrl)
        assertEquals(subtitle.url, reopened.externalSubtitles.single().url)
        assertEquals(subtitle.requestReferer, reopened.externalSubtitles.single().requestReferer)
    }
}
