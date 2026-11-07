package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape
import kotlin.math.roundToInt
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.MiowserActionButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserZoomSheet(
    zoomFactor: Float,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() }, shape = MiowserBottomSheetShape) {
        Column(Modifier.fillMaxWidth().padding(start = UiSpacing.xxl, end = UiSpacing.xxl, bottom = UiSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.m)) {
            Text("Page zoom", style = MaterialTheme.typography.titleLarge)
            Text("Pinch the page or use these controls. Zoom applies to the current tab.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = onZoomOut) { Icon(Icons.Rounded.Remove, "Zoom out") }
                Text("${(zoomFactor * 100).roundToInt()}%", style = MaterialTheme.typography.headlineSmall)
                OutlinedIconButton(onClick = onZoomIn) { Icon(Icons.Rounded.Add, "Zoom in") }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onReset) { Text("Reset zoom") }
                MiowserActionButton(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
