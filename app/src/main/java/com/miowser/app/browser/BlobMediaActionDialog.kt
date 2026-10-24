package com.miowser.app.browser

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.grabber.DetectedMedia

internal enum class BlobActionKind { PLAY, DOWNLOAD, ADD_TO_LIBRARY }
internal data class PendingBlobAction(val tabId: Long, val item: DetectedMedia, val kind: BlobActionKind)

@Composable
internal fun BlobMediaActionDialog(
    action: PendingBlobAction,
    sources: List<DetectedMedia>,
    onSource: (DetectedMedia) -> Unit,
    onSaveBlob: () -> Unit,
    onReturnToPage: () -> Unit,
    onDismiss: () -> Unit
) {
    val playing = action.kind == BlobActionKind.PLAY
    val savingToLibrary = action.kind == BlobActionKind.ADD_TO_LIBRARY
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(when {
            playing -> "Play page video"
            savingToLibrary -> "Add page video to library"
            else -> "Download page video"
        }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (sources.isEmpty()) {
                        if (savingToLibrary) "This page video has no verified media link to save in the library yet."
                        else "This blob belongs to the webpage. No separate stream has been verified yet. " +
                            "A file-backed blob can be saved; a MediaSource stream needs its original media link."
                    } else {
                        "These media links were detected on this tab. They may belong to this video; " +
                            "choose the right source to ${when {
                                playing -> "play"
                                savingToLibrary -> "add to the library"
                                else -> "download"
                            }} it."
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
                if (sources.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(sources, key = { it.url }) { source ->
                            Surface(
                                onClick = { onSource(source) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = MaterialTheme.shapes.medium,
                                color = MaterialTheme.colorScheme.surfaceContainerHigh
                            ) {
                                Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
                                    val host = runCatching { Uri.parse(source.url).host }.getOrNull()
                                        ?: "Media source"
                                    Text(
                                        listOfNotNull(source.type.name, source.resolution, host).joinToString(" · "),
                                        style = MaterialTheme.typography.labelLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        source.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Column(horizontalAlignment = Alignment.End) {
                if (!savingToLibrary) TextButton(onClick = onSaveBlob) {
                    Text(if (playing) "Try saving blob, then play" else "Save blob as file")
                }
                if (playing) TextButton(onClick = onReturnToPage) { Text("Play on website") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
