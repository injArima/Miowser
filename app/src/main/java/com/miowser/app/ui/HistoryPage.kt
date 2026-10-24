package com.miowser.app.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miowser.app.history.HistoryEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// A bulk delete pending confirmation — everything, or just one date's entries. Single-row
// deletion (LibraryRow's own cross button) never goes through this: only operations that erase
// more than one entry at once are risky enough to ask first.
private sealed class PendingBulkDelete {
    data class WholeDay(val date: LocalDate, val entries: List<HistoryEntry>) : PendingBulkDelete()
    data object Everything : PendingBulkDelete()
}

@Composable
fun HistoryPage(
    entries: List<HistoryEntry>,
    onOpenUrl: (String) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onRemoveEntries: (List<HistoryEntry>) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    var pendingDelete by remember { mutableStateOf<PendingBulkDelete?>(null) }

    SimplePageScaffold(title = "History", onBack = onBack) { modifier ->
        if (entries.isEmpty()) {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ExpressiveEmptyState(
                    title = "No history yet",
                    description = "Pages you visit will appear here.",
                    icon = Icons.Rounded.History
                )
            }
            return@SimplePageScaffold
        }
        // Deliberately not `remember`-ed: entries is HistoryLog's own mutableStateListOf, the
        // same list instance every recomposition, so keying remember() on it would never see it
        // as "changed" and this would silently go stale the first time an entry was added or
        // removed. Recomputing on every recomposition is cheap enough at HistoryLog's own cap
        // (2000 entries) not to need caching anyway.
        val grouped = entries.sortedByDescending { it.lastVisitedAt }.groupBy { it.visitedOnDate() }

        LazyColumn(modifier = modifier) {
            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TextButton(onClick = { pendingDelete = PendingBulkDelete.Everything }) { Text("Clear all history") }
                }
            }
            grouped.forEach { (date, dayEntries) ->
                item(key = "header-$date") {
                    DateHeader(date = date, onDelete = { pendingDelete = PendingBulkDelete.WholeDay(date, dayEntries) })
                }
                items(dayEntries, key = { it.url }) { entry ->
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

    pendingDelete?.let { pending ->
        val message: String
        val action: () -> Unit
        when (pending) {
            is PendingBulkDelete.WholeDay -> {
                val count = pending.entries.size
                message = "Delete $count ${if (count == 1) "entry" else "entries"} from ${formatDateHeader(pending.date)}?"
                action = { onRemoveEntries(pending.entries) }
            }
            PendingBulkDelete.Everything -> {
                message = "Delete all history? This can't be undone."
                action = onClear
            }
        }
        MiowserConfirmationDialog(
            title = "Delete history",
            message = message,
            onDismiss = { pendingDelete = null },
            actions = listOf(
                ConfirmationAction("Cancel", { pendingDelete = null }, ConfirmationTone.CAUTION),
                ConfirmationAction("Delete", { action(); pendingDelete = null }, ConfirmationTone.CAUTION)
            )
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatDateHeader(date),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        // Trash, not a cross, and behind a confirmation dialog: this deletes a whole day's worth
        // of entries at once, a different risk tier than LibraryRow's single-row cross button —
        // the error-role color (not just shape) marks it as the destructive one on this row.
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Rounded.Delete,
                contentDescription = "Delete ${formatDateHeader(date)}",
                tint = MaterialTheme.colorScheme.error
            )
        }
    }
}

private val HEADER_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d, yyyy")

private fun formatDateHeader(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> date.format(HEADER_DATE_FORMAT)
    }
}

private fun HistoryEntry.visitedOnDate(): LocalDate =
    Instant.ofEpochMilli(lastVisitedAt).atZone(ZoneId.systemDefault()).toLocalDate()
