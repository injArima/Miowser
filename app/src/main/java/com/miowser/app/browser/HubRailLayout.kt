package com.miowser.app.browser

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp

/** The rail has stable geometry during selection; its parent always sees the same viewport. */
@Composable
internal fun FixedHubRailViewport(content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxWidth().height(56.dp),
        measurePolicy = HubRailViewportMeasurePolicy)
}

internal object HubRailViewportMeasurePolicy : MeasurePolicy {
    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val rail = measurables.single().measure(constraints.copy(minWidth = 0, minHeight = 0))
        val width = constraints.maxWidth
        val height = constraints.constrainHeight(56.dp.roundToPx())
        return layout(width, height) {
            rail.placeRelative((width - rail.width) / 2, (height - rail.height) / 2)
        }
    }
}
