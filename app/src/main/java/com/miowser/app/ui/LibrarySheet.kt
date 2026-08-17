package com.miowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.bookmarks.Bookmark
import com.miowser.app.history.HistoryEntry

/**
 * History and bookmarks share one sheet with a tab switcher, rather than two separate BottomBar
 * destinations — both are "places you've been / want to go back to" lists over the same simple
 * open/remove actions, so one entry point keeps the bottom bar from getting crowded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibrarySheet(
    historyEntries: List<HistoryEntry>,
    bookmarks: List<Bookmark>,
    onOpenUrl: (String) -> Unit,
    onRemoveHistoryEntry: (HistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
    onRemoveBookmark: (Bookmark) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("History") })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Bookmarks") })
        }
        when (selectedTab) {
            0 -> HistoryTab(historyEntries, onOpenUrl, onRemoveHistoryEntry, onClearHistory)
            else -> BookmarksTab(bookmarks, onOpenUrl, onRemoveBookmark)
        }
    }
}

@Composable
private fun HistoryTab(
    entries: List<HistoryEntry>,
    onOpenUrl: (String) -> Unit,
    onRemove: (HistoryEntry) -> Unit,
    onClear: () -> Unit
) {
    if (entries.isEmpty()) {
        Text(text = "No history yet.", modifier = Modifier.padding(24.dp))
        return
    }
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        TextButton(onClick = onClear) { Text("Clear history") }
    }
    LazyColumn {
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

@Composable
private fun BookmarksTab(
    bookmarks: List<Bookmark>,
    onOpenUrl: (String) -> Unit,
    onRemove: (Bookmark) -> Unit
) {
    if (bookmarks.isEmpty()) {
        Text(text = "No bookmarks yet — tap the star in the address bar to add one.", modifier = Modifier.padding(24.dp))
        return
    }
    LazyColumn {
        items(bookmarks, key = { it.url }) { bookmark ->
            LibraryRow(
                title = bookmark.title,
                subtitle = bookmark.url,
                onClick = { onOpenUrl(bookmark.url) },
                onRemove = { onRemove(bookmark) }
            )
        }
    }
}

// The row's own tap (open) and the trailing IconButton's tap (remove) are two separate clickable
// regions nested inside each other — Compose resolves a tap inside the IconButton's bounds to
// its own handler first, falling through to the Row's clickable for everything else, so no
// explicit hit-testing is needed here.
@Composable
private fun LibraryRow(title: String, subtitle: String, onClick: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Default.Delete, contentDescription = "Remove")
        }
    }
}
