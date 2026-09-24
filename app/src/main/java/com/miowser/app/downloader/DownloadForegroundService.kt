package com.miowser.app.downloader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
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
private const val EXTRA_DOWNLOAD_ID = "download_id"

/**
 * Keeps active work alive and publishes one independent notification per download. One of those
 * notifications owns Android's foreground-service slot; the others are ordinary ongoing
 * notifications with the same progress, timing, tap target, and per-download pause/resume action.
 */
class DownloadForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val postedNotificationIds = mutableSetOf<Int>()
    private var foregroundNotificationId: Int? = null

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
        val download = DownloadQueue.downloads.firstOrNull { it.id == downloadId }
        when (intent?.action) {
            ACTION_PAUSE_DOWNLOAD -> download?.let { DownloadQueue.pause(applicationContext, it) }
            ACTION_RESUME_DOWNLOAD -> download?.let { DownloadQueue.resume(applicationContext, it) }
        }
        refreshNotifications()
        handler.removeCallbacks(tick)
        handler.postDelayed(tick, TICK_MS)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        cancelPostedNotifications()
        super.onDestroy()
    }

    /** Returns false after stopping because no active download remains. */
    private fun refreshNotifications(): Boolean {
        val active = DownloadQueue.downloads.filter { it.status.isNotificationActive() }
        if (active.isEmpty()) {
            cancelPostedNotifications()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return false
        }

        val manager = getSystemService(NotificationManager::class.java) ?: return true
        val foregroundDownload = active.firstOrNull {
            notificationId(it.id) == foregroundNotificationId
        } ?: active.first()
        val newForegroundId = notificationId(foregroundDownload.id)

        // Establish the replacement foreground notification before canceling a prior one, so the
        // service never momentarily runs without Android's required foreground notification.
        startForeground(newForegroundId, buildNotification(foregroundDownload))
        foregroundNotificationId = newForegroundId

        val currentIds = active.mapTo(mutableSetOf()) { download ->
            val id = notificationId(download.id)
            if (id != newForegroundId) manager.notify(id, buildNotification(download))
            id
        }
        (postedNotificationIds - currentIds).forEach(manager::cancel)
        postedNotificationIds.clear()
        postedNotificationIds.addAll(currentIds)
        return true
    }

    private fun buildNotification(download: Download): Notification {
        val percent = download.progressPercent()
        val timing = download.timeProgressLabel()
        val contentText = when (download.status) {
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
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)

        when {
            download.status == DownloadStatus.MUXING -> builder.setProgress(0, 0, true)
            percent != null -> builder.setProgress(100, percent, false)
            else -> builder.setProgress(0, 0, true)
        }
        when (download.status) {
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
        return builder.build()
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
        foregroundNotificationId = null
    }

    private fun createChannelIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, DownloadForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}

private fun DownloadStatus.isNotificationActive(): Boolean =
    this == DownloadStatus.QUEUED || this == DownloadStatus.DOWNLOADING ||
        this == DownloadStatus.PAUSED || this == DownloadStatus.MUXING

/** Download ids are monotonic and stay far below this range in real use. */
private fun notificationId(downloadId: Long): Int =
    NOTIFICATION_ID_BASE + (downloadId % 1_000_000L).toInt()
