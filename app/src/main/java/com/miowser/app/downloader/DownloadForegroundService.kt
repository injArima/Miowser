package com.miowser.app.downloader

import android.app.Notification
import kotlinx.coroutines.launch
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.miowser.app.MainActivity

private const val CHANNEL_ID = "miowser_downloads"
private const val NOTIFICATION_ID_BASE = 4_200
private const val TICK_MS = 1_000L
private const val ACTION_PAUSE_DOWNLOAD = "com.miowser.app.action.PAUSE_DOWNLOAD"
private const val ACTION_RESUME_DOWNLOAD = "com.miowser.app.action.RESUME_DOWNLOAD"
private const val ACTION_DISMISS_PAUSED = "com.miowser.app.action.DISMISS_PAUSED_DOWNLOAD"
private const val EXTRA_DOWNLOAD_ID = "download_id"
private const val DISMISSAL_PREFS = "download_notification_dismissals"
private const val DISMISSED_IDS = "paused_ids"

/**
 * Keeps active work alive and publishes one independent notification per download. One of those
 * notifications owns Android's foreground-service slot; the others are ordinary ongoing
 * notifications with the same progress, timing, tap target, and per-download pause/resume action.
 * Paused notifications are ordinary, dismissible notifications; paused work never owns the
 * foreground-service slot.
 */
class DownloadForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val postedNotificationIds = mutableSetOf<Int>()
    private var foregroundNotificationId: Int? = null
    private var leavePausedNotifications = false
    private var pendingResumeAfterRestore: Long? = null
    private var restoringRecords = false
    private var stoppingForTimeout = false
    private var restoreJob: kotlinx.coroutines.Job? = null
    private val presentations = mutableMapOf<Int, DownloadPresentation>()

    private val tick = object : Runnable {
        override fun run() {
            if (!refreshNotifications()) return
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getLongExtra(EXTRA_DOWNLOAD_ID, -1L) ?: -1L
        var download = DownloadQueue.downloads.firstOrNull { it.id == downloadId }
        if (intent?.action == ACTION_RESUME_DOWNLOAD && download == null && downloadId >= 0L) {
            pendingResumeAfterRestore = downloadId
            restoringRecords = true
            val id = notificationId(downloadId)
            startForeground(id, NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download).setContentTitle("Preparing download…")
                .setProgress(0, 0, true).setOngoing(true).build())
            foregroundNotificationId = id
            postedNotificationIds.add(id)
            if (restoreJob?.isActive != true) restoreJob = com.miowser.app.startup.AppStartup.scope.launch {
                try { DownloadQueue.restoreAsync(applicationContext) }
                catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (_: Exception) {
                    pendingResumeAfterRestore = null
                    android.widget.Toast.makeText(applicationContext,
                        "Couldn't restore this download. Open Miowser and retry.", android.widget.Toast.LENGTH_LONG).show()
                }
                finally { restoringRecords = false; refreshNotifications() }
            }
        }
        when (intent?.action) {
            ACTION_PAUSE_DOWNLOAD -> download?.let { DownloadQueue.pause(applicationContext, it) }
            ACTION_RESUME_DOWNLOAD -> download?.takeUnless { it.recoveryChecking }
                ?.let { DownloadQueue.resume(applicationContext, it) }
        }
        val keepTicking = refreshNotifications()
        handler.removeCallbacks(tick)
        if (keepTicking) handler.postDelayed(tick, TICK_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        restoreJob?.cancel()
        if (!DownloadQueue.isQuiescent() || DownloadQueue.downloads.any { it.status == DownloadStatus.QUEUED }) {
            DownloadQueue.checkpointForServiceStop(applicationContext)
        }
        if (!leavePausedNotifications) cancelPostedNotifications()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        stoppingForTimeout = true
        handler.removeCallbacks(tick)
        DownloadQueue.checkpointForServiceStop(applicationContext) {
            // Workers and child segment jobs are closed before this callback.
            stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundNotificationId = null
            stoppingForTimeout = false
            refreshNotifications()
            stopSelf(startId)
        }
    }

    private fun publish(manager: NotificationManager, download: Download, foreground: Boolean = false) {
        val id = notificationId(download.id)
        val presentation = download.presentation()
        if (presentations[id] == presentation && (!foreground || foregroundNotificationId == id)) return
        val notification = buildNotification(download, presentation)
        if (foreground) startForeground(id, notification) else manager.notify(id, notification)
        presentations[id] = presentation
    }

    /** Returns false after stopping because no active download remains. */
    private fun refreshNotifications(): Boolean {
        if (restoringRecords || stoppingForTimeout) return true
        pendingResumeAfterRestore?.let { id ->
            val restored = DownloadQueue.downloads.firstOrNull { it.id == id }
            if (restored == null || !restored.recoveryChecking) {
                pendingResumeAfterRestore = null
                if (restored?.status == DownloadStatus.PAUSED) DownloadQueue.resume(applicationContext, restored)
            }
        }
        val active = DownloadQueue.downloads.filter {
            it.status.isNotificationActive() || (it.status == DownloadStatus.COMPLETED && !it.recordSaved) ||
                (it.id == pendingResumeAfterRestore && it.recoveryChecking)
        }
        if (active.isEmpty()) {
            leavePausedNotifications = false
            val hadForeground = foregroundNotificationId != null
            if (hadForeground) stopForeground(STOP_FOREGROUND_REMOVE)
            cancelPostedNotifications()
            stopSelf()
            return false
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return true
        val paused = active.filter { it.status == DownloadStatus.PAUSED && !it.recoveryChecking }
        val dismissed = dismissedPausedIds().intersect(paused.mapTo(mutableSetOf()) { it.id.toString() })
        rememberDismissedPausedIds(dismissed)
        val running = active.filter { it.status != DownloadStatus.PAUSED || it.recoveryChecking }
        if (running.isEmpty()) {
            if (foregroundNotificationId != null) stopForeground(STOP_FOREGROUND_REMOVE)
            foregroundNotificationId = null
            val currentIds = paused.filterNot { it.id.toString() in dismissed }.mapTo(mutableSetOf()) { download ->
                notificationId(download.id).also { publish(manager, download) }
            }
            (postedNotificationIds - currentIds).forEach { manager.cancel(it); presentations.remove(it) }
            postedNotificationIds.clear()
            postedNotificationIds.addAll(currentIds)
            leavePausedNotifications = true
            stopSelf()
            return false
        }

        leavePausedNotifications = false
        val foregroundDownload = running.firstOrNull {
            notificationId(it.id) == foregroundNotificationId
        } ?: running.first()
        val newForegroundId = notificationId(foregroundDownload.id)

        // Establish the replacement foreground notification before canceling a prior one, so the
        // service never momentarily runs without Android's required foreground notification.
        publish(manager, foregroundDownload, foreground = true)
        foregroundNotificationId = newForegroundId

        val currentIds = (running + paused.filterNot { it.id.toString() in dismissed }).mapTo(mutableSetOf()) { download ->
            val id = notificationId(download.id)
            if (id != newForegroundId) publish(manager, download)
            id
        }
        (postedNotificationIds - currentIds).forEach { manager.cancel(it); presentations.remove(it) }
        postedNotificationIds.clear()
        postedNotificationIds.addAll(currentIds)
        return true
    }

    private fun buildNotification(download: Download, presentation: DownloadPresentation): Notification {
        val percent = presentation.percent
        val timing = presentation.timing
        val contentText = if (download.recoveryChecking) "Checking paused download…" else when (download.status) {
            DownloadStatus.QUEUED -> "Waiting to download…"
            DownloadStatus.DOWNLOADING -> buildString {
                append(percent?.let { "$it%" } ?: "Downloading…")
                timing?.let { append(" • $it") }
            }
            DownloadStatus.PAUSED -> buildString {
                append(percent?.let { "Paused • $it%" } ?: "Paused")
                timing?.let { append(" • $it") }
            }
            DownloadStatus.MUXING -> buildString {
                append("Combining into final file…")
                timing?.let { append(" • $it") }
            }
            DownloadStatus.COMPLETED -> "Saving download record…"
            else -> download.status.name.lowercase().replaceFirstChar(Char::uppercase)
        }
        val id = notificationId(download.id)
        val contentIntent = PendingIntent.getActivity(
            this,
            id,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(download.displayName)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(download.status != DownloadStatus.PAUSED || download.recoveryChecking)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        when {
            download.status == DownloadStatus.MUXING -> builder.setProgress(0, 0, true)
            percent != null -> builder.setProgress(100, percent, false)
            else -> builder.setProgress(0, 0, true)
        }
        when (download.status.takeUnless { download.recoveryChecking }) {
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> builder.addAction(
                android.R.drawable.ic_media_pause,
                "Pause",
                serviceAction(ACTION_PAUSE_DOWNLOAD, download.id, id * 2)
            )
            DownloadStatus.PAUSED -> builder.addAction(
                android.R.drawable.ic_media_play,
                "Resume",
                serviceAction(ACTION_RESUME_DOWNLOAD, download.id, id * 2 + 1)
            )
            else -> Unit
        }
        if (download.status == DownloadStatus.PAUSED && !download.recoveryChecking) {
            builder.setDeleteIntent(PendingIntent.getBroadcast(
                this, id,
                Intent(this, DownloadNotificationDismissReceiver::class.java)
                    .setAction(ACTION_DISMISS_PAUSED)
                    .putExtra(EXTRA_DOWNLOAD_ID, download.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
        }
        return builder.build()
    }

    private fun dismissedPausedIds(): Set<String> =
        getSharedPreferences(DISMISSAL_PREFS, MODE_PRIVATE)
            .getStringSet(DISMISSED_IDS, emptySet())?.toSet().orEmpty()

    private fun rememberDismissedPausedIds(ids: Set<String>) {
        if (ids == dismissedPausedIds()) return
        getSharedPreferences(DISMISSAL_PREFS, MODE_PRIVATE).edit()
            .putStringSet(DISMISSED_IDS, ids).apply()
    }

    private fun serviceAction(action: String, downloadId: Long, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            this,
            requestCode,
            Intent(this, DownloadForegroundService::class.java)
                .setAction(action)
                .putExtra(EXTRA_DOWNLOAD_ID, downloadId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelPostedNotifications() {
        val manager = getSystemService(NotificationManager::class.java)
        postedNotificationIds.forEach { manager?.cancel(it) }
        postedNotificationIds.clear()
        presentations.clear()
        foregroundNotificationId = null
    }

    private fun createChannelIfNeeded() {
        val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            context.startForegroundService(intent)
        }
    }
}

/** A swipe hides one paused notice without canceling or resuming its download. */
class DownloadNotificationDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DISMISS_PAUSED) return
        val id = intent.getLongExtra(EXTRA_DOWNLOAD_ID, -1L)
        if (id < 0L) return
        val prefs = context.getSharedPreferences(DISMISSAL_PREFS, Context.MODE_PRIVATE)
        val ids = prefs.getStringSet(DISMISSED_IDS, emptySet()).orEmpty().toMutableSet()
        ids.add(id.toString())
        prefs.edit().putStringSet(DISMISSED_IDS, ids).apply()
    }
}

private fun DownloadStatus.isNotificationActive(): Boolean =
    this == DownloadStatus.QUEUED || this == DownloadStatus.DOWNLOADING ||
        this == DownloadStatus.PAUSED || this == DownloadStatus.MUXING

/** Download ids are monotonic and stay far below this range in real use. */
private fun notificationId(downloadId: Long): Int =
    NOTIFICATION_ID_BASE + (downloadId % 1_000_000L).toInt()
