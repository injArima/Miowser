package com.miowser.app.browser

import com.miowser.app.grabber.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WebDownloadStreamOptionTest {
    private fun request(url: String, mimeType: String? = null) = WebDownloadRequest(
        originatingTabId = 1L,
        url = url,
        pageUrl = "https://example.test/watch",
        pageTitle = "Video",
        userAgent = "Miowser",
        contentDisposition = null,
        mimeType = mimeType,
        contentLength = -1L
    )

    @Test fun `video MIME and filename can each enable streaming`() {
        assertEquals(MediaType.MP4,
            streamableDownloadType(request("https://example.test/file", "video/mp4"), "movie"))
        assertEquals(MediaType.HLS,
            streamableDownloadType(request("https://example.test/file", "application/octet-stream"), "movie.m3u8"))
    }

    @Test fun `non-video downloads do not offer streaming`() {
        assertNull(streamableDownloadType(request("https://example.test/file.apk"), "app.apk"))
        assertNull(streamableDownloadType(request("https://example.test/captions.vtt"), "captions.vtt"))
        assertNull(streamableDownloadType(request("https://example.test/audio.mp3"), "audio.mp3"))
    }
}
