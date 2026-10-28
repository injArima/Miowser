package com.miowser.app.browser.video

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
import com.miowser.app.MainActivity
import com.miowser.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the page-owned audio engine alive without replacing it with ExoPlayer. */
internal class BrowserPlaybackService : Service() {
    private data class PublishedState(
        val playing: Boolean,
        val buffering: Boolean,
        val durationMs: Long,
        val pausedPositionMs: Long,
        val speed: Float,
        val sourceRevision: Long
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private var controls: BrowserPlaybackControls? = null
    private var stateJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Browser audio playback", NotificationManager.IMPORTANCE_LOW)
        )
        mediaSession = MediaSession(this, "Miowser browser audio").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    controls?.let { target ->
                        if (target.state.value.session?.state?.playing != true) target.playPause()
                    }
                }
                override fun onPause() { controls?.pause() }
                override fun onSeekTo(pos: Long) { controls?.seekTo(pos.coerceAtLeast(0L)) }
                override fun onStop() { controls?.pause() }
            })
            isActive = true
        }
        // startForegroundService() must always be paired with startForeground(), even when the
        // page rejects audio-only mode and Compose detaches the owner before onStartCommand runs.
        startForeground(NOTIFICATION_ID, notification(false))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val requested = requestedControls
        if (requested == null) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        bind(requested)
        when (intent?.action) {
            ACTION_PLAY -> if (requested.state.value.session?.state?.playing != true) requested.playPause()
            ACTION_PAUSE -> requested.pause()
        }
        publish()
        return START_NOT_STICKY
    }

    private fun bind(next: BrowserPlaybackControls) {
        if (controls === next && stateJob != null) return
        stateJob?.cancel()
        controls = next
        stateJob = scope.launch {
            next.state.map { state ->
                val video = state.session?.state
                PublishedState(
                    playing = video?.playing == true,
                    buffering = video?.buffering == true,
                    durationMs = video?.durationMs ?: -1L,
                    pausedPositionMs = if (video?.playing == true) -1L else video?.positionMs ?: 0L,
                    speed = video?.speed?.toFloat() ?: 1f,
                    sourceRevision = video?.sourceRevision ?: -1L
                )
            }.distinctUntilChanged().collect { publish() }
        }
    }

    private fun publish() {
        val target = controls ?: return
        val video = target.state.value.session?.state ?: return
        val duration = video.durationMs ?: -1L
        mediaSession.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, requestedTitle)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
            .build())
        val state = when {
            video.buffering -> PlaybackState.STATE_BUFFERING
            video.playing -> PlaybackState.STATE_PLAYING
            else -> PlaybackState.STATE_PAUSED
        }
        mediaSession.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SEEK_TO or PlaybackState.ACTION_STOP)
            .setState(state, video.positionMs.coerceAtLeast(0L),
                if (state == PlaybackState.STATE_PLAYING) video.speed.toFloat() else 0f,
                SystemClock.elapsedRealtime())
            .build())
        startForeground(NOTIFICATION_ID, notification(video.playing))
    }

    private fun notification(playing: Boolean): Notification {
        val mainIntent = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).apply { action = Intent.ACTION_MAIN },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val action = if (playing) ACTION_PAUSE else ACTION_PLAY
        val actionIntent = PendingIntent.getService(this, if (playing) 1 else 2,
            Intent(this, BrowserPlaybackService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(requestedTitle)
            .setContentText("Browser audio-only playback")
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
        stateJob?.cancel()
        stateJob = null
        controls = null
        mediaSession.release()
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "miowser_browser_audio"
        private const val NOTIFICATION_ID = 2003
        private const val ACTION_PLAY = "com.miowser.app.browser.video.PLAY"
        private const val ACTION_PAUSE = "com.miowser.app.browser.video.PAUSE"
        private var instance: BrowserPlaybackService? = null
        private var requestedControls: BrowserPlaybackControls? = null
        private var requestedTitle = "Browser video"

        fun attach(context: Context, controls: BrowserPlaybackControls, title: String) {
            requestedControls = controls
            requestedTitle = title.ifBlank { "Browser video" }
            instance?.let { service -> service.bind(controls); service.publish() }
                ?: ContextCompat.startForegroundService(context,
                    Intent(context, BrowserPlaybackService::class.java))
        }

        fun detach(context: Context, controls: BrowserPlaybackControls) {
            if (requestedControls !== controls) return
            requestedControls = null
            instance?.stateJob?.cancel()
            instance?.stateJob = null
            instance?.controls = null
            context.stopService(Intent(context, BrowserPlaybackService::class.java))
        }
    }
}
