package com.miowser.app.adblock

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class FilterInputRevisionTest {
    @Test fun changeAndChangeBackCannotInstallAnOlderBuildWithTheSameFingerprint() {
        val revisions = FilterInputRevision()
        var input = "A"
        val original = revisions.readStable { input }!!
        revisions.write { input = "B" }
        val intermediate = revisions.readStable { input }!!
        revisions.write { input = "A" }
        val current = revisions.readStable { input }!!
        assertEquals(original.second, current.second)
        assertNotEquals(original.first, current.first)
        var installed = "previous"
        assertNull(revisions.ifCurrent(original.first) { installed = "stale" })
        assertNull(revisions.ifCurrent(intermediate.first) { installed = "wrong" })
        assertEquals("previous", installed)
        assertEquals(true, revisions.ifCurrent(current.first) { installed = current.second; true })
        assertEquals("A", installed)
        assertEquals(current, revisions.readStable { input })
    }

    @Test fun mutationDuringAReadAndFailedWritesInvalidateTheReadWithoutLosingThePreviousInstall() {
        val revisions = FilterInputRevision()
        val prior = revisions.readStable { "installed" }!!
        assertNull(revisions.readStable { revisions.write { }; "mixed inputs" })
        runCatching { revisions.write { error("disk failure") } }
        assertNull(revisions.ifCurrent(prior.first) { "stale" })
        assertNotNull(revisions.readStable { "retry inputs" })
    }

    @Test fun overlappingWritersCannotExposeAStableSnapshotUntilBothFinish() {
        val revisions = FilterInputRevision()
        val pool = Executors.newFixedThreadPool(2)
        val started = CountDownLatch(2)
        val releaseFirst = CountDownLatch(1)
        val releaseSecond = CountDownLatch(1)
        val stamp = revisions.readStable { "before" }!!.first
        try {
            val first = pool.submit { revisions.write { started.countDown(); check(releaseFirst.await(2, TimeUnit.SECONDS)) } }
            val second = pool.submit { revisions.write { started.countDown(); check(releaseSecond.await(2, TimeUnit.SECONDS)) } }
            assertTrue(started.await(2, TimeUnit.SECONDS))
            assertNull(revisions.readStable { error("must not read partially replaced inputs") })
            assertNull(revisions.ifCurrent(stamp) { error("must not publish") })
            releaseFirst.countDown(); first.get(2, TimeUnit.SECONDS)
            assertNull(revisions.readStable { error("second writer still active") })
            releaseSecond.countDown(); second.get(2, TimeUnit.SECONDS)
            assertNotNull(revisions.readStable { "fully committed" })
        } finally {
            releaseFirst.countDown(); releaseSecond.countDown(); pool.shutdownNow()
        }
    }
}
