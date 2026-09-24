package com.miowser.app.player

import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import com.miowser.app.grabber.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenTest {

    @Test
    fun `hints an explicit MIME type for HLS and DASH so ExoPlayer doesn't have to guess from the URL`() {
        assertEquals(MimeTypes.APPLICATION_M3U8, MediaType.HLS.toExoMimeTypeHint())
        assertEquals(MimeTypes.APPLICATION_MPD, MediaType.DASH.toExoMimeTypeHint())
    }

    @Test
    fun `leaves direct file types to ExoPlayer's own extension-Content-Type inference`() {
        assertNull(MediaType.MP4.toExoMimeTypeHint())
        assertNull(MediaType.WEBM.toExoMimeTypeHint())
        assertNull(MediaType.MATROSKA.toExoMimeTypeHint())
        assertNull(MediaType.AUDIO.toExoMimeTypeHint())
        assertNull(MediaType.SUBTITLE.toExoMimeTypeHint())
        assertNull(MediaType.UNKNOWN.toExoMimeTypeHint())
    }

    @Test
    fun `formats short and long playback positions`() {
        assertEquals("00:00", formatPlayerTime(0))
        assertEquals("03:48", formatPlayerTime(228_999))
        assertEquals("2:35:25", formatPlayerTime(9_325_000))
    }

    @Test
    fun `seek swipe sensitivity scales with duration but stays within useful bounds`() {
        assertEquals(60_000L, gestureSeekSpanMs(0L))
        assertEquals(60_000L, gestureSeekSpanMs(5 * 60_000L))
        assertEquals(90_000L, gestureSeekSpanMs(15 * 60_000L))
        assertEquals(300_000L, gestureSeekSpanMs(4 * 60 * 60_000L))
    }

    @Test
    fun `gesture waits for a dominant axis and protects system edges`() {
        assertEquals(
            PlayerGestureMode.UNDECIDED,
            classifyPlayerGesture(20f, 19f, 500f, 1_000f, 24f)
        )
        assertEquals(
            PlayerGestureMode.SEEK,
            classifyPlayerGesture(40f, 10f, 500f, 1_000f, 24f)
        )
        assertEquals(
            PlayerGestureMode.BRIGHTNESS,
            classifyPlayerGesture(5f, 40f, 250f, 1_000f, 24f)
        )
        assertEquals(
            PlayerGestureMode.VOLUME,
            classifyPlayerGesture(5f, 40f, 750f, 1_000f, 24f)
        )
        assertEquals(
            PlayerGestureMode.UNDECIDED,
            classifyPlayerGesture(5f, 40f, 10f, 1_000f, 24f)
        )
    }

    @Test
    fun `double tap zones are exclusive responsive and avoid protected bars`() {
        assertEquals(
            PlayerDoubleTapAction.NONE,
            classifyPlayerDoubleTap(500f, 20f, 1_000f, 600f, 40f, 20f, isLandscape = true)
        )
        assertEquals(
            PlayerDoubleTapAction.SEEK_BACKWARD,
            classifyPlayerDoubleTap(200f, 300f, 1_000f, 600f, 40f, 20f, isLandscape = true)
        )
        assertEquals(
            PlayerDoubleTapAction.TOGGLE_PLAY_PAUSE,
            classifyPlayerDoubleTap(500f, 300f, 1_000f, 600f, 40f, 20f, isLandscape = true)
        )
        assertEquals(
            PlayerDoubleTapAction.SEEK_FORWARD,
            classifyPlayerDoubleTap(800f, 300f, 1_000f, 600f, 40f, 20f, isLandscape = true)
        )
        assertEquals(
            PlayerDoubleTapAction.TOGGLE_PLAY_PAUSE,
            classifyPlayerDoubleTap(335f, 500f, 1_000f, 1_800f, 80f, 360f, isLandscape = false)
        )
        assertEquals(
            PlayerDoubleTapAction.NONE,
            classifyPlayerDoubleTap(500f, 1_600f, 1_000f, 1_800f, 80f, 360f, isLandscape = false)
        )
    }

    @Test
    fun `visible controls restrict horizontal seek but hidden controls restore the usable surface`() {
        assertFalse(playerSeekGestureAllowed(100f, 600f, 40f, 20f, true, true))
        assertTrue(playerSeekGestureAllowed(200f, 600f, 40f, 20f, true, true))
        assertFalse(playerSeekGestureAllowed(500f, 600f, 40f, 20f, true, true))
        assertTrue(playerSeekGestureAllowed(500f, 600f, 40f, 20f, false, true))
        assertFalse(playerSeekGestureAllowed(1_600f, 1_800f, 80f, 360f, false, false))
    }

    @Test
    fun `middle zone rejects vertical controls and disallowed bands reject horizontal seeking`() {
        assertEquals(
            PlayerGestureMode.UNDECIDED,
            classifyPlayerGesture(
                dragX = 5f,
                dragY = 50f,
                startedAtX = 500f,
                width = 1_000f,
                edgeInset = 24f,
                verticalMode = null
            )
        )
        assertEquals(
            PlayerGestureMode.UNDECIDED,
            classifyPlayerGesture(
                dragX = 50f,
                dragY = 5f,
                startedAtX = 200f,
                width = 1_000f,
                edgeInset = 24f,
                allowSeek = false,
                verticalMode = PlayerGestureMode.BRIGHTNESS
            )
        )
    }

    @Test
    fun `playback failures produce actionable and honest recovery guidance`() {
        val network = playerErrorPresentation(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED)
        assertEquals("Connection problem", network.title)
        assertTrue(network.canRetry)

        val missing = playerErrorPresentation(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND)
        assertEquals("Video not found", missing.title)
        assertFalse(missing.canRetry)

        val drm = playerErrorPresentation(PlaybackException.ERROR_CODE_DRM_UNSPECIFIED)
        assertEquals("Protected video unsupported", drm.title)
        assertFalse(drm.canRetry)
    }
}
