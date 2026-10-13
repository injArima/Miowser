package com.miowser.app.network

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean

class ConnectionBudgetTest {
    @Test fun backgroundSaturationStillAllowsTwoPlaybackConnections() {
        val leases = (0 until ConnectionBudget.MAX_BACKGROUND_CONNECTIONS).map {
            ConnectionBudget.acquire(ConnectionBudget.Priority.TRANSFER)
        }.toMutableList()
        val worker = Executors.newSingleThreadExecutor()
        try {
            leases += worker.submit<AutoCloseable> { ConnectionBudget.acquire(ConnectionBudget.Priority.PLAYBACK) }
                .get(2, TimeUnit.SECONDS)
            leases += worker.submit<AutoCloseable> { ConnectionBudget.acquire(ConnectionBudget.Priority.PLAYBACK) }
                .get(2, TimeUnit.SECONDS)
        } finally { leases.forEach(AutoCloseable::close); worker.shutdownNow() }
    }

    @Test fun canceledQueuedConnectionLeavesNoPermitAndCloseIsIdempotent() {
        val held = (0 until ConnectionBudget.MAX_BACKGROUND_CONNECTIONS).map {
            ConnectionBudget.acquire(ConnectionBudget.Priority.DISCOVERY)
        }
        val stopped = AtomicBoolean()
        val queued = CountDownLatch(1)
        val worker = Executors.newSingleThreadExecutor()
        try {
            val waiting = worker.submit<Throwable?> {
                runCatching { ConnectionBudget.acquire(ConnectionBudget.Priority.PREFETCH) {
                    queued.countDown()
                    if (stopped.get()) throw CancellationException()
                } }.exceptionOrNull()
            }
            assertTrue(queued.await(2, TimeUnit.SECONDS))
            stopped.set(true)
            assertTrue(waiting.get(2, TimeUnit.SECONDS) is CancellationException)
            held.forEach { it.close(); it.close() }
            ConnectionBudget.acquire(ConnectionBudget.Priority.TRANSFER).close()
        } finally { held.forEach(AutoCloseable::close); worker.shutdownNow() }
    }
}
