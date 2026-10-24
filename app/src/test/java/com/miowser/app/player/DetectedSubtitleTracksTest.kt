package com.miowser.app.player

import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectedSubtitleTracksTest {
    private fun media(url: String, type: MediaType, page: String = "https://site.example/watch") =
        DetectedMedia(
            url = url,
            pageUrl = page,
            type = type,
            isConfirmed = true,
            sizeBytes = null,
            isSizeApproximate = false,
            durationSeconds = null,
            variantCount = null,
            resolution = null,
            isEncrypted = false,
            pageTitle = null
        )

    @Test fun attachesDistinctVttAndSrtFromTheVideosPage() {
        val video = media("https://cdn.example/episode.m3u8", MediaType.HLS)
        val english = media("https://cdn.example/eng.vtt?token=1", MediaType.SUBTITLE)
        val chinese = media("https://cdn.example/sub?id=5", MediaType.SUBTITLE)
            .copy(mimeType = "text/vtt; charset=utf-8", responseFileName = "chi.vtt")
        val unrelated = media("https://cdn.example/other.vtt", MediaType.SUBTITLE,
            page = "https://site.example/other")
        val subRip = media("https://cdn.example/eng.srt", MediaType.SUBTITLE)
        val guessedVideo = media("https://cdn.example/other.mp4", MediaType.MP4)

        assertEquals(listOf(english, chinese, subRip), detectedSubtitleTracks(video,
            listOf(video, english, english, chinese, unrelated, subRip, guessedVideo)))
        assertEquals("chi.vtt", detectedSubtitleLabel(chinese))
    }

    @Test fun usesTabScopedSubtitleWhenEmbeddedVideoReportsAnotherFrameUrl() {
        val video = media("https://cdn.example/episode.m3u8", MediaType.HLS,
            page = "https://player.example/embed")
        val subtitles = media("https://cdn.example/english.vtt", MediaType.SUBTITLE,
            page = "https://site.example/watch")
        assertEquals(listOf(subtitles), detectedSubtitleTracks(video, listOf(video, subtitles)))
    }
}
