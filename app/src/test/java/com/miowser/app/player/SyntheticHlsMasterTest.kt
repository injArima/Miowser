package com.miowser.app.player

import com.miowser.app.grabber.HlsRendition
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyntheticHlsMasterTest {
    @Test fun `builds an adaptive master from separate HLS leaves`() {
        val master = syntheticHlsMaster(listOf(
            HlsRendition("https://cdn.example.com/1080/index.m3u8", "1920x1080", 5_000_000),
            HlsRendition("https://cdn.example.com/720/index.m3u8", "1280x720", 2_500_000)
        ))!!

        assertTrue(master.startsWith("#EXTM3U"))
        assertTrue(master.contains("BANDWIDTH=5000000,RESOLUTION=1920x1080"))
        assertTrue(master.contains("https://cdn.example.com/720/index.m3u8"))
        assertTrue(syntheticHlsMasterDataUri(listOf(
            HlsRendition("https://cdn.example.com/1080/index.m3u8", "1920x1080"),
            HlsRendition("https://cdn.example.com/720/index.m3u8", "1280x720")
        ))!!.startsWith("data:application/vnd.apple.mpegurl;base64,"))
    }

    @Test fun `does not synthesize a master for one source`() {
        assertNull(syntheticHlsMaster(listOf(
            HlsRendition("https://cdn.example.com/only/index.m3u8", "1280x720")
        )))
    }
}
