package com.miowser.app.persistence

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger

class RestoreWriteFenceTest {
    @Test fun restoreWaitsForCurrentWriterAndRejectsLaterWriters() {
        val executor = Executors.newFixedThreadPool(2)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val writes = AtomicInteger()
        try {
            RestoreWriteFence.thawAfterPreflightFailure()
            val writer = executor.submit { RestoreWriteFence.write {
                entered.countDown(); release.await(); writes.incrementAndGet()
            } }
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            val restore = executor.submit { RestoreWriteFence.freeze() }
            assertFalse(restore.isDone)
            release.countDown()
            writer.get(2, TimeUnit.SECONDS); restore.get(2, TimeUnit.SECONDS)
            assertNull(RestoreWriteFence.write { writes.incrementAndGet() })
            assertEquals(1, writes.get())
        } finally { release.countDown(); RestoreWriteFence.thawAfterPreflightFailure(); executor.shutdownNow() }
    }
}
