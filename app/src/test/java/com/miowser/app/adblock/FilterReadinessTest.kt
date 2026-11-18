package com.miowser.app.adblock

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.*
import org.junit.Test

class FilterReadinessTest {
    @Test fun switchingProtectionOffReleasesEntryWithoutCancellingSharedBuild() = runBlocking {
        val enabled = MutableStateFlow(true)
        val build = CompletableFuture<Boolean>()
        val readiness = FilterReadiness({ false }, { build }, { null })
        val entry = async(start = CoroutineStart.UNDISPATCHED) { readiness.awaitWhileEnabled(enabled) }
        yield()
        assertFalse(entry.isCompleted)
        enabled.value = false
        entry.await()
        assertFalse(build.isDone)
    }

    @Test fun globallyDisabledProtectionDoesNotStartABuild() = runBlocking {
        val readiness = FilterReadiness({ false }, { error("must not load") }, { null })
        readiness.awaitWhileEnabled(MutableStateFlow(false))
    }

    @Test fun passiveWarmAndConcurrentWaitersShareOneBuildAndCancellationIsLocal() = runBlocking {
        val usable = AtomicBoolean(false)
        val loads = AtomicInteger()
        val build = CompletableFuture<Boolean>()
        val readiness = FilterReadiness(usable::get, { loads.incrementAndGet(); build }, { null })
        val passive = readiness.start()
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { readiness.await() }
        val active = async(start = CoroutineStart.UNDISPATCHED) { readiness.await() }
        cancelled.cancelAndJoin()
        assertFalse(build.isCancelled)
        assertFalse(active.isCompleted)
        assertEquals(1, loads.get())
        usable.set(true)
        build.complete(true)
        active.await()
        passive.get(1, TimeUnit.SECONDS)
        readiness.await()
        assertEquals(1, loads.get())
    }

    @Test fun cancelledPassiveObserverCannotCancelExplicitEntry() = runBlocking {
        var usable = false
        val build = CompletableFuture<Boolean>()
        val readiness = FilterReadiness({ usable }, { build }, { null })
        readiness.start().cancel(true)
        val explicit = async(start = CoroutineStart.UNDISPATCHED) { readiness.await() }
        usable = true
        build.complete(true)
        explicit.await()
        assertFalse(build.isCancelled)
    }

    @Test fun obsoleteInputAttemptFollowsNewSavedInputs() = runBlocking {
        var usable = false
        var attempts = 0
        val obsolete = CompletableFuture<Boolean>()
        val current = CompletableFuture<Boolean>()
        val readiness = FilterReadiness({ usable }, { if (++attempts == 1) obsolete else current }, { null })
        val entry = async(start = CoroutineStart.UNDISPATCHED) { readiness.await() }
        obsolete.complete(false)
        assertEquals(2, attempts)
        assertFalse(entry.isCompleted)
        usable = true
        current.complete(true)
        entry.await()
    }

    @Test fun continuouslyChangingInputsFailBoundedlyAndCanRetry() = runBlocking {
        var usable = false
        var attempts = 0
        val readiness = FilterReadiness({ usable }, {
            attempts++
            CompletableFuture.completedFuture(usable)
        }, { null })
        val error = runCatching { readiness.await() }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("changing"))
        assertEquals(3, attempts)
        usable = true
        readiness.await()
        assertEquals(3, attempts)
    }

    @Test fun loadFailureExposesDiagnosticAndExplicitRetryBuildsAgain() = runBlocking {
        var usable = false
        var diagnostic: String? = null
        var attempts = 0
        val readiness = FilterReadiness({ usable }, {
            attempts++
            if (attempts == 1) diagnostic = "Bundled EasyList could not be loaded"
            else { diagnostic = null; usable = true }
            CompletableFuture.completedFuture(usable)
        }, { diagnostic })
        val error = runCatching { readiness.await() }.exceptionOrNull()
        assertEquals(diagnostic, error?.message)
        assertEquals(1, attempts)
        readiness.await()
        assertEquals(2, attempts)
    }

    @Test fun stalledBuildTimesOutWithoutCancellingSharedWork() = runBlocking {
        var usable = false
        val build = CompletableFuture<Boolean>()
        val readiness = FilterReadiness({ usable }, { build }, { null })
        val error = runCatching { readiness.await(timeoutMillis = 20) }.exceptionOrNull()
        assertTrue(error is IllegalStateException)
        assertTrue(error!!.message!!.contains("timed out"))
        assertFalse(build.isDone)
        usable = true
        build.complete(true)
        readiness.await()
    }

    @Test fun exceptionalBackgroundBuildDoesNotReportUsable() = runBlocking {
        val build = CompletableFuture<Boolean>()
        val readiness = FilterReadiness({ false }, { build }, { null })
        build.completeExceptionally(IllegalStateException("snapshot load failed"))
        assertEquals("snapshot load failed", runCatching { readiness.await() }.exceptionOrNull()?.message)
    }

    @Test fun usablePreviousGenerationDoesNotWaitForAnUpdate() = runBlocking {
        val readiness = FilterReadiness({ true }, { error("must not rebuild") }, { "update failed" })
        readiness.await()
    }

    @Test fun identicalInputsShareBuildEvenWhenANewerObserverArrives() {
        val queue = ArrayDeque<Runnable>()
        val builds = SharedFilterBuilds<String>(Executor { queue.add(it) })
        var count = 0
        val first = builds.submit("saved-1") { count++; true }
        val second = builds.submit("saved-1") { error("duplicate build") }
        val abandoned = builds.submit("saved-1") { error("duplicate build") }
        abandoned.cancel(true)
        assertEquals(1, queue.size)
        queue.removeFirst().run()
        assertTrue(first.get())
        assertTrue(second.get())
        assertEquals(1, count)
    }

    @Test fun changedInputsDoNotObsoleteAnUnrelatedBuildReceipt() {
        val queue = ArrayDeque<Runnable>()
        val builds = SharedFilterBuilds<String>(Executor { queue.add(it) })
        val old = builds.submit("old") { false }
        val current = builds.submit("new") { true }
        queue.removeFirst().run()
        assertFalse(old.get())
        queue.removeFirst().run()
        assertTrue(current.get())
    }

    @Test fun retryFromCompletionDoesNotReattachToFailedBuild() {
        val queue = ArrayDeque<Runnable>()
        val builds = SharedFilterBuilds<String>(Executor { queue.add(it) })
        var retried: CompletableFuture<Boolean>? = null
        builds.submit("same") { false }.whenComplete { _, _ ->
            retried = builds.submit("same") { true }
        }
        queue.removeFirst().run()
        assertEquals(1, queue.size)
        queue.removeFirst().run()
        assertTrue(retried!!.get())
    }

    @Test fun simultaneousRequestsFromDifferentThreadsShareTheRunningBuild() {
        val workers = Executors.newFixedThreadPool(4)
        val buildWorker = Executors.newSingleThreadExecutor()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val count = AtomicInteger()
        val builds = SharedFilterBuilds<String>(buildWorker)
        try {
            val first = builds.submit("same-inputs") {
                count.incrementAndGet()
                entered.countDown()
                check(release.await(5, TimeUnit.SECONDS))
                true
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val observers = (1..12).map {
                workers.submit<CompletableFuture<Boolean>> {
                    builds.submit("same-inputs") { error("duplicate build") }
                }
            }.map { it.get(5, TimeUnit.SECONDS) }
            release.countDown()
            assertTrue(first.get(5, TimeUnit.SECONDS))
            observers.forEach { assertTrue(it.get(5, TimeUnit.SECONDS)) }
            assertEquals(1, count.get())
        } finally {
            release.countDown()
            workers.shutdownNow()
            buildWorker.shutdownNow()
        }
    }
}
