package com.miowser.app.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tab
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    linkText: String?,
    onOpenInNewTab: () -> Unit,
    onOpenInBackgroundTab: () -> Unit,
    onCopyLink: () -> Unit,
    onShareLink: () -> Unit,
    onAddToSpeedDial: () -> Unit,
    onDismiss: () -> Unit
) {
    var selectingText by remember(url, linkText) { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() },
        shape = MiowserBottomSheetShape) {
        if (selectingText && !linkText.isNullOrBlank()) {
            Text("Select text", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Text("Touch and hold to select part of the link text.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp))
            SelectionContainer {
                Text(
                    text = linkText,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            TextButton(onClick = onDismiss, modifier = Modifier.padding(horizontal = 12.dp)) {
                Text("Done")
            }
            androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
            return@ModalBottomSheet
        }
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
        MiowserMenuItem(
            text = { Text("Open in new tab") },
            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            onDismiss()
            onOpenInNewTab()
        }
        MiowserMenuItem(
            text = { Text("Open in background tab") },
            leadingIcon = { Icon(Icons.Rounded.Tab, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            onDismiss()
            onOpenInBackgroundTab()
        }
        MiowserMenuDivider()
        if (!linkText.isNullOrBlank()) {
            MiowserMenuItem(
                text = { Text("Select text") },
                leadingIcon = { Icon(Icons.Rounded.TextFields, contentDescription = null) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
            ) { selectingText = true }
        }
        MiowserMenuItem(
            text = { Text("Copy link") },
            leadingIcon = { Icon(Icons.Rounded.ContentCopy, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            onDismiss()
            onCopyLink()
        }
        MiowserMenuItem(
            text = { Text("Share link") },
            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            onDismiss()
            onShareLink()
        }
        MiowserMenuDivider()
        MiowserMenuItem(
            text = { Text("Add to speed dial") },
            leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        ) {
            onDismiss()
            onAddToSpeedDial()
        }
        androidx.compose.foundation.layout.Spacer(Modifier.height(4.dp))
    }
}
