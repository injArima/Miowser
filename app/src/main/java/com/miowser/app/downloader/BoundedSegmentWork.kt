package com.miowser.app.downloader

import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Only workerCount tasks enter the executor, regardless of the number of segments. */
internal fun <T> boundedSegmentWork(items: List<T>, executor: Executor, workerCount: Int = 4,
    checkActive: () -> Unit, work: (T) -> Unit) {
    if (items.isEmpty()) return
    val cursor = AtomicInteger()
    val failure = AtomicReference<Throwable?>(null)
    val latch = CountDownLatch(minOf(workerCount, items.size))
    repeat(latch.count.toInt()) {
        executor.execute {
            try {
                while (failure.get() == null) {
                    checkActive()
                    val index = cursor.getAndIncrement()
                    if (index >= items.size) break
                    work(items[index])
                }
            } catch (error: Throwable) { failure.compareAndSet(null, error) }
            finally { latch.countDown() }
        }
    }
    // Ownership cannot return to Queue until every segment worker has closed its files/sockets.
    var interrupted = false
    while (true) {
        try { latch.await(); break } catch (_: InterruptedException) { interrupted = true }
    }
    if (interrupted) Thread.currentThread().interrupt()
    failure.get()?.let { throw it }
}
