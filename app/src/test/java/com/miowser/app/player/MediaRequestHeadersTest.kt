package com.miowser.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaRequestHeadersTest {
    @Test fun `captured iframe referrer wins over top-level page`() {
        assertEquals(
            "https://embed.example.net/watch/1",
            mediaReferer("https://site.example/watch/1", "https://cdn.example/media.m3u8",
                "https://embed.example.net/watch/1")
        )
    }

    @Test fun `older entries send origin to a different site and full url to same origin`() {
        assertEquals("https://site.example/",
            mediaReferer("https://site.example/watch/1?x=2", "https://cdn.example/media.m3u8", null))
        assertEquals("https://site.example/watch/1?x=2",
            mediaReferer("https://site.example/watch/1?x=2#fragment", "https://site.example/media.mp4", null))
    }

    @Test fun `secure pages do not refer to insecure media`() {
        assertNull(mediaReferer("https://site.example/watch", "http://cdn.example/video.mp4", null))
    }
}
