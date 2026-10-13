package com.miowser.app.grabber

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DashPlaylistParserTest {

    @Test
    fun `resolves SegmentTemplate with duration+timescale into a numbered segment list`() {
        val body = """
            <?xml version="1.0"?>
            <MPD mediaPresentationDuration="PT30S">
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="v1" bandwidth="1000000" width="1920" height="1080">
                    <SegmentTemplate media="chunk-${'$'}Number%03d${'$'}.m4s" initialization="init.mp4" startNumber="1" duration="10" timescale="1"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd")

        assertEquals(1, manifest.tracks.size)
        val track = manifest.tracks[0]
        assertEquals("video", track.type)
        assertEquals(1000000L, track.bandwidth)
        assertEquals("1920x1080", track.resolution)
        assertEquals("https://example.com/dash/init.mp4", track.initSegmentUrl)
        // 30s total / 10s per segment = 3 segments, numbered from startNumber=1, zero-padded to 3 digits
        assertEquals(3, track.mediaSegments.size)
        assertEquals("https://example.com/dash/chunk-001.m4s", track.mediaSegments[0].url)
        assertEquals("https://example.com/dash/chunk-002.m4s", track.mediaSegments[1].url)
        assertEquals("https://example.com/dash/chunk-003.m4s", track.mediaSegments[2].url)
    }

    @Test
    fun `resolves SegmentTemplate with an explicit SegmentTimeline using $Time$`() {
        val body = """
            <?xml version="1.0"?>
            <MPD>
              <Period duration="PT20S">
                <AdaptationSet contentType="audio">
                  <Representation id="a1" bandwidth="128000">
                    <SegmentTemplate media="chunk-${'$'}Time${'$'}.m4s" initialization="init-a.mp4" timescale="1000">
                      <SegmentTimeline>
                        <S t="0" d="5000" r="1"/>
                        <S d="2000"/>
                      </SegmentTimeline>
                    </SegmentTemplate>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd")
        val track = manifest.tracks[0]

        assertEquals("audio", track.type)
        // First <S> repeats once (r=1): two segments at t=0 and t=5000; then the next <S> continues at t=10000.
        assertEquals(3, track.mediaSegments.size)
        assertEquals("https://example.com/dash/chunk-0.m4s", track.mediaSegments[0].url)
        assertEquals("https://example.com/dash/chunk-5000.m4s", track.mediaSegments[1].url)
        assertEquals("https://example.com/dash/chunk-10000.m4s", track.mediaSegments[2].url)
    }

    @Test
    fun `resolves SegmentList explicit SegmentURL entries`() {
        val body = """
            <?xml version="1.0"?>
            <MPD>
              <Period>
                <AdaptationSet mimeType="video/mp4">
                  <Representation id="v1" bandwidth="500000">
                    <SegmentList>
                      <Initialization sourceURL="init.mp4"/>
                      <SegmentURL media="seg1.m4s"/>
                      <SegmentURL media="seg2.m4s"/>
                    </SegmentList>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd")
        val track = manifest.tracks[0]

        assertEquals("video", track.type)
        assertEquals("https://example.com/dash/init.mp4", track.initSegmentUrl)
        assertEquals(2, track.mediaSegments.size)
        assertEquals("https://example.com/dash/seg1.m4s", track.mediaSegments[0].url)
    }

    @Test
    fun `cascades BaseURL from MPD down through Period, AdaptationSet, and Representation`() {
        val body = """
            <?xml version="1.0"?>
            <MPD mediaPresentationDuration="PT10S">
              <BaseURL>cdn/</BaseURL>
              <Period>
                <BaseURL>video/</BaseURL>
                <AdaptationSet contentType="video">
                  <Representation id="v1" bandwidth="1000000">
                    <BaseURL>1080p/</BaseURL>
                    <SegmentTemplate media="chunk-${'$'}Number${'$'}.m4s" startNumber="1" duration="10" timescale="1"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val manifest = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd")
        val track = manifest.tracks[0]

        // MPD BaseURL "cdn/" resolves against the manifest URL, then "video/" against that,
        // then "1080p/" against that — each level relative to its parent, not the manifest.
        assertEquals("https://example.com/dash/cdn/video/1080p/chunk-1.m4s", track.mediaSegments[0].url)
    }

    @Test
    fun `classifies representations by mimeType, falling back to the AdaptationSet's contentType`() {
        val body = """
            <?xml version="1.0"?>
            <MPD mediaPresentationDuration="PT10S">
              <Period>
                <AdaptationSet contentType="audio">
                  <Representation id="a1" bandwidth="64000">
                    <SegmentTemplate media="a-${'$'}Number${'$'}.m4s" startNumber="1" duration="10" timescale="1"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        assertEquals("audio", DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd").tracks[0].type)
    }

    @Test
    fun `rejects a body that isn't XML at all rather than throwing`() {
        val body = "<!DOCTYPE html><html><body>404 Not Found</body></html>"
        val manifest = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd")
        assertTrue(manifest.tracks.isEmpty())
    }

    @Test
    fun `skips a Representation whose SegmentTemplate can't determine a segment count`() {
        // No SegmentTimeline, no duration, and no Period/MPD duration to derive a count from.
        val body = """
            <?xml version="1.0"?>
            <MPD>
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="v1" bandwidth="1000000">
                    <SegmentTemplate media="chunk-${'$'}Number${'$'}.m4s" startNumber="1"/>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        assertTrue(DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd").tracks.isEmpty())
    }

    @Test
    fun `records SegmentBase index initialization ranges and cascaded media URL`() {
        val body = """
            <MPD>
              <BaseURL>cdn/</BaseURL>
              <Period>
                <AdaptationSet contentType="video">
                  <BaseURL>tracks/</BaseURL>
                  <Representation id="v1" bandwidth="2000000" width="1920" height="1080">
                    <BaseURL>video.mp4</BaseURL>
                    <SegmentBase timescale="1000" indexRange="800-999" indexRangeExact="true">
                      <Initialization range="0-799"/>
                    </SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val track = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd").tracks.single()
        val base = track.segmentBase!!
        assertEquals("https://example.com/dash/cdn/tracks/video.mp4", base.mediaUrl)
        assertEquals(DashByteRange(800, 999), base.indexRange)
        assertEquals(DashByteRange(0, 799), base.initializationRange)
        assertEquals(1000L, base.expectedTimescale)
        assertTrue(base.indexRangeExact)
        assertTrue(track.mediaSegments.isEmpty())
    }

    @Test
    fun `inherits SegmentBase from the AdaptationSet`() {
        val body = """
            <MPD>
              <Period>
                <AdaptationSet contentType="audio">
                  <SegmentBase indexRange="500-599"><Initialization range="0-499"/></SegmentBase>
                  <Representation id="a1" bandwidth="128000"><BaseURL>audio.mp4</BaseURL></Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val track = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd").tracks.single()
        assertEquals("audio", track.type)
        assertEquals(DashByteRange(500, 599), track.segmentBase?.indexRange)
    }

    @Test
    fun `keeps separate video and audio SegmentBase representations for muxing`() {
        val body = """
            <MPD>
              <Period>
                <AdaptationSet contentType="video">
                  <Representation id="v1" bandwidth="2000000" width="1920" height="1080">
                    <BaseURL>video/main.mp4</BaseURL>
                    <SegmentBase timescale="90000" indexRange="900-1099">
                      <Initialization range="0-899"/>
                    </SegmentBase>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio">
                  <Representation id="a1" bandwidth="128000">
                    <BaseURL>audio/main.mp4</BaseURL>
                    <SegmentBase timescale="48000" indexRange="500-699">
                      <Initialization range="0-499"/>
                    </SegmentBase>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val tracks = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd").tracks

        assertEquals(2, tracks.size)
        val video = tracks.single { it.type == "video" }.segmentBase!!
        val audio = tracks.single { it.type == "audio" }.segmentBase!!
        assertEquals("https://example.com/dash/video/main.mp4", video.mediaUrl)
        assertEquals(DashByteRange(900, 1099), video.indexRange)
        assertEquals(90000L, video.expectedTimescale)
        assertEquals("https://example.com/dash/audio/main.mp4", audio.mediaUrl)
        assertEquals(DashByteRange(500, 699), audio.indexRange)
        assertEquals(48000L, audio.expectedTimescale)
    }

    @Test
    fun `parses namespace-prefixed SegmentBase manifests`() {
        val body = """
            <mpd:MPD xmlns:mpd="urn:mpeg:dash:schema:mpd:2011">
              <mpd:Period>
                <mpd:AdaptationSet contentType="video">
                  <mpd:Representation id="v1" bandwidth="1000000">
                    <mpd:BaseURL>video.mp4</mpd:BaseURL>
                    <mpd:SegmentBase indexRange="700-899">
                      <mpd:Initialization range="0-699"/>
                    </mpd:SegmentBase>
                  </mpd:Representation>
                </mpd:AdaptationSet>
              </mpd:Period>
            </mpd:MPD>
        """.trimIndent()

        val track = DashPlaylistParser.parse(body, "https://example.com/dash/manifest.mpd").tracks.single()

        assertEquals("video", track.type)
        assertEquals("https://example.com/dash/video.mp4", track.segmentBase?.mediaUrl)
        assertEquals(DashByteRange(700, 899), track.segmentBase?.indexRange)
        assertEquals(DashByteRange(0, 699), track.segmentBase?.initializationRange)
    }

    @Test
    fun `rejects content protected DASH before choosing representations`() {
        val body = """
            <MPD><Period><AdaptationSet contentType="video">
              <ContentProtection schemeIdUri="urn:uuid:test"/>
              <Representation id="v1"><BaseURL>video.mp4</BaseURL><SegmentBase indexRange="10-20"/></Representation>
            </AdaptationSet></Period></MPD>
        """.trimIndent()

        val manifest = DashPlaylistParser.parse(body, "https://example.com/manifest.mpd")
        assertTrue(manifest.tracks.isEmpty())
        assertEquals("DRM-protected DASH streams are not supported", manifest.unsupportedReason)
    }

    @Test
    fun `keeps an invalid SegmentBase as a useful validation failure`() {
        val body = """
            <MPD><Period><AdaptationSet contentType="video">
              <Representation id="v1"><BaseURL>video.mp4</BaseURL><SegmentBase indexRange="broken"/></Representation>
            </AdaptationSet></Period></MPD>
        """.trimIndent()

        val base = DashPlaylistParser.parse(body, "https://example.com/manifest.mpd").tracks.single().segmentBase!!
        assertEquals("SegmentBase has an invalid indexRange", base.validationError)
    }
}
