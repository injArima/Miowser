package com.miowser.app.browser

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import androidx.lifecycle.repeatOnLifecycle
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.LibraryCardId
import com.miowser.app.library.device.*
import com.miowser.app.library.videoQualityLabel
import com.miowser.app.ui.AppButtonStyle
import com.miowser.app.ui.ChoiceRow
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.MiowserActionButton
import com.miowser.app.ui.MiowserLoadingIndicator
import com.miowser.app.ui.MiowserDropdownMenu
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.theme.HubLibraryTokens
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(FlowPreview::class)
@Composable
internal fun DeviceVideosPage(
    imported: List<String>,
    downloads: List<DeviceDownload>,
    initialView: HubSettings,
    destinationTabs: @Composable () -> Unit,
    librarySettingsOpen: Boolean,
    onDismissLibrarySettings: () -> Unit,
    onOpenDirectories: () -> Unit,
    discoveryEnabled: Boolean,
    autoRescan: Boolean,
    onOpenVideo: () -> Unit,
    onDiscoverySettings: () -> Unit,
    onBrowse: () -> Unit,
    onPlay: (DevicePlaybackSelection) -> Unit,
    onMessage: (String) -> Unit,
    onIdleChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val app = context.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var finishedRefresh by remember { mutableIntStateOf(0) }
    var refreshSource by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(DeviceCatalogStatus(access = deviceMediaAccess(app))) }
    var loadFailed by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var searchVisible by rememberSaveable { mutableStateOf(false) }
    var headerMenu by remember { mutableStateOf(false) }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var titleOrder by rememberSaveable { mutableStateOf(initialView.deviceTitleSort) }
    var listMode by rememberSaveable { mutableStateOf(initialView.deviceList) }
    var foldersMode by rememberSaveable { mutableStateOf(initialView.deviceFolders) }
    var folderPath by rememberSaveable { mutableStateOf<String?>(null) }
    var folderSource by rememberSaveable { mutableStateOf<String?>(null) }
    fun closeFolder() { folderPath = null; folderSource = null }
    fun closeSearch() {
        searchVisible = false
        search = ""
        focusManager.clearFocus()
    }
    BackHandler(enabled = discoveryEnabled && (searchVisible || folderPath != null)) {
        if (searchVisible) closeSearch() else closeFolder()
    }
    val browserPositions = rememberSaveableStateHolder()
    fun saveView() = HubSettingsStore.saveDeviceView(app, foldersMode, listMode, titleOrder)
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
        if (uri != null) scope.launch {
            try {
                val id = DeviceVideoCatalog.link(app, uri)
                closeFolder()
                source = id
                refreshCatalog(id)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { onMessage("Couldn’t link this folder. Choose it again.") }
        }
    }
    LaunchedEffect(lifecycle, discoveryEnabled, autoRescan, refresh, imported, downloads) {
        if (!discoveryEnabled) return@LaunchedEffect
        val request = refresh
        val target = refreshSource
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            val access = deviceMediaAccess(app)
            if (access != DeviceMediaAccess.FULL) {
                status = status.copy(access = access,
                    visible = status.visible.filterNot { it.startsWith("media:") }.toSet())
            }
            val changes = if (autoRescan) DeviceVideoCatalog.changes(app).debounce(250) else emptyFlow()
            changes.onStart { emit(Unit) }.conflate().collect {
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

    Column(Modifier.fillMaxSize().navigationBarsPadding()) {
        LibraryHubTopBar(
            title = {
                Text(folderPath?.substringAfterLast('/')?.ifBlank { "Storage root" } ?: "Library",
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            navigation = if (folderPath != null) {
                {
                    IconButton(onClick = ::closeFolder) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to video folders")
                    }
                }
            } else null
        ) {
            if (discoveryEnabled) {
                FilledTonalIconButton(onClick = {
                    if (searchVisible) closeSearch() else searchVisible = true
                }) {
                    Icon(if (searchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                        if (searchVisible) "Close device video search" else "Search device videos")
                }
                IconButton(onClick = { refreshCatalog(folderSource ?: source) }) {
                    Icon(Icons.Rounded.Refresh, "Refresh device videos")
                }
            }
            Box {
                IconButton(onClick = { headerMenu = true }) {
                    Icon(Icons.Rounded.MoreVert, "Device Videos menu")
                }
                MiowserDropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                    MiowserMenuItem(
                        text = { Text("Open video file") },
                        leadingIcon = { Icon(Icons.Rounded.FolderOpen, contentDescription = null) },
                        onClick = { headerMenu = false; onOpenVideo() }
                    )
                    MiowserMenuItem(
                        text = { Text("Browse") },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                        onClick = { headerMenu = false; onBrowse() }
                    )
                }
            }
        }
        destinationTabs()
        Column(Modifier.fillMaxSize().padding(horizontal = UiSpacing.m)) content@ {
            if (!discoveryEnabled) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    ExpressiveEmptyState(
                        title = "Device discovery is off",
                        description = "Open a video directly or turn discovery back on in Media settings.",
                        icon = Icons.Rounded.VideoLibrary
                    )
                    MiowserActionButton(style = AppButtonStyle.TONAL, onClick = onOpenVideo) { Text("Open video") }
                    MiowserActionButton(style = AppButtonStyle.TEXT, onClick = onDiscoverySettings) { Text("Media settings") }
                }
                return@content
            }
            if (searchVisible) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = UiSpacing.s).testTag("device_video_search"),
                    shape = MaterialTheme.shapes.extraLarge,
                    placeholder = { Text("Search videos and folders") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    trailingIcon = {
                        SearchFieldActions(
                            showClear = search.isNotEmpty(),
                            onClear = { search = "" },
                            onSubmit = { focusManager.clearFocus() },
                            submitEnabled = search.isNotBlank(),
                            submitDescription = "Finish device video search"
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                )
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
                    if (issue == DeviceSourceIssue.NEEDS_ACCESS) {
                        MiowserActionButton(style = AppButtonStyle.TEXT, onClick = onOpenDirectories) { Text("Manage folders") }
                    } else {
                        MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { refreshCatalog(folderSource ?: source) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            val term = search.trim()
            val emptyState: @Composable (String) -> Unit = { title ->
                DeviceLibraryEmptyState(title, status.access, { pickFolder.launch(null) }) {
                    requestAccess.launch(deviceVideoPermissions())
                }
            }
            if (foldersMode && folderPath == null && term.isEmpty()) {
                browserPositions.SaveableStateProvider("device-folders:$listMode") {
                    key(source) {
                        DeviceVideoFolders(status, source, listMode, Modifier.weight(1f), emptyState) { folder ->
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
                        Modifier.weight(1f), emptyState)
                }
                key(foldersMode, folderSource ?: source, folderPath, term, titleOrder, listMode) {
                    if (term.isEmpty()) {
                        val location = if (folderPath == null) "device-videos" else "device-open-folder"
                        browserPositions.SaveableStateProvider("$location:$listMode", content)
                    } else content()
                }
            }
        }
    }
    if (librarySettingsOpen) {
        LibraryOptionsSheet("Device Videos", onDismissLibrarySettings) {
            if (discoveryEnabled) {
                LibraryOptionsGroup("Sources", Icons.Rounded.Storage) {
                    ChoiceRow(listOf(null) + status.sources.map { it.id }, source, { id ->
                        val item = status.sources.firstOrNull { it.id == id }
                        if (item == null) "All sources"
                        else item.label + (status.issues[item.id]?.let { " · ${it.label}" } ?: "")
                    }) { selected ->
                        closeFolder()
                        source = selected
                    }
                }
                LibraryOptionsGroup("Browse by", Icons.Rounded.Folder) {
                    ChoiceRow(listOf(false, true), foldersMode, { if (it) "Folders" else "Videos" }) {
                        if (it != foldersMode) {
                            closeFolder()
                            foldersMode = it
                            saveView()
                        }
                    }
                }
                LibraryOptionsGroup("View", Icons.Rounded.GridView) {
                    ChoiceRow(listOf(false, true), listMode, { if (it) "List" else "Grid" }) {
                        listMode = it
                        saveView()
                    }
                }
                LibraryOptionsGroup("Sort videos", Icons.AutoMirrored.Rounded.Sort) {
                    ChoiceRow(listOf(false, true), titleOrder, { if (it) "Title" else "Recently modified" }) {
                        titleOrder = it
                        saveView()
                    }
                }
            }
            LibraryOptionsGroup("Storage access", Icons.Rounded.FolderOpen) {
                LibraryOptionAction("Manage folders", Icons.Rounded.FolderOpen) {
                    onDismissLibrarySettings()
                    onOpenDirectories()
                }
                if (discoveryEnabled && status.access != DeviceMediaAccess.FULL) {
                    LibraryOptionAction("Allow all videos", Icons.Rounded.VideoLibrary) {
                        onDismissLibrarySettings()
                        requestAccess.launch(deviceVideoPermissions())
                    }
                }
                if (!discoveryEnabled) {
                    LibraryOptionAction("Media settings", Icons.Rounded.Storage) {
                        onDismissLibrarySettings()
                        onDiscoverySettings()
                    }
                }
            }
        }
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
    modifier: Modifier,
    emptyState: @Composable (String) -> Unit
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
            if (status.scanning || window == null) MiowserLoadingIndicator(description = "Checking device videos")
            else if (term.isNotEmpty()) ExpressiveEmptyState(
                title = "No matching videos",
                description = "Try a different search.",
                icon = Icons.Rounded.VideoLibrary
            )
            else emptyState("No videos here")
        }
        return
    }
    LazyVerticalGrid(
        columns = if (listMode) GridCells.Fixed(1) else GridCells.Adaptive(HubLibraryTokens.gridMinCell),
        state = grid, modifier = modifier.fillMaxWidth().testTag("device_video_grid"),
        contentPadding = PaddingValues(bottom = HubLibraryTokens.gridBottomClearance),
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
