package com.miowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// Shared by HistoryPage and BookmarksPage — both are "title + URL, open on tap, remove via
// trailing icon" lists over different data. The row's own tap (open) and the trailing
// IconButton's tap (remove) are two separate clickable regions nested inside each other —
// Compose resolves a tap inside the IconButton's bounds to its own handler first, falling
// through to the Row's clickable for everything else, so no explicit hit-testing is needed here.
//
// A plain cross rather than a trash icon: removing one row is instant, no confirmation (see
// HistoryPage's own bulk-delete dialogs for the operations that actually warrant one) — the
// lightweight icon matches the lightweight, easily-undone-by-just-revisiting-the-page stakes.
@Composable
internal fun LibraryRow(title: String, subtitle: String, onClick: () -> Unit, onRemove: () -> Unit) {
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
            Icon(Icons.Rounded.Close, contentDescription = "Remove")
        }
    }
}
