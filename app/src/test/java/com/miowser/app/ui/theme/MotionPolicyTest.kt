package com.miowser.app.ui.theme

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class MotionPolicyTest {
    private class SystemScale(override val scaleFactor: Float) : MotionDurationScale

    private class AdvancingClock : MonotonicFrameClock {
        var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            return onFrame(frames++ * 16_000_000L)
        }
    }

    @Test fun strictestPreferenceWinsWithoutApplyingSystemMultiplierTwice() {
        for (scale in listOf(0f, 0.5f, 1f, 2f)) {
            for (requested in listOf(false, true)) {
                val policy = MotionPolicy.resolve(requested, scale)
                assertEquals(requested || scale == 0f, policy.reduced)
                assertEquals(if (policy.reduced) 0 else MotionTokens.controlsEnter.millis,
                    policy.spec<Float>(MotionTokens.controlsEnter).durationMillis)
            }
        }
    }

    @Test fun disabledAndReducedControlsKeepTheirRestingScale() {
        assertEquals(0.97f, MotionPolicy().pressedScale(pressed = true), 0.0001f)
        assertEquals(1f, MotionPolicy().pressedScale(pressed = false), 0f)
        assertEquals(1f, MotionPolicy().pressedScale(pressed = true, enabled = false), 0f)
        assertEquals(1f, MotionPolicy(reduced = true).pressedScale(pressed = true), 0f)
    }

    @Test fun sharedCurvesAreMonotonicAndDoNotOvershoot() {
        for (curve in listOf(MotionTokens.enterEasing, MotionTokens.exitEasing, MotionTokens.selectionEasing)) {
            var previous = 0f
            repeat(101) { step ->
                val value = curve.transform(step / 100f)
                assertTrue("Curve reversed at $step", value >= previous - 0.0001f)
                assertTrue("Curve overshot at $step", value in 0f..1f)
                previous = value
            }
            assertEquals(0f, curve.transform(0f), 0f)
            assertEquals(1f, curve.transform(1f), 0f)
        }
    }

    @Test fun reducedGestureCompletesWithoutRequestingAnAnimationFrame() = runBlocking {
        val updates = mutableListOf<Float>()
        settleMotionFloat(42f, 0f, MotionTokens.tabReturn, MotionPolicy(reduced = true)) { updates += it }
        assertEquals(listOf(0f), updates)
    }

    @Test fun systemZeroCompletesEvenWhenCapturedPolicyWasNotReduced() = runBlocking {
        val updates = mutableListOf<Float>()
        withContext(SystemScale(0f)) {
            settleMotionFloat(10f, 300f, MotionTokens.tabDismiss, MotionPolicy()) { updates += it }
        }
        assertEquals(listOf(300f), updates)
    }

    @Test fun normalSettlementReachesTargetWithoutOvershoot() = runBlocking {
        val clock = AdvancingClock()
        val updates = mutableListOf<Float>()
        withTimeout(2_000) {
            withContext(clock + SystemScale(1f)) {
                settleMotionFloat(100f, 0f, MotionTokens.tabReturn, MotionPolicy()) { updates += it }
            }
        }
        assertTrue(updates.size > 2)
        assertTrue(updates.all { it in 0f..100f })
        assertTrue(updates.zipWithNext().all { (before, after) -> after <= before })
        assertEquals(0f, updates.last(), 0f)
    }

    @Test fun composeAppliesPositiveSystemScaleOnce() = runBlocking {
        suspend fun frameCount(scale: Float): Int {
            val clock = AdvancingClock()
            withTimeout(2_000) {
                withContext(clock + SystemScale(scale)) {
                    settleMotionFloat(0f, 100f, MotionTokens.tabReturn, MotionPolicy.resolve(false, scale)) {}
                }
            }
            return clock.frames
        }
        val normal = frameCount(1f)
        val doubled = frameCount(2f)
        assertTrue(doubled > normal)
        assertTrue("Positive scale was applied twice", doubled <= normal * 2 + 1)
    }

    @Test fun cancellingSettlementDoesNotRunTheCloseCallback() = runBlocking {
        val requests = Channel<Unit>(Channel.UNLIMITED)
        val frames = Channel<Long>()
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                requests.send(Unit)
                return onFrame(frames.receive())
            }
        }
        var closed = false
        val updates = mutableListOf<Float>()
        val job = launch(clock + SystemScale(1f)) {
            settleMotionFloat(0f, 300f, MotionTokens.tabDismiss, MotionPolicy()) { updates += it }
            closed = true
        }
        try {
            withTimeout(2_000) {
                requests.receive(); frames.send(0L)
                requests.receive(); frames.send(16_000_000L)
                requests.receive() // Suspend in the next frame request before cancelling.
            }
            job.cancelAndJoin()
            val stoppedUpdates = updates.toList()
            repeat(5) { yield() }
            assertEquals(stoppedUpdates, updates)
            assertFalse(closed)
            assertTrue(updates.last() < 300f)
        } finally {
            job.cancelAndJoin()
            requests.close()
            frames.close()
        }
    }

    @Test fun zeroDurationStillRespectsCancellationBeforeCompletionCallback() = runBlocking {
        var closed = false
        val job = launch {
            settleMotionFloat(0f, 300f, MotionTokens.tabDismiss, MotionPolicy(reduced = true)) {
                coroutineContext[kotlinx.coroutines.Job]!!.cancel()
            }
            closed = true
        }
        job.join()
        assertFalse(closed)
    }
}
