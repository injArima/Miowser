package com.miowser.app.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Clip sheet contents at the curved top edge and paint the rim over them. */
fun Modifier.miowserSheetSurface(outline: Color): Modifier =
    clip(MiowserBottomSheetShape)
        .border(1.dp, outline.copy(alpha = 0.32f), MiowserBottomSheetShape)

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
