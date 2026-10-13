package com.miowser.app.browser

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.downloader.RendererResourceKind
import com.miowser.app.downloader.rendererResourceKind
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.DiscoveryProgress
import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.meetsMinimumDuration
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.SimplePageScaffold
import kotlinx.coroutines.CancellationException
import kotlin.math.roundToInt

/** Browser media list, grouped by file kind. Scan configuration is kept off the results surface. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedMediaPage(
    items: List<DetectedMedia>,
    userAgent: String,
    onCopyUrl: (String) -> Unit,
    onPlay: (DetectedMedia) -> Unit,
    onAddToLibrary: (DetectedMedia) -> Unit,
    onDownload: (DetectedMedia) -> Unit,
    savedMediaUrls: Set<String>,
    canSaveToLibrary: Boolean,
    scanProgress: DiscoveryProgress?,
    onScan: (depth: Int, pageLimit: Int) -> Unit,
    onCancelScan: () -> Unit,
    minimumDurationSeconds: Int,
    onBack: () -> Unit
) {
    var depth by remember { mutableIntStateOf(1) }
    var pageLimit by remember { mutableIntStateOf(8) }
    var showScanSettings by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<DetectorCategory?>(null) }
    var selectedSubtitle by remember { mutableStateOf<DetectedMedia?>(null) }

    val visibleItems = items.filter { it.meetsMinimumDuration(minimumDurationSeconds) }
    val groups = detectorGroups(visibleItems)
    val activeCategory = selectedCategory?.takeIf { groups.containsKey(it) }
    val hiddenCount = items.size - visibleItems.size

    SimplePageScaffold(
        title = "Detected media",
        onBack = onBack,
        actions = {
            IconButton(onClick = { showScanSettings = true }) {
                Icon(Icons.Rounded.Construction, contentDescription = "Scan settings")
            }
        }
    ) { modifier ->
        Column(modifier = modifier) {
            if (hiddenCount > 0) {
                Text(
                    "$hiddenCount shorter or unknown-length item${if (hiddenCount == 1) "" else "s"} hidden by Browser Settings",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (groups.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetectorFilterPill("All", visibleItems.size, activeCategory == null) {
                        selectedCategory = null
                    }
                    groups.forEach { (category, entries) ->
                        DetectorFilterPill(category.label, entries.size, activeCategory == category) {
                            selectedCategory = category
                        }
                    }
                }
            }
            scanProgress?.let { progress ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (progress.complete) "Scanned ${progress.pagesScanned} pages · Media checks may still finish"
                        else "Scanning ${progress.pagesScanned}/${progress.pageLimit} pages…",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!progress.complete) TextButton(onClick = onCancelScan) { Text("Stop") }
                }
                if (!progress.complete) LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                )
            }
            if (groups.isEmpty()) {
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
                    groups.forEach { (category, entries) ->
                        if (activeCategory == null || activeCategory == category) {
                            item(key = "heading-${category.name}") {
                                DetectorGroupHeading(category, entries.size)
                            }
                            items(entries, key = { it.url }) { item ->
                                DetectedMediaRow(
                                    item = item,
                                    onCopyUrl = onCopyUrl,
                                    onOpen = {
                                        if (item.type == MediaType.SUBTITLE) selectedSubtitle = item
                                        else onPlay(item)
                                    },
                                    onDownload = onDownload,
                                    onAddToLibrary = onAddToLibrary,
                                    isSaved = item.url in savedMediaUrls,
                                    canSaveToLibrary = canSaveToLibrary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScanSettings) {
        ModalBottomSheet(onDismissRequest = { showScanSettings = false },
            dragHandle = { com.miowser.app.ui.CompactSheetDragHandle() },
            shape = com.miowser.app.ui.MiowserBottomSheetShape) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Scan site", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Search this site's HTML for media links.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("Link depth", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(0, 1, 2).forEach { value ->
                        FilterChip(selected = depth == value, onClick = { depth = value },
                            label = { Text("Depth $value") })
                    }
                }
                Text("Page limit", style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(4, 8, 16).forEach { value ->
                        FilterChip(selected = pageLimit == value, onClick = { pageLimit = value },
                            label = { Text("$value pages") })
                    }
                }
                Button(
                    onClick = {
                        showScanSettings = false
                        onScan(depth, pageLimit)
                    },
                    enabled = scanProgress?.complete != false
                ) { Text("Scan site") }
                if (scanProgress?.complete == false) {
                    TextButton(onClick = { onCancelScan(); showScanSettings = false }) { Text("Stop scan") }
                }
            }
        }
    }
    selectedSubtitle?.let { subtitle ->
        SubtitleViewer(subtitle, userAgent, onDismiss = { selectedSubtitle = null })
    }
}

@Composable
private fun DetectorFilterPill(label: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (selected) colors.secondaryContainer else colors.surfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) colors.secondary else colors.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Surface(
                shape = CircleShape,
                color = if (selected) colors.secondary else colors.surfaceContainerHighest
            ) {
                Text(
                    count.toString(),
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (selected) colors.onSecondary else colors.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun DetectorGroupHeading(category: DetectorCategory, count: Int) {
    val icon: ImageVector = when (category) {
        DetectorCategory.VIDEOS -> Icons.Rounded.VideoLibrary
        DetectorCategory.AUDIO -> Icons.Rounded.MusicNote
        DetectorCategory.SUBTITLES -> Icons.Rounded.Subtitles
        DetectorCategory.OTHER -> Icons.Rounded.InsertDriveFile
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, top = 17.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary)
        Text("${category.heading} ($count)", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun isPlayable(item: DetectedMedia): Boolean = item.type != MediaType.SUBTITLE &&
    (item.isConfirmed || rendererResourceKind(item.url) == RendererResourceKind.BLOB)

@Composable
private fun DetectedMediaRow(
    item: DetectedMedia,
    onCopyUrl: (String) -> Unit,
    onOpen: () -> Unit,
    onDownload: (DetectedMedia) -> Unit,
    onAddToLibrary: (DetectedMedia) -> Unit,
    isSaved: Boolean,
    canSaveToLibrary: Boolean
) {
    if (item.type == MediaType.SUBTITLE || item.mimeType?.startsWith("text/", ignoreCase = true) == true) {
        TextualMediaRow(item, onCopyUrl, onOpen, onDownload)
        return
    }

    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 3.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clip(shape)
                .clickable(enabled = isPlayable(item), onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(shape = RoundedCornerShape(7.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(item.type.label(), modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                item.resolution?.let { resolution ->
                    Text(resolution, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 1)
                }
                if (item.isEncrypted) Icon(Icons.Rounded.Lock, contentDescription = "Encrypted",
                    modifier = Modifier.size(15.dp), tint = MaterialTheme.colorScheme.tertiary)
                Spacer(Modifier.weight(1f))
                item.durationSeconds?.let { duration ->
                    Text(formatDuration(duration), modifier = Modifier.widthIn(min = 50.dp),
                        style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.End)
                }
            }
            Text(displayName(item), style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val details = listOfNotNull(
                    item.sizeBytes?.let { (if (item.isSizeApproximate) "~" else "") + formatBytes(it) },
                    item.bitrateBitsPerSecond?.let(::formatBitrate),
                    item.variantCount?.let { "$it qualities" },
                    if (!item.isConfirmed) "Unverified" else null
                ).joinToString(" · ")
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.url, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (details.isNotEmpty()) Text(details,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { onCopyUrl(item.url) }, modifier = Modifier.size(38.dp)) {
                    Icon(Icons.Rounded.ContentCopy, contentDescription = "Copy link",
                        modifier = Modifier.size(18.dp))
                }
                if (detectorCategory(item) == DetectorCategory.VIDEOS) {
                    IconButton(
                        onClick = { onAddToLibrary(item) },
                        enabled = canSaveToLibrary && !isSaved &&
                            rendererResourceKind(item.url) != RendererResourceKind.DATA,
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd,
                            contentDescription = when {
                                isSaved -> "Already in library"
                                !canSaveToLibrary -> "Library unavailable in private tab"
                                rendererResourceKind(item.url) == RendererResourceKind.DATA ->
                                    "Data URL cannot be added to library"
                                else -> "Add to library"
                            }, modifier = Modifier.size(20.dp))
                    }
                }
                if (item.isConfirmed || rendererResourceKind(item.url) != null) {
                    IconButton(onClick = { onDownload(item) }, modifier = Modifier.size(38.dp)) {
                        Icon(Icons.Rounded.Download, contentDescription = "Download",
                            modifier = Modifier.size(19.dp))
                    }
                }
            }
        }
    }
}

/** Keep text/subtitle files in the simple row they used before the detector card redesign. */
@Composable
private fun TextualMediaRow(
    item: DetectedMedia,
    onCopyUrl: (String) -> Unit,
    onOpen: () -> Unit,
    onDownload: (DetectedMedia) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable(enabled = item.type == MediaType.SUBTITLE, onClick = onOpen)
                .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName(item), fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(item.type.label() + if (!item.isConfirmed) " · Unverified" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(item.url, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                item.sizeBytes?.let { size ->
                    Text((if (item.isSizeApproximate) "~" else "") + formatBytes(size),
                        style = MaterialTheme.typography.bodySmall)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SubtitleViewer(item: DetectedMedia, userAgent: String, onDismiss: () -> Unit) {
    var preview by remember(item.url) { mutableStateOf<SubtitlePreview?>(null) }
    var error by remember(item.url) { mutableStateOf<String?>(null) }
    LaunchedEffect(item.url, userAgent) {
        try {
            preview = loadSubtitlePreview(item, userAgent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            error = failure.message ?: "Could not load this subtitle."
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss,
        dragHandle = { com.miowser.app.ui.CompactSheetDragHandle() },
        shape = com.miowser.app.ui.MiowserBottomSheetShape) {
        BoxWithConstraints {
        val previewHeight = maxHeight * 0.58f
        Column(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(displayName(item), style = MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Subtitle preview", style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            when {
                error != null -> Text(error!!, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error)
                preview == null -> Box(Modifier.fillMaxWidth().heightIn(min = 72.dp),
                    contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> {
                    if (preview!!.truncated) Text("Showing the first 512 KB of this subtitle.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val lines = remember(preview) { preview!!.text.lines() }
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth()
                            .heightIn(max = previewHeight)
                            .clip(RoundedCornerShape(14.dp)),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        itemsIndexed(lines) { _, line ->
                            Text(line.ifEmpty { " " },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 1.dp),
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }
    }
    }
}

private fun displayName(item: DetectedMedia): String {
    item.responseFileName?.trim()?.takeIf(String::isNotEmpty)?.let { return it }
    val filename = item.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
    if (item.type == MediaType.SUBTITLE && filename.isNotBlank()) return filename
    val title = item.pageTitle?.trim()
    if (!title.isNullOrEmpty() && title != "New Tab") return title
    return filename.ifBlank { "Untitled media" }
}

private fun MediaType.label(): String = when (this) {
    MediaType.HLS -> "HLS"
    MediaType.DASH -> "DASH"
    MediaType.MP4 -> "MP4"
    MediaType.WEBM -> "WebM"
    MediaType.MATROSKA -> "MKV"
    MediaType.AUDIO -> "Audio"
    MediaType.SUBTITLE -> "Subtitle"
    MediaType.UNKNOWN -> "Media"
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
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
}

private fun formatBitrate(bitsPerSecond: Long): String = when {
    bitsPerSecond >= 1_000_000L -> "%.1f Mbps".format(bitsPerSecond / 1_000_000.0)
    bitsPerSecond >= 1_000L -> "%.0f Kbps".format(bitsPerSecond / 1_000.0)
    else -> "$bitsPerSecond bps"
}
