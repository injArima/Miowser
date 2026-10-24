package com.miowser.app.browser

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import org.junit.Assert.*
import org.junit.Test

class HubRailLayoutTest {
    private class Scope(override val density: Float = 1f,
        override val layoutDirection: LayoutDirection = LayoutDirection.Ltr) : MeasureScope {
        override val fontScale = 1f
    }

    private class Rail(var desiredWidth: Int, var desiredHeight: Int) : Measurable {
        override val parentData: Any? = null
        var lastConstraints: Constraints = Constraints()
        var placement: IntOffset? = null
        override fun measure(constraints: Constraints): Placeable {
            lastConstraints = constraints
            return object : Placeable() {
                init {
                    measuredSize = IntSize(desiredWidth.coerceIn(constraints.minWidth, constraints.maxWidth),
                        desiredHeight.coerceIn(constraints.minHeight, constraints.maxHeight))
                }
                override fun placeAt(position: IntOffset, zIndex: Float,
                    layerBlock: (GraphicsLayerScope.() -> Unit)?) { placement = position }
                override fun get(alignmentLine: AlignmentLine) = AlignmentLine.Unspecified
            }
        }
        override fun minIntrinsicWidth(height: Int) = desiredWidth
        override fun maxIntrinsicWidth(height: Int) = desiredWidth
        override fun minIntrinsicHeight(width: Int) = desiredHeight
        override fun maxIntrinsicHeight(width: Int) = desiredHeight
    }

    private fun measure(rail: Rail, constraints: Constraints, scope: Scope = Scope()): MeasureResult =
        with(HubRailViewportMeasurePolicy) { with(scope) { measure(listOf(rail), constraints) } }

    @Test fun animatedChildWidthsDoNotChangeTheSizeReportedToTheScreen() {
        val rail = Rail(190, 56)
        val constraints = Constraints.fixed(350, 56)
        for (width in listOf(190, 210, 240, 210, 190)) {
            rail.desiredWidth = width
            val result = measure(rail, constraints)
            assertEquals(350, result.width)
            assertEquals(56, result.height)
            assertEquals(0, rail.lastConstraints.minWidth)
            result.placeChildren()
            assertEquals(IntOffset((350 - width) / 2, 0), rail.placement)
        }
    }

    @Test fun viewportUsesDensityAndIncomingHeightLimits() {
        val rail = Rail(300, 112)
        val result = measure(rail, Constraints(maxWidth = 800, maxHeight = 200), Scope(density = 2f))
        assertEquals(112, result.height)
        val limited = measure(rail, Constraints.fixed(800, 90), Scope(density = 2f))
        assertEquals(90, limited.height)
        limited.placeChildren()
        assertEquals(IntOffset(250, 0), rail.placement)
    }

    @Test fun childCannotOverflowIncomingViewportConstraints() {
        val rail = Rail(500, 100)
        val result = measure(rail, Constraints.fixed(200, 56))
        assertEquals(200, rail.lastConstraints.maxWidth)
        assertEquals(56, rail.lastConstraints.maxHeight)
        result.placeChildren()
        assertEquals(IntOffset.Zero, rail.placement)
    }

    @Test fun compactRailStaysCenteredInRtl() {
        val rail = Rail(200, 56)
        val result = measure(rail, Constraints.fixed(350, 56), Scope(layoutDirection = LayoutDirection.Rtl))
        result.placeChildren()
        assertEquals(IntOffset(75, 0), rail.placement)
    }
}
