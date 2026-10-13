package com.miowser.app.browser

import androidx.compose.runtime.MonotonicFrameClock
import com.miowser.app.ui.theme.MotionPolicy
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Test

class TabSwipeBoundsTest {
    @Test fun outwardVelocityOnCancelledSwipeStillReturnsToRest() = runBlocking {
        for (direction in listOf(-1f, 1f)) {
            val swipe = TabSwipeMotion()
            val token = swipe.takeControl()
            swipe.drag(token, 190f * direction, 200f)
            val clock = object : MonotonicFrameClock {
                var frames = 0
                override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
                    yield()
                    return onFrame(frames++ * 16_000_000L)
                }
            }
            withTimeout(3_000) { withContext(clock) {
                swipe.settle(token, 0f, 200f, 1_600f * direction, MotionPolicy())
            } }
            assertEquals(0f, swipe.offset, 0.01f)
        }
    }
}
