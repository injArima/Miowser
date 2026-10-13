package com.miowser.app.browser

import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import android.net.Uri
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import com.miowser.app.ui.MiowserDropdownMenu
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.PublicationPage
import com.miowser.app.ui.PublicationPageContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.key
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.ApkIconLoader
import com.miowser.app.downloader.DownloadedFileType
import com.miowser.app.downloader.DownloadedFileInspector
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibraryFolders
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.library.isLibraryEntryAvailable
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.ExpressiveBottomSheet
import com.miowser.app.ui.ExpressiveChoiceSetting
import com.miowser.app.ui.ExpressiveSectionCard
import com.miowser.app.ui.ExpressiveSheetHeading
import com.miowser.app.ui.theme.ExpressiveTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.awaitCancellation
import com.miowser.app.library.LocalInspectionCache
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
    onPlayLibraryEntry: (LibraryEntry, List<LibraryEntry>) -> Unit,
    onRelinkLibraryEntry: (LibraryEntry) -> Unit,
    onRemoveLibraryEntry: (LibraryEntry) -> Unit,
    onSaveLibraryEntry: (LibraryEntry) -> Unit,
    onClearLibrary: () -> Unit,
    downloads: List<Download>,
    onPauseDownload: (Download) -> Unit,
    onResumeDownload: (Download) -> Unit,
    onCancelDownload: (Download) -> Unit,
    onRetryDownload: (Download) -> Unit,
    onDeleteDownload: (Download, Boolean) -> Unit,
    onRefreshDownloadLink: (Download) -> Unit,
    onOpenLocalDownload: (Download) -> Unit,
    onRelinkDownload: (Download) -> Unit,
    onOpenBrowser: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenRecordInBrowser: (String, Boolean) -> Unit,
    uiVisible: Boolean = true,
    onLibraryIdleChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var inspectionEpoch by remember { mutableLongStateOf(LocalInspectionCache.epoch) }
    LaunchedEffect(lifecycle, uiVisible) {
        if (uiVisible) lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            inspectionEpoch = withContext(Dispatchers.IO) { LocalInspectionCache.prepare(context.applicationContext) }
            awaitCancellation()
        }
    }
    var librarySearchVisible by rememberSaveable { mutableStateOf(false) }
    var librarySearchQuery by rememberSaveable { mutableStateOf("") }
    var librarySortMenuExpanded by remember { mutableStateOf(false) }
    var showClearLibraryConfirmation by remember { mutableStateOf(false) }
    var hubMenuExpanded by remember { mutableStateOf(false) }
    var publicationPage by remember { mutableStateOf<PublicationPage?>(null) }
    var folderCreationMode by remember { mutableStateOf<FolderCreationMode?>(null) }
    var folderCreationParentId by remember { mutableStateOf<String?>(null) }
    // Older saved state may still contain null from the removed All view.
    var selectedFolderId by rememberSaveable { mutableStateOf<String?>(UNFILED_FOLDER_ID) }
    val activeFolderId = selectedFolderId ?: UNFILED_FOLDER_ID
    var folderHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    BackHandler(enabled = uiVisible && publicationPage == null && selectedSection == HubSection.LIBRARY &&
        (activeFolderId != UNFILED_FOLDER_ID || folderHistory.isNotEmpty())) {
        val previousIndex = folderHistory.indexOfLast { id ->
            id == UNFILED_FOLDER_ID || LibraryFolders.entries.any { it.id == id }
        }
        val previous = folderHistory.getOrNull(previousIndex)
        folderHistory = if (previousIndex < 0) emptyList() else folderHistory.take(previousIndex)
        selectedFolderId = previous ?: LibraryFolders.entries.firstOrNull {
            it.id == activeFolderId
        }?.parentId ?: UNFILED_FOLDER_ID
    }
    val countedFolderIds = activeFolderId.takeIf { it.isNotEmpty() }?.let(LibraryFolders::descendantsIncluding)
    val selectedFolderVideoCount = libraryEntries.count { entry ->
        when (activeFolderId) {
            UNFILED_FOLDER_ID -> entry.folderId == null
            else -> entry.folderId in countedFolderIds.orEmpty()
        }
    }
    val destinations = rememberSaveableStateHolder()
    val libraryProjection = remember { HubLibraryProjection() }
    LaunchedEffect(activeFolderId, LibraryFolders.revision) {
        if (selectedFolderId == null || (LibraryFolders.revision > 0L &&
                activeFolderId != UNFILED_FOLDER_ID && LibraryFolders.entries.none { it.id == activeFolderId })) {
            selectedFolderId = UNFILED_FOLDER_ID
        }
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
                                    "$selectedFolderVideoCount ${if (selectedFolderVideoCount == 1) "video" else "videos"}",
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
                                MiowserDropdownMenu(
                                    expanded = librarySortMenuExpanded,
                                    onDismissRequest = { librarySortMenuExpanded = false }
                                ) {
                                    HubLibrarySort.entries.forEach { option ->
                                        com.miowser.app.ui.MiowserMenuItem(
                                            text = { Text(option.label) },
                                            selected = option == librarySort,
                                            onClick = {
                                                librarySortMenuExpanded = false
                                                if (option != librarySort) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                                }
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
                        }
                        Box {
                            IconButton(onClick = { hubMenuExpanded = true }) {
                                Icon(Icons.Rounded.MoreVert, contentDescription = "Hub menu")
                            }
                            MiowserDropdownMenu(
                                expanded = hubMenuExpanded,
                                onDismissRequest = { hubMenuExpanded = false }
                            ) {
                                MiowserMenuItem(
                                    text = { Text("Privacy policy") },
                                    onClick = {
                                        hubMenuExpanded = false
                                        publicationPage = PublicationPage.PRIVACY
                                    }
                                )
                                MiowserMenuItem(
                                    text = { Text("Third-party notices") },
                                    onClick = {
                                        hubMenuExpanded = false
                                        publicationPage = PublicationPage.NOTICES
                                    }
                                )
                                if (selectedSection == HubSection.LIBRARY) {
                                    MiowserMenuItem(
                                        text = { Text("Clear Library") },
                                        leadingIcon = {
                                            Icon(Icons.Rounded.DeleteSweep, contentDescription = null)
                                        },
                                        enabled = libraryEntries.isNotEmpty(),
                                        onClick = {
                                            hubMenuExpanded = false
                                            showClearLibraryConfirmation = true
                                        }
                                    )
                                }
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenBrowser,
                modifier = Modifier.padding(bottom = 64.dp),
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
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Save only scroll/filter state; inactive grids and their effects are disposed.
            if (uiVisible) destinations.SaveableStateProvider(selectedSection.name) {
                key(inspectionEpoch) {
                    when (selectedSection) {
                        HubSection.LIBRARY -> HubLibraryContent(
                            onIdleChanged = onLibraryIdleChanged,
                            projection = libraryProjection,
                            uiVisible = uiVisible,
                            entries = libraryEntries,
                            onPlay = onPlayLibraryEntry,
                            onRelink = onRelinkLibraryEntry,
                            onRemove = onRemoveLibraryEntry,
                            onSave = onSaveLibraryEntry,
                            sort = librarySort,
                            searchQuery = librarySearchQuery,
                            selectedFolderId = activeFolderId,
                            onFolderSelected = { id ->
                                if (id != activeFolderId) {
                                    folderHistory = (folderHistory + activeFolderId).takeLast(32)
                                    selectedFolderId = id
                                }
                            },
                            onCreateFolder = { mode, parentId ->
                                folderCreationParentId = parentId
                                folderCreationMode = mode
                            },
                            modifier = Modifier.fillMaxSize().padding(top = 8.dp)
                        )
                        HubSection.DOWNLOADS -> HubDownloadsTabContent(
                            uiVisible = uiVisible,
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
                            onRelink = onRelinkDownload,
                            libraryEntries = libraryEntries,
                            modifier = Modifier.fillMaxSize()
                        )
                        HubSection.MORE -> HubMoreContent(
                            defaultDownloadSection = selectedDownloadSection,
                            onDefaultDownloadSectionSelected = onDownloadSectionSelected,
                            onOpenBackupRestore = onOpenBackupRestore,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            HubBottomNavigation(
                selectedSection = selectedSection,
                activeDownloadCount = downloads.count { it.status.isHubActiveDownload() },
                librarySort = librarySort,
                onLibrarySortSelected = onLibrarySortSelected,
                onOpenRecordInBrowser = onOpenRecordInBrowser,
                onSectionSelected = onSectionSelected,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    folderCreationMode?.let { mode ->
        LibraryFolderCreationFlow(mode, folderCreationParentId, onFinished = { folderCreationMode = null })
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
    publicationPage?.let { page ->
        PublicationPageContent(page, onBack = { publicationPage = null })
    }
}

@Composable
private fun HubBottomNavigation(
    selectedSection: HubSection,
    activeDownloadCount: Int,
    librarySort: HubLibrarySort,
    onLibrarySortSelected: (HubLibrarySort) -> Unit,
    onOpenRecordInBrowser: (String, Boolean) -> Unit,
    onSectionSelected: (HubSection) -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalMotionPolicy.current
    var librarySettingsExpanded by remember { mutableStateOf(false) }
    var failedVideoLogExpanded by remember { mutableStateOf(false) }
    val view = LocalView.current
    // The Scaffold content inset keeps this overlay clear of gesture and three-button navigation.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val showAllLabels = maxWidth >= 600.dp && density.fontScale <= 1.25f
        val textStyle = MaterialTheme.typography.labelLarge
        val textMeasurer = rememberTextMeasurer()
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val expandedWidths = remember(textMeasurer, textStyle, density, layoutDirection) {
            HubSection.entries.associateWith { section ->
                val label = when (section) {
                    HubSection.LIBRARY -> "Library"
                    HubSection.DOWNLOADS -> "Downloads"
                    HubSection.MORE -> "More"
                }
                56.dp + with(density) { textMeasurer.measure(label, style = textStyle).size.width.toDp() }
            }
        }
        // Reserve label space once. Changing selection must not resize text, touch targets,
        // or the shadowed Surface while the destination is composing.
        val slotLimit = ((maxWidth - 12.dp) / 3).coerceAtLeast(48.dp)
        val compactWidth = expandedWidths.values.maxOf { it }.coerceAtMost(slotLimit)
        fun segmentWidth(section: HubSection): Dp =
            if (showAllLabels) expandedWidths.getValue(section).coerceAtMost(slotLimit) else compactWidth
        // One geometry transition clock; its values are read only in drawing/layers.
        val selection = updateTransition(selectedSection, label = "Hub destination")
        val pillLeft = selection.animateDp(
            transitionSpec = { motion.spec(MotionTokens.selection) },
            label = "Hub pill position"
        ) { active ->
            // Match the segment layout's individual pixel rounding, including its gaps.
            with(density) {
                HubSection.entries.takeWhile { it != active }
                    .sumOf { section -> segmentWidth(section).roundToPx() + 2.dp.roundToPx() }
                    .toDp()
            }
        }
        val pillWidth = selection.animateDp(
            transitionSpec = { motion.spec(MotionTokens.selection) },
            label = "Hub pill width"
        ) { active -> with(density) { segmentWidth(active).roundToPx().toDp() } }
        val pillColor = MaterialTheme.colorScheme.primaryContainer
        val pillHighlight = MaterialTheme.colorScheme.onPrimaryContainer
        val pillBrush = remember(pillColor, pillHighlight) {
            Brush.horizontalGradient(listOf(pillColor, lerp(pillColor, pillHighlight, 0.06f)))
        }
        FixedHubRailViewport {
            Surface(
                shape = RoundedCornerShape(30.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp
            ) {
                Box(modifier = Modifier.padding(4.dp)) {
                    // The pill is paint only: its animation never measures a layout child.
                    Box(Modifier.matchParentSize().drawBehind {
                        val width = pillWidth.value.toPx().coerceIn(0f, size.width)
                        val start = pillLeft.value.toPx().coerceIn(0f, size.width - width)
                        val left = if (layoutDirection == LayoutDirection.Rtl) size.width - start - width else start
                        withTransform({ inset(left, 0f, size.width - left - width, 0f) }) {
                            drawRoundRect(brush = pillBrush, cornerRadius = CornerRadius(26.dp.toPx()))
                        }
                    })
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
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
                            val itemWidth = segmentWidth(section)
                            val iconOffset = selection.animateDp(
                                transitionSpec = { motion.spec(MotionTokens.selection) },
                                label = "${label} icon position"
                            ) { active ->
                                if (showAllLabels || section == active) 0.dp
                                else ((itemWidth - 24.dp) / 2 - 12.dp).coerceAtLeast(0.dp)
                            }
                            val labelAlpha = selection.animateFloat(
                                transitionSpec = { motion.spec(MotionTokens.selection) },
                                label = "${label} label"
                            ) { active -> if (showAllLabels || section == active) 1f else 0f }
                            HubNavigationItem(
                                selected = selectedSection == section,
                                width = itemWidth,
                                iconOffset = iconOffset,
                                labelAlpha = labelAlpha,
                                onClick = {
                                    if (selectedSection != section) {
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                    }
                                    onSectionSelected(section)
                                },
                                onLongClick = if (section == HubSection.LIBRARY) {
                                    {
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                        librarySettingsExpanded = true
                                    }
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
        }
    }

    if (librarySettingsExpanded) {
        ExpressiveBottomSheet(onDismissRequest = { librarySettingsExpanded = false }) {
            ExpressiveSheetHeading("Library settings")
            ExpressiveChoiceSetting(
                title = "Library order",
                description = "Choose how videos are arranged when the Library opens."
            ) {
                HubLibrarySort.entries.forEach { option ->
                    FilterChip(
                        selected = option == librarySort,
                        onClick = {
                            if (option != librarySort) {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onLibrarySortSelected(option)
                            }
                        },
                        label = { Text(option.label) }
                    )
                }
            }
            Surface(
                onClick = {
                    librarySettingsExpanded = false
                    failedVideoLogExpanded = true
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.History, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Video references", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Review playback issues and saved video details",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
    if (failedVideoLogExpanded) {
        FailedVideoLogSheet(
            onDismiss = { failedVideoLogExpanded = false },
            onOpenInBrowser = { value, search ->
                failedVideoLogExpanded = false
                onOpenRecordInBrowser(value, search)
            }
        )
    }
}

/**
 * A connected rail segment with one gesture owner, preserving the Library long-press action.
 * Fixed touch bounds; the icon position and label opacity animate only in graphics layers.
 */
@Composable
private fun HubNavigationItem(
    selected: Boolean,
    width: Dp,
    iconOffset: State<Dp>,
    labelAlpha: State<Float>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
    label: String,
    icon: @Composable () -> Unit
) {
    val motion = LocalMotionPolicy.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val iconScale = animateFloatAsState(
        targetValue = motion.pressedScale(isPressed),
        animationSpec = motion.spec(if (isPressed) MotionTokens.press else MotionTokens.release),
        label = "Hub navigation press"
    )
    val contentColor = animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motion.spec(MotionTokens.selection),
        label = "Hub navigation content"
    )
    Box(
        modifier = Modifier
            .width(width)
            .height(48.dp)
            .clip(RoundedCornerShape(26.dp))
            .semantics { this.selected = selected; contentDescription = label }
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                role = Role.Tab,
                onClick = onClick,
                onLongClickLabel = longClickLabel,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        HubNavigationIcon(iconScale, iconOffset, contentColor, icon)
        HubNavigationLabel(label, labelAlpha, contentColor)
    }
}

@Composable
private fun HubNavigationIcon(scale: State<Float>, offset: State<Dp>, color: State<Color>,
    icon: @Composable () -> Unit) {
    val direction = androidx.compose.ui.platform.LocalLayoutDirection.current
    Box(modifier = Modifier.padding(start = 12.dp).graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
        translationX = offset.value.toPx() * if (direction == LayoutDirection.Rtl) -1f else 1f
    }) {
        androidx.compose.runtime.CompositionLocalProvider(
            androidx.compose.material3.LocalContentColor provides color.value
        ) { icon() }
    }
}

@Composable
private fun HubNavigationLabel(label: String, opacity: State<Float>, color: State<Color>) {
    BasicText(
        label,
        modifier = Modifier
            .padding(start = 44.dp, end = 12.dp)
            .graphicsLayer { alpha = opacity.value }
            .clearAndSetSemantics { },
        style = MaterialTheme.typography.labelLarge,
        color = { color.value },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun HubMoreContent(
    defaultDownloadSection: HubDownloadSection,
    onDefaultDownloadSectionSelected: (HubDownloadSection) -> Unit,
    onOpenBackupRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    Column(
        modifier = modifier.testTag("hub_more").verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 88.dp),
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
                        "Choose Hub defaults and shared accessibility preferences. Browser settings are in the browser menu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        ExpressiveChoiceSetting(
            title = "Reduce motion",
            description = "Use simpler transitions in the browser and player."
        ) {
            val context = LocalContext.current
            val reduced = com.miowser.app.settings.AppSettings.current.reduceMotion
            listOf(false to "Follow system", true to "Reduce").forEach { (enabled, label) ->
                FilterChip(selected = reduced == enabled,
                    onClick = { com.miowser.app.settings.AppSettings.update(context) { it.copy(reduceMotion = enabled) } },
                    label = { Text(label) })
            }
        }

        ExpressiveChoiceSetting(
            title = "Default Downloads section",
            description = "Choose which download list opens first."
        ) {
            HubDownloadSection.entries.forEach { option ->
                FilterChip(
                    selected = option == defaultDownloadSection,
                    onClick = {
                        if (option != defaultDownloadSection) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onDefaultDownloadSectionSelected(option)
                        }
                    },
                    label = {
                        Text(if (option == HubDownloadSection.DOWNLOADING) "Downloading" else "Downloaded")
                    }
                )
            }
        }
        androidx.compose.material3.OutlinedButton(onClick = onOpenBackupRestore) {
            Text("Backup & restore")
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
    onRelink: (Download) -> Unit,
    libraryEntries: List<LibraryEntry>,
    modifier: Modifier = Modifier,
    uiVisible: Boolean = true
) {
    val downloading = downloadsForSection(downloads, HubDownloadSection.DOWNLOADING)
    val downloaded = downloadsForSection(downloads, HubDownloadSection.DOWNLOADED)
    val lists = rememberSaveableStateHolder()
    val view = LocalView.current
    Column(modifier = modifier) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            HubDownloadSection.entries.forEachIndexed { index, section ->
                SegmentedButton(
                    modifier = Modifier.weight(1f),
                    selected = selectedSection == section,
                    onClick = {
                        if (selectedSection != section) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSectionSelected(section)
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = HubDownloadSection.entries.size
                    )
                ) {
                    val count = if (section == HubDownloadSection.DOWNLOADING) {
                        downloading.size
                    } else {
                        downloaded.size
                    }
                    Text(
                        text = "${if (section == HubDownloadSection.DOWNLOADING) "Downloading" else "Downloaded"} ($count)",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
        lists.SaveableStateProvider(selectedSection.name) {
            if (selectedSection == HubDownloadSection.DOWNLOADING) {
                HubDownloadsContent(
                    downloads = downloading,
                    uiVisible = uiVisible,
                    onPause = onPause,
                    onResume = onResume,
                    onCancel = onCancel,
                    onRetry = onRetry,
                    onDelete = onDelete,
                    onRefreshLink = onRefreshLink,
                    onPlayLocal = onOpenLocal,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    bottomContentPadding = 80.dp,
                    emptyMessage = "No active or unfinished downloads."
                )
            } else {
                HubDownloadedGrid(
                    downloads = downloaded,
                    libraryEntries = libraryEntries,
                    onOpenLocal = onOpenLocal,
                    onRelink = onRelink,
                    onDelete = onDelete,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HubDownloadedGrid(
    downloads: List<Download>,
    libraryEntries: List<LibraryEntry>,
    onOpenLocal: (Download) -> Unit,
    onRelink: (Download) -> Unit,
    onDelete: (Download, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDelete by remember { mutableStateOf<Download?>(null) }
    val gridState = rememberLazyGridState()
    val libraryByMediaUri by remember(libraryEntries) {
        derivedStateOf { libraryEntries.associateBy(LibraryEntry::mediaUri) }
    }
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
            state = gridState,
            columns = GridCells.Adaptive(minSize = 168.dp),
            modifier = modifier.testTag("downloaded_grid"),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 80.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(downloads, key = { it.id }, contentType = { "download-card" }) { download ->
                val savedUri = download.savedUri?.toString()
                val context = LocalContext.current
                val scope = rememberCoroutineScope()
                val scrolling = gridState.isScrollInProgress
                var opening by remember(savedUri) { mutableStateOf(false) }
                var availabilityOverride by remember(savedUri) { mutableStateOf<Boolean?>(null) }
                val savedAvailability by produceState<Boolean?>(
                    savedUri?.let { LocalInspectionCache.peek("available", it)?.toBooleanStrictOrNull() }, savedUri, scrolling) {
                    if (scrolling || savedUri == null) return@produceState
                    if (LocalInspectionCache.peek("available", savedUri) == null) delay(250)
                    value = withContext(Dispatchers.IO) {
                        LocalInspectionCache.available(context.applicationContext, savedUri)
                    }
                }
                val fileAvailable = availabilityOverride ?: savedAvailability
                val fileType by produceState<DownloadedFileType?>(
                    initialValue = DownloadedFileInspector.cachedOrBasic(download),
                    savedUri,
                    download.displayName,
                    fileAvailable,
                    scrolling
                ) {
                    if (scrolling || fileAvailable != true) return@produceState
                    value = withContext(Dispatchers.IO) {
                        DownloadedFileInspector.inspect(context.applicationContext, download)
                    }
                }
                val libraryEntry = if (fileType == DownloadedFileType.MEDIA) {
                    libraryByMediaUri[savedUri]
                } else {
                    null
                }
                val qualityHeight by produceState<Int?>(
                    initialValue = libraryEntry?.qualityHeight ?: DownloadedFileInspector.cachedQualityHeight(savedUri),
                    savedUri,
                    fileType,
                    libraryEntry?.qualityHeight,
                    fileAvailable,
                    scrolling
                ) {
                    if (scrolling || fileAvailable != true) return@produceState
                    value = libraryEntry?.qualityHeight ?: if (fileType == DownloadedFileType.MEDIA) {
                        download.savedUri?.let { uri ->
                            withContext(Dispatchers.IO) {
                                DownloadedFileInspector.videoQualityHeight(context.applicationContext, uri)
                            }
                        }
                    } else null
                }
                val localThumbnail by produceState<String?>(
                    initialValue = libraryEntry?.thumbnailUrl ?: savedUri?.let {
                        LocalInspectionCache.peek("download-preview", it)?.ifBlank { null }
                    },
                    savedUri,
                    fileType,
                    libraryEntry?.thumbnailUrl,
                    fileAvailable,
                    gridState.isScrollInProgress
                ) {
                    if (gridState.isScrollInProgress || fileAvailable != true) return@produceState
                    delay(200)
                    value = libraryEntry?.thumbnailUrl ?: if (fileType == DownloadedFileType.MEDIA) {
                        download.savedUri?.let { uri ->
                            withContext(LibraryThumbnailStore.artworkDispatcher) {
                                LibraryThumbnailStore.ensureDownloadedThumbnail(context.applicationContext, uri)
                            }
                        }
                    } else null
                }
                val apkIcon by produceState<android.graphics.Bitmap?>(
                    initialValue = ApkIconLoader.peek(savedUri),
                    savedUri,
                    fileType,
                    fileAvailable,
                    gridState.isScrollInProgress
                ) {
                    if (gridState.isScrollInProgress || fileAvailable != true) return@produceState
                    delay(200)
                    value = if (fileType == DownloadedFileType.APK && download.savedUri != null) {
                        withContext(LibraryThumbnailStore.artworkDispatcher) {
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
                    deferArtwork = { gridState.isScrollInProgress },
                    centerArtwork = apkIcon?.asImageBitmap(),
                    actionIcon = when {
                        fileAvailable == false -> Icons.Rounded.FolderOpen
                        else -> when (fileType) {
                        DownloadedFileType.MEDIA -> Icons.Rounded.PlayCircle
                        DownloadedFileType.APK -> Icons.Rounded.InstallMobile
                        DownloadedFileType.OTHER -> Icons.AutoMirrored.Rounded.OpenInNew
                        null -> Icons.Rounded.MoreHoriz
                    } },
                    actionContentDescription = when {
                        fileAvailable == false -> "Locate downloaded file"
                        else -> when (fileType) {
                        DownloadedFileType.MEDIA -> "Play"
                        DownloadedFileType.APK -> "Install"
                        DownloadedFileType.OTHER -> "Open"
                        null -> "Checking file type"
                    } },
                    statusLabel = when {
                        fileAvailable == false -> "Locate file"
                        fileAvailable == null -> "Checking file…"
                        fileType == null -> "Checking file type…"
                        else -> null
                    },
                    qualityLabel = com.miowser.app.library.videoQualityLabel(qualityHeight),
                    onClick = {
                        if (!opening && savedUri != null) scope.launch {
                            opening = true
                            try {
                                val available = withContext(Dispatchers.IO) {
                                    LocalInspectionCache.available(context.applicationContext, savedUri, force = true)
                                }
                                availabilityOverride = available
                                if (available) onOpenLocal(download) else onRelink(download)
                            } finally { opening = false }
                        }
                    },
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
    onIdleChanged: (Boolean) -> Unit,
    projection: HubLibraryProjection,
    entries: List<LibraryEntry>,
    onPlay: (LibraryEntry, List<LibraryEntry>) -> Unit,
    onRelink: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onSave: (LibraryEntry) -> Unit,
    sort: HubLibrarySort,
    searchQuery: String,
    selectedFolderId: String,
    onFolderSelected: (String) -> Unit,
    onCreateFolder: (FolderCreationMode, String?) -> Unit,
    modifier: Modifier = Modifier,
    uiVisible: Boolean = true
) {
    var unavailableEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    val gridState = rememberLazyGridState()
    val placement = remember(selectedFolderId, sort, searchQuery) { HubListPlacement() }
    val childFolders = LibraryFolders.entries.filter { folder ->
        folder.parentId == selectedFolderId && selectedFolderId != UNFILED_FOLDER_ID &&
            (searchQuery.isBlank() || folder.name.contains(searchQuery.trim(), ignoreCase = true))
    }.sortedBy { it.id != LibraryFolders.defaultFolderId }
    val selectedFolder = LibraryFolders.entries.firstOrNull { it.id == selectedFolderId }
    // Snapshot on the UI owner; sort/filter immutable records on a worker. collectLatest
    // prevents an older query from publishing over a newer folder/search selection.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val repositoryOwned = entries === MediaLibrary.entries
    val revision = if (repositoryOwned) MediaLibrary.contentRevision else -1L
    val visibleEntries by key(selectedFolderId, sort, searchQuery) {
        produceState<List<LibraryEntry>?>(projection.peek(revision, selectedFolderId, sort, searchQuery), entries, uiVisible, lifecycle) {
            if (!uiVisible) return@produceState
            lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                snapshotFlow { (if (repositoryOwned) MediaLibrary.contentRevision else -1L) to entries.toList() }
                    .collectLatest { (version, snapshot) ->
                    val projected = projection.peek(version, selectedFolderId, sort, searchQuery) ?: withContext(Dispatchers.Default) {
                        val inFolder = snapshot.filter { entry ->
                            when (selectedFolderId) {
                                UNFILED_FOLDER_ID -> entry.folderId == null
                                else -> entry.folderId == selectedFolderId
                            }
                        }
                        sortLibraryEntries(filterLibraryEntries(inFolder, searchQuery), sort)
                    }
                    if (!repositoryOwned || MediaLibrary.contentRevision == version) {
                        projection.publish(version, selectedFolderId, sort, searchQuery, projected)
                        value = projected
                    }
                }
            }
        }
    }
    val layoutKeys = remember(childFolders, visibleEntries) {
        childFolders.map { "folder-${it.id}" } + visibleEntries.orEmpty().map { it.identityKey }
    }
    val reportIdle by rememberUpdatedState(onIdleChanged)
    DisposableEffect(Unit) { onDispose { reportIdle(false) } }
    LaunchedEffect(visibleEntries != null, gridState.isScrollInProgress, uiVisible) {
        reportIdle(false)
        if (visibleEntries != null && !gridState.isScrollInProgress && uiVisible) {
            // Let the prepared grid/empty state draw before scheduling optional browser work.
            withFrameNanos { }
            withFrameNanos { }
            reportIdle(true)
        }
    }
    val motion = LocalMotionPolicy.current
    var preparedLayout by remember(placement) { mutableStateOf<List<String>?>(null) }
    val placeItems = remember(layoutKeys, preparedLayout, visibleEntries != null,
        motion.reduced, gridState.isScrollInProgress, uiVisible) {
        visibleEntries != null && placement.update(layoutKeys, uiVisible && !motion.reduced && !gridState.isScrollInProgress)
    }
    // After a snapped structural change has laid out, re-arm the stable nodes for the next
    // manual edit. One frame per layout change, never a frame loop or an action-start delay.
    LaunchedEffect(layoutKeys, motion.reduced) {
        if (!motion.reduced) withFrameNanos { }
        preparedLayout = layoutKeys
    }
    fun removeEntry(entry: LibraryEntry) {
        onRemove(entry)
        if (entries.none { it.identityKey == entry.identityKey }) placement.removedByUser(entry.identityKey)
    }
    var previousView by rememberSaveable { mutableStateOf(listOf(selectedFolderId, sort.name, searchQuery)) }
    LaunchedEffect(selectedFolderId, sort, searchQuery) {
        val view = listOf(selectedFolderId, sort.name, searchQuery)
        if (view != previousView) gridState.scrollToItem(0)
        previousView = view
    }

    Column(modifier = modifier) {
        LibraryFolderStrip(selectedFolderId, onFolderSelected, onCreateFolder)
        if (selectedFolder?.parentId != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryFolders.lineage(selectedFolder.id).forEachIndexed { index, folder ->
                    if (index > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onFolderSelected(folder.id) }) { Text(folder.name, maxLines = 1) }
                }
            }
        }
        if (entries.isEmpty() && childFolders.isEmpty()) {
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
        } else if (visibleEntries == null) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Loading library…")
            }
        } else if (visibleEntries.orEmpty().isEmpty() && childFolders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveEmptyState(
                    title = if (searchQuery.isNotBlank()) "No matching videos" else "This folder is empty",
                    description = if (searchQuery.isNotBlank()) {
                        "Try a different search in your Library."
                    } else {
                        "Hold a video to move it here."
                    },
                    icon = Icons.Rounded.Search
                )
            }
        } else {
            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 168.dp),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("library_grid"),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 80.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(childFolders, key = { "folder-${it.id}" }, contentType = { "folder-card" }) { folder ->
                    LibrarySubfolderCard(
                        folder = folder,
                        videoCount = entries.count { it.folderId in LibraryFolders.descendantsIncluding(folder.id) },
                        onOpen = { onFolderSelected(folder.id) },
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (placeItems) motion.spec(MotionTokens.selection) else null)
                    )
                }
                items(visibleEntries.orEmpty(), key = { it.identityKey }, contentType = { "library-card" }) { entry ->
                    ManagedLibraryCard(
                        entry = entry,
                        onPlay = { selected -> onPlay(selected, visibleEntries.orEmpty()) },
                        onRemove = ::removeEntry,
                        onSave = onSave,
                        onUnavailable = { unavailableEntry = it },
                        deferArtwork = { gridState.isScrollInProgress },
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (placeItems) motion.spec(MotionTokens.selection) else null),
                        onMovedOut = { placement.removedByUser(it.identityKey) }
                    )
                }
            }
        }
    }

    unavailableEntry?.let { entry ->
        AlertDialog(
            onDismissRequest = { unavailableEntry = null },
            title = { Text("File unavailable") },
            text = { Text("Miowser can no longer open this file. If it is still on the device, locate it to reconnect this Library entry.") },
            confirmButton = {
                TextButton(onClick = {
                    unavailableEntry = null
                    onRelink(entry)
                }) { Text("Locate file") }
            },
            dismissButton = {
                Row {
                TextButton(onClick = {
                    unavailableEntry = null
                    removeEntry(entry)
                }) { Text("Remove record") }
                TextButton(onClick = { unavailableEntry = null }) { Text("Keep") }
                }
            }
        )
    }
}

@Composable
private fun ManagedLibraryCard(
    entry: LibraryEntry,
    onPlay: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onSave: (LibraryEntry) -> Unit,
    onUnavailable: (LibraryEntry) -> Unit,
    deferArtwork: () -> Boolean,
    modifier: Modifier = Modifier,
    onMovedOut: (LibraryEntry) -> Unit = { }
) {
    val context = LocalContext.current
    val isLocalFile = entry.sourceKind == LibrarySourceKind.LOCAL_FILE
    val scope = rememberCoroutineScope()
    val scrolling = deferArtwork()
    var opening by remember(entry.identityKey) { mutableStateOf(false) }
    var availabilityOverride by remember(entry.identityKey) { mutableStateOf<Boolean?>(null) }
    val savedAvailability by produceState<Boolean?>(
        initialValue = if (isLocalFile) LocalInspectionCache.peek("available", entry.mediaUri)?.toBooleanStrictOrNull() else true,
        entry.identityKey, entry.mediaUri, scrolling
    ) {
        if (scrolling) return@produceState
        if (isLocalFile && LocalInspectionCache.peek("available", entry.mediaUri) == null) delay(250)
        value = if (isLocalFile) {
            withContext(Dispatchers.IO) {
                isLibraryEntryAvailable(context.applicationContext, entry)
            }
        } else {
            true
        }
    }
    val available = availabilityOverride ?: savedAvailability
    val isUnavailable = available == false

    // Upgrade old first-frame thumbnails for local videos as cards become visible. This also
    // repairs records whose thumbnail was never captured, without reopening the player.
    LaunchedEffect(entry.identityKey, entry.qualityHeight, available, scrolling) {
        if (scrolling || !isLocalFile || available != true || entry.qualityHeight != null) {
            return@LaunchedEffect
        }
        val height = withContext(Dispatchers.IO) {
            DownloadedFileInspector.videoQualityHeight(context.applicationContext, Uri.parse(entry.mediaUri))
        }
        if (height != null) MediaLibrary.updateQuality(context.applicationContext, entry.identityKey, height)
    }
    LaunchedEffect(entry.identityKey, entry.thumbnailUrl, available, scrolling) {
        if (scrolling || !isLocalFile || available != true) {
            return@LaunchedEffect
        }
        delay(350)
        val source = entry.mediaUri
        val thumbnail = entry.thumbnailUrl.orEmpty()
        val checked = LocalInspectionCache.peek("library-preview", source)
        if (checked == thumbnail || checked == "missing:$thumbnail") return@LaunchedEffect
        val ticket = LocalInspectionCache.ticket()
        val hasMiddleFrame = withContext(LibraryThumbnailStore.artworkDispatcher) {
            LibraryThumbnailStore.isMiddleFrame(entry.thumbnailUrl)
        }
        if (hasMiddleFrame) {
            LocalInspectionCache.remember("library-preview", source, thumbnail, ticket = ticket)
            return@LaunchedEffect
        }
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            val cancel = LibraryThumbnailStore.captureMiddleFrame(
                context = context.applicationContext,
                mediaUri = Uri.parse(entry.mediaUri), headers = null,
                durationMs = entry.durationMs, identityKey = entry.identityKey
            ) { uri ->
                if (continuation.isActive) {
                    LocalInspectionCache.remember("library-preview", source, uri ?: "missing:$thumbnail",
                        if (uri != null) LocalInspectionCache.SUCCESS_MS else LocalInspectionCache.RETRY_MS, ticket)
                    if (uri != null) MediaLibrary.updateThumbnail(context.applicationContext, entry.identityKey, uri)
                    continuation.resumeWith(Result.success(Unit))
                }
            }
            continuation.invokeOnCancellation { cancel() }
        }
    }

    HubVideoCard(
        title = entry.title.ifBlank { "Untitled video" },
        sourceLabel = entry.sourceLabel,
        positionMs = entry.positionMs,
        durationMs = entry.durationMs,
        modifier = modifier.fillMaxWidth(),
        thumbnailUrl = entry.thumbnailUrl,
        deferArtwork = deferArtwork,
        statusLabel = when {
            isUnavailable -> "File unavailable"
            isLocalFile && available == null -> "Checking file…"
            else -> null
        },
        qualityLabel = com.miowser.app.library.videoQualityLabel(
            entry.qualityHeight ?: if (isLocalFile) DownloadedFileInspector.cachedQualityHeight(entry.mediaUri) else null),
        menuOnLongPress = true,
        onClick = {
            if (!opening) {
                if (!isLocalFile) onPlay(entry)
                else scope.launch {
                    opening = true
                    try {
                        val fresh = withContext(Dispatchers.IO) { isLibraryEntryAvailable(context, entry, force = true) }
                        availabilityOverride = fresh
                        if (fresh) onPlay(entry) else onUnavailable(entry)
                    } finally { opening = false }
                }
            }
        },
        menuContentDescription = "Library options",
        menuActionLabel = "Remove from Library",
        onMenuAction = { onRemove(entry) },
        folderMenuOnSelect = { folderId ->
            runCatching { MediaLibrary.moveToFolder(context, entry.identityKey, folderId) }.onSuccess {
                if (folderId != entry.folderId) onMovedOut(entry)
            }.onFailure {
                android.widget.Toast.makeText(context, it.message ?: "Could not move video", android.widget.Toast.LENGTH_LONG).show()
            }
        },
        currentFolderId = entry.folderId,
        tertiaryMenuActionLabel = "Save video details",
        onTertiaryMenuAction = { onSave(entry) }
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
