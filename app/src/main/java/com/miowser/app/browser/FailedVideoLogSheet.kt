package com.miowser.app.browser

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miowser.app.library.FailedVideoLog
import com.miowser.app.library.FailedVideoRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FailedVideoLogSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var records by remember { mutableStateOf<List<FailedVideoRecord>?>(null) }
    LaunchedEffect(Unit) {
        records = withContext(Dispatchers.IO) { FailedVideoLog.load(context.applicationContext) }
    }

    fun copyAndRefresh(record: FailedVideoRecord, label: String, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
        // Persist before the sheet can close; a dismissed composable would cancel a launched job.
        val now = System.currentTimeMillis()
        FailedVideoLog.touch(context.applicationContext, record.id, now)
        records = records?.map { if (it.id == record.id) it.copy(lastActionAt = now) else it }
            ?.sortedByDescending(FailedVideoRecord::lastActionAt)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Playback issue history", style = MaterialTheme.typography.titleLarge)
            Text(
                "Videos removed when you returned to their source page. Records expire after 90 days without a copy action.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            when (val loaded = records) {
                null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                else -> if (loaded.isEmpty()) {
                    Text("No records yet", modifier = Modifier.padding(vertical = 24.dp))
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                        items(loaded, key = FailedVideoRecord::id) { record ->
                            var menuOpen by remember(record.id) { mutableStateOf(false) }
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(record.videoName, fontWeight = FontWeight.SemiBold)
                                    Text(record.pageTitle, style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        record.domain,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Column {
                                    IconButton(onClick = { menuOpen = true }) {
                                        Icon(Icons.Rounded.MoreVert, contentDescription = "Record options for ${record.videoName}")
                                    }
                                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                        val details = "Video: ${record.videoName}\nPage: ${record.pageTitle}\nWebsite: ${record.domain}\nURL: ${record.pageUrl}"
                                        listOf(
                                            "Copy details" to details,
                                            "Copy video name" to record.videoName,
                                            "Copy page title" to record.pageTitle,
                                            "Copy URL" to record.pageUrl
                                        ).forEach { (label, value) ->
                                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                                menuOpen = false
                                                copyAndRefresh(record, label, value)
                                            })
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
