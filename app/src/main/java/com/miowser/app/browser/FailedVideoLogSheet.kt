package com.miowser.app.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miowser.app.library.FailedVideoLog
import com.miowser.app.library.FailedVideoRecord
import com.miowser.app.ui.ExpressiveBottomSheet
import com.miowser.app.ui.ExpressiveSheetHeading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class VideoRecordSection { RECENT, SAVED }

/** The optional text records are read only while this sheet is open. */
@Composable
internal fun FailedVideoLogSheet(
    onDismiss: () -> Unit,
    onOpenInBrowser: (value: String, search: Boolean) -> Unit
) {
    val context = LocalContext.current
    var recent by remember { mutableStateOf<List<FailedVideoRecord>?>(null) }
    var saved by remember { mutableStateOf<List<FailedVideoRecord>?>(null) }
    var section by remember { mutableStateOf(VideoRecordSection.RECENT) }
    var expandedId by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf<FailedVideoRecord?>(null) }

    LaunchedEffect(section) {
        when (section) {
            VideoRecordSection.RECENT -> if (recent == null) {
                recent = withContext(Dispatchers.IO) { FailedVideoLog.load(context.applicationContext) }
            }
            VideoRecordSection.SAVED -> if (saved == null) {
                saved = withContext(Dispatchers.IO) { FailedVideoLog.loadSaved(context.applicationContext) }
            }
        }
    }

    fun touchRecent(record: FailedVideoRecord) {
        if (section != VideoRecordSection.RECENT) return
        val now = System.currentTimeMillis()
        FailedVideoLog.touch(context.applicationContext, record.id, now)
        recent = recent?.map { if (it.id == record.id) it.copy(lastActionAt = now) else it }
            ?.sortedByDescending(FailedVideoRecord::lastActionAt)
    }

    fun copy(record: FailedVideoRecord, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        touchRecent(record)
    }

    ExpressiveBottomSheet(onDismissRequest = onDismiss) {
        ExpressiveSheetHeading(
            "Video references",
            "Recent playback issues expire after 90 days without an action. Saved records stay until you delete them."
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = section == VideoRecordSection.RECENT,
                onClick = { section = VideoRecordSection.RECENT; expandedId = null },
                label = { Text("Recent issues") }
            )
            FilterChip(
                selected = section == VideoRecordSection.SAVED,
                onClick = { section = VideoRecordSection.SAVED; expandedId = null },
                label = { Text("Saved") }
            )
        }
        val records = if (section == VideoRecordSection.RECENT) recent else saved
        when (records) {
            null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            else -> if (records.isEmpty()) {
                Text(
                    if (section == VideoRecordSection.RECENT) "No playback issues recorded"
                    else "No saved video records yet",
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(records, key = FailedVideoRecord::id) { record ->
                        val expanded = expandedId == record.id
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp)
                            ) {
                                Surface(
                                    onClick = { expandedId = if (expanded) null else record.id },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.small,
                                    color = MaterialTheme.colorScheme.surfaceContainerLow
                                ) {
                                    Column(
                                        modifier = Modifier.padding(4.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(record.videoName, fontWeight = FontWeight.SemiBold)
                                        Text(record.pageTitle, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            record.domain,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                if (expanded) {
                                    Spacer(Modifier.height(10.dp))
                                    val details = "Video: ${record.videoName}\nPage: ${record.pageTitle}\nWebsite: ${record.domain}\nURL: ${record.pageUrl}"
                                    Row(
                                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RecordActionRow("Video name", record.videoName.isNotBlank(),
                                            onCopy = { copy(record, "Video name", record.videoName) },
                                            onOpen = { touchRecent(record); onOpenInBrowser(record.videoName, true) })
                                        RecordActionRow("Page title", record.pageTitle.isNotBlank(),
                                            onCopy = { copy(record, "Page title", record.pageTitle) },
                                            onOpen = { touchRecent(record); onOpenInBrowser(record.pageTitle, true) })
                                        RecordActionRow("URL", record.pageUrl.isNotBlank(),
                                            canCopy = record.pageUrl.isNotBlank(),
                                            onCopy = { copy(record, "Page URL", record.pageUrl) },
                                            onOpen = { touchRecent(record); onOpenInBrowser(record.pageUrl, false) })
                                        RecordActionRow("Full details", record.pageUrl.isNotBlank(),
                                            onCopy = { copy(record, "Video details", details) },
                                            onOpen = { touchRecent(record); onOpenInBrowser(record.pageUrl, false) })
                                        if (section == VideoRecordSection.RECENT) {
                                            TextButton(modifier = Modifier.heightIn(min = 44.dp), onClick = {
                                                if (FailedVideoLog.moveToSaved(context.applicationContext, record.id)) {
                                                    recent = recent?.filterNot { it.id == record.id }
                                                    saved = FailedVideoLog.loadSaved(context.applicationContext)
                                                    section = VideoRecordSection.SAVED
                                                }
                                                expandedId = null
                                            }) { Text("Move to Saved") }
                                        } else {
                                            IconButton(
                                                onClick = { deleting = record; expandedId = null },
                                                modifier = Modifier.size(44.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Delete,
                                                    contentDescription = "Delete saved record",
                                                    tint = MaterialTheme.colorScheme.error,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    deleting?.let { record ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete saved record?") },
            text = { Text(record.videoName) },
            confirmButton = {
                TextButton(onClick = {
                    if (FailedVideoLog.removeSaved(context.applicationContext, record.id)) {
                        saved = saved?.filterNot { it.id == record.id }
                    }
                    deleting = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RecordActionRow(
    label: String,
    canOpen: Boolean,
    canCopy: Boolean = true,
    onCopy: () -> Unit,
    onOpen: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            onClick = onCopy,
            enabled = canCopy,
            modifier = Modifier.widthIn(min = 144.dp).heightIn(min = 44.dp)
                .semantics { contentDescription = "Copy $label" },
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                Text(label, style = MaterialTheme.typography.labelLarge)
            }
        }
        Surface(
            onClick = onOpen,
            enabled = canOpen,
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(44.dp)
                .semantics { contentDescription = "Open $label in browser" }
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null,
                    modifier = Modifier.size(18.dp))
            }
        }
    }
}
