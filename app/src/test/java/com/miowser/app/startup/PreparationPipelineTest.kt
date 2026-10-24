package com.miowser.app.startup

import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class PreparationPipelineTest {
    @Test fun warmAndExplicitEntryShareStagesInOrder() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val events = mutableListOf<String>()
        val pipeline = PreparationPipeline(listOf(
            { events.add("provider"); entered.complete(Unit); release.await() },
            { events.add("saved data") },
            { events.add("protection") }
        ))
        val warm = async { pipeline.prepare() }
        entered.await()
        val explicit = async { pipeline.prepare() }
        yield()
        assertFalse(explicit.isCompleted)
        assertFalse(pipeline.ready)
        release.complete(Unit)
        warm.await(); explicit.await()
        assertEquals(listOf("provider", "saved data", "protection"), events)
        assertTrue(pipeline.ready)
    }

    @Test fun interruptedWarmupRetainsCompletedStagesForEntry() = runBlocking {
        val entered = CompletableDeferred<Unit>()
        var firstLoads = 0
        var secondLoads = 0
        val pipeline = PreparationPipeline(listOf(
            { firstLoads += 1 },
            { secondLoads++; if (secondLoads == 1) { entered.complete(Unit); awaitCancellation() } }
        ))
        val warm = launch { pipeline.prepare() }
        entered.await()
        warm.cancelAndJoin()
        assertFalse(pipeline.ready)
        pipeline.prepare()
        assertEquals(1, firstLoads)
        assertEquals(2, secondLoads)
        assertTrue(pipeline.ready)
    }

    @Test fun failedStageRetriesWithoutReloadingEarlierData() = runBlocking {
        var firstLoads = 0
        var protectionLoads = 0
        val pipeline = PreparationPipeline(listOf(
            { firstLoads += 1 },
            { protectionLoads++; check(protectionLoads > 1) { "unavailable cache" } }
        ))
        assertTrue(runCatching { pipeline.prepare() }.isFailure)
        assertFalse(pipeline.ready)
        pipeline.prepare()
        assertEquals(1, firstLoads)
        assertEquals(2, protectionLoads)
        assertTrue(pipeline.ready)
    }

    @Test fun checkpointStopsNewStagesUntilLibraryIsEligibleAgain() = runBlocking {
        val eligible = CompletableDeferred<Unit>()
        val checking = CompletableDeferred<Unit>()
        var loads = 0
        val pipeline = PreparationPipeline(listOf({ loads += 1 }))
        val warm = launch { pipeline.prepare { checking.complete(Unit); eligible.await() } }
        checking.await()
        assertEquals(0, loads)
        assertFalse(pipeline.ready)
        warm.cancelAndJoin()
        pipeline.prepare()
        assertEquals(1, loads)
        assertTrue(pipeline.ready)
    }

    @Test fun readyPipelineDoesNotWaitForAnotherIdleWindow() = runBlocking {
        var loads = 0
        val pipeline = PreparationPipeline(listOf({ loads += 1 }))
        pipeline.prepare()
        pipeline.prepare { error("a cache hit must not await idle") }
        assertEquals(1, loads)
        assertTrue(pipeline.ready)
    }
}
