package com.miowser.app.browser

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import com.miowser.app.ui.theme.HubLibraryTokens
import com.miowser.app.ui.theme.LocalMotionPolicy
import kotlin.math.abs

internal fun Modifier.swipeHubSections(
    sections: List<HubSection>,
    selected: HubSection,
    layoutDirection: LayoutDirection,
    onSelect: (HubSection) -> Unit
): Modifier = pointerInput(sections, selected, layoutDirection) {
    var distance = 0f
    val minimum = HubLibraryTokens.tabSwipeDistance.toPx()
    detectHorizontalDragGestures(
        onDragStart = { distance = 0f },
        onDragEnd = {
            if (abs(distance) >= minimum) {
                val forward = (distance < 0f) == (layoutDirection == LayoutDirection.Ltr)
                val index = sections.indexOf(selected)
                if (index >= 0) sections.getOrNull(index + if (forward) 1 else -1)?.let(onSelect)
            }
        },
        onHorizontalDrag = { _, delta -> distance += delta }
    )
}

@Composable
internal fun libraryBusyOutline(): Modifier {
    val motion = LocalMotionPolicy.current
    val phase = if (motion.reduced) null else {
        rememberInfiniteTransition(label = "Library loading").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(HubLibraryTokens.busyOutlineCycleMillis, easing = LinearEasing)),
            label = "Library loading position"
        )
    }
    val accent = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    return Modifier.semantics {
        contentDescription = "Library loading"
        progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
    }.drawWithCache {
        val strokeWidth = HubLibraryTokens.busyOutlineWidth.toPx()
        val inset = strokeWidth / 2f
        if (size.width <= strokeWidth || size.height <= strokeWidth) {
            onDrawWithContent { drawContent() }
        } else {
            val bounds = Rect(inset, inset, size.width - inset, size.height - inset)
            val radius = CornerRadius(bounds.height / 2f)
            val outline = Path().apply { addRoundRect(RoundRect(bounds, radius)) }
            val measure = PathMeasure().apply { setPath(outline, forceClosed = true) }
            val segment = Path()
            val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            onDrawWithContent {
                drawContent()
                drawPath(outline, track, style = stroke)
                val start = (phase?.value ?: 0f) * measure.length
                val end = start + measure.length * HubLibraryTokens.busyOutlineFraction
                segment.reset()
                measure.getSegment(start, end.coerceAtMost(measure.length), segment, startWithMoveTo = true)
                if (end > measure.length) {
                    measure.getSegment(0f, end - measure.length, segment, startWithMoveTo = true)
                }
                drawPath(segment, accent, style = stroke)
            }
        }
    }
}
