package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaticMediaHtmlParserTest {
    @Test fun `finds direct and manifest sources and resolves relative urls`() {
        val html = """
            <video src="/movie.mp4"></video>
            <source src='../hls/index.m3u8?x=1&amp;y=2' type="application/vnd.apple.mpegurl">
            <source src="/opaque/stream" type="video/mp4">
            <a href="https://cdn.example.org/stream.mpd">Play</a>
            <a href="/signed.mp4?token=a%2Fb&amp;exp=1">Signed</a>
            <a href="/watch/next">Next</a>
            <a href="/logout">Log out</a>
        """.trimIndent()
        val found = StaticMediaHtmlParser.parse(html, "https://example.org/watch/one")
        assertEquals(listOf(
            "https://example.org/movie.mp4",
            "https://example.org/hls/index.m3u8?x=1&y=2",
            "https://example.org/opaque/stream",
            "https://cdn.example.org/stream.mpd",
            "https://example.org/signed.mp4?token=a%2Fb&exp=1"
        ), found.mediaUrls)
        assertEquals(MediaType.MP4, found.mediaTypes["https://example.org/opaque/stream"])
        assertEquals(listOf("https://example.org/watch/next"), found.pageUrls)
    }

    @Test fun `ignores scripts unsafe schemes and duplicate segment links`() {
        val html = """
            <script>"<video src='https://evil.example/fake.mp4'>"</script>
            <a href="javascript:alert(1)">bad</a>
            <source src="part.m4s"><source src="part.ts">
            <source src="clip.mp4"><source src="clip.mp4">
        """.trimIndent()
        val found = StaticMediaHtmlParser.parse(html, "https://example.org/watch")
        assertEquals(listOf("https://example.org/clip.mp4"), found.mediaUrls)
        assertFalse(found.pageUrls.any { it.startsWith("javascript:") })
    }

    @Test fun `caps extracted links`() {
        val html = (1..20).joinToString("") { "<a href='/watch/page$it'>p</a>" }
        val found = StaticMediaHtmlParser.parse(html, "https://example.org/", maxLinks = 5)
        assertEquals(5, found.pageUrls.size)
        assertTrue(found.mediaUrls.isEmpty())
    }
}
