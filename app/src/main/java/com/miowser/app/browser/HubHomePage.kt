package com.miowser.app.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import android.net.Uri
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.ApkIconLoader
import com.miowser.app.downloader.DownloadedFileType
import com.miowser.app.downloader.DownloadedFileInspector
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.library.isLibraryEntryAvailable
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.ExpressiveSectionCard
import com.miowser.app.ui.theme.ExpressiveTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

enum class HubSection { LIBRARY, DOWNLOADS, MORE }
enum class HubDownloadSection { DOWNLOADING, DOWNLOADED }
enum class HubLibrarySort(val label: String) {
    RECENT("Recently played"),
    TITLE("Title"),
    PROGRESS("Watched progress")
}

/** Root Hub with bottom navigation for Library, Downloads, and independent Hub settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HubHomePage(
    selectedSection: HubSection,
    onSectionSelected: (HubSection) -> Unit,
    selectedDownloadSection: HubDownloadSection,
    onDownloadSectionSelected: (HubDownloadSection) -> Unit,
    librarySort: HubLibrarySort,
    onLibrarySortSelected: (HubLibrarySort) -> Unit,
    libraryEntries: List<LibraryEntry>,
    onPlayLibraryEntry: (LibraryEntry) -> Unit,
    onRemoveLibraryEntry: (LibraryEntry) -> Unit,
    onClearLibrary: () -> Unit,
    downloads: List<Download>,
    onPauseDownload: (Download) -> Unit,
    onResumeDownload: (Download) -> Unit,
    onCancelDownload: (Download) -> Unit,
    onRetryDownload: (Download) -> Unit,
    onDeleteDownload: (Download, Boolean) -> Unit,
    onRefreshDownloadLink: (Download) -> Unit,
    onOpenLocalDownload: (Download) -> Unit,
    onOpenBrowser: () -> Unit
) {
    val context = LocalContext.current
    var librarySearchVisible by rememberSaveable { mutableStateOf(false) }
    var librarySearchQuery by rememberSaveable { mutableStateOf("") }
    var librarySortMenuExpanded by remember { mutableStateOf(false) }
    var showClearLibraryConfirmation by remember { mutableStateOf(false) }
    var folderCreationMode by remember { mutableStateOf<FolderCreationMode?>(null) }
    // Unfiled is the Library's home view. All remains available for cross-folder browsing.
    var selectedFolderId by rememberSaveable { mutableStateOf<String?>(UNFILED_FOLDER_ID) }
    var previousSection by remember { mutableStateOf(selectedSection) }
    var movingEntry by remember { mutableStateOf<LibraryEntry?>(null) }

    LaunchedEffect(selectedSection) {
        if (selectedSection == HubSection.LIBRARY && previousSection != HubSection.LIBRARY) {
            selectedFolderId = UNFILED_FOLDER_ID
        }
        previousSection = selectedSection
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        if (selectedSection == HubSection.LIBRARY) {
                            Column {
                                Text("Library")
                                Text(
                                    "${libraryEntries.size} ${if (libraryEntries.size == 1) "video" else "videos"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            Text(
                                if (selectedSection == HubSection.DOWNLOADS) "Downloads" else "More"
                            )
                        }
                    },
                    actions = {
                        if (selectedSection == HubSection.LIBRARY) {
                            IconButton(onClick = {
                                librarySearchVisible = !librarySearchVisible
                                if (!librarySearchVisible) librarySearchQuery = ""
                            }) {
                                Icon(
                                    if (librarySearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = if (librarySearchVisible) "Close Library search" else "Search Library"
                                )
                            }
                            Box {
                                IconButton(onClick = { librarySortMenuExpanded = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Sort Library")
                                }
                                DropdownMenu(
                                    expanded = librarySortMenuExpanded,
                                    onDismissRequest = { librarySortMenuExpanded = false }
                                ) {
                                    HubLibrarySort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = {
                                                librarySortMenuExpanded = false
                                                onLibrarySortSelected(option)
                                            },
                                            trailingIcon = {
                                                if (option == librarySort) {
                                                    Text("Selected", color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            IconButton(
                                onClick = { showClearLibraryConfirmation = true },
                                enabled = libraryEntries.isNotEmpty()
                            ) {
                                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear Library")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        titleContentColor = MaterialTheme.colorScheme.onSurface
                    )
                )
                if (selectedSection == HubSection.LIBRARY && librarySearchVisible) {
                    OutlinedTextField(
                        value = librarySearchQuery,
                        onValueChange = { librarySearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                        placeholder = { Text("Search videos") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge
                    )
                }
            }
        },
        bottomBar = {
            HubBottomNavigation(
                selectedSection = selectedSection,
                activeDownloadCount = downloads.count { it.status.isHubActiveDownload() },
                librarySort = librarySort,
                onLibrarySortSelected = onLibrarySortSelected,
                onSectionSelected = { section ->
                    if (section == HubSection.LIBRARY) selectedFolderId = UNFILED_FOLDER_ID
                    onSectionSelected(section)
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenBrowser,
                shape = MaterialTheme.shapes.large,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    Icons.Rounded.Language,
                    contentDescription = "Browse",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        when (selectedSection) {
            HubSection.LIBRARY -> HubLibraryContent(
                entries = libraryEntries,
                onPlay = onPlayLibraryEntry,
                onRemove = onRemoveLibraryEntry,
                onMoveToFolder = { movingEntry = it },
                sort = librarySort,
                searchQuery = librarySearchQuery,
                selectedFolderId = selectedFolderId,
                onFolderSelected = { selectedFolderId = it },
                onCreateFolder = { folderCreationMode = it },
                modifier = Modifier.fillMaxSize().padding(innerPadding).padding(top = 8.dp)
            )
            HubSection.DOWNLOADS -> HubDownloadsTabContent(
                selectedSection = selectedDownloadSection,
                onSectionSelected = onDownloadSectionSelected,
                downloads = downloads,
                onPause = onPauseDownload,
                onResume = onResumeDownload,
                onCancel = onCancelDownload,
                onRetry = onRetryDownload,
                onDelete = onDeleteDownload,
                onRefreshLink = onRefreshDownloadLink,
                onOpenLocal = onOpenLocalDownload,
                libraryEntries = libraryEntries,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
            HubSection.MORE -> HubMoreContent(
                defaultDownloadSection = selectedDownloadSection,
                onDefaultDownloadSectionSelected = onDownloadSectionSelected,
                modifier = Modifier.fillMaxSize().padding(innerPadding)
            )
        }
    }

    folderCreationMode?.let { mode ->
        LibraryFolderCreationFlow(mode, onFinished = { folderCreationMode = null })
    }
    movingEntry?.let { entry ->
        LibraryFolderPickerSheet(
            onSelected = { folderId ->
                MediaLibrary.moveToFolder(context, entry.identityKey, folderId)
                movingEntry = null
            },
            onDismiss = { movingEntry = null }
        )
    }

    if (showClearLibraryConfirmation) {
        AlertDialog(
            onDismissRequest = { showClearLibraryConfirmation = false },
            title = { Text("Clear Library?") },
            text = { Text("This removes all playback records from the Library. Downloaded files will not be deleted.") },
            confirmButton = {
                TextButton(onClick = {
                    showClearLibraryConfirmation = false
                    onClearLibrary()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { showClearLibraryConfirmation = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HubBottomNavigation(
    selectedSection: HubSection,
    activeDownloadCount: Int,
    librarySort: HubLibrarySort,
    onLibrarySortSelected: (HubLibrarySort) -> Unit,
    onSectionSelected: (HubSection) -> Unit
) {
    var librarySettingsExpanded by remember { mutableStateOf(false) }
    var failedVideoLogExpanded by remember { mutableStateOf(false) }
    val navigationHeight = (64f * LocalDensity.current.fontScale.coerceAtLeast(1f)).dp
    // Keep the controls compact while allowing the device's navigation mode to contribute its
    // own bottom inset outside that 64dp control area. Constraining both to one fixed height clips
    // labels and icons on devices using the three-button navigation bar.
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Box(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
            )
        ) {
            NavigationBar(
                modifier = Modifier.height(navigationHeight),
                windowInsets = WindowInsets(0, 0, 0, 0),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                HubSection.entries.forEach { section ->
                    val label = when (section) {
                        HubSection.LIBRARY -> "Library"
                        HubSection.DOWNLOADS -> "Downloads"
                        HubSection.MORE -> "More"
                    }
                    val icon = when (section) {
                        HubSection.LIBRARY -> Icons.Rounded.VideoLibrary
                        HubSection.DOWNLOADS -> Icons.Rounded.Download
                        HubSection.MORE -> Icons.Rounded.MoreHoriz
                    }
                    HubNavigationItem(
                        selected = selectedSection == section,
                        onClick = { onSectionSelected(section) },
                        onLongClick = if (section == HubSection.LIBRARY) {
                            { librarySettingsExpanded = true }
                        } else {
                            null
                        },
                        longClickLabel = if (section == HubSection.LIBRARY) {
                            "Open Library settings"
                        } else {
                            null
                        },
                        label = label,
                        icon = {
                            if (section == HubSection.DOWNLOADS && activeDownloadCount > 0) {
                                BadgedBox(
                                    badge = { Badge { Text(activeDownloadCount.coerceAtMost(99).toString()) } }
                                ) {
                                    Icon(icon, contentDescription = null)
                                }
                            } else {
                                Icon(icon, contentDescription = null)
                            }
                        }
                    )
                }
            }
        }
    }

    if (librarySettingsExpanded) {
        ModalBottomSheet(onDismissRequest = { librarySettingsExpanded = false }) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Library settings",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
                Text(
                    "Library order",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)
                )
                Text(
                    "Choose how videos are arranged when the Library opens.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HubLibrarySort.entries.forEach { option ->
                        FilterChip(
                            selected = option == librarySort,
                            onClick = { onLibrarySortSelected(option) },
                            label = { Text(option.label) }
                        )
                    }
                }
                TextButton(
                    onClick = {
                        librarySettingsExpanded = false
                        failedVideoLogExpanded = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Playback issue history") }
            }
        }
    }
    if (failedVideoLogExpanded) {
        FailedVideoLogSheet(onDismiss = { failedVideoLogExpanded = false })
    }
}

/**
 * Compact Material navigation item with one gesture owner. Layering combinedClickable onto
 * NavigationBarItem does not work reliably because NavigationBarItem consumes the pointer stream
 * with its own selectable handler before a long press can finish.
 */
@Composable
private fun RowScope.HubNavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
    label: String,
    icon: @Composable () -> Unit
) {
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(ExpressiveTokens.compactMotionMillis),
        label = "Hub navigation content"
    )
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer
        else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(ExpressiveTokens.compactMotionMillis),
        label = "Hub navigation indicator"
    )
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .semantics { this.selected = selected }
            .combinedClickable(
                role = Role.Tab,
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = indicatorColor,
            contentColor = contentColor,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Box(
                modifier = Modifier.size(width = 64.dp, height = 30.dp),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
private fun HubMoreContent(
    defaultDownloadSection: HubDownloadSection,
    onDefaultDownloadSectionSelected: (HubDownloadSection) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(ExpressiveTokens.sectionGap)
    ) {
        ExpressiveSectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Rounded.Settings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Hub settings", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "These preferences affect the Hub only. Browser settings remain in the browser menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        HubChoiceSetting(
            title = "Default Downloads section",
            description = "Choose which download list opens first."
        ) {
            HubDownloadSection.entries.forEach { option ->
                FilterChip(
                    selected = option == defaultDownloadSection,
                    onClick = { onDefaultDownloadSectionSelected(option) },
                    label = {
                        Text(if (option == HubDownloadSection.DOWNLOADING) "Downloading" else "Downloaded")
                    }
                )
            }
        }
    }
}

@Composable
private fun HubChoiceSetting(
    title: String,
    description: String,
    choices: @Composable RowScope.() -> Unit
) {
    ExpressiveSectionCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(
            description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = choices
        )
        }
    }
}

private fun DownloadStatus.isHubActiveDownload(): Boolean =
    this == DownloadStatus.QUEUED || this == DownloadStatus.DOWNLOADING ||
        this == DownloadStatus.PAUSED || this == DownloadStatus.MUXING

@Composable
private fun HubDownloadsTabContent(
    selectedSection: HubDownloadSection,
    onSectionSelected: (HubDownloadSection) -> Unit,
    downloads: List<Download>,
    onPause: (Download) -> Unit,
    onResume: (Download) -> Unit,
    onCancel: (Download) -> Unit,
    onRetry: (Download) -> Unit,
    onDelete: (Download, Boolean) -> Unit,
    onRefreshLink: (Download) -> Unit,
    onOpenLocal: (Download) -> Unit,
    libraryEntries: List<LibraryEntry>,
    modifier: Modifier = Modifier
) {
    val downloading = downloadsForSection(downloads, HubDownloadSection.DOWNLOADING)
    val downloaded = downloadsForSection(downloads, HubDownloadSection.DOWNLOADED)
    Column(modifier = modifier) {
        SecondaryTabRow(selectedTabIndex = selectedSection.ordinal) {
            Tab(
                selected = selectedSection == HubDownloadSection.DOWNLOADING,
                onClick = { onSectionSelected(HubDownloadSection.DOWNLOADING) },
                text = { Text("Downloading (${downloading.size})") }
            )
            Tab(
                selected = selectedSection == HubDownloadSection.DOWNLOADED,
                onClick = { onSectionSelected(HubDownloadSection.DOWNLOADED) },
                text = { Text("Downloaded (${downloaded.size})") }
            )
        }
        if (selectedSection == HubDownloadSection.DOWNLOADING) {
            HubDownloadsContent(
                downloads = downloading,
                onPause = onPause,
                onResume = onResume,
                onCancel = onCancel,
                onRetry = onRetry,
                onDelete = onDelete,
                onRefreshLink = onRefreshLink,
                onPlayLocal = onOpenLocal,
                modifier = Modifier.fillMaxWidth().weight(1f),
                emptyMessage = "No active or unfinished downloads."
            )
        } else {
            HubDownloadedGrid(
                downloads = downloaded,
                libraryEntries = libraryEntries,
                onOpenLocal = onOpenLocal,
                onDelete = onDelete,
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }
    }
}

@Composable
private fun HubDownloadedGrid(
    downloads: List<Download>,
    libraryEntries: List<LibraryEntry>,
    onOpenLocal: (Download) -> Unit,
    onDelete: (Download, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<Download?>(null) }
    val libraryByMediaUri = libraryEntries.associateBy(LibraryEntry::mediaUri)
    if (downloads.isEmpty()) {
        Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
            ExpressiveEmptyState(
                title = "No downloaded files",
                description = "Completed downloads will appear here.",
                icon = Icons.Rounded.Download
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = modifier,
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(downloads, key = { it.id }) { download ->
                val savedUri = download.savedUri?.toString()
                val context = LocalContext.current
                val fileType by produceState<DownloadedFileType?>(
                    initialValue = DownloadedFileInspector.cachedOrBasic(download),
                    savedUri,
                    download.displayName
                ) {
                    value = withContext(Dispatchers.IO) {
                        DownloadedFileInspector.inspect(context.applicationContext, download)
                    }
                }
                val libraryEntry = if (fileType == DownloadedFileType.MEDIA) {
                    libraryByMediaUri[savedUri]
                } else {
                    null
                }
                val localThumbnail by produceState<String?>(
                    initialValue = libraryEntry?.thumbnailUrl ?: if (fileType == DownloadedFileType.MEDIA) {
                        download.savedUri?.let {
                            LibraryThumbnailStore.existingDownloadedThumbnail(context.applicationContext, it)
                        }
                    } else null,
                    savedUri,
                    fileType,
                    libraryEntry?.thumbnailUrl
                ) {
                    value = libraryEntry?.thumbnailUrl ?: if (fileType == DownloadedFileType.MEDIA) {
                        download.savedUri?.let { uri ->
                            withContext(Dispatchers.IO) {
                                LibraryThumbnailStore.ensureDownloadedThumbnail(context.applicationContext, uri)
                            }
                        }
                    } else null
                }
                val apkIcon by produceState<android.graphics.Bitmap?>(
                    initialValue = null,
                    savedUri,
                    fileType
                ) {
                    value = if (fileType == DownloadedFileType.APK && download.savedUri != null) {
                        withContext(Dispatchers.IO) {
                            ApkIconLoader.load(context.applicationContext, download.savedUri!!)
                        }
                    } else {
                        null
                    }
                }
                HubVideoCard(
                    title = download.displayName.ifBlank { "Downloaded file" },
                    sourceLabel = downloadSourceLabel(download),
                    positionMs = libraryEntry?.positionMs ?: 0L,
                    durationMs = libraryEntry?.durationMs ?: 0L,
                    thumbnailUrl = localThumbnail,
                    centerArtwork = apkIcon?.asImageBitmap(),
                    actionIcon = when (fileType) {
                        DownloadedFileType.MEDIA -> Icons.Rounded.PlayCircle
                        DownloadedFileType.APK -> Icons.Rounded.InstallMobile
                        DownloadedFileType.OTHER -> Icons.AutoMirrored.Rounded.OpenInNew
                        null -> Icons.Rounded.MoreHoriz
                    },
                    actionContentDescription = when (fileType) {
                        DownloadedFileType.MEDIA -> "Play"
                        DownloadedFileType.APK -> "Install"
                        DownloadedFileType.OTHER -> "Open"
                        null -> "Checking file type"
                    },
                    statusLabel = when {
                        download.savedUri == null -> "File unavailable"
                        fileType == null -> "Checking file type…"
                        else -> null
                    },
                    onClick = if (download.savedUri != null && fileType != null) {
                        { onOpenLocal(download) }
                    } else null,
                    menuContentDescription = "Download options",
                    menuActionLabel = "Delete",
                    onMenuAction = { pendingDelete = download },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }

    pendingDelete?.let { download ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete download?") },
            text = { Text("Remove only Miowser's record, or delete the downloaded file too?") },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        pendingDelete = null
                        onDelete(download, false)
                    }) { Text("Record only") }
                    TextButton(onClick = {
                        pendingDelete = null
                        onDelete(download, true)
                    }) { Text("File and record") }
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            }
        )
    }
}

internal fun downloadSourceLabel(download: Download): String {
    val address = download.pageUrl.ifBlank { download.sourceUrl }
    return runCatching { URI(address).host }
        .getOrNull()
        ?.removePrefix("www.")
        ?.takeIf { it.isNotBlank() }
        ?: "Downloaded file"
}

internal fun downloadsForSection(
    downloads: List<Download>,
    section: HubDownloadSection
): List<Download> = when (section) {
    HubDownloadSection.DOWNLOADING -> downloads.filter { it.status != DownloadStatus.COMPLETED }
    HubDownloadSection.DOWNLOADED -> downloads.filter { it.status == DownloadStatus.COMPLETED }
}

@Composable
private fun HubLibraryContent(
    entries: List<LibraryEntry>,
    onPlay: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onMoveToFolder: (LibraryEntry) -> Unit,
    sort: HubLibrarySort,
    searchQuery: String,
    selectedFolderId: String?,
    onFolderSelected: (String?) -> Unit,
    onCreateFolder: (FolderCreationMode) -> Unit,
    modifier: Modifier = Modifier
) {
    var unavailableEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    // SnapshotStateList mutations keep the same List identity, so calculate directly during
    // composition rather than caching by the list object and accidentally showing stale order.
    val inFolder = entries.filter { entry ->
        when (selectedFolderId) {
            null -> true
            UNFILED_FOLDER_ID -> entry.folderId == null
            else -> entry.folderId == selectedFolderId
        }
    }
    val visibleEntries = filterLibraryEntries(sortLibraryEntries(inFolder, sort), searchQuery)

    Column(modifier = modifier) {
        LibraryFolderStrip(selectedFolderId, onFolderSelected, onCreateFolder)
        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveEmptyState(
                    title = "Your Library is empty",
                    description = "Videos opened in Miowser's player will appear here.",
                    icon = Icons.Rounded.VideoLibrary
                )
            }
        } else if (visibleEntries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveEmptyState(
                    title = if (searchQuery.isNotBlank()) "No matching videos" else "This folder is empty",
                    description = if (searchQuery.isNotBlank()) {
                        "Try a different search in your Library."
                    } else {
                        "Use a video's three-dot menu to move it here."
                    },
                    icon = Icons.Rounded.Search
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(visibleEntries, key = { it.identityKey }) { entry ->
                    ManagedLibraryCard(
                        entry = entry,
                        onPlay = onPlay,
                        onRemove = onRemove,
                        onMoveToFolder = onMoveToFolder,
                        onUnavailable = { unavailableEntry = it }
                    )
                }
            }
        }
    }

    unavailableEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { unavailableEntry = null },
            title = { Text("File unavailable") },
            text = { Text("The downloaded file can no longer be found. Remove its Library record?") },
            confirmButton = {
                TextButton(onClick = {
                    unavailableEntry = null
                    onRemove(entry)
                }) { Text("Remove record") }
            },
            dismissButton = {
                TextButton(onClick = { unavailableEntry = null }) { Text("Keep") }
            }
        )
    }
}

@Composable
private fun ManagedLibraryCard(
    entry: LibraryEntry,
    onPlay: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onMoveToFolder: (LibraryEntry) -> Unit,
    onUnavailable: (LibraryEntry) -> Unit
) {
    val context = LocalContext.current
    val isLocalFile = entry.sourceKind == LibrarySourceKind.LOCAL_FILE
    val available by produceState<Boolean?>(
        initialValue = if (isLocalFile) null else true,
        entry.identityKey
    ) {
        value = if (isLocalFile) {
            withContext(Dispatchers.IO) {
                isLibraryEntryAvailable(context.applicationContext, entry)
            }
        } else {
            true
        }
    }
    val isUnavailable = available == false

    // Upgrade old first-frame thumbnails for local videos as cards become visible. This also
    // repairs records whose thumbnail was never captured, without reopening the player.
    LaunchedEffect(entry.identityKey, entry.thumbnailUrl, available) {
        if (!isLocalFile || available != true || LibraryThumbnailStore.isMiddleFrame(entry.thumbnailUrl)) {
            return@LaunchedEffect
        }
        LibraryThumbnailStore.captureMiddleFrame(
            context = context.applicationContext,
            mediaUri = Uri.parse(entry.mediaUri),
            headers = null,
            durationMs = entry.durationMs,
            identityKey = entry.identityKey
        ) { uri ->
            if (uri != null) MediaLibrary.updateThumbnail(context.applicationContext, entry.identityKey, uri)
        }
    }

    HubVideoCard(
        title = entry.title.ifBlank { "Untitled video" },
        sourceLabel = entry.sourceLabel,
        positionMs = entry.positionMs,
        durationMs = entry.durationMs,
        modifier = Modifier.fillMaxWidth(),
        thumbnailUrl = entry.thumbnailUrl,
        statusLabel = when {
            isUnavailable -> "File unavailable"
            isLocalFile && available == null -> "Checking file…"
            else -> null
        },
        onClick = {
            when (available) {
                false -> onUnavailable(entry)
                true -> onPlay(entry)
                null -> Unit
            }
        },
        menuContentDescription = "Library options",
        menuActionLabel = "Remove from Library",
        onMenuAction = { onRemove(entry) },
        secondaryMenuActionLabel = "Move to folder",
        onSecondaryMenuAction = { onMoveToFolder(entry) }
    )
}

internal fun sortLibraryEntries(
    entries: List<LibraryEntry>,
    sort: HubLibrarySort
): List<LibraryEntry> = when (sort) {
    HubLibrarySort.RECENT -> entries.sortedByDescending { it.lastPlayedAt }
    HubLibrarySort.TITLE -> entries.sortedWith(
        compareBy<LibraryEntry> { it.title.lowercase() }
            .thenByDescending { it.lastPlayedAt }
    )
    HubLibrarySort.PROGRESS -> entries.sortedWith(
        compareByDescending<LibraryEntry> { mediaWatchProgress(it.positionMs, it.durationMs) }
            .thenByDescending { it.lastPlayedAt }
    )
}

internal fun filterLibraryEntries(
    entries: List<LibraryEntry>,
    query: String
): List<LibraryEntry> {
    val normalized = query.trim()
    if (normalized.isEmpty()) return entries
    return entries.filter { entry ->
        entry.title.contains(normalized, ignoreCase = true) ||
            entry.sourceLabel.contains(normalized, ignoreCase = true) ||
            entry.mediaUri.contains(normalized, ignoreCase = true)
    }
}
