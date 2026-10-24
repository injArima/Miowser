package com.miowser.app.startup

import android.content.Context
import android.os.Process
import android.os.SystemClock
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewOutcomeReceiver
import androidx.webkit.WebViewStartUpConfig
import androidx.webkit.WebViewStartUpResult
import androidx.webkit.WebViewStartupException
import com.miowser.app.diagnostics.SafeLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

/** Process-wide provider preparation, without a hidden WebView or speculative page load. */
internal object WebViewStartup {
    private val worker = ThreadPoolExecutor(1, 1, 10, TimeUnit.SECONDS,
        LinkedBlockingQueue(), { work ->
            Thread({ Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND); work.run() },
                "browser-provider-prepare")
        }).apply { allowCoreThreadTimeOut(true) }
    // Main-owned receipts. Cancelling one awaiter does not cancel provider work already issued.
    private var background: CompletableDeferred<Unit>? = null
    private var complete: CompletableDeferred<Unit>? = null

    suspend fun prepareBackground(context: Context) = prepare(context, false)
    suspend fun ensureReady(context: Context) = prepare(context, true)

    private suspend fun prepare(context: Context, full: Boolean) = withContext(Dispatchers.Main.immediate) {
        if (full) background?.takeUnless { it.isCancelled }?.await()
        val previous = if (full) complete else background
        val receipt = previous?.takeUnless { it.isCancelled } ?: CompletableDeferred<Unit>().also { result ->
            if (full) complete = result else background = result
            val started = SystemClock.uptimeMillis()
            // Older providers may throw while AndroidX discovers their implementation.
            // Deliver that failure to callers instead of losing the worker/callback.
            val executor = Executor { command ->
                worker.execute {
                    try { command.run() }
                    catch (error: Exception) { result.completeExceptionally(error) }
                }
            }
            try {
                WebViewCompat.startUpWebView(context.applicationContext,
                    WebViewStartUpConfig.Builder(executor)
                        .setShouldRunUiThreadStartUpTasks(full).build(),
                    object : WebViewOutcomeReceiver<WebViewStartUpResult, WebViewStartupException> {
                        override fun onResult(value: WebViewStartUpResult) {
                            SafeLog.debug("Miowser/BrowserStartup",
                                "provider ${if (full) "complete" else "background"}: ${SystemClock.uptimeMillis() - started} ms")
                            result.complete(Unit)
                        }
                        override fun onError(error: WebViewStartupException) {
                            result.completeExceptionally(error)
                        }
                    })
            } catch (error: Exception) { result.completeExceptionally(error) }
        }
        receipt.await()
    }
}
