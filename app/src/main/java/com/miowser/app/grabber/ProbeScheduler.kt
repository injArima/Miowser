package com.miowser.app.grabber

import java.net.HttpURLConnection
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class MediaProbeHandle internal constructor(val detailed: Boolean) {
    internal val stopped = AtomicBoolean(false)
    internal var future: FutureTask<Unit>? = null
    private val connections = mutableListOf<HttpURLConnection>()
    internal fun track(connection: HttpURLConnection) = synchronized(connections) {
        checkActive()
        connections.add(connection)
    }
    internal fun untrack(connection: HttpURLConnection) = synchronized(connections) { connections.remove(connection) }
    internal fun checkActive() {
        if (stopped.get() || Thread.currentThread().isInterrupted) throw CancellationException()
    }
    fun cancel() {
        stopped.set(true)
        future?.let { it.cancel(true); ProbeScheduler.remove(it) }
        val retired = synchronized(connections) { connections.toList().also { connections.clear() } }
        if (retired.isNotEmpty()) ProbeScheduler.disconnect(retired)
    }
}

/** Two active workers, 48 queued tasks; user-requested checks go ahead of passive discovery. */
internal object ProbeScheduler {
    private val queue = LinkedBlockingDeque<Runnable>(48)
    private val workers = ThreadPoolExecutor(2, 2, 30, TimeUnit.SECONDS, queue, ThreadFactory { work ->
        Thread({ android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND); work.run() }, "Miowser-media-probe")
    }).apply { prestartAllCoreThreads() }
    private val cleanup = Executors.newSingleThreadExecutor { Thread(it, "Miowser-probe-cleanup") }
    val current = ThreadLocal<MediaProbeHandle>()
    fun submit(detailed: Boolean, urgent: Boolean, work: () -> Unit, rejected: () -> Unit): MediaProbeHandle {
        val handle = MediaProbeHandle(detailed)
        val future = FutureTask<Unit> {
            current.set(handle)
            try { handle.checkActive(); work() } finally { current.remove() }
        }
        handle.future = future
        var accepted = if (urgent) queue.offerFirst(future) else queue.offerLast(future)
        if (!accepted && urgent) {
            (queue.pollLast() as? FutureTask<*>)?.cancel(true)
            accepted = queue.offerFirst(future)
        }
        if (!accepted) { handle.stopped.set(true); rejected() }
        return handle
    }
    fun remove(task: Runnable) { queue.remove(task) }
    fun disconnect(connections: List<HttpURLConnection>) {
        cleanup.execute { connections.forEach { runCatching { it.disconnect() } } }
    }
}
