package com.miowser.app.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.downloader.remainingTimeLabel
import com.miowser.app.ui.SimplePageScaffold

/** Every download this session has queued, with live progress — reads DownloadQueue.downloads directly, so it updates itself. */
@Composable
fun DownloadsPage(
    downloads: List<Download>,
    onCancel: (Download) -> Unit,
    onRetry: (Download) -> Unit,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "Downloads", onBack = onBack) { modifier ->
        if (downloads.isEmpty()) {
            Text(text = "No downloads yet.", modifier = modifier.padding(24.dp))
        } else {
            LazyColumn(modifier = modifier) {
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
            // Neutral while in flight, then color-coded the moment there's an actual outcome to
            // report — error for FAILED, primary for COMPLETED — so scanning a long list for
            // "did that one work?" doesn't require reading every line of text.
            Text(
                text = statusLabel(download),
                style = MaterialTheme.typography.bodySmall,
                color = when (download.status) {
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
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
            buildString {
                append("Downloading segment ${download.bytesDownloaded}/${download.totalBytes}")
                download.remainingTimeLabel()?.let { append(" • $it") }
            }
        } else if (download.totalBytes > 0) {
            buildString {
                append("Downloading… ${(download.bytesDownloaded * 100 / download.totalBytes)}%")
                download.remainingTimeLabel()?.let { append(" • $it") }
            }
        } else {
            "Downloading…"
        }
    }
    DownloadStatus.PAUSED -> {
        val percent = if (download.totalBytes > 0) download.bytesDownloaded * 100 / download.totalBytes else null
        if (percent != null) "Paused • $percent%" else "Paused"
    }
    DownloadStatus.MUXING -> "Combining into final file…"
    DownloadStatus.COMPLETED -> "Done — saved to Downloads/Miowser"
    DownloadStatus.FAILED -> "Failed" + (download.errorMessage?.let { ": $it" } ?: "")
    DownloadStatus.CANCELED -> "Canceled"
}
