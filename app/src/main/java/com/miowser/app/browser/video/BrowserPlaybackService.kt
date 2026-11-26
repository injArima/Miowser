package com.miowser.app.browser.video

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Icon
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebSettings
import com.miowser.app.browser.media.MediaArtworkPolicy
import kotlinx.coroutines.delay
import android.util.LruCache
import androidx.core.content.ContextCompat
import com.miowser.app.MainActivity
import com.miowser.app.R
import com.miowser.app.browser.media.PageMediaControls
import com.miowser.app.browser.media.PageMediaNotificationAccess
import com.miowser.app.browser.media.PageMediaNotificationKey
import com.miowser.app.browser.media.PageMediaPositionAnchor
import com.miowser.app.browser.media.PageMediaSnapshot
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Publishes controls for the page's engine. This service never creates a player. */
internal class BrowserPlaybackService : Service() {
    private data class Owner(val controls: Any, val snapshot: PageMediaSnapshot)
    private data class MetadataKey(val title: String, val artist: String, val album: String,
        val pageUrl: String, val durationMs: Long, val artworkUrl: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var mediaSession: MediaSession
    private var artworkUserAgent = ""
    private var owner: Owner? = null
    private var ownerToken = ""
    private var stateJob: Job? = null
    private var artworkJob: Job? = null
    private var artworkUrl: String? = null
    private var artwork: Bitmap? = null
    private var anchor: PageMediaPositionAnchor? = null
    private var metadataKey: MetadataKey? = null
    private var notificationKey: PageMediaNotificationKey? = null
    private var foreground = false
    private var stopping = false
    private var destroyed = false
    private val artworkSlots = Semaphore(2)
    private val artworkCache = object : LruCache<String, Bitmap>(MediaArtworkPolicy.cacheBytes) {
        // Charge a minimum per entry as well: at most eight URLs, even for tiny bitmaps.
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount.coerceAtLeast(MediaArtworkPolicy.minCacheCharge)
    }

    override fun onCreate() {
        super.onCreate()
        artworkUserAgent = WebSettings.getDefaultUserAgent(this)
        instance = this
        startPending = false
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Browser audio playback", NotificationManager.IMPORTANCE_LOW)
        )
        mediaSession = MediaSession(this, "Miowser browser audio").apply {
            setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        }
        // Pair every foreground-service start, including a detach before onStartCommand arrives.
        startForeground(NOTIFICATION_ID, notification(null))
        foreground = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        instance = this
        startPending = false
        stopping = false
        // An attach can race a queued stopSelf. Pair the new start before handling commands.
        if (!foreground) {
            startForeground(NOTIFICATION_ID, notification(null))
            foreground = true
        }
        synchronizeOwner()
        if (intent?.action == ACTION_COMMAND) {
            val token = intent.getStringExtra(EXTRA_TOKEN)
            val action = intent.getStringExtra(EXTRA_COMMAND)
            if (token != null && action != null) dispatch(token, action)
        }
        return START_NOT_STICKY
    }

    private fun selectedOwner(): Owner? {
        requestedControls?.let { controls ->
            val session = controls.state.value.session
            val video = session?.state
            return Owner(controls, PageMediaSnapshot(
                tabId = -1, ownerKey = "fullscreen:${session?.id}:${video?.sourceRevision}",
                title = requestedTitle, artist = "Browser audio-only playback", album = "",
                artworkUrl = null, pageUrl = "", playing = video?.playing == true,
                buffering = video?.buffering == true, positionMs = video?.positionMs ?: 0,
                durationMs = video?.durationMs ?: -1, speed = video?.speed?.toFloat() ?: 1f,
                actions = setOf("play", "pause", "seekto", "stop")
            ))
        }
        val controls = requestedPage ?: return null
        val snapshot = controls.snapshot?.takeIf { it.ownerKey.isNotBlank() } ?: return null
        return Owner(controls, snapshot)
    }

    private fun sameOwner(a: Owner?, b: Owner?): Boolean = a != null && b != null &&
        a.controls === b.controls && a.snapshot.hasSameOwner(b.snapshot)

    private fun synchronizeOwner() {
        if (destroyed) return
        val next = selectedOwner()
        if (next == null) {
            clearOwner()
            stopForeground(STOP_FOREGROUND_REMOVE)
            foreground = false
            stopping = true
            stopSelf()
            return
        }
        if (!sameOwner(owner, next)) {
            clearOwner()
            owner = next
            ownerToken = UUID.randomUUID().toString()
            val token = ownerToken
            // Capture the token so callbacks queued for a replaced owner cannot control it.
            mediaSession.setCallback(object : MediaSession.Callback() {
                override fun onPlay() = dispatch(token, "play")
                override fun onPause() = dispatch(token, "pause")
                override fun onSeekTo(pos: Long) = dispatch(token, "seekto", pos)
                override fun onSkipToNext() = dispatch(token, "nexttrack")
                override fun onSkipToPrevious() = dispatch(token, "previoustrack")
                override fun onFastForward() = dispatch(token, "seekforward")
                override fun onRewind() = dispatch(token, "seekbackward")
                override fun onStop() = dispatch(token, "stop")
            }, Handler(Looper.getMainLooper()))
            mediaSession.isActive = true
            val legacy = next.controls as? BrowserPlaybackControls
            if (legacy != null) stateJob = scope.launch {
                legacy.state.collect { synchronizeOwner() }
            }
        }
        owner = next
        publish(next.snapshot.forPublication())
    }

    private fun clearOwner() {
        stateJob?.cancel()
        stateJob = null
        artworkJob?.cancel()
        artworkJob = null
        artworkUrl = null
        artwork = null
        owner = null
        ownerToken = ""
        anchor = null
        metadataKey = null
        notificationKey = null
        mediaSession.isActive = false
        mediaSession.setCallback(null)
        mediaSession.setSessionActivity(null)
        mediaSession.setMetadata(null)
        mediaSession.setPlaybackState(null)
    }

    private fun dispatch(token: String, action: String, positionMs: Long? = null) {
        val current = owner ?: return
        val selected = selectedOwner()
        if (token != ownerToken || !sameOwner(current, selected)) return
        val snapshot = selected?.snapshot?.forPublication() ?: return
        val isStop = action == "stop"
        if (!isStop && action !in snapshot.actions) return
        val position = positionMs?.coerceAtLeast(0)?.let {
            if (snapshot.durationMs > 0) it.coerceAtMost(snapshot.durationMs) else it
        }
        when (val target = current.controls) {
            is BrowserPlaybackControls -> when (action) {
                "play" -> if (!snapshot.playing) target.playPause()
                "pause", "stop" -> target.pause()
                "seekto" -> position?.let(target::seekTo)
            }
            is PageMediaControls -> {
                // Pages without a registered stop handler pause; retirement belongs to coordinator.
                target.command(if (isStop && "stop" !in snapshot.actions) "pause" else action, position)
            }
        }
        synchronizeOwner()
    }

    private fun publish(snapshot: PageMediaSnapshot) {
        updateArtwork(snapshot)
        val nextMetadata = MetadataKey(snapshot.title, snapshot.artist, snapshot.album,
            snapshot.pageUrl, snapshot.durationMs, snapshot.artworkUrl)
        if (metadataKey != nextMetadata) {
            metadataKey = nextMetadata
            mediaSession.setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, snapshot.title)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, snapshot.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, snapshot.artist)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, snapshot.artist.ifBlank { snapshot.album })
                .putString(MediaMetadata.METADATA_KEY_ALBUM, snapshot.album)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, snapshot.album)
                .putString(MediaMetadata.METADATA_KEY_MEDIA_URI, snapshot.pageUrl)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, snapshot.durationMs)
                .apply { artwork?.let {
                    putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, it)
                    putBitmap(MediaMetadata.METADATA_KEY_ART, it)
                    putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, it)
                } }
                .build())
        }
        val now = SystemClock.elapsedRealtime()
        if (anchor?.needsUpdate(snapshot, now) != false) {
            anchor = PageMediaPositionAnchor(snapshot, now)
            val state = when {
                snapshot.buffering -> PlaybackState.STATE_BUFFERING
                snapshot.playing -> PlaybackState.STATE_PLAYING
                else -> PlaybackState.STATE_PAUSED
            }
            mediaSession.setPlaybackState(PlaybackState.Builder()
                .setActions(playbackActions(snapshot.actions))
                .setState(state, snapshot.positionMs,
                    if (state == PlaybackState.STATE_PLAYING) snapshot.speed else 0f, now)
                .build())
        }
        val nextNotification = snapshot.notificationKey()
        if (!foreground || notificationKey != nextNotification) {
            notificationKey = nextNotification
            val notification = notification(snapshot)
            if (!foreground) {
                startForeground(NOTIFICATION_ID, notification)
                foreground = true
            } else getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun playbackActions(actions: Set<String>): Long {
        var result = PlaybackState.ACTION_STOP
        if ("play" in actions) result = result or PlaybackState.ACTION_PLAY
        if ("pause" in actions) result = result or PlaybackState.ACTION_PAUSE
        if ("play" in actions && "pause" in actions) result = result or PlaybackState.ACTION_PLAY_PAUSE
        if ("seekto" in actions) result = result or PlaybackState.ACTION_SEEK_TO
        if ("nexttrack" in actions) result = result or PlaybackState.ACTION_SKIP_TO_NEXT
        if ("previoustrack" in actions) result = result or PlaybackState.ACTION_SKIP_TO_PREVIOUS
        if ("seekforward" in actions) result = result or PlaybackState.ACTION_FAST_FORWARD
        if ("seekbackward" in actions) result = result or PlaybackState.ACTION_REWIND
        return result
    }

    private fun notification(snapshot: PageMediaSnapshot?): Notification {
        val isPage = owner?.controls is PageMediaControls
        val main = Intent(this, MainActivity::class.java).apply {
            action = if (isPage) ACTION_OPEN_PAGE else Intent.ACTION_MAIN
            if (isPage && snapshot != null) {
                putExtra(EXTRA_TAB_ID, snapshot.tabId)
                putExtra(EXTRA_TOKEN, ownerToken)
                data = intentIdentity("open")
            }
        }
        val tap = PendingIntent.getActivity(this, 0, main,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        mediaSession.setSessionActivity(tap)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setSmallIcon(R.drawable.ic_notification_miowser)
            .setContentTitle(snapshot?.title ?: "Browser media")
            .setContentText(snapshot?.artist?.ifBlank { snapshot.album }?.ifBlank { "Browser playback" })
            .setContentIntent(tap)
            .setVisibility(if (snapshot?.isPrivate == true) Notification.VISIBILITY_PRIVATE
                else Notification.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setOngoing(snapshot?.playing == true)
        artwork?.let { builder.setLargeIcon(it) }
        val compact = mutableListOf<Int>()
        var index = 0
        fun add(action: String, label: String, icon: Int, inCompact: Boolean = true) {
            val pending = PendingIntent.getService(this, 0,
                Intent(this, BrowserPlaybackService::class.java).apply {
                    this.action = ACTION_COMMAND
                    data = intentIdentity(action)
                    putExtra(EXTRA_TOKEN, ownerToken)
                    putExtra(EXTRA_COMMAND, action)
                }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            builder.addAction(Notification.Action.Builder(
                Icon.createWithResource(this, icon), label, pending).build())
            if (inCompact) compact += index
            index++
        }
        if (snapshot != null) {
            if ("previoustrack" in snapshot.actions) {
                add("previoustrack", "Previous", android.R.drawable.ic_media_previous)
            }
            val toggle = if (snapshot.playing) "pause" else "play"
            if (toggle in snapshot.actions) add(toggle, if (snapshot.playing) "Pause" else "Play",
                if (snapshot.playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
            if ("nexttrack" in snapshot.actions) add("nexttrack", "Next", android.R.drawable.ic_media_next)
            add("stop", "Stop", android.R.drawable.ic_menu_close_clear_cancel, inCompact = compact.size < 3)
        }
        return builder.setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(*compact.take(3).toIntArray())).build()
    }

    private fun intentIdentity(action: String): Uri = Uri.Builder().scheme("miowser-media")
        .authority("owner").appendPath(ownerToken).appendPath(action).build()

    private fun updateArtwork(snapshot: PageMediaSnapshot) {
        val url = snapshot.artworkUrl?.takeIf { !snapshot.isPrivate && it.length <= 4096 }
        if (artworkUrl == url) return
        artworkJob?.cancel()
        artworkJob = null
        artworkUrl = url
        artwork = url?.let(artworkCache::get)
        metadataKey = null
        notificationKey = null
        if (url == null || artwork != null) return
        val token = ownerToken
        artworkJob = scope.launch {
            val bitmap = withContext(Dispatchers.IO) {
                var loaded: Bitmap? = null
                for (attempt in 0 until MediaArtworkPolicy.attempts) {
                    if (attempt > 0) delay(MediaArtworkPolicy.retryDelayMs)
                    loaded = artworkSlots.withPermit { fetchArtwork(url, snapshot.pageUrl) }
                    if (loaded != null) break
                }
                loaded
            } ?: return@launch
            if (ownerToken != token || artworkUrl != url ||
                !sameOwner(owner, selectedOwner()) || selectedOwner()?.snapshot?.isPrivate != false) return@launch
            artworkCache.put(url, bitmap)
            artwork = bitmap
            metadataKey = null
            notificationKey = null
            synchronizeOwner()
        }
    }

    /** Network, bounded reads, bounds inspection and sampled decode all run off the main thread. */
    private suspend fun fetchArtwork(address: String, pageUrl: String): Bitmap? {
        try {
            var url = URL(address)
            val deadline = SystemClock.elapsedRealtime() + 8_000L
            repeat(4) {
                currentCoroutineContext().ensureActive()
                if (SystemClock.elapsedRealtime() > deadline ||
                    url.protocol !in setOf("http", "https") || url.userInfo != null) return null
                val connection = url.openConnection() as? HttpURLConnection ?: return null
                try {
                    connection.connectTimeout = 2_000
                    connection.readTimeout = 2_000
                    connection.instanceFollowRedirects = false
                    connection.useCaches = true
                    if (artworkUserAgent.isNotBlank()) connection.setRequestProperty("User-Agent", artworkUserAgent)
                    connection.setRequestProperty("Accept", "image/webp,image/png,image/jpeg,image/*;q=0.8")
                    MediaArtworkPolicy.referrer(pageUrl, url.toString())?.let { connection.setRequestProperty("Referer", it) }
                    val code = connection.responseCode
                    if (code in setOf(301, 302, 303, 307, 308)) {
                        val next = URL(url, connection.getHeaderField("Location") ?: return null)
                        if (url.protocol == "https" && next.protocol != "https") return null
                        url = next
                    } else {
                        if (code != 200 || connection.contentLengthLong > MediaArtworkPolicy.maxEncodedBytes) return null
                        val bytes = ByteArrayOutputStream()
                        connection.inputStream.use { input ->
                            val buffer = ByteArray(8192)
                            while (true) {
                                currentCoroutineContext().ensureActive()
                                if (SystemClock.elapsedRealtime() > deadline) return null
                                val count = input.read(buffer)
                                if (count < 0) break
                                if (bytes.size() + count > MediaArtworkPolicy.maxEncodedBytes) return null
                                bytes.write(buffer, 0, count)
                            }
                        }
                        val data = bytes.toByteArray()
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeByteArray(data, 0, data.size, options)
                        if (options.outWidth !in 1..8192 || options.outHeight !in 1..8192) return null
                        options.inSampleSize = MediaArtworkPolicy.sampleSize(options.outWidth, options.outHeight)
                        options.inJustDecodeBounds = false
                        options.inPreferredConfig = Bitmap.Config.ARGB_8888
                        currentCoroutineContext().ensureActive()
                        return BitmapFactory.decodeByteArray(data, 0, data.size, options)
                    }
                } finally { connection.disconnect() }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Artwork is optional. A bad URL or unavailable site does not interrupt playback.
        }
        return null
    }

    override fun onDestroy() {
        destroyed = true
        clearOwner()
        scope.cancel()
        artworkCache.evictAll()
        mediaSession.release()
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        internal const val ACTION_OPEN_PAGE = "com.miowser.app.browser.media.OPEN_PAGE"
        internal const val EXTRA_TAB_ID = "com.miowser.app.browser.media.TAB_ID"
        private const val EXTRA_TOKEN = "com.miowser.app.browser.media.TOKEN"
        private const val EXTRA_COMMAND = "com.miowser.app.browser.media.COMMAND"
        private const val ACTION_COMMAND = "com.miowser.app.browser.media.COMMAND"
        private const val CHANNEL_ID = "miowser_browser_audio"
        private const val NOTIFICATION_ID = 2003
        private var instance: BrowserPlaybackService? = null
        private var startPending = false
        private var requestedControls: BrowserPlaybackControls? = null
        private var requestedTitle = "Browser video"
        private var requestedPage: PageMediaControls? = null

        /** All ownership APIs are called on the main thread, like the browser controllers. */
        fun attach(context: Context, controls: BrowserPlaybackControls, title: String) {
            requestedControls = controls
            requestedTitle = title.ifBlank { "Browser video" }
            refreshOrStart(context)
        }

        fun detach(context: Context, controls: BrowserPlaybackControls) {
            if (requestedControls !== controls) return
            requestedControls = null
            refreshOrStart(context)
        }

        fun attachPage(context: Context, controls: PageMediaControls): Boolean {
            requestedPage = controls
            return refreshOrStart(context)
        }

        /** Refresh never takes ownership from a newer tracker. */
        fun refreshPage(context: Context, controls: PageMediaControls): Boolean =
            requestedPage === controls && refreshOrStart(context)

        fun detachPage(context: Context, controls: PageMediaControls) {
            if (requestedPage !== controls) return
            requestedPage = null
            refreshOrStart(context)
        }

        /** MainActivity is exported: only a current, process-issued notification may select a tab. */
        fun notificationTab(intent: Intent): Long? {
            if (intent.action != ACTION_OPEN_PAGE || !intent.hasExtra(EXTRA_TAB_ID)) return null
            val service = instance ?: return null
            val owner = service.owner ?: return null
            if (owner.controls !is PageMediaControls || requestedPage !== owner.controls ||
                !service.sameOwner(owner, service.selectedOwner())) return null
            val tabId = intent.getLongExtra(EXTRA_TAB_ID, Long.MIN_VALUE)
            return PageMediaNotificationAccess(owner.snapshot, service.ownerToken)
                .tabId(intent.getStringExtra(EXTRA_TOKEN), tabId, requestedPage?.snapshot)
        }

        private fun refreshOrStart(context: Context): Boolean {
            val service = instance
            if (service != null && !service.stopping) {
                service.synchronizeOwner()
                return service.owner != null
            }
            if (startPending) return true
            if (requestedControls != null || requestedPage?.snapshot?.ownerKey?.isNotBlank() == true) {
                startPending = true
                try {
                    ContextCompat.startForegroundService(context.applicationContext,
                        Intent(context, BrowserPlaybackService::class.java))
                    return true
                } catch (_: RuntimeException) {
                    // Background-start restrictions and provider/security failures are not fatal
                    // to a page callback or Compose. Don't retain an unhosted playback owner.
                    startPending = false
                    requestedControls = null
                    requestedPage = null
                }
            }
            return false
        }
    }
}
