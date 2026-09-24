package com.miowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The long-press-on-a-link menu — a real browser convention this app didn't have at all before:
 * long-pressing a link inside a page's content (see Tab's own onLongClickListener/hitTestResult
 * wiring) surfaces this instead of leaving the press to do nothing.
 *
 * "Add to speed dial" writes into the browser homepage's persistent speed-dial collection.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkContextMenu(
    url: String,
    onOpenInNewTab: () -> Unit,
    onOpenInBackgroundTab: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onAddToSpeedDial: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = url,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LinkMenuRow(Icons.AutoMirrored.Rounded.OpenInNew, "Open in new tab") {
            onDismiss()
            onOpenInNewTab()
        }
        LinkMenuRow(Icons.Rounded.Tab, "Open in background tab") {
            onDismiss()
            onOpenInBackgroundTab()
        }
        LinkMenuRow(Icons.Rounded.ContentCopy, "Copy link") {
            onDismiss()
            onCopyLink()
        }
        LinkMenuRow(Icons.Rounded.Share, "Share link") {
            onDismiss()
            onShareLink()
        }
        LinkMenuRow(Icons.Rounded.GridView, "Add to speed dial") {
            onDismiss()
            onAddToSpeedDial()
        }
    }
}

@Composable
private fun LinkMenuRow(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val contentColor = if (enabled) LocalContentColor.current else LocalContentColor.current.copy(alpha = 0.38f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = contentColor)
        Text(label, color = contentColor, modifier = Modifier.padding(start = 24.dp))
    }
}
