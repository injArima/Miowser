package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.ElementRuleAction
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ElementPickerSheet(
    selection: ElementPickerSelection,
    onPreview: (Int) -> Unit,
    onConfirm: (Int, ElementRuleAction) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedIndex by remember(selection) { mutableIntStateOf(0) }
    var action by remember(selection) { mutableStateOf(ElementRuleAction.HIDE) }

    LaunchedEffect(selectedIndex) { onPreview(selectedIndex) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() },
        shape = MiowserBottomSheetShape
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("Block page element", style = MaterialTheme.typography.titleLarge)
            Text(
                "Choose exactly how much of the page to change. The preview is temporary until you save.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Selected element", style = MaterialTheme.typography.labelLarge)
                    Text(
                        selection.labels.getOrElse(selectedIndex) { "Page element" },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        selection.selectors.getOrElse(selectedIndex) { "" },
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { selectedIndex-- },
                    enabled = selectedIndex > 0,
                    modifier = Modifier.weight(1f)
                ) { Text("Smaller") }
                OutlinedButton(
                    onClick = { selectedIndex = 0 },
                    enabled = selectedIndex != 0,
                    modifier = Modifier.weight(1f)
                ) { Text("This element") }
                OutlinedButton(
                    onClick = { selectedIndex++ },
                    enabled = selectedIndex < selection.selectors.lastIndex,
                    modifier = Modifier.weight(1f)
                ) { Text("Larger") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Action", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ElementRuleAction.entries.forEach { option ->
                        FilterChip(
                            selected = action == option,
                            onClick = { action = option },
                            label = { Text(option.label) }
                        )
                    }
                }
                Text(
                    if (action == ElementRuleAction.HIDE)
                        "Hide keeps the element in the page but makes it invisible. It is usually safer."
                    else
                        "Delete removes matching elements from the page. Reverting it reloads the page to restore them.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Cancel") }
                Button(
                    onClick = { onConfirm(selectedIndex, action) },
                    modifier = Modifier.weight(1f)
                ) { Text("Save change") }
            }
        }
    }
}

@Composable
fun ElementPickerPrompt(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 18.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Tap an element to block", style = MaterialTheme.typography.labelLarge)
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
