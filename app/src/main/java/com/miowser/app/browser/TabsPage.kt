package com.miowser.app.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
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
import com.miowser.app.ui.SimplePageScaffold

/**
 * The Tabs destination: every open tab in one scrollable list (title + URL), with
 * switch/close/new-tab actions — the only tab-management surface now that there's no
 * always-visible top strip; BottomBar's tab-count icon is the sole other hint of how many are open.
 */
@Composable
fun TabsPage(
    tabs: List<Tab>,
    activeTabId: Long?,
    onSelectTab: (Long) -> Unit,
    onCloseTab: (Long) -> Unit,
    onNewTab: () -> Unit,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "Tabs", onBack = onBack) { modifier ->
        LazyColumn(modifier = modifier) {
            items(tabs, key = { it.id }) { tab ->
                TabListRow(
                    title = tab.title,
                    url = tab.url,
                    isActive = tab.id == activeTabId,
                    onClick = { onSelectTab(tab.id) },
                    onClose = { onCloseTab(tab.id) }
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onNewTab)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(text = "New tab", modifier = Modifier.padding(start = 16.dp))
                }
            }
        }
    }
}

@Composable
private fun TabListRow(
    title: String,
    url: String,
    isActive: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (url.isNotBlank()) {
                Text(
                    text = url,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        IconButton(onClick = onClose) {
            Icon(Icons.Rounded.Close, contentDescription = "Close tab")
        }
    }
}
