package com.miowser.app.player

import androidx.media3.common.MimeTypes
import com.miowser.app.grabber.MediaType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
}
