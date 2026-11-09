package com.miowser.app.library

import java.util.concurrent.Executor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay

/** Two workers at most, with one slot reserved for visible artwork. */
internal class ArtworkQueue<T>(private val executor: Executor) {
    private class Request<T>(val key: String, val frame: Boolean, val load: () -> T?) {
        val result = CompletableDeferred<T?>()
        var subscribers = 0
        var visibleSubscribers = 0
        val visible get() = visibleSubscribers > 0
        var running = false
    }

    private val requests = linkedMapOf<String, Request<T>>()
    private var running = 0

    suspend fun load(key: String, visible: Boolean, frame: Boolean = false, action: () -> T?): T? {
        var request = subscribe(key, visible, frame, action)
        while (request == null && visible) {
            delay(32)
            request = subscribe(key, visible, frame, action)
        }
        request ?: return null
        return try {
            request.result.await()
        } finally {
            synchronized(this) {
                if (visible) request.visibleSubscribers--
                if (--request.subscribers == 0 && !request.running) requests.remove(key, request)
            }
        }
    }

    @Synchronized
    private fun subscribe(key: String, visible: Boolean, frame: Boolean, action: () -> T?): Request<T>? {
        val request = requests[key] ?: run {
            val limit = if (visible) 24 else 6
            if (requests.values.count { !it.running && it.visible == visible } >= limit) return null
            Request(key, frame, action).also { requests[key] = it }
        }
        request.subscribers++
        if (visible) request.visibleSubscribers++
        dispatch()
        return request
    }

    private fun dispatch() {
        while (running < 2) {
            val frameRunning = requests.values.any { it.running && it.frame }
            val ready = requests.values.asSequence().filter { !it.running && (!it.frame || !frameRunning) }
            val next = ready.firstOrNull { it.visible }
                ?: requests.values.takeIf { entries -> entries.none { it.running && !it.visible } }
                    ?.let { ready.firstOrNull() } ?: return
            next.running = true
            running++
            executor.execute {
                val value = runCatching(next.load).getOrNull()
                synchronized(this) {
                    requests.remove(next.key, next)
                    running--
                    dispatch()
                }
                next.result.complete(value)
            }
        }
    }
}
