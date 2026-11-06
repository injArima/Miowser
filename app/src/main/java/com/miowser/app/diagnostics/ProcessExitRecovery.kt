package com.miowser.app.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.miowser.app.BuildConfig
import kotlin.math.abs

/** Android can report native crashes, ANRs, and kills that never reach a Java exception handler. */
internal object ProcessExitRecovery {
    private const val PREFS = "crash_diagnostics_state"
    private const val LAST_EXIT = "last_observed_exit_ms"

    fun initialize(context: Context) {
        if (Build.VERSION.SDK_INT < 30) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.contains(LAST_EXIT)) {
            // Begin at installation of this handler; do not import historical exits recorded
            // before the user had local crash saving available.
            prefs.edit().putLong(LAST_EXIT, System.currentTimeMillis()).commit()
        }
    }

    fun recover(context: Context) {
        if (Build.VERSION.SDK_INT < 30 || !CrashDiagnostics.enabled) return
        val app = context.applicationContext
        Thread({ runCatching { recoverOnWorker(app) } }, "miowser-exit-recovery")
            .apply { isDaemon = true }.start()
    }

    @androidx.annotation.RequiresApi(30)
    private fun recoverOnWorker(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var lastSeen = prefs.getLong(LAST_EXIT, System.currentTimeMillis())
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return
        val exits = manager.getHistoricalProcessExitReasons(context.packageName, 0, 32)
            .filter {
                val name = it.processName.orEmpty()
                it.timestamp > lastSeen &&
                    (name == context.packageName || name.startsWith("${context.packageName}:"))
            }
            .sortedBy(ApplicationExitInfo::getTimestamp)
        for (exit in exits) {
            if (!CrashDiagnostics.enabled) return
            val reason = reasonLabel(exit.reason)
            if (reason != null && !alreadySaved(context, exit)) {
                val saved = runCatching {
                    CrashReportStore.create(context, exit.timestamp) { writer ->
                        writer.write("Miowser process exit report (Android system record)\n")
                        writer.write("created_at_ms=${exit.timestamp}\n")
                        writer.write("reporter_version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                        writer.write("pid=${exit.pid}\n")
                        writer.write("reason=$reason\n")
                        writer.write("status=${exit.status}\n")
                        writer.write("pss_kb=${exit.pss}\n")
                        writer.write("rss_kb=${exit.rss}\n")
                        writer.write("No Java stack was captured before the process exited.\n")
                    }
                }.getOrDefault(false)
                if (!saved) return // Retry after the next start if storage was unavailable.
            }
            lastSeen = exit.timestamp
            prefs.edit().putLong(LAST_EXIT, lastSeen).commit()
        }
    }

    @androidx.annotation.RequiresApi(30)
    private fun alreadySaved(context: Context, exit: ApplicationExitInfo): Boolean =
        CrashReportStore.list(context).any { summary ->
            abs(summary.createdAt - exit.timestamp) < 10_000L &&
                CrashReportStore.read(context, summary.fileName)
                    ?.contains("pid=${exit.pid}\n") == true
        }

    @androidx.annotation.RequiresApi(30)
    private fun reasonLabel(reason: Int): String? = when (reason) {
        ApplicationExitInfo.REASON_CRASH -> "Java crash"
        ApplicationExitInfo.REASON_CRASH_NATIVE -> "Native crash"
        ApplicationExitInfo.REASON_ANR -> "App not responding"
        ApplicationExitInfo.REASON_LOW_MEMORY -> "System low-memory kill"
        ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "Process initialization failure"
        else -> null
    }
}
