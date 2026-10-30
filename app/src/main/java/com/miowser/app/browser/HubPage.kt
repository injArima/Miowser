package com.miowser.app.browser
import androidx.compose.ui.platform.testTag

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadKind
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.downloader.timeProgressLabel
import com.miowser.app.ui.SimplePageScaffold
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.MiowserMenuDivider
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.MiowserDropdownMenu
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.MiowserConfirmationDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
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
    emptyMessage: String = "No downloads yet.",
    bottomContentPadding: Dp = 0.dp,
    uiVisible: Boolean = true
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
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = modifier.testTag("downloads_list"),
            contentPadding = PaddingValues(bottom = bottomContentPadding)
        ) {
            items(downloads, key = { it.id }) { download ->
                val visible by remember(listState, download.id) {
                    derivedStateOf { listState.layoutInfo.visibleItemsInfo.any { it.key == download.id } }
                }
                LibraryRow(
                    download,
                    onPause,
                    onResume,
                    onCancel,
                    onRetry,
                    onDelete,
                    onRefreshLink,
                    onPlayLocal,
                    visible && uiVisible
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
    onPlayLocal: (Download) -> Unit,
    visible: Boolean
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
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
            DownloadStatusText(download, visible)
            if (download.status != DownloadStatus.QUEUED && !download.recoveryChecking) {
                DownloadTimingText(download, visible)
            }
            if (download.status == DownloadStatus.DOWNLOADING) {
                if (download.threadProgress.isNotEmpty()) {
                    // Multi-threaded direct-file download in progress — see DirectFileDownloader.
                    MultiThreadProgressBar(download, visible, modifier = Modifier.padding(top = 4.dp))
                } else if (download.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { if (visible) (download.bytesDownloaded.toFloat() / download.totalBytes).coerceIn(0f, 1f) else 0f },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    )
                } else {
                    if (visible) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                    else LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            } else if (download.status == DownloadStatus.MUXING || download.status == DownloadStatus.QUEUED) {
                if (visible) LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                else LinearProgressIndicator(progress = { 0f }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
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
            MiowserDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                if (download.status == DownloadStatus.QUEUED ||
                    download.status == DownloadStatus.DOWNLOADING ||
                    download.status == DownloadStatus.PAUSED
                ) {
                    MiowserMenuItem(
                        text = { Text("Cancel") },
                        leadingIcon = { Icon(Icons.Rounded.Close, contentDescription = null) },
                        onClick = { menuExpanded = false; onCancel(download) }
                    )
                }
                if (!download.recoveryChecking && (download.status == DownloadStatus.FAILED || download.status == DownloadStatus.CANCELED)) {
                    MiowserMenuItem(
                        text = { Text("Re-download") },
                        leadingIcon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                        onClick = { menuExpanded = false; onRetry(download) }
                    )
                }
                if (download.status != DownloadStatus.COMPLETED && download.pageUrl.startsWith("http")) {
                    MiowserMenuItem(
                        text = { Text("Refresh link") },
                        leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null) },
                        onClick = { menuExpanded = false; onRefreshLink(download) }
                    )
                }
                MiowserMenuDivider()
                MiowserMenuItem(
                    text = { Text("Delete") },
                    leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                    onClick = { menuExpanded = false; showDeleteDialog = true }
                )
            }
        }
    }
    }

    if (showDeleteDialog) {
        MiowserConfirmationDialog(
            title = "Delete download?",
            message = "Remove only Miowser's record, or delete the downloaded file too?",
            onDismiss = { showDeleteDialog = false },
            actions = listOf(
                ConfirmationAction("Cancel", { showDeleteDialog = false }, ConfirmationTone.CAUTION),
                ConfirmationAction("Record only", { showDeleteDialog = false; onDelete(download, false) }, ConfirmationTone.CAUTION),
                ConfirmationAction("File and record", { showDeleteDialog = false; onDelete(download, true) }, ConfirmationTone.CAUTION)
            )
        )
    }
}

/** Byte publications invalidate this text, rather than the row title, actions or list. */
@Composable
private fun DownloadStatusText(download: Download, visible: Boolean) {
    Text(
        text = statusLabel(download, includeProgress = visible), style = MaterialTheme.typography.bodySmall,
        maxLines = if (download.status == DownloadStatus.FAILED) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        color = if (download.recoveryChecking) MaterialTheme.colorScheme.onSurfaceVariant else when (download.status) {
            DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
            DownloadStatus.COMPLETED -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
}

@Composable
private fun DownloadTimingText(download: Download, visible: Boolean) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val status = download.status
    val elapsedSeconds by produceState(download.elapsedActiveMillis() / 1_000L,
        download.id, status, visible, lifecycle) {
        value = download.elapsedActiveMillis() / 1_000L
        if (visible && (status == DownloadStatus.DOWNLOADING || status == DownloadStatus.MUXING)) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    value = download.elapsedActiveMillis() / 1_000L
                    delay(1_000L)
                }
            }
        }
    }
    Text(text = if (visible) download.timeProgressLabel(elapsedSeconds).orEmpty()
        else com.miowser.app.downloader.formatSpentTime(elapsedSeconds),
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
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
private fun MultiThreadProgressBar(download: Download, visible: Boolean, modifier: Modifier = Modifier) {
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
            val color = colors[index % colors.size]
            Box(
                modifier = Modifier
                    .weight(rangeSize.toFloat())
                    .fillMaxHeight()
                    .background(color.copy(alpha = 0.25f))
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    // Progress changes redraw this chunk without recomposing or measuring the row.
                    val fraction = if (visible) (chunk.publishedBytes.toFloat() / rangeSize).coerceIn(0f, 1f)
                        else 0f
                    drawRect(color, size = Size(size.width * fraction, size.height))
                }
            }
        }
    }
}

@Composable
private fun threadChunkColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(scheme.primary, scheme.tertiary, scheme.secondary, scheme.error)
}

private fun statusLabel(download: Download, includeProgress: Boolean): String {
    if (download.recoveryChecking) return "Checking interrupted download…"
    if (!includeProgress) {
        when (download.status) {
            DownloadStatus.DOWNLOADING -> return "Downloading..."
            DownloadStatus.PAUSED -> return "Paused"
            else -> Unit
        }
    }
    return when (download.status) {
    DownloadStatus.QUEUED -> "Waiting…"
    DownloadStatus.DOWNLOADING -> {
        if (download.kind == DownloadKind.HLS && download.totalBytes > 0) {
            "Downloading segment ${download.bytesDownloaded}/${download.totalBytes}"
        } else if (download.totalBytes > 0) {
            val percent = download.bytesDownloaded * 100 / download.totalBytes
            "Downloading… $percent%"
        } else {
            "Downloading…"
        }
    }
    DownloadStatus.PAUSED -> {
        val percent = if (download.totalBytes > 0) download.bytesDownloaded * 100 / download.totalBytes else null
        if (percent != null) "Paused • $percent%" else "Paused"
    }
    DownloadStatus.MUXING -> "Combining into final file…"
    DownloadStatus.COMPLETED -> if (!download.recordSaved) "Saving download record…"
        else "Done"
    DownloadStatus.FAILED -> "Failed" + (download.errorMessage?.let { ": $it" } ?: "")
    DownloadStatus.CANCELED -> "Canceled"
    }
}
