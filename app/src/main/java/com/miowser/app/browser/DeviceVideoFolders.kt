package com.miowser.app.browser

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.device.*
import com.miowser.app.ui.theme.HubLibraryTokens
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
internal fun DeviceVideoFolders(
    status: DeviceCatalogStatus,
    source: String?,
    listMode: Boolean,
    cache: HubLibraryCache,
    modifier: Modifier,
    emptyState: @Composable (String) -> Unit,
    onOpen: (DeviceVideoFolder) -> Unit
) {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val grid = rememberLazyGridState()
    val query = remember(status.visible, source) { DeviceBrowseQuery(status.visible, source, "", false) }
    val current = rememberDeviceCatalogWindow(query, status.revision, grid, cache.deviceFolders, DeviceVideoCatalog::readFolders)
    if (current == null || (current.count == 0 && status.scanning)) {
        Spacer(modifier.fillMaxWidth())
        return
    }
    if (current.count == 0) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            emptyState("No video folders")
        }
        return
    }
    LazyVerticalGrid(
        columns = if (listMode) GridCells.Fixed(1) else GridCells.Adaptive(HubLibraryTokens.gridMinCell),
        state = grid,
        modifier = modifier.fillMaxWidth().testTag("device_video_folders"),
        contentPadding = PaddingValues(bottom = HubLibraryTokens.gridBottomClearance),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.m)
    ) {
        items(current.count, key = { index ->
            current[index]?.let { "${it.sourceId.length}:${it.sourceId}${it.folder}" } ?: "pending:$index"
        }) { index ->
            val folder = current[index]
            if (folder == null) LibraryCardPlaceholder(listMode)
            else DeviceVideoFolderCard(folder, status.sources.firstOrNull { it.id == folder.sourceId }?.label,
                status.revision, cache, grid.isScrollInProgress, foreground, listMode) { onOpen(folder) }
        }
    }
}

@Composable
private fun DeviceVideoFolderCard(
    folder: DeviceVideoFolder,
    sourceLabel: String?,
    revision: Long,
    cache: HubLibraryCache,
    scrolling: Boolean,
    foreground: Boolean,
    listMode: Boolean,
    onOpen: () -> Unit
) {
    val app = LocalContext.current.applicationContext
    val coverKey = remember(folder.sourceId, folder.folder, revision) {
        HubLibraryCache.FolderCoverKey(folder.sourceId, folder.folder, revision)
    }
    var covers by remember(coverKey) {
        mutableStateOf(cache.folderCovers.get(coverKey).orEmpty().mapNotNull {
            LibraryThumbnailStore.peekDeviceFrame(it, compact = true)
        })
    }
    var resolved by remember(coverKey) { mutableStateOf(false) }
    LaunchedEffect(coverKey, scrolling, foreground) {
        if (!foreground) {
            covers = emptyList()
            resolved = false
            return@LaunchedEffect
        }
        if (scrolling || resolved) return@LaunchedEffect
        val cached = cache.folderCovers.get(coverKey)
        if (cached != null && covers.size == cached.size) return@LaunchedEffect
        delay(220)
        val candidates = try {
            cached ?: withContext(Dispatchers.IO) { DeviceVideoCatalog.readFolderCover(app, folder) }
                .also { cache.folderCovers.put(coverKey, it) }
        }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { emptyList() }
        covers = candidates.mapNotNull {
            LibraryThumbnailStore.loadDeviceFrame(app, it, visible = false, compact = true, cacheOnly = true)
        }.ifEmpty {
            listOfNotNull(candidates.firstOrNull()?.let {
                LibraryThumbnailStore.loadDeviceFrame(app, it, compact = true)
            })
        }
        resolved = covers.isNotEmpty()
    }
    val name = folder.folder.substringAfterLast('/').ifBlank { sourceLabel ?: "Storage root" }
    val cover: @Composable () -> Unit = {
        Box(
            (if (listMode) Modifier.width(136.dp).height(84.dp) else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            contentAlignment = Alignment.Center
        ) {
            if (covers.isEmpty()) Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.primary)
            else if (covers.size == 1) {
                Image(covers.first().asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
                    covers.chunked(2).forEach { row ->
                        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(UiSpacing.xxs)) {
                            row.forEach { bitmap ->
                                Image(bitmap.asImageBitmap(), null, Modifier.weight(1f).fillMaxHeight(), contentScale = ContentScale.Crop)
                            }
                        }
                    }
                }
            }
        }
    }
    val details: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = UiSpacing.m, vertical = UiSpacing.compact)) {
            Text(name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val count = folder.videoCount
            Text("$count ${if (count == 1) "video" else "videos"}", style = MaterialTheme.typography.bodySmall)
            val parent = folder.folder.substringBeforeLast('/', missingDelimiterValue = "")
            if (parent.isNotEmpty() || (sourceLabel != null && sourceLabel != name)) {
                Text(parent.ifEmpty { sourceLabel.orEmpty() }, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        if (listMode) Row(verticalAlignment = Alignment.CenterVertically) { cover(); details() }
        else Column { cover(); details() }
    }
}
