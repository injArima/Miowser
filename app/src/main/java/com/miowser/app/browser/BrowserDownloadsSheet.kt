package com.miowser.app.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.dp
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape

/** Browser-side view of work that is still in progress or needs attention. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserDownloadsSheet(
    downloads: List<Download>,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onCancel: (Download) -> Unit,
    onRetry: (Download) -> Unit,
    onDelete: (Download, Boolean) -> Unit,
    onRefreshLink: (Download) -> Unit,
    onOpenHubDownloads: () -> Unit,
    onDismiss: () -> Unit,
    uiVisible: Boolean = true
) {
    val visible = downloads.filter { download ->
        download.status == DownloadStatus.QUEUED ||
            download.status == DownloadStatus.DOWNLOADING ||
            download.status == DownloadStatus.PAUSED ||
            download.status == DownloadStatus.MUXING ||
            download.status == DownloadStatus.FAILED
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MiowserBottomSheetShape
    ) {
        BoxWithConstraints {
            val maxListHeight = maxHeight * 0.58f
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Downloads", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onOpenHubDownloads) {
                    Text("All downloads")
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                }
            }
            HubDownloadsContent(
                downloads = visible,
                uiVisible = uiVisible,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onRetry = onRetry,
                onDelete = onDelete,
                onRefreshLink = onRefreshLink,
                onPlayLocal = {},
                modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp, max = maxListHeight),
                emptyMessage = "No active or failed downloads. Open all downloads to see completed files."
            )
        }
    }
    }
}
