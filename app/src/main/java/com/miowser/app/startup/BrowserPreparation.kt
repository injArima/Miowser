package com.miowser.app.startup

import android.app.ActivityManager
import android.content.Context
import android.os.Looper
import android.os.MessageQueue
import android.os.SystemClock
import com.miowser.app.adblock.AdBlockFilters
import com.miowser.app.bookmarks.BookmarkList
import com.miowser.app.browser.InactiveTabs
import com.miowser.app.browser.SpeedDials
import com.miowser.app.diagnostics.SafeLog
import com.miowser.app.history.HistoryLog
import com.miowser.app.settings.AppSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** One sequential preparation pipeline in this process, shared with explicit browser entry. */
internal object BrowserPreparation {
    private var pipeline: PreparationPipeline? = null
    private var passiveFailed = false

    private fun pipeline(context: Context): PreparationPipeline {
        val app = context.applicationContext
        return pipeline ?: PreparationPipeline(listOf(
            timed("provider background") { WebViewStartup.prepareBackground(app) },
            timed("inactive records") { InactiveTabs.restoreAsync(app, AppSettings.current.autoDeleteInactiveTabs) },
            timed("history") { HistoryLog.restoreAsync(app) },
            timed("bookmarks") { BookmarkList.restoreAsync(app) },
            timed("speed dial") { SpeedDials.restoreAsync(app) },
            timed("saved filters") { AdBlockFilters.initializeAsync(app) }
        )).also { pipeline = it }
    }

    suspend fun warmAfterLibrary(context: Context) {
        com.miowser.app.persistence.Persistence.requireOwner()
        if (passiveFailed || pipeline?.ready == true || constrained(context)) return
        try {
            pipeline(context).prepare {
                if (constrained(context)) throw CancellationException("Defer browser preparation under memory pressure")
                awaitMainIdle()
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            passiveFailed = true // No automatic retry loop; explicit browser Retry can retry the stage.
            SafeLog.warning("Miowser/BrowserStartup", "Background preparation deferred after failure", error)
        }
    }

    suspend fun ensureSavedData(context: Context) {
        com.miowser.app.persistence.Persistence.requireOwner()
        pipeline(context).prepare()
        // The user may enable protection after passive preparation skipped the disabled engine.
        AdBlockFilters.initializeAsync(context.applicationContext)
    }

    private fun constrained(context: Context): Boolean {
        val manager = context.getSystemService(ActivityManager::class.java) ?: return false
        val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        return manager.isLowRamDevice || manager.memoryClass < 192 || memory.lowMemory
    }

    private fun timed(label: String, work: suspend () -> Unit): suspend () -> Unit = {
        val start = SystemClock.uptimeMillis()
        work()
        SafeLog.debug("Miowser/BrowserStartup", "$label: ${SystemClock.uptimeMillis() - start} ms")
    }

    private suspend fun awaitMainIdle() = suspendCancellableCoroutine<Unit> { continuation ->
        val queue = Looper.myQueue()
        val handler = MessageQueue.IdleHandler {
            if (continuation.isActive) continuation.resume(Unit)
            false
        }
        queue.addIdleHandler(handler)
        continuation.invokeOnCancellation { queue.removeIdleHandler(handler) }
    }
}
