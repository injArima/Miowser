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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BrowserTextSizeSheet(
    textZoomPercent: Int,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() }, shape = MiowserBottomSheetShape) {
        Column(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Text size", style = MaterialTheme.typography.titleLarge)
            Text("Adjust the text on this tab without changing page zoom.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onChange(textZoomPercent - 10) },
                    enabled = textZoomPercent > 50) {
                    Icon(Icons.Rounded.Remove, "Decrease text size")
                }
                Text("$textZoomPercent%", style = MaterialTheme.typography.headlineSmall)
                IconButton(onClick = { onChange(textZoomPercent + 10) },
                    enabled = textZoomPercent < 200) {
                    Icon(Icons.Rounded.Add, "Increase text size")
                }
            }
            Slider(value = textZoomPercent.toFloat(),
                onValueChange = { onChange((it / 10f).roundToInt() * 10) },
                valueRange = 50f..200f, steps = 14,
                modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { onChange(100) }) { Text("Reset text size") }
                Button(onClick = onDismiss) { Text("Done") }
            }
        }
    }
}
