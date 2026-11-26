package com.miowser.app.browser.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** A short, renewed CPU lease belongs only to active playback; cancellation always releases it. */
internal class PlaybackWakeLease(
    private val scope: CoroutineScope,
    private val acquire: (Long) -> Unit,
    private val release: () -> Unit,
    private val renewDelay: suspend (Long) -> Unit = { delay(it) }
) : AutoCloseable {
    private var job: Job? = null
    private var failed = false

    fun update(playing: Boolean) {
        if (!playing) { failed = false; job?.cancel(); return }
        if (failed || job?.isActive == true) return
        val previous = job
        job = scope.launch {
            previous?.cancelAndJoin() // Release an outgoing lease before reusing its platform lock.
            try {
                while (isActive) {
                    acquire(60_000L)
                    renewDelay(45_000L)
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: RuntimeException) { failed = true } // Optional power support must not crash playback.
            finally { runCatching(release) }
        }
    }

    override fun close() = update(false)
}
