package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.ElementRuleAction
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape
import kotlinx.coroutines.flow.first

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
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    LaunchedEffect(selectedIndex) { onPreview(selectedIndex) }
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.hasPartiallyExpandedState }.first { it }
        sheetState.partialExpand()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() },
        shape = MiowserBottomSheetShape,
        sheetState = sheetState
    ) {
        BoxWithConstraints {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
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

                if (sheetState.currentValue == SheetValue.PartiallyExpanded) {
                    Text(
                        "Swipe up for actions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }

                HorizontalDivider()

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
            Column(Modifier.weight(1f)) {
                Text("Choose an element", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Tap once to outline it, then tap the outlined area again",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onCancel) { Text("Cancel") }
        }
    }
}
