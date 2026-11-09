package com.miowser.app.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.LibraryCardId
import com.miowser.app.library.device.*
import com.miowser.app.library.videoQualityLabel
import com.miowser.app.ui.AppButtonStyle
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.MiowserActionButton
import com.miowser.app.ui.MiowserConfirmationDialog
import com.miowser.app.ui.MiowserLoadingIndicator
import com.miowser.app.ui.theme.HubLibraryTokens
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@Composable
internal fun DeviceVideosPage(
    imported: List<String>,
    downloads: List<DeviceDownload>,
    onBrowse: () -> Unit,
    onPlay: (DeviceVideo) -> Unit,
    onMessage: (String) -> Unit,
    onIdleChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf(DeviceCatalogStatus(access = deviceMediaAccess(app))) }
    var loadFailed by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var titleOrder by rememberSaveable { mutableStateOf(false) }
    var listMode by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var removeLink by remember { mutableStateOf<DeviceSource?>(null) }
    val requestAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val access = deviceMediaAccess(app)
        status = status.copy(access = access,
            visible = status.visible.filterNot { it.startsWith("media:") }.toSet())
        refresh++
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    DeviceVideoCatalog.link(app, uri)
                }
                source = uri.toString()
                refresh++
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onMessage("Couldn’t link this folder. Choose it again.") }
        }
    }
    LaunchedEffect(lifecycle, refresh, imported, downloads) {
        var forced = refresh > 0
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val access = deviceMediaAccess(app)
            if (access != DeviceMediaAccess.FULL) {
                status = status.copy(access = access,
                    visible = status.visible.filterNot { it.startsWith("media:") }.toSet())
            }
            DeviceVideoCatalog.changes(app).debounce(250).onStart { emit(Unit) }.collectLatest {
                try {
                    loadFailed = false
                    DeviceVideoCatalog.scan(app, imported, downloads, forced).collect { status = it }
                    forced = false
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) {
                    loadFailed = true
                    status = status.copy(scanning = false, visible = emptySet())
                }
            }
        }
    }
    val reportIdle by rememberUpdatedState(onIdleChanged)
    DisposableEffect(Unit) { onDispose { reportIdle(false) } }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        withFrameNanos { }
        reportIdle(true)
    }
    LaunchedEffect(status.sources, status.scanning) {
        if (!status.scanning && source != null && status.sources.none { it.id == source }) source = null
    }

    Column(Modifier.fillMaxSize().navigationBarsPadding().padding(horizontal = UiSpacing.m)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Device Videos", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = { refresh++ }) { Icon(Icons.Rounded.Refresh, "Refresh device videos") }
            IconButton(onClick = onBrowse) { Icon(Icons.Rounded.Language, "Browse") }
        }
        OutlinedTextField(
            value = search, onValueChange = { search = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("device_video_search"),
            placeholder = { Text("Search videos and folders") }
        )
        Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
            Box {
                FilterChip(selected = source != null, onClick = { menu = true }, label = {
                    Text(status.sources.firstOrNull { it.id == source }?.label ?: "All sources",
                        maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                }, modifier = Modifier.widthIn(max = HubLibraryTokens.gridMinCell))
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("All sources") }, onClick = { source = null; menu = false })
                    status.sources.forEach { item ->
                        DropdownMenuItem(text = {
                            Text(item.label + if (item.id in status.unavailable) " · Unavailable" else "")
                        }, onClick = { source = item.id; menu = false })
                    }
                }
            }
            FilterChip(selected = titleOrder, onClick = { titleOrder = !titleOrder },
                label = { Text(if (titleOrder) "Title" else "Recently modified") })
            FilterChip(selected = listMode, onClick = { listMode = !listMode },
                label = { Text(if (listMode) "List" else "Grid") })
        }
        if (status.access != DeviceMediaAccess.FULL || source?.let { id -> status.sources.any { it.id == id && it.kind == DEVICE_LINK } } == true) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                if (status.access != DeviceMediaAccess.FULL) {
                    MiowserActionButton(style = AppButtonStyle.TONAL, onClick = { requestAccess.launch(deviceVideoPermissions()) }) {
                        Text(if (status.access == DeviceMediaAccess.SELECTED) "Change video access" else "Allow video access")
                    }
                }
                MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { pickFolder.launch(source?.takeIf { it in status.unavailable }?.let(Uri::parse)) }) {
                    Text("Link folder")
                }
            }
        } else {
            MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { pickFolder.launch(null) }) { Text("Link folder") }
        }
        status.sources.firstOrNull { it.id == source && it.kind == DEVICE_LINK }?.let { linked ->
            MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { removeLink = linked }) { Text("Unlink folder") }
        }
        if (status.scanning) Text("Checking videos…", style = MaterialTheme.typography.labelMedium)
        if (status.unavailable.isNotEmpty() || loadFailed) {
            Text("Some sources are unavailable. Refresh or link the folder again.", style = MaterialTheme.typography.bodySmall)
        }
        val term = search.trim()
        key(source, term, titleOrder, listMode) {
            DeviceVideoGrid(status, source, term, titleOrder, listMode, onPlay, Modifier.weight(1f))
        }
    }
    removeLink?.let { linked ->
        MiowserConfirmationDialog(
            title = "Unlink folder?",
            message = "This removes the folder from Device Videos. Your files stay on the device.",
            onDismiss = { removeLink = null },
            actions = listOf(
                ConfirmationAction("Cancel", { removeLink = null }),
                ConfirmationAction("Unlink", {
                    removeLink = null
                    scope.launch {
                        try {
                            DeviceVideoCatalog.unlink(app, linked.id)
                            source = null
                            refresh++
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { onMessage("Couldn’t unlink this folder") }
                    }
                }, ConfirmationTone.CAUTION)
            )
        )
    }
}

@Composable
private fun DeviceVideoGrid(
    status: DeviceCatalogStatus,
    source: String?,
    term: String,
    titleOrder: Boolean,
    listMode: Boolean,
    onPlay: (DeviceVideo) -> Unit,
    modifier: Modifier
) {
    val app = LocalContext.current.applicationContext
    val grid = rememberLazyGridState()
    val query = remember(status.visible, source, term, titleOrder) { DeviceBrowseQuery(status.visible, source, term, titleOrder) }
    var window by remember(query) { mutableStateOf<DeviceVideoWindow?>(null) }
    var windowRevision by remember(query) { mutableLongStateOf(-1L) }
    LaunchedEffect(query, status.revision) {
        snapshotFlow { grid.firstVisibleItemIndex / DEVICE_PAGE_SIZE }.collectLatest { page ->
            val index = page * DEVICE_PAGE_SIZE
            val count = window?.count?.takeIf { windowRevision == status.revision }
            try {
                window = withContext(Dispatchers.IO) { DeviceVideoCatalog.readWindow(app, query, index, count) }
                windowRevision = status.revision
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { window = DeviceVideoWindow(0, emptyList(), 0) }
        }
    }
    val current = window
    if (current == null || current.count == 0) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (status.scanning) MiowserLoadingIndicator(description = "Checking device videos")
            else ExpressiveEmptyState(
                title = if (term.isNotEmpty()) "No matching videos" else "No videos here",
                description = if (status.access == DeviceMediaAccess.NONE) "Allow video access or link a folder to get started."
                    else "Try another source or refresh to check for new videos.",
                icon = Icons.Rounded.VideoLibrary
            )
        }
        return
    }
    LazyVerticalGrid(
        columns = if (listMode) GridCells.Fixed(1) else GridCells.Adaptive(HubLibraryTokens.gridMinCell),
        state = grid, modifier = modifier.fillMaxWidth().testTag("device_video_grid"),
        contentPadding = PaddingValues(bottom = UiSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
        verticalArrangement = Arrangement.spacedBy(UiSpacing.m)
    ) {
        items(current.count, key = { current[it]?.canonicalUri?.let { uri -> "device:$uri" } ?: "pending:$it" }) { index ->
            val video = current[index]
            if (video == null) LibraryCardPlaceholder(listMode)
            else {
                val scrolling = grid.isScrollInProgress
                var preview by remember(video.uri, video.revision) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(video.uri, video.revision, scrolling) {
                    if (!scrolling) {
                        delay(140)
                        preview = LibraryThumbnailStore.loadDeviceFrame(app, Uri.parse(video.uri), video.revision, video.durationMs)
                    }
                }
                val canonical = observeLibraryCard(LibraryCardId("LOCAL_FILE:${video.canonicalUri}"))
                val saved = canonical ?: if (video.uri != video.canonicalUri) {
                    observeLibraryCard(LibraryCardId("LOCAL_FILE:${video.uri}"))
                } else null
                HubVideoCard(title = video.title, sourceLabel = video.folder.ifBlank {
                    status.sources.firstOrNull { it.id == video.sourceId }?.label ?: "Device video"
                }, positionMs = saved?.positionMs ?: 0, durationMs = video.durationMs.takeIf { it > 0 }
                    ?: saved?.durationMs ?: 0, listMode = listMode,
                    qualityLabel = videoQualityLabel(video.height), onClick = { onPlay(video) }, devicePreview = preview)
            }
        }
    }
}
