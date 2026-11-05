package com.miowser.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Draw only the top rim; the Material sheet keeps ownership of its shape and layout. */
fun Modifier.sheetTopRim(outline: Color): Modifier = drawWithContent {
    drawContent()
    val radius = 28.dp.toPx().coerceAtMost(size.width / 2f).coerceAtMost(size.height)
    if (radius <= 0f) return@drawWithContent
    val rim = Path().apply {
        moveTo(0f, radius)
        quadraticTo(0f, 0f, radius, 0f)
        lineTo(size.width - radius, 0f)
        quadraticTo(size.width, 0f, size.width, radius)
    }
    drawPath(rim, outline.copy(alpha = 0.32f), style = Stroke(width = 1.dp.toPx()))
}

/** Place this outside verticalScroll so the fade stays fixed to its viewport. */
fun Modifier.sheetVerticalEdgeFade(scroll: ScrollState, surface: Color): Modifier =
    sheetVerticalEdgeFade(surface, { scroll.value > 0 }, { scroll.value < scroll.maxValue })

/** Place this outside a LazyColumn's content drawing. */
fun Modifier.sheetVerticalEdgeFade(scroll: LazyListState, surface: Color): Modifier =
    sheetVerticalEdgeFade(surface, { scroll.canScrollBackward }, { scroll.canScrollForward })

private fun Modifier.sheetVerticalEdgeFade(
    surface: Color,
    canScrollBack: () -> Boolean,
    canScrollForward: () -> Boolean
): Modifier = drawWithContent {
    drawContent()
    val edge = 16.dp.toPx().coerceAtMost(size.height / 3f)
    if (edge <= 0f) return@drawWithContent
    if (canScrollBack()) drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(surface, surface.copy(alpha = 0f)),
            startY = 0f,
            endY = edge
        ),
        size = Size(size.width, edge)
    )
    if (canScrollForward()) drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(surface.copy(alpha = 0f), surface),
            startY = size.height - edge,
            endY = size.height
        ),
        topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - edge),
        size = Size(size.width, edge)
    )
}
