package com.miowser.app.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory

/** Player resource owner. The screen removes listeners and saves progress before closing it. */
@androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])
internal class PlaybackSession private constructor(val player: ExoPlayer, private val transport: RemotePlayerTransport?) {
    private var closed = false
    private val cleanups = mutableListOf<() -> Unit>()
    fun ownCleanup(cleanup: () -> Unit) {
        if (closed) cleanup() else cleanups.add(cleanup)
    }
    fun close() {
        if (closed) return
        closed = true
        try {
            cleanups.forEach { it() }; cleanups.clear()
            transport?.prefetcher?.cancel()
        } finally { player.release() }
    }
    companion object {
        fun create(context: Context, request: PlaybackRequest, transport: RemotePlayerTransport?, externalCaptions: Boolean): PlaybackSession {
            val factory = when (request) {
                is PlaybackRequest.Remote -> requireNotNull(transport).mediaSourceFactory
                is PlaybackRequest.Local -> DefaultMediaSourceFactory(context)
            }
            val item = when (request) {
                is PlaybackRequest.Remote -> remotePlayerMediaItem(request)
                is PlaybackRequest.Local -> MediaItem.fromUri(request.uri)
            }
            val player = ExoPlayer.Builder(context, DefaultRenderersFactory(context).setEnableDecoderFallback(true))
                .setMediaSourceFactory(factory)
                // User policy: do not request audio focus or suppress playback on focus loss.
                .setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), false)
                .setHandleAudioBecomingNoisy(true)
                .setLoadControl(if (request is PlaybackRequest.Remote) DefaultLoadControl.Builder()
                    .setBufferDurationsMs(30_000, 90_000, 2_500, 5_000).setBackBuffer(30_000, true)
                    .setTargetBufferBytes(64 * 1024 * 1024).build() else DefaultLoadControl())
                .build()
            try {
                player.setMediaItem(item)
                if (externalCaptions) player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                if (request.startPositionMs > 0) player.seekTo(request.startPositionMs)
                player.playWhenReady = true
                player.prepare()
            } catch (error: Exception) { player.release(); throw error }
            return PlaybackSession(player, transport)
        }
    }
}
