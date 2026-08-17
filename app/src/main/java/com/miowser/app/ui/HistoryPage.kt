package com.miowser.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miowser.app.history.HistoryEntry

@Composable
fun HistoryPage(
    entries: List<HistoryEntry>,
    onOpenUrl: (String) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "History", onBack = onBack) { modifier ->
        if (entries.isEmpty()) {
            Text(text = "No history yet.", modifier = modifier.padding(24.dp))
            return@SimplePageScaffold
        }
        LazyColumn(modifier = modifier) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TextButton(onClick = onClear) { Text("Clear history") }
                }
            }
            items(entries, key = { it.url }) { entry ->
                LibraryRow(
                    title = entry.title,
                    subtitle = entry.url,
                    onClick = { onOpenUrl(entry.url) },
                    onRemove = { onRemove(entry) }
                )
            }
        }
    }
}
