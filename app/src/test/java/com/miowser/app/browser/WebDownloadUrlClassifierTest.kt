package com.miowser.app.browser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDownloadUrlClassifierTest {
    @Test
    fun `recognizes application packages and ordinary downloadable files`() {
        assertTrue(WebDownloadUrlClassifier.isLikelyDownload("https://example.com/releases/app.apk"))
        assertTrue(WebDownloadUrlClassifier.isLikelyDownload("https://example.com/book.PDF?token=abc"))
        assertTrue(WebDownloadUrlClassifier.isLikelyDownload("https://example.com/archive.7z"))
    }

    @Test
    fun `recognizes encoded filename query parameters`() {
        assertTrue(
            WebDownloadUrlClassifier.isLikelyDownload(
                "https://example.com/download?filename=Miowser%20Release.apk&token=abc"
            )
        )
        assertTrue(
            WebDownloadUrlClassifier.isLikelyDownload(
                "https://example.com/redirect?url=https%3A%2F%2Fcdn.example.com%2Frelease.apk"
            )
        )
    }

    @Test
    fun `does not hijack normal pages or media detected by the grabber`() {
        assertFalse(WebDownloadUrlClassifier.isLikelyDownload("https://example.com/watch?id=12"))
        assertFalse(WebDownloadUrlClassifier.isLikelyDownload("https://example.com/video.mp4"))
        assertFalse(WebDownloadUrlClassifier.isLikelyDownload("https://example.com/master.m3u8"))
    }
}
