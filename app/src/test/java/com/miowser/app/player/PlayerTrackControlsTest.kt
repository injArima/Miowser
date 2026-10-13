package com.miowser.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerTrackControlsTest {

    @Test fun detectedVttSelectionHasAStableKeyBeforeTracksLoad() {
        val url = "https://cdn.example/subs/en.vtt?token=one"
        val selection = PlayerTrackSelection(PlayerTrackMode.SPECIFIC,
            externalSubtitleSelectionKey(url))
        assertEquals(url, externalSubtitleUrlFromSelection(selection))
        val restored = PlayerTrackPreferenceCodec.decode(
            PlayerTrackPreferenceCodec.encode(PlayerTrackPreferences(subtitles = selection)))
        assertEquals(selection, restored?.subtitles)
        assertNull(externalSubtitleUrlFromSelection(PlayerTrackSelection(PlayerTrackMode.AUTO,
            externalSubtitleSelectionKey(url))))
    }
    @Test
    fun `preference codec round trips explicit choices and rejects broken specific choices`() {
        val preferences = PlayerTrackPreferences(
            subtitles = PlayerTrackSelection(PlayerTrackMode.OFF),
            audio = PlayerTrackSelection(PlayerTrackMode.SPECIFIC, "audio-key"),
            video = PlayerTrackSelection(PlayerTrackMode.SPECIFIC, "video-key")
        )
        assertEquals(preferences, PlayerTrackPreferenceCodec.decode(PlayerTrackPreferenceCodec.encode(preferences)))
        assertNull(PlayerTrackPreferenceCodec.decode("AUTO\t\tSPECIFIC\t\tAUTO\t"))
    }

    @Test
    fun `button label reflects off auto and pinned quality states`() {
        val video = PlayerTrackOption("1080", PlayerTrackKind.VIDEO, 0, 0, "1080p", "5 Mbps")
        val snapshot = PlayerTrackSnapshot(video = listOf(video))
        assertEquals("Sources", playerTrackButtonLabel(PlayerTrackKind.VIDEO, PlayerTrackSelection(), snapshot))
        assertEquals(
            "1080p",
            playerTrackButtonLabel(
                PlayerTrackKind.VIDEO,
                PlayerTrackSelection(PlayerTrackMode.SPECIFIC, "1080"),
                snapshot
            )
        )
        assertEquals(
            "Off",
            playerTrackButtonLabel(PlayerTrackKind.SUBTITLE, PlayerTrackSelection(PlayerTrackMode.OFF), snapshot)
        )
    }

    @Test
    fun `bitrate labels stay compact`() {
        assertEquals("192 kbps", trackBitrateLabel(192_000))
        assertEquals("2 Mbps", trackBitrateLabel(2_000_000))
        assertEquals("1.5 Mbps", trackBitrateLabel(1_500_000))
        assertNull(trackBitrateLabel(0))
    }
}
