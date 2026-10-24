package com.miowser.app.player

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.IBinder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.miowser.app.MainActivity
import com.miowser.app.R

/** Exposes the existing player to Android media controls while its screen owns playback. */
internal class PlayerPlaybackService : Service() {
    private lateinit var mediaSession: MediaSession
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Media playback", NotificationManager.IMPORTANCE_LOW)
        )
        mediaSession = MediaSession(this, "Miowser playback").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { externalControlRevision++; player?.play() }
                override fun onPause() { externalControlRevision++; player?.pause() }
                override fun onSeekTo(pos: Long) {
                    externalControlRevision++; player?.seekTo(pos.coerceAtLeast(0L))
                }
                override fun onStop() { externalControlRevision++; player?.pause() }
            })
            isActive = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> { externalControlRevision++; player?.play() }
            ACTION_PAUSE -> { externalControlRevision++; player?.pause() }
            else -> Unit
        }
        val requested = requestedPlayer
        if (requested == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        bindPlayer(requested)
        return START_NOT_STICKY
    }

    private fun bindPlayer(next: ExoPlayer) {
        if (player !== next) {
            playerListener?.let { player?.removeListener(it) }
            player = next
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) { publish() }
                override fun onPlaybackStateChanged(playbackState: Int) { publish() }
                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) { publish() }
                override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo, reason: Int) { publish() }
            }
            playerListener = listener
            next.addListener(listener)
        }
        publish()
    }

    private fun publish() {
        val active = player ?: return
        val duration = active.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: -1L
        mediaSession.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, requestedTitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .build())
        val state = when {
            active.playbackState == Player.STATE_BUFFERING -> PlaybackState.STATE_BUFFERING
            active.playbackState == Player.STATE_ENDED -> PlaybackState.STATE_STOPPED
            active.playWhenReady -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        mediaSession.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .setState(state, active.currentPosition.coerceAtLeast(0L),
                if (state == PlaybackState.STATE_PLAYING) active.playbackParameters.speed else 0f,
                SystemClock.elapsedRealtime())
            .build())
        startForeground(NOTIFICATION_ID, notification(active.playWhenReady))
    }

    private fun notification(playing: Boolean): Notification {
        val mainIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = if (playing) ACTION_PAUSE else ACTION_PLAY
        val actionIntent = PendingIntent.getService(this, if (playing) 1 else 2,
            Intent(this, PlayerPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(requestedTitle)
            .setContentText("Miowser")
            .setContentIntent(mainIntent)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(playing)
            .addAction(Notification.Action.Builder(
                Icon.createWithResource(this,
                    if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                if (playing) "Pause" else "Play", actionIntent).build())
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)
                .setShowActionsInCompactView(0))
            .build()
    }

    override fun onDestroy() {
        playerListener?.let { player?.removeListener(it) }
        playerListener = null
        player = null
        mediaSession.release()
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "miowser_media_playback"
        private const val NOTIFICATION_ID = 2002
        private const val ACTION_PLAY = "com.miowser.app.player.PLAY"
        private const val ACTION_PAUSE = "com.miowser.app.player.PAUSE"
        private var instance: PlayerPlaybackService? = null
        private var requestedPlayer: ExoPlayer? = null
        private var requestedTitle = "Video"
        var externalControlRevision = 0L
            private set

        fun attach(context: Context, player: ExoPlayer, title: String) {
            requestedPlayer = player
            requestedTitle = title.ifBlank { "Video" }
            val service = instance
            if (service != null) service.bindPlayer(player)
            else ContextCompat.startForegroundService(context,
                Intent(context, PlayerPlaybackService::class.java))
        }

        fun updateTitle(player: ExoPlayer, title: String) {
            if (requestedPlayer !== player) return
            requestedTitle = title.ifBlank { "Video" }
            instance?.publish()
        }

        fun detach(context: Context, player: ExoPlayer) {
            if (requestedPlayer !== player) return
            requestedPlayer = null
            instance?.let { service ->
                service.playerListener?.let { player.removeListener(it) }
                service.playerListener = null
                service.player = null
            }
            context.stopService(Intent(context, PlayerPlaybackService::class.java))
        }
    }
}
