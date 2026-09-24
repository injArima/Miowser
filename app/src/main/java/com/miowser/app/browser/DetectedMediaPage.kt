package com.miowser.app.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.DiscoveryProgress
import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.meetsMinimumDuration
import com.miowser.app.downloader.rendererResourceKind
import com.miowser.app.ui.SimplePageScaffold
import com.miowser.app.ui.ExpressiveEmptyState
import kotlin.math.roundToInt

/**
 * Listing of what the grabber has found on the current page. Tapping a row streams it directly in
 * the rich player (PlayerScreen) — no separate "Play" affordance needed. A copy icon and a
 * download icon sit trailing on every fetchable row instead, unlabeled: this is a short, familiar
 * enough pair of actions (copy/download) not to need text spelling them out, matching how the
 * icon-only badges elsewhere in the app already work. Only unconfirmed entries (e.g. a blob: URL,
 * which has no independently fetchable source) get neither the tap-to-play nor the download icon
 * — just copy; see [isFetchable].
 *
 * Reachable two ways: the page-contextual badge beside AddressBar's reload button (only shown
 * once something's actually been found), and the overflow menu's always-present Media tile —
 * both land here, on whatever the active tab has detected.
 */
@Composable
fun DetectedMediaPage(
    items: List<DetectedMedia>,
    onCopyUrl: (String) -> Unit,
    onPlay: (DetectedMedia) -> Unit,
    onDownload: (DetectedMedia) -> Unit,
    scanProgress: DiscoveryProgress?,
    onScan: (depth: Int, pageLimit: Int) -> Unit,
    onCancelScan: () -> Unit,
    minimumDurationSeconds: Int,
    onBack: () -> Unit
) {
    SimplePageScaffold(title = "Detected media", onBack = onBack) { modifier ->
        var depth by remember { mutableIntStateOf(1) }
        var pageLimit by remember { mutableIntStateOf(8) }
        val visibleItems = items.filter { it.meetsMinimumDuration(minimumDurationSeconds) }
        val hiddenCount = items.size - visibleItems.size
        Column(modifier = modifier) {
            if (hiddenCount > 0) Text(
                "$hiddenCount shorter or unknown-length item${if (hiddenCount == 1) "" else "s"} hidden by Browser Settings",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Scan this site's HTML for media links", modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                listOf(0, 1, 2).forEach { value ->
                    FilterChip(selected = depth == value, onClick = { depth = value }, label = { Text("Depth $value") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp)) {
                listOf(4, 8, 16).forEach { value ->
                    FilterChip(selected = pageLimit == value, onClick = { pageLimit = value }, label = { Text("$value pages") })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                Button(onClick = { onScan(depth, pageLimit) }, enabled = scanProgress?.complete != false) {
                    Text("Scan site")
                }
                if (scanProgress?.complete == false) {
                    TextButton(onClick = onCancelScan) { Text("Stop") }
                }
            }
            if (scanProgress != null) {
                Text(
                    if (scanProgress.complete) "Scanned ${scanProgress.pagesScanned} pages. Media checks may still finish."
                    else "Scanning ${scanProgress.pagesScanned}/${scanProgress.pageLimit} pages…",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            if (visibleItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    ExpressiveEmptyState(
                        title = "No media to show",
                        description = if (items.isEmpty()) "Nothing detected on this page yet."
                        else "No detected media meets the selected minimum length.",
                        icon = Icons.Rounded.VideoLibrary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                items(visibleItems, key = { it.url }) { item ->
                    DetectedMediaRow(item, onCopyUrl, onPlay, onDownload)
                }
                }
            }
        }
    }
}

/** A standalone subtitle can be downloaded/copied, but it is not primary player media. */
private fun isPlayable(item: DetectedMedia): Boolean = item.isConfirmed && item.type != MediaType.SUBTITLE

@Composable
private fun DetectedMediaRow(
    item: DetectedMedia,
    onCopyUrl: (String) -> Unit,
    onPlay: (DetectedMedia) -> Unit,
    onDownload: (DetectedMedia) -> Unit
) {
    val isPlayable = isPlayable(item)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (isPlayable) it.clickable { onPlay(item) } else it }
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
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
                    // Tertiary, not the neutral onSurfaceVariant every other bit of metadata on
                    // this row uses — encryption is the one attribute here worth a second glance.
                    Icon(
                        imageVector = Icons.Rounded.Lock,
                        contentDescription = "Encrypted",
                        modifier = Modifier.padding(start = 4.dp).size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary
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
                item.durationSeconds?.let { formatDuration(it) }
                    ?: if (item.type != MediaType.SUBTITLE) "Length unavailable" else null,
                item.bitrateBitsPerSecond?.let { formatBitrate(it) },
                item.variantCount?.let { "$it quality variant${if (it == 1) "" else "s"}" }
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(text = details, style = MaterialTheme.typography.bodySmall)
            }
        }
        IconButton(onClick = { onCopyUrl(item.url) }) {
            Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy link")
        }
        if (item.isConfirmed || rendererResourceKind(item.url) != null) {
            IconButton(onClick = { onDownload(item) }) {
                Icon(Icons.Rounded.Download, contentDescription = "Download")
            }
        }
    }
    }
}

/**
 * The closest thing to a "video name" this layer can offer: the page's own title at detection
 * time, when it's a real one — a fresh tab's placeholder title ("New Tab") or a blank one aren't
 * useful, so those fall back to the URL's own filename instead (e.g. "x36xhzz.m3u8").
 */
private fun displayName(item: DetectedMedia): String {
    item.responseFileName?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
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

private fun formatBitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000L -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
    bitsPerSecond >= 1_000L -> "%.0f Kbps".format(bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond bps"
}
