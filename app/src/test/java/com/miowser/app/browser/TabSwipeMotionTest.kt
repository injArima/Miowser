package com.miowser.app.browser

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.MotionDurationScale
import com.miowser.app.ui.theme.MotionPolicy
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class TabSwipeMotionTest {
    private class Clock : MonotonicFrameClock {
        var frames = 0
        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            yield()
            return onFrame(frames++ * 16_000_000L)
        }
    }
    private class Scale(override val scaleFactor: Float) : MotionDurationScale

    @Test fun grabbingAgainStartsAtVisibleOffsetAndRejectsOldUpdates() {
        val swipe = TabSwipeMotion()
        val first = swipe.takeControl()
        swipe.drag(first, 95f, 200f)
        val second = swipe.takeControl()
        assertEquals(95f, swipe.offset, 0f)
        swipe.drag(first, -200f, 200f)
        assertEquals(95f, swipe.offset, 0f)
        swipe.drag(second, swipe.offset + 5f, 200f)
        assertEquals(100f, swipe.offset, 0f)
    }

    @Test fun reducedAndSystemZeroCloseExactlyOnceWithoutFrames() = runBlocking {
        for (systemZero in listOf(false, true)) {
            val swipe = TabSwipeMotion()
            val token = swipe.takeControl()
            swipe.drag(token, 150f, 200f)
            var closed = 0
            withContext(Scale(if (systemZero) 0f else 1f)) {
                val policy = MotionPolicy(reduced = !systemZero)
                swipe.settle(token, 200f, 200f, 2_000f, policy) { closed++ }
                swipe.settle(token, 200f, 200f, 2_000f, policy) { closed++ }
                swipe.closeImmediately { closed++ }
            }
            assertEquals(1, closed)
            assertEquals(200f, swipe.offset, 0f)
        }
    }

    @Test fun staleQueuedSettlementCannotMoveOrCloseNewGesture() = runBlocking {
        val swipe = TabSwipeMotion()
        val stale = swipe.takeControl()
        swipe.drag(stale, 150f, 200f)
        val current = swipe.takeControl()
        swipe.drag(current, 80f, 200f)
        var closed = false
        swipe.settle(stale, 200f, 200f, 0f, MotionPolicy(true)) { closed = true }
        assertEquals(80f, swipe.offset, 0f)
        assertFalse(closed)
    }

    @Test fun springUsesReleaseVelocityAndStaysWithinCardBounds() = runBlocking {
        suspend fun samples(velocity: Float): List<Float> {
            val swipe = TabSwipeMotion()
            val token = swipe.takeControl()
            swipe.drag(token, 140f, 200f)
            val clock = Clock()
            val values = mutableListOf<Float>()
            val samplingClock = object : MonotonicFrameClock {
                override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R =
                    clock.withFrameNanos { time -> onFrame(time).also { values += swipe.offset } }
            }
            var closed = 0
            withTimeout(3_000) { withContext(samplingClock + Scale(1f)) {
                swipe.settle(token, 200f, 200f, velocity, MotionPolicy()) { closed++ }
            } }
            assertEquals(1, closed)
            assertEquals(200f, swipe.offset, 0.01f)
            assertTrue(values.all { it in -200f..200f })
            return values
        }
        val slow = samples(0f)
        val fast = samples(1_000f)
        assertTrue(fast[1] > slow[1])
    }

    @Test fun cancelledSpringCannotCloseAndNewGestureContinuesFromItsVisiblePosition() = runBlocking {
        val requests = Channel<Unit>(Channel.UNLIMITED)
        val frames = Channel<Long>()
        val clock = object : MonotonicFrameClock {
            override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                requests.send(Unit)
                return onFrame(frames.receive())
            }
        }
        val swipe = TabSwipeMotion()
        val token = swipe.takeControl()
        swipe.drag(token, 145f, 200f)
        var closed = false
        val job = launch(clock + Scale(1f)) {
            swipe.settle(token, 200f, 200f, 300f, MotionPolicy()) { closed = true }
        }
        try {
            withTimeout(3_000) {
                requests.receive(); frames.send(0L)
                requests.receive(); frames.send(16_000_000L)
                requests.receive()
            }
            val visible = swipe.offset
            val next = swipe.takeControl()
            job.cancelAndJoin()
            assertFalse(closed)
            assertEquals(visible, swipe.offset, 0f)
            swipe.drag(next, visible - 20f, 200f)
            assertEquals(visible - 20f, swipe.offset, 0f)
            swipe.settle(next, 0f, 200f, 0f, MotionPolicy(true))
            assertEquals(0f, swipe.offset, 0f)
        } finally { job.cancelAndJoin(); requests.close(); frames.close() }
    }

    @Test fun cancelledPointerReturnNeverInvokesClose() = runBlocking {
        val swipe = TabSwipeMotion()
        val token = swipe.takeControl()
        swipe.drag(token, -180f, 200f)
        withTimeout(3_000) { withContext(Clock() + Scale(1f)) {
            swipe.settle(token, 0f, 200f, 0f, MotionPolicy())
        } }
        assertEquals(0f, swipe.offset, 0.01f)
        var closed = 0
        swipe.closeImmediately { closed++ }
        assertEquals(1, closed)
    }
}
