package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HlsPlaylistParserTest {

    @Test
    fun `enumerates every segment with resolved URLs and durations`() {
        val body = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:9.9,
            segment0.ts
            #EXTINF:8.8,
            segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val playlist = HlsPlaylistParser.parseLeaf(body, "https://example.com/hls/gear3/prog_index.m3u8")

        assertEquals(2, playlist.segments.size)
        assertEquals("https://example.com/hls/gear3/segment0.ts", playlist.segments[0].url)
        assertEquals(9.9, playlist.segments[0].durationSeconds, 0.01)
        assertEquals("https://example.com/hls/gear3/segment1.ts", playlist.segments[1].url)
        assertEquals(18.7, playlist.totalDurationSeconds, 0.01)
    }

    @Test
    fun `numbers segments starting from EXT-X-MEDIA-SEQUENCE`() {
        val body = """
            #EXTM3U
            #EXT-X-MEDIA-SEQUENCE:42
            #EXTINF:10.0,
            segment0.ts
            #EXTINF:10.0,
            segment1.ts
        """.trimIndent()

        val playlist = HlsPlaylistParser.parseLeaf(body, "https://example.com/hls/index.m3u8")

        assertEquals(42L, playlist.segments[0].sequenceNumber)
        assertEquals(43L, playlist.segments[1].sequenceNumber)
    }

    @Test
    fun `defaults sequence numbering to zero when EXT-X-MEDIA-SEQUENCE is absent`() {
        val body = """
            #EXTM3U
            #EXTINF:10.0,
            segment0.ts
        """.trimIndent()

        assertEquals(0L, HlsPlaylistParser.parseLeaf(body, "https://example.com/hls/index.m3u8").segments[0].sequenceNumber)
    }

    @Test
    fun `attaches the resolved key URI and explicit IV to segments under that key`() {
        val body = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key1",IV=0x00000000000000000000000000000001
            #EXTINF:10.0,
            segment0.ts
        """.trimIndent()

        val segment = HlsPlaylistParser.parseLeaf(body, "https://example.com/hls/index.m3u8").segments[0]

        assertEquals("https://example.com/hls/key1", segment.keyUri) // resolved relative to the playlist
        assertEquals("00000000000000000000000000000001", segment.ivHex)
    }

    @Test
    fun `leaves IV null when the KEY tag doesn't specify one`() {
        val body = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key1"
            #EXTINF:10.0,
            segment0.ts
        """.trimIndent()

        assertNull(HlsPlaylistParser.parseLeaf(body, "https://example.com/hls/index.m3u8").segments[0].ivHex)
    }

    @Test
    fun `treats METHOD=NONE as unencrypted even mid-playlist`() {
        val body = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key1"
            #EXTINF:10.0,
            segment0.ts
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:10.0,
            segment1.ts
        """.trimIndent()

        val segments = HlsPlaylistParser.parseLeaf(body, "https://example.com/hls/index.m3u8").segments
        assertEquals("https://example.com/hls/key1", segments[0].keyUri)
        assertNull(segments[1].keyUri) // key rotation mid-playlist: segment1 is not under key1
    }

    @Test
    fun `applies a new key to segments following a second KEY tag`() {
        val body = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="key1"
            #EXTINF:10.0,
            segment0.ts
            #EXT-X-KEY:METHOD=AES-128,URI="key2"
            #EXTINF:10.0,
            segment1.ts
        """.trimIndent()

        val segments = HlsPlaylistParser.parseLeaf(body, "https://example.com/hls/index.m3u8").segments
        assertEquals("https://example.com/hls/key1", segments[0].keyUri)
        assertEquals("https://example.com/hls/key2", segments[1].keyUri)
    }
}
