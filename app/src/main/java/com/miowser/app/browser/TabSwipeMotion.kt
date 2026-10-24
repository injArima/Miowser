package com.miowser.app.browser

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.MotionDurationScale
import com.miowser.app.ui.theme.MotionPolicy
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** One owner per tab. Generation checks also reject jobs queued before an interruption. */
internal class TabSwipeMotion {
    private val animation = Animatable(0f)
    private var generation = 0L
    private var closed = false
    var offset by mutableFloatStateOf(0f)
        private set

    fun takeControl(): Long = ++generation
    fun owns(token: Long): Boolean = token == generation && !closed

    fun drag(token: Long, position: Float, width: Float) {
        if (token == generation && !closed) offset = position.coerceIn(-width, width)
    }

    fun closeImmediately(onClose: () -> Unit) {
        ++generation
        if (!closed) { closed = true; onClose() }
    }

    suspend fun settle(token: Long, destination: Float, width: Float, velocity: Float,
        policy: MotionPolicy, onClose: (() -> Unit)? = null) {
        val context = currentCoroutineContext()
        context.ensureActive()
        if (token != generation || closed) return
        if (policy.reduced || context[MotionDurationScale]?.scaleFactor == 0f) {
            offset = destination
        } else {
            animation.updateBounds(-width, width)
            animation.snapTo(offset)
            val result = animation.animateTo(destination, policy.swipeSettleSpec(),
                initialVelocity = velocity.coerceIn(-width * 8f, width * 8f)) {
                if (token == generation && !closed) offset = value
            }
            // An outward fling can hit a bound during return. Finish returning, never strand it.
            if (result.endReason == AnimationEndReason.BoundReached && animation.value != destination) {
                context.ensureActive()
                if (token != generation || closed) return
                animation.animateTo(destination, policy.swipeSettleSpec(), initialVelocity = 0f) {
                    if (token == generation && !closed) offset = value
                }
            }
        }
        context.ensureActive()
        if (token == generation && !closed && onClose != null) {
            closed = true
            onClose()
        }
    }
}
