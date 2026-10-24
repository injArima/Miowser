package com.miowser.app.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.unit.dp
import com.miowser.app.settings.AppSettings
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

@Immutable
data class MotionTiming(val millis: Int, val easing: Easing)

/** Timings apply to app-owned motion; Material3 retains its own animation/gesture owners. */
object MotionTokens {
    val enterEasing: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val exitEasing: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val selectionEasing: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val press = MotionTiming(90, enterEasing)
    val release = MotionTiming(140, enterEasing)
    val feedbackEnter = MotionTiming(200, enterEasing)
    val feedbackExit = MotionTiming(140, exitEasing)
    val controlsEnter = MotionTiming(220, enterEasing)
    val controlsExit = MotionTiming(160, exitEasing)
    val selection = MotionTiming(220, selectionEasing)
    val playlistEnter = MotionTiming(240, enterEasing)
    val playlistExit = MotionTiming(180, exitEasing)
    val subtitleEnter = MotionTiming(200, enterEasing)
    val subtitleExit = MotionTiming(140, exitEasing)
    // Keep the existing tab settling owner until the retained/velocity gesture step.
    val tabDismiss = MotionTiming(120, exitEasing)
    val tabReturn = MotionTiming(180, enterEasing)
    val compactTravel = 12.dp
    const val pressedScale = 0.97f
    const val swipeDamping = 1f
    const val swipeStiffness = 400f
}

@Immutable
data class MotionPolicy(val reduced: Boolean = false) {
    fun duration(timing: MotionTiming): Int = if (reduced) 0 else timing.millis
    fun <T> spec(timing: MotionTiming) = tween<T>(durationMillis = duration(timing), easing = timing.easing)
    fun pressedScale(pressed: Boolean, enabled: Boolean = true): Float =
        if (pressed && enabled && !reduced) MotionTokens.pressedScale else 1f

    /** Reserved for the later retained swipe/velocity owner, not for labels or seeking. */
    fun <T> swipeSettleSpec(): FiniteAnimationSpec<T> = if (reduced) snap()
        else spring(dampingRatio = MotionTokens.swipeDamping, stiffness = MotionTokens.swipeStiffness)

    companion object {
        fun resolve(requestedReduction: Boolean, platformScale: Float) =
            MotionPolicy(reduced = requestedReduction || platformScale == 0f)
    }
}

val LocalMotionPolicy = staticCompositionLocalOf { MotionPolicy() }

@Composable
internal fun ProvideMotionPolicy(content: @Composable () -> Unit) {
    // Compose's installed Android scale is observable and is also inherited by animations.
    // Do not multiply durations here: Compose already applies positive system multipliers.
    val platformScale = rememberCoroutineScope().coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
    val requested = AppSettings.current.reduceMotion
    val policy = remember(requested, platformScale) { MotionPolicy.resolve(requested, platformScale) }
    CompositionLocalProvider(LocalMotionPolicy provides policy, content = content)
}

/** Zero-duration gesture settlement needs neither an animation clock nor a delayed callback. */
internal suspend fun settleMotionFloat(from: Float, to: Float, timing: MotionTiming,
    policy: MotionPolicy, update: (Float) -> Unit) {
    val context = currentCoroutineContext()
    context.ensureActive()
    if (policy.reduced || context[MotionDurationScale]?.scaleFactor == 0f) {
        update(to)
    } else {
        Animatable(from).animateTo(to, policy.spec(timing)) { update(value) }
    }
    currentCoroutineContext().ensureActive()
}
