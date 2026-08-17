package com.miowser.app.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadStatus

/** Lists every download this session has queued, with live progress — reads DownloadQueue.downloads directly, so it updates itself. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsSheet(
    downloads: List<Download>,
    onCancel: (Download) -> Unit,
    onRetry: (Download) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (downloads.isEmpty()) {
            Text(text = "No downloads yet.", modifier = Modifier.padding(24.dp))
        } else {
            LazyColumn {
                items(downloads, key = { it.id }) { download ->
                    DownloadRow(download, onCancel, onRetry)
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(download: Download, onCancel: (Download) -> Unit, onRetry: (Download) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = download.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = statusLabel(download),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (download.status == DownloadStatus.DOWNLOADING) {
                if (download.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            } else if (download.status == DownloadStatus.MUXING || download.status == DownloadStatus.QUEUED) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
            }
        }
        if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.QUEUED) {
            TextButton(onClick = { onCancel(download) }) {
                Text("Cancel")
            }
        }
        if (download.status == DownloadStatus.FAILED || download.status == DownloadStatus.CANCELED) {
            TextButton(onClick = { onRetry(download) }) {
                Text("Retry")
            }
        }
    }
}

private fun statusLabel(download: Download): String = when (download.status) {
    DownloadStatus.QUEUED -> "Waiting…"
    DownloadStatus.DOWNLOADING -> {
        if (download.kind == com.miowser.app.downloader.DownloadKind.HLS && download.totalBytes > 0) {
            "Downloading segment ${download.bytesDownloaded}/${download.totalBytes}"
        } else if (download.totalBytes > 0) {
            "Downloading… ${(download.bytesDownloaded * 100 / download.totalBytes)}%"
        } else {
            "Downloading…"
        }
    }
    DownloadStatus.MUXING -> "Combining into final file…"
    DownloadStatus.COMPLETED -> "Done — saved to Downloads/Miowser"
    DownloadStatus.FAILED -> "Failed" + (download.errorMessage?.let { ": $it" } ?: "")
    DownloadStatus.CANCELED -> "Canceled"
}
