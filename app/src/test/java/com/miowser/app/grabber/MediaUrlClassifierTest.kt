package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaUrlClassifierTest {

    @Test
    fun `classifies known manifest and media extensions`() {
        assertEquals(MediaType.HLS, MediaUrlClassifier.classify("https://example.com/master.m3u8"))
        assertEquals(MediaType.DASH, MediaUrlClassifier.classify("https://example.com/stream.mpd"))
        assertEquals(MediaType.MP4, MediaUrlClassifier.classify("https://example.com/video.mp4"))
        assertEquals(MediaType.WEBM, MediaUrlClassifier.classify("https://example.com/video.webm"))
        assertEquals(MediaType.AUDIO, MediaUrlClassifier.classify("https://example.com/track.mp3"))
        assertEquals(MediaType.SUBTITLE, MediaUrlClassifier.classify("https://example.com/subs.vtt"))
    }

    @Test
    fun `ignores query string and fragment when reading the extension`() {
        assertEquals(MediaType.HLS, MediaUrlClassifier.classify("https://example.com/master.m3u8?token=abc&exp=123"))
        assertEquals(MediaType.MP4, MediaUrlClassifier.classify("https://example.com/video.mp4#t=10"))
    }

    @Test
    fun `excludes HLS DASH segment and key files even though they share the media file family`() {
        assertNull(MediaUrlClassifier.classify("https://example.com/segment0001.ts"))
        assertNull(MediaUrlClassifier.classify("https://example.com/init.m4s"))
        assertNull(MediaUrlClassifier.classify("https://example.com/stream.key"))
    }

    @Test
    fun `returns null for unrecognized extensions and extensionless URLs`() {
        assertNull(MediaUrlClassifier.classify("https://example.com/page.html"))
        assertNull(MediaUrlClassifier.classify("https://example.com/api/data"))
    }

    @Test
    fun `is case-insensitive on the extension`() {
        assertEquals(MediaType.MP4, MediaUrlClassifier.classify("https://example.com/VIDEO.MP4"))
    }

    @Test
    fun `isManifestType is true only for HLS and DASH`() {
        assertEquals(true, MediaUrlClassifier.isManifestType(MediaType.HLS))
        assertEquals(true, MediaUrlClassifier.isManifestType(MediaType.DASH))
        assertEquals(false, MediaUrlClassifier.isManifestType(MediaType.MP4))
    }

    @Test
    fun `classifies known manifest and media MIME types, ignoring charset parameters`() {
        assertEquals(MediaType.HLS, MediaUrlClassifier.classifyByMimeType("application/vnd.apple.mpegurl"))
        assertEquals(MediaType.HLS, MediaUrlClassifier.classifyByMimeType("application/x-mpegurl"))
        assertEquals(MediaType.DASH, MediaUrlClassifier.classifyByMimeType("application/dash+xml; charset=utf-8"))
        assertEquals(MediaType.MP4, MediaUrlClassifier.classifyByMimeType("video/mp4"))
        assertEquals(MediaType.AUDIO, MediaUrlClassifier.classifyByMimeType("audio/mpeg"))
        assertEquals(MediaType.SUBTITLE, MediaUrlClassifier.classifyByMimeType("text/vtt"))
    }

    @Test
    fun `falls back to a generic video or audio guess for an unlisted but still video or audio MIME type`() {
        assertEquals(MediaType.MP4, MediaUrlClassifier.classifyByMimeType("video/quicktime"))
        assertEquals(MediaType.AUDIO, MediaUrlClassifier.classifyByMimeType("audio/x-something-unlisted"))
    }

    @Test
    fun `returns null for a null or unrelated MIME type`() {
        assertNull(MediaUrlClassifier.classifyByMimeType(null))
        assertNull(MediaUrlClassifier.classifyByMimeType("text/html"))
        assertNull(MediaUrlClassifier.classifyByMimeType("application/octet-stream"))
    }
}
