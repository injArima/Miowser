package com.miowser.app.persistence

import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class OrderedWriterTest {
    private class ManualExecutor : Executor {
        val jobs = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { jobs.add(command) }
        fun next() { jobs.removeFirst().run() }
        fun drain() { while (jobs.isNotEmpty()) next() }
    }

    @Test fun slowOldWriteCannotOverwriteNewerEdit() {
        val pool = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var disk = ""
        try {
            val writer = OrderedWriter(pool)
            val first = writer.submit("library") {
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                disk = "old"
                true
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val second = writer.submit("library") { disk = "new"; true }
            assertFalse(first.isDone)
            assertFalse(second.isDone)
            release.countDown()
            assertTrue(second.get(5, TimeUnit.SECONDS))
            assertEquals("new", disk)
        } finally { release.countDown(); pool.shutdownNow() }
    }

    @Test fun receiptAndBarrierWaitForCommit() {
        val executor = ManualExecutor()
        val writer = OrderedWriter(executor)
        val receipt = writer.submit("history") { true }
        val barrier = writer.barrier()
        assertFalse(receipt.isDone)
        assertFalse(barrier.isDone)
        executor.next()
        assertTrue(receipt.get())
        assertFalse(barrier.isDone)
        executor.next()
        assertTrue(barrier.get())
    }

    @Test fun falseCommitRemainsVisibleAndCanBeRetried() {
        val executor = ManualExecutor()
        var state = WriteState()
        val writer = OrderedWriter(executor) { state = it }
        var attempts = 0
        val receipt = writer.submit("history") { ++attempts > 1 }
        executor.drain()
        assertFalse(receipt.get())
        assertEquals(setOf("history"), state.failedKeys)
        val failedBarrier = writer.barrier()
        executor.drain()
        assertFalse(failedBarrier.get())
        writer.retryFailed()
        writer.retryFailed()
        executor.drain()
        assertEquals(2, attempts)
        assertEquals(WriteState(), state)
        val saved = writer.barrier()
        executor.drain()
        assertTrue(saved.get())
    }

    @Test fun retryNeverRestoresAnOlderFailedSnapshot() {
        val executor = ManualExecutor()
        val writer = OrderedWriter(executor)
        var disk = ""
        writer.submit("library") { false }
        executor.drain()
        writer.submit("library") { disk = "new"; true }
        writer.retryFailed()
        executor.drain()
        assertEquals("new", disk)
        assertTrue(executor.jobs.isEmpty())
    }

    @Test fun failureBeforeBarrierCannotBeHiddenByALaterSubmission() {
        val executor = ManualExecutor()
        val writer = OrderedWriter(executor)
        writer.submit("tabs") { false }
        val boundary = writer.barrier()
        writer.submit("tabs") { true }
        executor.drain()
        assertFalse(boundary.get())
        val later = writer.barrier()
        executor.drain()
        assertTrue(later.get())
    }

    @Test fun exceptionDoesNotStopOtherStoresOrDiscardFailure() {
        val executor = ManualExecutor()
        var state = WriteState()
        val writer = OrderedWriter(executor) { state = it }
        val failed = writer.submit("library") { error("Disk unavailable") }
        val success = writer.submit("settings") { true }
        executor.drain()
        assertFalse(failed.get())
        assertTrue(success.get())
        assertEquals(WriteState(0, setOf("library")), state)
    }

    @Test fun acknowledgedClearSurvivesReopening() {
        val directory = Files.createTempDirectory("miowser-persistence-test")
        val file = directory.resolve("history")
        try {
            val executor = ManualExecutor()
            val writer = OrderedWriter(executor)
            writer.submit("history") { Files.writeString(file, "old records"); true }
            val clear = writer.submit("history") { Files.writeString(file, ""); true }
            executor.drain()
            assertTrue(clear.get())
            // A newly opened reader sees the committed representation, not an in-memory cache.
            assertEquals("", Files.readString(file))
        } finally { Files.deleteIfExists(file); Files.deleteIfExists(directory) }
    }

    @Test fun acknowledgementWaitsThroughFailureAndRetry() {
        val executor = ManualExecutor()
        val writer = OrderedWriter(executor)
        var canWrite = false
        writer.submit("history") { canWrite }
        val acknowledgement = writer.awaitCommitted()
        executor.drain()
        assertFalse(acknowledgement.isDone)
        canWrite = true
        writer.retryFailed()
        executor.drain()
        assertTrue(acknowledgement.isDone)
    }
}
