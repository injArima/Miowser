package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ManifestSnifferTest {

    @Test
    fun `confirms an HLS master playlist, counts variants, and resolves the best variant's URL`() {
        val body = """
            #EXTM3U
            #EXT-X-STREAM-INF:BANDWIDTH=1280000,RESOLUTION=640x360
            gear1/prog_index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2560000,RESOLUTION=960x540
            gear2/prog_index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=7680000,RESOLUTION=1280x720
            gear3/prog_index.m3u8
        """.trimIndent()

        val info = ManifestSniffer.sniff(body, "https://example.com/hls/master.m3u8")

        assertEquals(MediaType.HLS, info.confirmedType)
        assertTrue(info.isMasterOrMultiVariant)
        assertEquals(3, info.variantCount)
        assertFalse(info.isEncrypted)
        assertEquals("1280x720", info.resolution) // highest of the three advertised variants
        // Resolved against the master playlist's own URL, not left as the relative line text —
        // MediaProbe fetches this directly to sample a real segment for its size estimate.
        assertEquals("https://example.com/hls/gear3/prog_index.m3u8", info.bestVariantUrl)
        assertNull(info.durationSeconds) // a master has no segments of its own to sum
        assertNull(info.firstSegmentUrl)
    }

    @Test
    fun `confirms an HLS leaf playlist, sums duration, and locates the first real segment`() {
        val body = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:9.9,
            segment0.ts
            #EXTINF:8.8,
            segment1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val info = ManifestSniffer.sniff(body, "https://example.com/hls/gear3/prog_index.m3u8")

        assertEquals(MediaType.HLS, info.confirmedType)
        assertFalse(info.isMasterOrMultiVariant)
        assertEquals(0, info.variantCount)
        assertNull(info.resolution) // no #EXT-X-STREAM-INF lines to carry a RESOLUTION attribute
        assertEquals(18.7, info.durationSeconds!!, 0.01) // 9.9 + 8.8
        assertEquals("https://example.com/hls/gear3/segment0.ts", info.firstSegmentUrl) // resolved, sibling of the playlist
        assertEquals(9.9, info.firstSegmentDurationSeconds!!, 0.01) // that segment's own #EXTINF, not the total
    }

    @Test
    fun `detects real AES-128 encryption on an HLS playlist`() {
        val body = """
            #EXTM3U
            #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/key",IV=0x1234
            #EXTINF:9.9,
            segment0.ts
        """.trimIndent()

        assertTrue(ManifestSniffer.sniff(body, "https://example.com/hls/index.m3u8").isEncrypted)
    }

    @Test
    fun `does not flag METHOD=NONE as encryption`() {
        val body = """
            #EXTM3U
            #EXT-X-KEY:METHOD=NONE
            #EXTINF:9.9,
            segment0.ts
        """.trimIndent()

        assertFalse(ManifestSniffer.sniff(body, "https://example.com/hls/index.m3u8").isEncrypted)
    }

    @Test
    fun `confirms a DASH manifest, counts representations, and reads duration`() {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" mediaPresentationDuration="PT1H2M3S">
              <Period>
                <AdaptationSet>
                  <Representation id="1" bandwidth="1000000" width="640" height="360"/>
                  <Representation id="2" bandwidth="2000000" width="1920" height="1080"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val info = ManifestSniffer.sniff(body, "https://example.com/dash/manifest.mpd")

        assertEquals(MediaType.DASH, info.confirmedType)
        assertEquals(2, info.variantCount)
        assertTrue(info.isMasterOrMultiVariant)
        assertEquals(3723.0, info.durationSeconds!!, 0.01) // 1h2m3s
        assertEquals("1920x1080", info.resolution) // highest of the two Representations
        assertNull(info.bestVariantUrl) // DASH segment addressing isn't resolved here — see MediaProbe's doc comment
    }

    @Test
    fun `detects DASH content protection as encrypted`() {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011">
              <Period>
                <AdaptationSet>
                  <ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/>
                  <Representation id="1" bandwidth="1000000"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        assertTrue(ManifestSniffer.sniff(body, "https://example.com/dash/manifest.mpd").isEncrypted)
    }

    @Test
    fun `rejects a body that is neither HLS nor DASH despite a manifest-like extension`() {
        // The "fake extension, real error page" case: a .m3u8 URL that actually 200'd an HTML page.
        val body = "<!DOCTYPE html><html><body>404 Not Found</body></html>"
        assertNull(ManifestSniffer.sniff(body, "https://example.com/fake.m3u8").confirmedType)
    }
}
