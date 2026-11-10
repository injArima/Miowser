package com.miowser.app.browser

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
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
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@Composable
internal fun DeviceVideosPage(
    imported: List<String>,
    downloads: List<DeviceDownload>,
    initialView: HubSettings,
    discoveryEnabled: Boolean,
    onOpenVideo: () -> Unit,
    onDiscoverySettings: () -> Unit,
    onBrowse: () -> Unit,
    onPlay: (DevicePlaybackSelection) -> Unit,
    onMessage: (String) -> Unit,
    onIdleChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var finishedRefresh by remember { mutableIntStateOf(0) }
    var refreshSource by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(DeviceCatalogStatus(access = deviceMediaAccess(app))) }
    var loadFailed by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var titleOrder by rememberSaveable { mutableStateOf(initialView.deviceTitleSort) }
    var listMode by rememberSaveable { mutableStateOf(initialView.deviceList) }
    var foldersMode by rememberSaveable { mutableStateOf(initialView.deviceFolders) }
    var folderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var folderSource by rememberSaveable { mutableStateOf<String?>(null) }
    fun closeFolder() { folderPath = null; folderSource = null }
    BackHandler(enabled = discoveryEnabled && folderPath != null) { closeFolder() }
    val browserPositions = rememberSaveableStateHolder()
    fun saveView() = HubSettingsStore.saveDeviceView(app, foldersMode, listMode, titleOrder)
    var menu by remember { mutableStateOf(false) }
    var removeLink by remember { mutableStateOf<DeviceSource?>(null) }
    var replacingLink by rememberSaveable { mutableStateOf<String?>(null) }
    fun refreshCatalog(target: String?) {
        refreshSource = target
        refresh++
    }
    val requestAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val access = deviceMediaAccess(app)
        status = status.copy(access = access,
            visible = status.visible.filterNot { it.startsWith("media:") }.toSet())
        refreshCatalog(null)
    }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val replaceId = replacingLink
        replacingLink = null
        if (uri != null) scope.launch {
            try {
                val id = withContext(Dispatchers.IO) {
                    app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    DeviceVideoCatalog.link(app, uri, replaceId)
                }
                closeFolder()
                source = id
                refreshCatalog(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onMessage("Couldn’t link this folder. Choose it again.") }
        }
    }
    LaunchedEffect(lifecycle, discoveryEnabled, refresh, imported, downloads) {
        if (!discoveryEnabled) return@LaunchedEffect
        val request = refresh
        val target = refreshSource
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val access = deviceMediaAccess(app)
            if (access != DeviceMediaAccess.FULL) {
                status = status.copy(access = access,
                    visible = status.visible.filterNot { it.startsWith("media:") }.toSet())
            }
            DeviceVideoCatalog.changes(app).debounce(250).onStart { emit(Unit) }.conflate().collect {
                try {
                    loadFailed = false
                    val forced = request != finishedRefresh
                    DeviceVideoCatalog.scan(app, imported, downloads, forced, target.takeIf { forced }, previous = status)
                        .collect { status = it }
                    finishedRefresh = request
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
            if (folderPath != null) {
                IconButton(onClick = ::closeFolder) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to video folders") }
            }
            Text(folderPath?.substringAfterLast('/')?.ifBlank { "Storage root" } ?: "Device Videos",
                Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (discoveryEnabled) {
                IconButton(onClick = { refreshCatalog(folderSource ?: source) }) { Icon(Icons.Rounded.Refresh, "Refresh device videos") }
            }
            IconButton(onClick = onOpenVideo) { Icon(Icons.Rounded.FolderOpen, "Open video file") }
            IconButton(onClick = onBrowse) { Icon(Icons.Rounded.Language, "Browse") }
        }
        if (!discoveryEnabled) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally) {
                ExpressiveEmptyState(
                    title = "Device discovery is off",
                    description = "Open a video directly or turn discovery back on in Hub settings.",
                    icon = Icons.Rounded.VideoLibrary
                )
                MiowserActionButton(style = AppButtonStyle.TONAL, onClick = onOpenVideo) { Text("Open video") }
                MiowserActionButton(style = AppButtonStyle.TEXT, onClick = onDiscoverySettings) { Text("Hub settings") }
            }
            return@Column
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
                    DropdownMenuItem(text = { Text("All sources") }, onClick = { closeFolder(); source = null; menu = false })
                    status.sources.forEach { item ->
                        DropdownMenuItem(text = {
                            Text(item.label + (status.issues[item.id]?.let { " · ${it.label}" } ?: ""))
                        }, onClick = { closeFolder(); source = item.id; menu = false })
                    }
                }
            }
            FilterChip(selected = foldersMode, onClick = {
                closeFolder()
                foldersMode = !foldersMode
                saveView()
            }, label = { Text("Folders") })
            FilterChip(selected = titleOrder, onClick = { titleOrder = !titleOrder; saveView() },
                label = { Text(if (titleOrder) "Title" else "Recently modified") })
            FilterChip(selected = listMode, onClick = { listMode = !listMode; saveView() },
                label = { Text(if (listMode) "List" else "Grid") })
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
            if (status.access != DeviceMediaAccess.FULL) {
                MiowserActionButton(style = AppButtonStyle.TONAL, onClick = { requestAccess.launch(deviceVideoPermissions()) }) {
                    Text(if (status.access == DeviceMediaAccess.SELECTED) "Change video access" else "Allow video access")
                }
            }
            MiowserActionButton(style = AppButtonStyle.TEXT, onClick = {
                replacingLink = null
                pickFolder.launch(null)
            }) { Text("Link folder") }
            status.sources.firstOrNull { it.id == (folderSource ?: source) && it.kind == DEVICE_LINK }?.let { linked ->
                MiowserActionButton(style = AppButtonStyle.TEXT, onClick = {
                    replacingLink = linked.id
                    pickFolder.launch(Uri.parse(linked.id))
                }) {
                    Text("Relink folder")
                }
                MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { removeLink = linked }) { Text("Unlink folder") }
            }
        }
        if (status.scanning) {
            Text(status.checkingSource?.let { "Checking $it…" } ?: "Checking videos…",
                Modifier.testTag("device_scan_status"), style = MaterialTheme.typography.labelMedium)
        }
        if (!status.scanning && (status.issues.isNotEmpty() || loadFailed)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val issue = (folderSource ?: source)?.let { status.issues[it] }
                Text(when (issue) {
                    DeviceSourceIssue.OFFLINE -> "Reconnect this storage to see its videos."
                    DeviceSourceIssue.NEEDS_ACCESS -> "Relink this folder to restore access."
                    DeviceSourceIssue.SCAN_FAILED -> "Couldn’t check this source."
                    null -> if (loadFailed) "Couldn’t load Device Videos." else "Some video sources are unavailable."
                }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { refreshCatalog(folderSource ?: source) }) {
                    Text("Retry")
                }
            }
        }
        val term = search.trim()
        if (foldersMode && folderPath == null && term.isEmpty()) {
            browserPositions.SaveableStateProvider("device-folders:$listMode") {
                key(source) {
                    DeviceVideoFolders(status, source, listMode, Modifier.weight(1f)) { folder ->
                        browserPositions.removeState("device-open-folder:false")
                        browserPositions.removeState("device-open-folder:true")
                        folderSource = folder.sourceId
                        folderPath = folder.folder
                    }
                }
            }
        } else {
            val content: @Composable () -> Unit = {
                DeviceVideoGrid(status, folderSource ?: source, folderPath, term, titleOrder, listMode, onPlay,
                    Modifier.weight(1f))
            }
            key(foldersMode, folderSource ?: source, folderPath, term, titleOrder, listMode) {
                if (term.isEmpty()) {
                    val location = if (folderPath == null) "device-videos" else "device-open-folder"
                    browserPositions.SaveableStateProvider("$location:$listMode", content)
                } else content()
            }
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
                            closeFolder()
                            source = null
                            refreshCatalog(null)
                        } catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { onMessage("Couldn’t unlink this folder") }
                    }
                }, ConfirmationTone.CAUTION)
            )
        )
    }
}

private val DeviceSourceIssue.label: String get() = when (this) {
    DeviceSourceIssue.OFFLINE -> "Offline"
    DeviceSourceIssue.NEEDS_ACCESS -> "Needs access"
    DeviceSourceIssue.SCAN_FAILED -> "Scan failed"
}

@Composable
private fun DeviceVideoGrid(
    status: DeviceCatalogStatus,
    source: String?,
    folder: String?,
    term: String,
    titleOrder: Boolean,
    listMode: Boolean,
    onPlay: (DevicePlaybackSelection) -> Unit,
    modifier: Modifier
) {
    val app = LocalContext.current.applicationContext
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)
    val grid = rememberLazyGridState()
    val query = remember(status.visible, source, term, titleOrder, folder) {
        DeviceBrowseQuery(status.visible, source, term, titleOrder, folder)
    }
    var window by remember { mutableStateOf<DeviceVideoWindow?>(null) }
    var loadedQuery by remember { mutableStateOf<DeviceBrowseQuery?>(null) }
    var windowRevision by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(query, status.revision) {
        snapshotFlow { grid.firstVisibleItemIndex / DEVICE_PAGE_SIZE }.collectLatest { page ->
            val index = page * DEVICE_PAGE_SIZE
            val count = window?.count?.takeIf { loadedQuery == query && windowRevision == status.revision }
            try {
                window = withContext(Dispatchers.IO) { DeviceVideoCatalog.readWindow(app, query, index, count) }
                loadedQuery = query
                windowRevision = status.revision
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { window = DeviceVideoWindow(0, emptyList(), 0) }
        }
    }
    val windowQuery = loadedQuery
    val current = window?.takeIf { windowQuery != null && query.allowedSources.containsAll(windowQuery.allowedSources) }
    LaunchedEffect(current, grid, foreground) {
        if (!foreground) return@LaunchedEffect
        snapshotFlow {
            if (grid.isScrollInProgress) null else grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.collectLatest { lastVisible ->
            if (lastVisible == null || current == null) return@collectLatest
            delay(250)
            for (index in lastVisible + 1..lastVisible + 4) {
                val video = current[index] ?: break
                LibraryThumbnailStore.loadDeviceFrame(app, video, visible = false, compact = listMode)
            }
        }
    }
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
                var preview by remember(video.uri, video.revision, listMode) {
                    mutableStateOf(LibraryThumbnailStore.peekDeviceFrame(video, compact = listMode))
                }
                LaunchedEffect(video.uri, video.revision, scrolling, foreground) {
                    if (!foreground) preview = null
                    else if (!scrolling && preview == null) {
                        delay(140)
                        preview = LibraryThumbnailStore.loadDeviceFrame(app, video, compact = listMode)
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
                    qualityLabel = videoQualityLabel(video.height),
                    onClick = { onPlay(DevicePlaybackSelection(checkNotNull(windowQuery), current, index)) }, localPreview = preview)
            }
        }
    }
}
