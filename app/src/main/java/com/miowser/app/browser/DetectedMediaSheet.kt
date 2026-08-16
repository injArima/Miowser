package com.miowser.app.browser

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import kotlin.math.roundToInt

/**
 * Read-only listing of what the grabber has found on the current page — detection only for
 * now, per scope; no download/play actions yet, just a copy-URL affordance since that's useful
 * without pulling in anything beyond what this screen already needs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedMediaSheet(
    items: List<DetectedMedia>,
    onCopyUrl: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (items.isEmpty()) {
            Text(
                text = "Nothing detected on this page yet.",
                modifier = Modifier.padding(24.dp)
            )
        } else {
            LazyColumn {
                items(items, key = { it.url }) { item ->
                    DetectedMediaRow(item, onCopyUrl)
                }
            }
        }
    }
}

@Composable
private fun DetectedMediaRow(item: DetectedMedia, onCopyUrl: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName(item),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.type.label(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!item.isConfirmed) {
                    Text(
                        text = "  (unverified)",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (item.isEncrypted) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Encrypted",
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = item.url,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val details = listOfNotNull(
                item.resolution,
                // "~" mirrors 1DM's own convention for this exact kind of number — a real sampled
                // segment extrapolated by duration, not a server-verified total (see MediaProbe).
                item.sizeBytes?.let { (if (item.isSizeApproximate) "~" else "") + formatBytes(it) },
                item.durationSeconds?.let { formatDuration(it) },
                item.variantCount?.let { "$it quality variant${if (it == 1) "" else "s"}" }
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
        }
        TextButton(onClick = { onCopyUrl(item.url) }) {
            Text("Copy")
        }
    }
}

/**
 * The closest thing to a "video name" this layer can offer: the page's own title at detection
 * time, when it's a real one — a fresh tab's placeholder title ("New Tab") or a blank one aren't
 * useful, so those fall back to the URL's own filename instead (e.g. "x36xhzz.m3u8").
 */
private fun displayName(item: DetectedMedia): String {
    val title = item.pageTitle?.trim()
    if (!title.isNullOrEmpty() && title != "New Tab") return title
    val filename = item.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    return filename.ifBlank { "Untitled video" }
}

private fun MediaType.label(): String = when (this) {
    MediaType.HLS -> "HLS"
    MediaType.DASH -> "DASH"
    MediaType.MP4 -> "MP4"
    MediaType.WEBM -> "WebM"
    MediaType.MATROSKA -> "MKV"
    MediaType.AUDIO -> "Audio"
    MediaType.SUBTITLE -> "Subtitle"
    MediaType.UNKNOWN -> "Video"
}

private fun formatBytes(bytes: Long): String {
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    val rounded = (value * 10).roundToInt() / 10.0
    return "$rounded ${units[unitIndex]}"
}

private fun formatDuration(totalSeconds: Double): String {
    val total = totalSeconds.roundToInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
