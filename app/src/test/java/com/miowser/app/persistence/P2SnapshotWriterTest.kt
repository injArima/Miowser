package com.miowser.app.persistence

import java.util.concurrent.Executor
import org.junit.Assert.*
import org.junit.Test

class P2SnapshotWriterTest {
    private class Manual : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(task: Runnable) { jobs.addLast(task) }
        fun next() { jobs.removeFirst().run() }
        fun drain() { while (jobs.isNotEmpty()) next() }
    }
    @Test fun queuedSnapshotsAcknowledgeOnlyDurableReplacement() {
        val executor = Manual(); val writer = OrderedWriter(executor)
        var saved = -1
        val receipts = (0 until 10_000).map { value -> writer.submitSnapshot("settings") { saved = value; true } }
        assertTrue(receipts.none { it.isDone })
        assertEquals(1, writer.metrics().peakPending)
        executor.drain()
        assertEquals(9999, saved)
        assertTrue(receipts.all { it.get() })
        assertEquals(1L, writer.metrics().executed)
        assertEquals(9999L, writer.metrics().coalesced)
    }
    @Test fun barriersAndCommandsSealCoalescingSegments() {
        val executor = Manual(); val writer = OrderedWriter(executor); val saved = mutableListOf<Int>()
        writer.submitSnapshot("a") { saved += 1; true }
        val boundary = writer.barrier()
        writer.submitSnapshot("a") { saved += 2; true }
        writer.submit("delete") { saved += 3; true }
        writer.submitSnapshot("a") { saved += 4; true }
        executor.next(); assertFalse(boundary.isDone)
        executor.next(); assertTrue(boundary.get()); assertEquals(listOf(1), saved)
        executor.drain(); assertEquals(listOf(1, 2, 3, 4), saved)
    }
    @Test fun failedReplacementRemainsRetryableAndCannotRollBackANewerSnapshot() {
        val executor = Manual(); val writer = OrderedWriter(executor)
        var saved = 0
        val old = writer.submitSnapshot("a") { saved = 1; true }
        val failed = writer.submitSnapshot("a") { false }
        val acknowledgement = writer.awaitCommitted()
        executor.drain(); assertFalse(old.get()); assertFalse(failed.get()); assertFalse(acknowledgement.isDone)
        writer.submitSnapshot("a") { saved = 2; true }
        writer.retryFailed(); executor.drain()
        assertTrue(acknowledgement.isDone); assertEquals(2, saved)
    }
    @Test fun runningSnapshotsAreNotReplaced() {
        val executor = Manual(); val writer = OrderedWriter(executor); val saved = mutableListOf<Int>()
        writer.submitSnapshot("a") {
            saved += 1
            writer.submitSnapshot("a") { saved += 2; true }
            writer.submitSnapshot("a") { saved += 3; true }
            true
        }
        executor.drain(); assertEquals(listOf(1, 3), saved)
    }
    @Test fun anAcknowledgedBoundaryIsNotCoalescedWithLaterWork() {
        val executor = Manual(); val writer = OrderedWriter(executor); val saved = mutableListOf<Int>()
        writer.submitSnapshot("a") { saved += 1; true }
        val ack = writer.awaitCommitted()
        writer.submitSnapshot("a") { saved += 2; true }
        executor.next(); assertTrue(ack.isDone); assertEquals(listOf(1), saved)
        executor.drain(); assertEquals(listOf(1, 2), saved)
    }
    @Test fun interleavedStoresKeepTheirDependencyOrderAndDoNotFloodStateObservers() {
        val executor = Manual(); val states = mutableListOf<WriteState>()
        val writer = OrderedWriter(executor, states::add); val saved = mutableListOf<Int>()
        repeat(1000) { writer.submitSnapshot("folders") { saved += 1; true } }
        assertEquals(1, states.size)
        writer.submitSnapshot("library") { saved += 2; true }
        writer.submitSnapshot("folders") { saved += 3; true }
        executor.drain(); assertEquals(listOf(1, 2, 3), saved)
    }
}
