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
private const val NOTIFICATION_ID = 4201
private const val TICK_MS = 1000L

/**
 * A minimal foreground service whose only job is keeping the process alive and showing one
 * summary notification while DownloadQueue has active work — Android's background-execution
 * limits would otherwise threaten a multi-segment HLS download the moment the app is backgrounded.
 * The actual download logic runs on DownloadQueue's own executor regardless of this service's
 * lifecycle; this doesn't drive it, just keeps the process around and reports on it. Started by
 * DownloadQueue.enqueue and stops itself once nothing is left running.
 */
class DownloadForegroundService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            if (!DownloadQueue.hasActiveWork()) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return
            }
            updateNotification()
            handler.postDelayed(this, TICK_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createChannelIfNeeded()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification())
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        super.onDestroy()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val active = DownloadQueue.downloads.filter {
            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MUXING
        }
        val title = when {
            active.isEmpty() -> "Downloads"
            active.size == 1 -> active[0].displayName
            else -> "Downloading ${active.size} items"
        }
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val isMuxing = active.any { it.status == DownloadStatus.MUXING }
        val single = active.singleOrNull()
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
        when {
            isMuxing -> builder.setProgress(0, 0, true) // indeterminate: muxing has no byte/segment progress of its own
            single != null && single.totalBytes > 0 -> builder.setProgress(100, ((single.bytesDownloaded * 100) / single.totalBytes).toInt(), false)
            else -> builder.setProgress(0, 0, false)
        }
        return builder.build()
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
    }
}
