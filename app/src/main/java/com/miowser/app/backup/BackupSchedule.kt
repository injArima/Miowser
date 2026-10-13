package com.miowser.app.backup

import android.content.Context
import android.net.Uri
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.WorkManager
import com.miowser.app.persistence.Persistence
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

enum class BackupInterval(val label: String, val days: Long) {
    OFF("Off", 0), DAILY("Daily", 1), THREE_DAYS("Every 3 days", 3), WEEKLY("Weekly", 7), MONTHLY("Every 30 days", 30)
}

data class BackupSettings(
    val treeUri: Uri?,
    val interval: BackupInterval,
    val lastSuccessfulAt: Long,
    val lastError: String?,
    val skippedArtwork: Int = 0
)

object BackupSettingsStore {
    private const val PREFS = "backup_settings"
    private const val WORK_NAME = "miowser-periodic-backup"

    fun load(context: Context): BackupSettings = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).run {
        BackupSettings(
            treeUri = getString("tree_uri", null)?.let(Uri::parse),
            interval = getString("interval", null)?.let { runCatching { BackupInterval.valueOf(it) }.getOrNull() }
                ?: BackupInterval.OFF,
            lastSuccessfulAt = getLong("last_success", 0L),
            lastError = getString("last_error", null),
            skippedArtwork = getInt("skipped_artwork", 0)
        )
    }

    fun setDirectory(context: Context, uri: Uri?) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit().putString("tree_uri", uri?.toString())
        if (uri != null && !prefs.contains("interval")) editor.putString("interval", BackupInterval.DAILY.name)
        check(Persistence.submit("backup-settings") { editor.commit() }.get()) { "Could not save the backup folder." }
        schedule(context)
    }

    fun setInterval(context: Context, interval: BackupInterval) {
        check(Persistence.submit("backup-settings") {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("interval", interval.name).commit()
        }.get()) { "Could not save the backup interval." }
        schedule(context)
    }

    fun report(context: Context, error: String?, skippedArtwork: Int = 0) {
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("last_error", error)
        if (error == null) edit.putLong("last_success", System.currentTimeMillis()).putInt("skipped_artwork", skippedArtwork)
        check(Persistence.submit("backup-report") { edit.commit() }.get()) { "Could not save backup status" }
    }

    private val scheduling = java.util.concurrent.Executors.newSingleThreadExecutor()
    fun schedule(context: Context) {
        val app = context.applicationContext
        scheduling.execute { scheduleNow(app) }
    }
    private fun scheduleNow(context: Context) {
        val current = load(context)
        val work = WorkManager.getInstance(context.applicationContext)
        if (current.treeUri == null || current.interval == BackupInterval.OFF) {
            work.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequest.Builder(BackupWorker::class.java, current.interval.days, TimeUnit.DAYS)
            .build()
        work.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}

class BackupWorker(context: Context, parameters: WorkerParameters) : Worker(context, parameters) {
    override fun doWork(): Result {
        val settings = BackupSettingsStore.load(applicationContext)
        val tree = settings.treeUri ?: return Result.success()
        if (settings.interval == BackupInterval.OFF) return Result.success()
        return try {
            val pendingWrites = CountDownLatch(1)
            Persistence.afterWrites { pendingWrites.countDown() }
            check(pendingWrites.await(20, TimeUnit.SECONDS)) { "App data was still being saved." }
            val summary = BackupEngine.create(applicationContext, tree, automatic = true)
            BackupSettingsStore.report(applicationContext, null, summary.skippedArtwork)
            Result.success()
        } catch (error: Exception) {
            runCatching { BackupSettingsStore.report(applicationContext, error.message ?: "Backup failed") }
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
