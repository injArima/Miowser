package com.miowser.app.browser.media

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Test

class PlaybackWakeLeaseTest {
    @Test fun playingRenewsABoundedLeaseAndPauseReleasesItOnce() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val timeouts = mutableListOf<Long>()
        val delays = mutableListOf<Long>()
        val gates = ArrayDeque<CompletableDeferred<Unit>>()
        var releases = 0
        val lease = PlaybackWakeLease(scope, { timeouts += it }, { releases++ }, {
            delays += it
            val gate = CompletableDeferred<Unit>(); gates.addLast(gate); gate.await()
        })
        try {
            lease.update(false); assertTrue(timeouts.isEmpty())
            lease.update(true); lease.update(true)
            assertEquals(1, timeouts.size)
            assertTrue(timeouts.single() in 1..60_000L)
            assertTrue(delays.single() < timeouts.single())
            gates.removeFirst().complete(Unit); assertEquals(2, timeouts.size)
            lease.update(false); lease.close(); assertEquals(1, releases)
            lease.update(true); assertEquals(3, timeouts.size)
            scope.cancel(); assertEquals(2, releases)
        } finally { scope.cancel() }
    }

    @Test fun failedPowerSupportDoesNotRetryOnEveryPlaybackSample() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        var attempts = 0
        var releases = 0
        val lease = PlaybackWakeLease(scope, { attempts++; throw SecurityException("provider") }, { releases++ })
        try {
            repeat(20) { lease.update(true) }
            assertEquals(1, attempts); assertEquals(1, releases)
            lease.update(false); lease.update(true)
            assertEquals(2, attempts); assertEquals(2, releases)
        } finally { scope.cancel() }
    }
}
