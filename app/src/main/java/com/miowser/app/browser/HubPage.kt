package com.miowser.app.browser

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadKind
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.downloader.timeProgressLabel
import com.miowser.app.ui.SimplePageScaffold
import com.miowser.app.ui.ExpressiveEmptyState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * The Hub's Library section — every download this session has queued or ever completed, with
 * live progress for in-flight ones (identical to the sheet DownloadsPage used to be) and tap-to-
 * play for anything actually finished, via [Download.savedUri] and
 * [com.miowser.app.player.PlaybackRequest.Local].
 *
 * This is a checkpoint shape, not the final one: Hub is being redesigned around a real
 * Library (playback history) tab and a Downloads tab with its own Downloading/Downloaded
 * sub-sections, at which point this file gets rebuilt around that instead. An earlier version of
 * this file also carried an "On this page" section + tab switcher (the grabber's per-page
 * detections) — reverted back out to its own standalone page (DetectedMediaPage, via
 * Screen.DETECTED, untouched throughout) per an explicit decision that Hub shouldn't include it.
 */
@Composable
fun HubPage(
    downloads: List<Download>,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onCancel: (Download) -> Unit,
    onRetry: (Download) -> Unit,
    onDelete: (Download, Boolean) -> Unit,
    onRefreshLink: (Download) -> Unit,
    onPlayLocal: (Download) -> Unit,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "Downloads", onBack = onBack) { modifier ->
        HubDownloadsContent(
            downloads = downloads,
            onPause = onPause,
            onResume = onResume,
            onCancel = onCancel,
            onRetry = onRetry,
            onDelete = onDelete,
            onRefreshLink = onRefreshLink,
            onPlayLocal = onPlayLocal,
            modifier = modifier
        )
    }
}

/** Reusable download list used by both the Hub tab and the temporary standalone page. */
@Composable
fun HubDownloadsContent(
    downloads: List<Download>,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onCancel: (Download) -> Unit,
    onRetry: (Download) -> Unit,
    onDelete: (Download, Boolean) -> Unit,
    onRefreshLink: (Download) -> Unit,
    onPlayLocal: (Download) -> Unit,
    modifier: Modifier = Modifier,
    emptyMessage: String = "No downloads yet."
) {
    if (downloads.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            ExpressiveEmptyState(
                title = "No downloads to show",
                description = emptyMessage,
                icon = Icons.Rounded.Download
            )
        }
    } else {
        LazyColumn(modifier = modifier) {
            items(downloads, key = { it.id }) { download ->
                LibraryRow(
                    download,
                    onPause,
                    onResume,
                    onCancel,
                    onRetry,
                    onDelete,
                    onRefreshLink,
                    onPlayLocal
                )
            }
        }
    }
}

@Composable
private fun LibraryRow(
    download: Download,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onCancel: (Download) -> Unit,
    onRetry: (Download) -> Unit,
    onDelete: (Download, Boolean) -> Unit,
    onRefreshLink: (Download) -> Unit,
    onPlayLocal: (Download) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val elapsedSeconds by produceState(
        initialValue = download.elapsedActiveMillis() / 1_000L,
        download.id,
        download.status
    ) {
        while (isActive &&
            (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.MUXING)
        ) {
            value = download.elapsedActiveMillis() / 1_000L
            delay(1_000L)
        }
        value = download.elapsedActiveMillis() / 1_000L
    }
    // savedUri is what actually makes a COMPLETED download playable from here — a status of
    // COMPLETED alone isn't quite enough, since a download finished before Download.savedUri
    // existed (or one whose persisted record predates it — see DownloadStore's own migration
    // handling) legitimately has no local file reference to play, only a status saying it once
    // succeeded.
    val isPlayable = download.status == DownloadStatus.COMPLETED && download.savedUri != null
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
    Row(
        modifier = Modifier.fillMaxWidth()
            .let { if (isPlayable) it.clickable { onPlayLocal(download) } else it }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = download.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            // Neutral while in flight, then color-coded the moment there's an actual outcome to
            // report — error for FAILED, primary for COMPLETED — so scanning a long list for
            // "did that one work?" doesn't require reading every line of text.
            Text(
                text = statusLabel(download, elapsedSeconds),
                style = MaterialTheme.typography.bodySmall,
                color = when (download.status) {
                    DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
                    DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            if (download.status == DownloadStatus.DOWNLOADING) {
                if (download.threadProgress.isNotEmpty()) {
                    // Multi-threaded direct-file download in progress — see DirectFileDownloader.
                    MultiThreadProgressBar(download, modifier = Modifier.padding(top = 4.dp))
                } else if (download.totalBytes > 0) {
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
        when (download.status) {
            DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> IconButton(onClick = { onPause(download) }) {
                Icon(Icons.Rounded.Pause, contentDescription = "Pause download")
            }
            DownloadStatus.PAUSED -> IconButton(onClick = { onResume(download) }) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = "Resume download")
            }
            else -> Unit
        }
        // The row's own onClick above is the real affordance; this is just what makes it visible
        // at a glance — same reasoning as AddressBar's detected-media badge, a primary-tinted
        // icon standing in for "there's something here worth tapping."
        if (isPlayable) {
            Icon(
                Icons.Rounded.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "Download options")
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (download.status == DownloadStatus.QUEUED ||
                    download.status == DownloadStatus.DOWNLOADING ||
                    download.status == DownloadStatus.PAUSED
                ) {
                    DropdownMenuItem(
                        text = { Text("Cancel") },
                        onClick = { menuExpanded = false; onCancel(download) }
                    )
                }
                if (download.status == DownloadStatus.FAILED || download.status == DownloadStatus.CANCELED) {
                    DropdownMenuItem(
                        text = { Text("Re-download") },
                        onClick = { menuExpanded = false; onRetry(download) }
                    )
                }
                if (download.status != DownloadStatus.COMPLETED && download.pageUrl.startsWith("http")) {
                    DropdownMenuItem(
                        text = { Text("Refresh link") },
                        onClick = { menuExpanded = false; onRefreshLink(download) }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { menuExpanded = false; showDeleteDialog = true }
                )
            }
        }
    }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete download?") },
            text = { Text("Remove only Miowser's record, or delete the downloaded file too?") },
            confirmButton = {
                Row {
                    TextButton(onClick = { showDeleteDialog = false; onDelete(download, false) }) {
                        Text("Record only")
                    }
                    TextButton(onClick = { showDeleteDialog = false; onDelete(download, true) }) {
                        Text("File and record")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * One bar, divided into as many chunks as the download has threads, each chunk sized by its
 * share of the file (a thread covering more bytes gets a proportionally wider chunk) and filled
 * independently by that thread's own progress — closer to how IDM/FDM actually render a
 * multi-connection download than N separate bars would be, and it reads at a glance as "one
 * download happening in parallel pieces" rather than "several unrelated downloads." Colors just
 * cycle through the app's own Material You accents — there's no meaning attached to which thread
 * gets which color, only to keeping adjacent chunks visually distinct.
 */
@Composable
private fun MultiThreadProgressBar(download: Download, modifier: Modifier = Modifier) {
    val colors = threadChunkColors()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp)),
        horizontalArrangement = Arrangement.spacedBy(1.5.dp)
    ) {
        download.threadProgress.forEachIndexed { index, chunk ->
            val rangeSize = (chunk.endByte - chunk.startByte + 1).coerceAtLeast(1)
            val fraction = (chunk.downloadedBytes.toFloat() / rangeSize).coerceIn(0f, 1f)
            val color = colors[index % colors.size]
            Box(
                modifier = Modifier
                    .weight(rangeSize.toFloat())
                    .fillMaxHeight()
                    .background(color.copy(alpha = 0.25f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction)
                        .background(color)
                )
            }
        }
    }
}

@Composable
private fun threadChunkColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.error)
}

private fun statusLabel(download: Download, elapsedSeconds: Long): String {
    val timing = download.timeProgressLabel(elapsedSeconds)
    return when (download.status) {
    DownloadStatus.QUEUED -> "Waiting…"
    DownloadStatus.DOWNLOADING -> {
        if (download.kind == DownloadKind.HLS && download.totalBytes > 0) {
            buildString {
                append("Downloading segment ${download.bytesDownloaded}/${download.totalBytes}")
                timing?.let { append(" • $it") }
            }
        } else if (download.totalBytes > 0) {
            val percent = download.bytesDownloaded * 100 / download.totalBytes
            buildString {
                append("Downloading… $percent%")
                timing?.let { append(" • $it") }
            }
        } else {
            "Downloading…" + (timing?.let { " • $it" } ?: "")
        }
    }
    DownloadStatus.PAUSED -> {
        val percent = if (download.totalBytes > 0) download.bytesDownloaded * 100 / download.totalBytes else null
        buildString {
            append(if (percent != null) "Paused • $percent%" else "Paused")
            timing?.let { append(" • $it") }
        }
    }
    DownloadStatus.MUXING -> "Combining into final file…" + (timing?.let { " • $it" } ?: "")
    DownloadStatus.COMPLETED -> "Done — saved to Downloads/Miowser" + (timing?.let { " • $it" } ?: "")
    DownloadStatus.FAILED -> "Failed" + (download.errorMessage?.let { ": $it" } ?: "") +
        (timing?.let { " • $it" } ?: "")
    DownloadStatus.CANCELED -> "Canceled" + (timing?.let { " • $it" } ?: "")
    }
}
