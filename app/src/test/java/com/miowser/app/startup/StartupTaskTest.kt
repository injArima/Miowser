package com.miowser.app.startup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class StartupTaskTest {
    @Test fun concurrentReadersShareOneSuccessfulLoad() = runBlocking {
        val task = StartupTask<String>()
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        var loads = 0
        val first = async { task.get { loads++; entered.complete(Unit); release.await(); "saved" } }
        entered.await()
        val second = async { task.get { loads++; "wrong" } }
        yield()
        assertFalse(second.isCompleted)
        release.complete(Unit)
        assertEquals("saved", first.await())
        assertEquals("saved", second.await())
        assertEquals(1, loads)
    }

    @Test fun readFailureDoesNotBecomeAnEmptySuccessfulSnapshot() = runBlocking {
        val task = StartupTask<List<String>>()
        assertTrue(runCatching { task.get { throw java.io.IOException("read failed") } }.isFailure)
        assertEquals(listOf("saved entry"), task.get { listOf("saved entry") })
        assertEquals(listOf("saved entry"), task.get { emptyList() })
    }

    @Test fun cancelledLoadCanBeRetried() = runBlocking {
        val task = StartupTask<String>()
        val entered = CompletableDeferred<Unit>()
        val job = launch { task.get { entered.complete(Unit); awaitCancellation() } }
        entered.await()
        job.cancelAndJoin()
        assertEquals("restored", task.get { "restored" })
    }

    @Test fun successfulEmptySnapshotIsCached() = runBlocking {
        val task = StartupTask<List<String>>()
        assertTrue(task.get { emptyList() }.isEmpty())
        assertTrue(task.get { error("must not reload after activity recreation") }.isEmpty())
    }
}
