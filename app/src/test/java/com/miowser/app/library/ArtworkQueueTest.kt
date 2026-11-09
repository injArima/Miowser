package com.miowser.app.library

import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkQueueTest {
    private class ManualExecutor : Executor {
        val pending = ArrayDeque<Runnable>()
        override fun execute(command: Runnable) { pending.add(command) }
        fun runNext() = pending.removeFirst().run()
    }

    @Test fun visibleSubscriberPromotesAndSharesQueuedLookAhead() = runBlocking {
        val executor = ManualExecutor()
        val queue = ArtworkQueue<String>(executor)
        val busy = async(start = CoroutineStart.UNDISPATCHED) { queue.load("busy", false) { "busy" } }
        val older = async(start = CoroutineStart.UNDISPATCHED) { queue.load("older", false) { "older" } }
        val lookAhead = async(start = CoroutineStart.UNDISPATCHED) { queue.load("same", false) { "shared" } }
        val visible = async(start = CoroutineStart.UNDISPATCHED) { queue.load("same", true) { error("duplicate") } }

        assertEquals(2, executor.pending.size)
        executor.pending.removeLast().run()
        assertEquals("shared", visible.await())
        assertEquals("shared", lookAhead.await())
        executor.runNext()
        executor.runNext()
        assertEquals("busy", busy.await())
        assertEquals("older", older.await())
    }

    @Test fun cancelledFrameIsRemovedAndSavedArtCanUseTheOtherSlot() = runBlocking {
        val executor = ManualExecutor()
        val queue = ArtworkQueue<String>(executor)
        val first = async(start = CoroutineStart.UNDISPATCHED) { queue.load("first", true, frame = true) { "first" } }
        val cancelled = async(start = CoroutineStart.UNDISPATCHED) {
            queue.load("cancelled", true, frame = true) { error("left viewport") }
        }
        val other = async(start = CoroutineStart.UNDISPATCHED) { queue.load("other", true) { "other" } }
        assertEquals(2, executor.pending.size)
        cancelled.cancelAndJoin()
        executor.runNext()
        executor.runNext()
        assertEquals("first", first.await())
        assertEquals("other", other.await())
        assertTrue(executor.pending.isEmpty())
    }

    @Test fun lookAheadBacklogIsBoundedAndCancellationReleasesIt() = runBlocking {
        val executor = ManualExecutor()
        val queue = ArtworkQueue<Int>(executor)
        val first = async(start = CoroutineStart.UNDISPATCHED) { queue.load("running", false) { 1 } }
        val queued = List(6) { index ->
            async(start = CoroutineStart.UNDISPATCHED) { queue.load("waiting:$index", false) { index } }
        }
        assertNull(queue.load("overflow", false) { 99 })
        assertEquals(1, executor.pending.size)
        queued.forEach { it.cancelAndJoin() }
        executor.runNext()
        assertEquals(1, first.await())
        assertTrue(executor.pending.isEmpty())
    }
}
