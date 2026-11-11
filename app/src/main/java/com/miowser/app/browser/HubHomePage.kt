package com.miowser.app.browser

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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import com.miowser.app.ui.MiowserLoadingIndicator
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.Surface
import androidx.compose.material3.FilterChip
import com.miowser.app.ui.AppText as Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miowser.app.downloader.Download
import com.miowser.app.R
import com.miowser.app.downloader.ApkIconLoader
import com.miowser.app.downloader.DownloadedFileType
import com.miowser.app.downloader.DownloadedFileInspector
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.downloader.DownloadDirectory
import com.miowser.app.downloader.DownloadDirectoryMode
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibraryFolder
import com.miowser.app.library.LibraryFolders
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.LibraryRepository
import com.miowser.app.library.LibraryBrowseQuery
import com.miowser.app.library.LibraryCardId
import com.miowser.app.ui.theme.HubVideoCardTokens
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.library.isLibraryEntryAvailable
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.ExpressiveBottomSheet
import com.miowser.app.ui.ClearLibraryConfirmationDialog
import com.miowser.app.ui.ChoiceRow
import com.miowser.app.ui.ExpressiveChoiceSetting
import com.miowser.app.ui.SettingsSection
import com.miowser.app.ui.SectionDivider
import com.miowser.app.ui.NavigationRow
import com.miowser.app.ui.ExpressiveSectionCard
import com.miowser.app.ui.ExpressiveSheetHeading
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.MiowserConfirmationDialog
import com.miowser.app.ui.MiowserSnackbarHost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import com.miowser.app.library.LocalInspectionCache
import java.net.URI
import com.miowser.app.ui.theme.ExpressiveTokens
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import com.miowser.app.ui.theme.UiElevation
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.theme.HubLibraryTokens
import com.miowser.app.ui.AppButtonStyle
import com.miowser.app.ui.MiowserActionButton

enum class HubSection { DEVICE, LIBRARY, DOWNLOADS, MORE }
enum class HubDownloadSection { DOWNLOADING, DOWNLOADED }
enum class HubLibrarySort(val label: String) {
    RECENT("Recently played"),
    TITLE("Title"),
    PROGRESS("Watched progress")
}
private enum class HubLibraryView { GRID, LIST }

/** Root Hub with bottom navigation for Library, Downloads, and independent Hub settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HubHomePage(
    selectedSection: HubSection,
    libraryCache: HubLibraryCache,
    inspectionEpoch: Long,
    librarySettingsOpen: Boolean,
    onDismissLibrarySettings: () -> Unit,
    libraryHealthVisible: Boolean,
    onLibraryHealthVisibilityChanged: (Boolean) -> Unit,
    selectedDownloadSection: HubDownloadSection,
    onDownloadSectionSelected: (HubDownloadSection) -> Unit,
    libraryEntries: List<LibraryEntry>,
    onPlayLibraryEntry: (LibraryEntry, LibraryBrowseQuery) -> Unit,
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
    onOpenMediaSettings: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onOpenRecordInBrowser: (String, Boolean) -> Unit,
    bottomBarHeight: Dp,
    uiVisible: Boolean = true,
    onLibraryIdleChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val libraryEnabled = com.miowser.app.settings.AppSettings.current.libraryBehavior.isAvailable
    val focusManager = LocalFocusManager.current
    val view = LocalView.current
    var librarySearchVisible by rememberSaveable { mutableStateOf(false) }
    var librarySearchQuery by rememberSaveable { mutableStateOf("") }
    var showClearLibraryConfirmation by remember { mutableStateOf(false) }
    val viewPrefs = remember(context) { context.getSharedPreferences("hub_library_view", android.content.Context.MODE_PRIVATE) }
    var libraryView by rememberSaveable { mutableStateOf(
        if (viewPrefs.getBoolean("list", false)) HubLibraryView.LIST else HubLibraryView.GRID) }
    var creatingFolder by remember { mutableStateOf(false) }
    var folderCreationParentId by remember { mutableStateOf<String?>(null) }
    // Older saved state may still contain null from the removed All view.
    var selectedFolderId by rememberSaveable { mutableStateOf<String?>(UNFILED_FOLDER_ID) }
    val activeFolderId = selectedFolderId ?: UNFILED_FOLDER_ID
    var librarySort by remember(activeFolderId) {
        mutableStateOf(HubSettingsStore.loadLibrarySort(context, activeFolderId))
    }
    val selectLibrarySort: (HubLibrarySort) -> Unit = { sort ->
        librarySort = sort
        HubSettingsStore.saveLibrarySort(context, activeFolderId, sort)
    }
    var folderHistory by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val libraryActionScope = rememberCoroutineScope()
    val librarySnackbar = remember { SnackbarHostState() }
    var failedVideoLogExpanded by remember { mutableStateOf(false) }
    if (libraryHealthVisible) {
        LibraryHealthPage(
            entries = libraryEntries.toList(),
            folders = LibraryFolders.entries.toList(),
            onBack = { onLibraryHealthVisibilityChanged(false) },
            onLocate = onRelinkLibraryEntry,
            onRemove = onRemoveLibraryEntry,
            onOpenSource = { url -> onOpenRecordInBrowser(url, false) }
        )
        return
    }
    BackHandler(enabled = uiVisible && selectedSection == HubSection.LIBRARY &&
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
    val directCounts = rememberLibraryFolderCounts(uiVisible && selectedSection == HubSection.LIBRARY, libraryCache)
    val foldersForCounts = LibraryFolders.entries.toList()
    val folderVideoCounts = remember(directCounts, foldersForCounts) { countLibraryBranches(directCounts, foldersForCounts) }
    val destinations = rememberSaveableStateHolder()
    val libraryViews = rememberSaveableStateHolder()
    val libraryHasEntries by remember(libraryEntries) { derivedStateOf { libraryEntries.isNotEmpty() } }
    LaunchedEffect(activeFolderId, LibraryFolders.revision) {
        if (selectedFolderId == null || (LibraryFolders.revision > 0L &&
                activeFolderId != UNFILED_FOLDER_ID && LibraryFolders.entries.none { it.id == activeFolderId })) {
            selectedFolderId = UNFILED_FOLDER_ID
        }
    }

    Scaffold(
        snackbarHost = {
            MiowserSnackbarHost(
                hostState = librarySnackbar,
                modifier = Modifier.padding(bottom = bottomBarHeight + UiSpacing.s)
            )
        },
        topBar = {
            Column {
                LibraryHubTopBar(
                    branded = selectedSection != HubSection.MORE,
                    title = {
                        Text(if (selectedSection == HubSection.MORE) "More" else stringResource(R.string.app_name),
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    actions = {
                        if (selectedSection == HubSection.LIBRARY) {
                            FilledTonalIconButton(onClick = {
                                librarySearchVisible = !librarySearchVisible
                                if (!librarySearchVisible) librarySearchQuery = ""
                            }) {
                                Icon(
                                    if (librarySearchVisible) Icons.Rounded.Close else Icons.Rounded.Search,
                                    contentDescription = if (librarySearchVisible) "Close Library search" else "Search Library"
                                )
                            }
                        }
                    }
                )
                if (selectedSection == HubSection.LIBRARY && librarySearchVisible) {
                    OutlinedTextField(
                        value = librarySearchQuery,
                        onValueChange = { librarySearchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.l, vertical = 6.dp)
                            .testTag("library_search"),
                        placeholder = { Text("Search videos") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            SearchFieldActions(
                                showClear = librarySearchQuery.isNotEmpty(),
                                onClear = { librarySearchQuery = "" },
                                onSubmit = { focusManager.clearFocus() },
                                submitEnabled = librarySearchQuery.isNotBlank(),
                                submitDescription = "Finish Library search"
                            )
                        },
                        singleLine = true,
                        shape = MaterialTheme.shapes.extraLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // Save only scroll/filter state; inactive grids and their effects are disposed.
            if (uiVisible) destinations.SaveableStateProvider(selectedSection.name) {
                key(inspectionEpoch) {
                    when (selectedSection) {
                        HubSection.DEVICE -> Unit
                        HubSection.LIBRARY -> LibraryViewState(
                            libraryViews, "${libraryView.name}:${librarySort.name}:$activeFolderId", librarySearchQuery
                        ) { HubLibraryContent(
                            cache = libraryCache,
                            onIdleChanged = onLibraryIdleChanged,
                            hasEntries = libraryHasEntries,
                            folderVideoCounts = folderVideoCounts,
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
                            onCreateFolder = { parentId ->
                                folderCreationParentId = parentId
                                creatingFolder = true
                            },
                            onDeleteFolder = { folder, withVideos ->
                                val beforeFolders = LibraryFolders.entries.toList()
                                val previousDefault = LibraryFolders.defaultFolderId
                                val affectedIds = if (withVideos) LibraryFolders.descendantsIncluding(folder.id)
                                    else setOf(folder.id) + beforeFolders.filter { it.parentId == folder.id }.map { it.id }
                                val originalEntries = libraryEntries.filter { entry ->
                                    entry.folderId == folder.id || (withVideos && entry.folderId in affectedIds)
                                }
                                LibraryRepository.batchStructure(context) {
                                    if (withVideos) {
                                        MediaLibrary.removeAll(context, originalEntries)
                                        LibraryFolders.removeTree(context, folder.id)
                                    } else {
                                        MediaLibrary.relocateFolder(context, folder.id, folder.parentId)
                                        LibraryFolders.remove(context, folder.id)
                                    }
                                }
                                if (activeFolderId == folder.id || (withVideos && activeFolderId in affectedIds)) {
                                    selectedFolderId = folder.parentId ?: UNFILED_FOLDER_ID
                                }
                                librarySnackbar.currentSnackbarData?.dismiss()
                                libraryActionScope.launch {
                                    val result = librarySnackbar.showSnackbar(
                                        if (withVideos) "Folder and Library entries removed" else "Folder dissolved",
                                        actionLabel = "Undo", duration = SnackbarDuration.Long)
                                    if (result == SnackbarResult.ActionPerformed) {
                                        LibraryRepository.batchStructure(context) {
                                            LibraryFolders.restoreDeletion(context, beforeFolders, affectedIds, previousDefault)
                                            MediaLibrary.restoreFolderEntries(context, originalEntries)
                                        }
                                    }
                                }
                            },
                            libraryView = libraryView,
                            modifier = Modifier.fillMaxSize().padding(top = UiSpacing.s)
                        ) }
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
                            libraryEntries = if (libraryEnabled) libraryEntries else emptyList(),
                            modifier = Modifier.fillMaxSize()
                        )
                        HubSection.MORE -> HubMoreContent(
                            onOpenMediaSettings = onOpenMediaSettings,
                            onOpenBackupRestore = onOpenBackupRestore,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }

    if (librarySettingsOpen && libraryEnabled) {
        LibraryOptionsSheet("Saved Library", onDismissLibrarySettings) {
            LibraryOptionsGroup("View", Icons.Rounded.GridView) {
                ChoiceRow(HubLibraryView.entries, libraryView, { if (it == HubLibraryView.GRID) "Grid" else "List" }) {
                    libraryView = it
                    viewPrefs.edit().putBoolean("list", it == HubLibraryView.LIST).apply()
                }
            }
            LibraryOptionsGroup("Sort this folder", Icons.AutoMirrored.Rounded.Sort) {
                ChoiceRow(HubLibrarySort.entries, librarySort, { it.label }, selectLibrarySort)
            }
            LibraryOptionsGroup("Library tools", Icons.Rounded.Settings) {
                LibraryOptionAction("Library health", Icons.Rounded.HealthAndSafety) {
                    onDismissLibrarySettings()
                    onLibraryHealthVisibilityChanged(true)
                }
                LibraryOptionAction("Video references", Icons.Rounded.History) {
                    onDismissLibrarySettings()
                    failedVideoLogExpanded = true
                }
                LibraryOptionAction("Clear Library", Icons.Rounded.DeleteSweep, enabled = libraryHasEntries,
                    destructive = true) {
                    onDismissLibrarySettings()
                    showClearLibraryConfirmation = true
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

    if (creatingFolder) {
        LibraryFolderCreationFlow(folderCreationParentId, onFinished = { creatingFolder = false })
    }
    if (showClearLibraryConfirmation) {
        ClearLibraryConfirmationDialog(
            onDismiss = { showClearLibraryConfirmation = false },
            onClear = onClearLibrary
        )
    }
}

@Composable
internal fun HubBottomNavigation(
    selectedSection: HubSection,
    activeDownloadCount: Int,
    sections: List<HubSection>,
    libraryBusy: Boolean,
    onOpenLibrarySettings: (HubSection) -> Unit,
    selectedDownloadSection: HubDownloadSection,
    onDownloadSectionSelected: (HubDownloadSection) -> Unit,
    downloadDirectory: DownloadDirectory?,
    customDownloadDirectoryAccessible: Boolean,
    onUseDefaultDownloadDirectory: () -> Unit,
    onChooseCustomDownloadDirectory: () -> Unit,
    onRelinkConfiguredDownloads: () -> Unit,
    onSectionSelected: (HubSection) -> Unit,
    onOpenBrowser: () -> Unit,
    modifier: Modifier = Modifier
) {
    val motion = LocalMotionPolicy.current
    var downloadSettingsExpanded by remember { mutableStateOf(false) }
    val view = LocalView.current
    val labels = mapOf(
        HubSection.DEVICE to stringResource(R.string.nav_device),
        HubSection.LIBRARY to stringResource(R.string.nav_network),
        HubSection.DOWNLOADS to stringResource(R.string.nav_downloads),
        HubSection.MORE to stringResource(R.string.nav_more)
    )
    // The Scaffold content inset keeps this overlay clear of gesture and three-button navigation.
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = UiSpacing.m, vertical = UiSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val textStyle = MaterialTheme.typography.labelLarge
        val textMeasurer = rememberTextMeasurer()
        val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
        val expandedWidths = remember(textMeasurer, textStyle, density, layoutDirection, labels, sections) {
            sections.associateWith { section ->
                val label = labels.getValue(section)
                56.dp + with(density) { textMeasurer.measure(label, style = textStyle).size.width.toDp() }
            }
        }
        val segmentSpace = (maxWidth - UiControlSize.touchTarget - UiSpacing.s -
            UiSpacing.xs * 2 - UiSpacing.xxs * (sections.size - 1)).coerceAtLeast(0.dp)
        val iconWidth = UiControlSize.touchTarget.coerceAtMost(segmentSpace / sections.size)
        fun segmentWidth(section: HubSection, active: HubSection): Dp =
            if (section == active) expandedWidths.getValue(section)
                .coerceAtMost(segmentSpace - iconWidth * (sections.size - 1))
            else iconWidth
        val selection = updateTransition(selectedSection, label = "Hub destination")
        val pillLeft = selection.animateDp(
            transitionSpec = { motion.spec(MotionTokens.selection) },
            label = "Hub pill position"
        ) { active ->
            with(density) {
                sections.takeWhile { it != active }
                    .sumOf { section -> segmentWidth(section, active).roundToPx() + 2.dp.roundToPx() }
                    .toDp()
            }
        }
        val pillWidth = selection.animateDp(
            transitionSpec = { motion.spec(MotionTokens.selection) },
            label = "Hub pill width"
        ) { active ->
            with(density) { segmentWidth(active, active).roundToPx().toDp() }
        }
        val pillColor = MaterialTheme.colorScheme.primaryContainer
        val pillHighlight = MaterialTheme.colorScheme.onPrimaryContainer
        val pillBrush = remember(pillColor, pillHighlight) {
            Brush.horizontalGradient(listOf(pillColor, lerp(pillColor, pillHighlight, 0.06f)))
        }
        FixedHubRailViewport {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)
            ) {
                Surface(
                    modifier = if (libraryBusy) libraryBusyOutline() else Modifier,
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shadowElevation = UiElevation.raised
                ) {
                    Box(modifier = Modifier.padding(UiSpacing.xs)) {
                        // The highlight and segment widths share the same transition clock.
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
                            horizontalArrangement = Arrangement.spacedBy(UiSpacing.xxs)
                        ) {
                            sections.forEach { section ->
                                val label = labels.getValue(section)
                                val itemWidth = selection.animateDp(
                                    transitionSpec = { motion.spec(MotionTokens.selection) },
                                    label = "${label} width"
                                ) { active -> segmentWidth(section, active) }
                                val labelAlpha = selection.animateFloat(
                                    transitionSpec = { motion.spec(MotionTokens.selection) },
                                    label = "${label} label"
                                ) { active -> if (section == active) 1f else 0f }
                                HubNavigationItem(
                                    testTag = if (section == HubSection.LIBRARY) "hub_nav_network"
                                        else "hub_nav_${section.name.lowercase()}",
                                    selected = selectedSection == section,
                                    width = itemWidth,
                                    labelAlpha = labelAlpha,
                                    onClick = {
                                        if (selectedSection != section) {
                                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        }
                                        onSectionSelected(section)
                                    },
                                    onLongClick = when (section) {
                                        HubSection.DEVICE, HubSection.LIBRARY -> ({
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            onOpenLibrarySettings(section)
                                        })
                                        HubSection.DOWNLOADS -> ({
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            downloadSettingsExpanded = true
                                        })
                                        HubSection.MORE -> null
                                    },
                                    longClickLabel = when (section) {
                                        HubSection.DEVICE, HubSection.LIBRARY ->
                                            stringResource(R.string.nav_open_section_settings, label)
                                        HubSection.DOWNLOADS -> "Open Downloads settings"
                                        HubSection.MORE -> null
                                    },
                                    label = label,
                                    spokenLabel = if (section == HubSection.DOWNLOADS && activeDownloadCount > 0) {
                                        pluralStringResource(R.plurals.nav_downloads_active, activeDownloadCount, activeDownloadCount)
                                    } else label,
                                    icon = {
                                        if (section == HubSection.DOWNLOADS && activeDownloadCount > 0) {
                                            BadgedBox(
                                                badge = { Badge(Modifier.clearAndSetSemantics { }) {
                                                    Text(activeDownloadCount.coerceAtMost(99).toString())
                                                } }
                                            ) {
                                                Icon(Icons.Rounded.Download, contentDescription = null)
                                            }
                                        } else when (section) {
                                            HubSection.DEVICE -> Icon(painterResource(R.drawable.ic_hexagon_symbol_rounded), null)
                                            HubSection.LIBRARY -> Icon(painterResource(R.drawable.ic_blur_on_symbol_rounded), null)
                                            HubSection.DOWNLOADS -> Icon(Icons.Rounded.Download, null)
                                            HubSection.MORE -> Icon(Icons.Rounded.MoreHoriz, null)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                FloatingActionButton(
                    onClick = onOpenBrowser,
                    modifier = Modifier.size(UiControlSize.touchTarget),
                    shape = MaterialTheme.shapes.large,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = UiElevation.raised)
                ) {
                    Icon(Icons.Rounded.Language, contentDescription = "Browse",
                        modifier = Modifier.size(UiIconSize.standard))
                }
            }
        }
    }

    if (downloadSettingsExpanded) {
        ExpressiveBottomSheet(onDismissRequest = { downloadSettingsExpanded = false }) {
            ExpressiveSheetHeading("Downloads settings")
            ExpressiveChoiceSetting(
                title = "Open Downloads on",
                description = "Choose which list is shown first when you open Downloads."
            ) {
                HubDownloadSection.entries.forEach { option ->
                    FilterChip(
                        selected = option == selectedDownloadSection,
                        onClick = { onDownloadSectionSelected(option) },
                        label = { Text(if (option == HubDownloadSection.DOWNLOADING) "Downloading" else "Downloaded") }
                    )
                }
            }
            ExpressiveChoiceSetting(
                title = "Download folder",
                description = when {
                    downloadDirectory == null ->
                        "No folder selected. Your next download will ask where to save files."
                    downloadDirectory.mode == DownloadDirectoryMode.CUSTOM && !customDownloadDirectoryAccessible ->
                        "${downloadDirectory.label} needs permission again before Miowser can use or reconnect its files."
                    else -> "Files are saved in ${downloadDirectory.label}."
                }
            ) {
                FilterChip(
                    selected = downloadDirectory?.mode == DownloadDirectoryMode.DEFAULT,
                    onClick = {
                        downloadSettingsExpanded = false
                        onUseDefaultDownloadDirectory()
                    },
                    label = { Text("Default") }
                )
                FilterChip(
                    selected = downloadDirectory?.mode == DownloadDirectoryMode.CUSTOM,
                    onClick = {
                        downloadSettingsExpanded = false
                        onChooseCustomDownloadDirectory()
                    },
                    label = { Text(if (downloadDirectory?.mode == DownloadDirectoryMode.CUSTOM) "Change custom" else "Custom") }
                )
            }
            Surface(
                onClick = {
                    downloadSettingsExpanded = false
                    onRelinkConfiguredDownloads()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Row(
                    modifier = Modifier.padding(UiSpacing.l),
                    horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text("Reconnect downloaded files", style = MaterialTheme.typography.titleSmall)
                        Text("Match saved records to files in this folder",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/**
 * A connected rail segment with one gesture owner, preserving the Library long-press action.
 * Only the active segment has space for its label; inactive segments keep 48dp touch targets.
 */
@Composable
private fun HubNavigationItem(
    testTag: String,
    selected: Boolean,
    width: State<Dp>,
    labelAlpha: State<Float>,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
    label: String,
    spokenLabel: String,
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
            .testTag(testTag)
            .layout { measurable, constraints ->
                // Read the animated width during measure, leaving composition off the frame clock.
                val widthPx = width.value.roundToPx().coerceIn(constraints.minWidth, constraints.maxWidth)
                val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
                layout(widthPx, placeable.height) { placeable.placeRelative(0, 0) }
            }
            .height(48.dp)
            .clip(RoundedCornerShape(26.dp))
            .semantics { this.selected = selected; contentDescription = spokenLabel }
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
        HubNavigationIcon(iconScale, contentColor, icon)
        HubNavigationLabel(label, labelAlpha, contentColor)
    }
}

@Composable
private fun HubNavigationIcon(scale: State<Float>, color: State<Color>,
    icon: @Composable () -> Unit) {
    Box(modifier = Modifier.padding(start = UiSpacing.m).graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
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
            .padding(start = 44.dp, end = UiSpacing.m)
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
    onOpenMediaSettings: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.testTag("hub_more").verticalScroll(rememberScrollState())
            .padding(start = UiSpacing.xl, end = UiSpacing.xl, top = UiSpacing.xl, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(ExpressiveTokens.sectionGap)
    ) {
        ExpressiveSectionCard(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(UiSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.comfortable),
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
                        "Manage media, playback, and app preferences.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        SettingsSection("Settings") {
            NavigationRow("Media settings", "Manage device folders, playback, downloads, and appearance.",
                onClick = onOpenMediaSettings)
            SectionDivider()
            NavigationRow("Backup & restore", "Save app data or restore a previous backup.",
                onClick = onOpenBackupRestore)
        }
    }
}

internal fun DownloadStatus.isHubActiveDownload(): Boolean =
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
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.l, vertical = UiSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
        ) {
            HubDownloadSection.entries.forEachIndexed { index, section ->
                ToggleButton(
                    modifier = Modifier.weight(1f)
                        .testTag("hub_downloads_${section.name.lowercase()}")
                        .semantics { role = Role.RadioButton },
                    checked = selectedSection == section,
                    onCheckedChange = { checked ->
                        if (checked && selectedSection != section) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            onSectionSelected(section)
                        }
                    },
                    shapes = if (index == 0) ButtonGroupDefaults.connectedLeadingButtonShapes()
                        else ButtonGroupDefaults.connectedTrailingButtonShapes()
                ) {
                    val count = if (section == HubDownloadSection.DOWNLOADING) {
                        downloading.size
                    } else {
                        downloaded.size
                    }
                    Text(
                        text = stringResource(
                            if (section == HubDownloadSection.DOWNLOADING) R.string.download_section_downloading
                            else R.string.download_section_downloaded,
                            count
                        ),
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
                    bottomContentPadding = HubLibraryTokens.gridBottomClearance,
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
    val context = LocalContext.current
    val gridState = rememberLazyGridState()
    val libraryByMediaUri by remember(libraryEntries) {
        derivedStateOf { libraryEntries.associateBy(LibraryEntry::mediaUri) }
    }
    LaunchedEffect(downloads, libraryByMediaUri, gridState) {
        snapshotFlow {
            if (gridState.isScrollInProgress) null
            else gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.distinctUntilChanged().collectLatest { lastVisible ->
            if (lastVisible == null) return@collectLatest
            downloads.asSequence().drop(lastVisible + 1).take(6).mapNotNull { download ->
                val uri = download.savedUri?.toString() ?: return@mapNotNull null
                libraryByMediaUri[uri]?.thumbnailUrl
            }.distinct().forEach { LibraryThumbnailStore.prefetch(context.applicationContext, it) }
        }
    }
    if (downloads.isEmpty()) {
        Box(modifier = modifier.padding(UiSpacing.xxl), contentAlignment = Alignment.Center) {
            ExpressiveEmptyState(
                title = "No downloaded files",
                description = "Completed downloads will appear here.",
                icon = Icons.Rounded.Download
            )
        }
    } else {
        LazyVerticalGrid(
            state = gridState,
            columns = GridCells.Adaptive(minSize = HubLibraryTokens.gridMinCell),
            modifier = modifier.testTag("downloaded_grid"),
            contentPadding = PaddingValues(start = HubLibraryTokens.gridPadding,
                end = HubLibraryTokens.gridPadding, top = HubLibraryTokens.gridPadding,
                bottom = HubLibraryTokens.gridBottomClearance),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.m)
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
                val localProgress = if (fileType == DownloadedFileType.MEDIA && savedUri != null)
                    com.miowser.app.player.LocalPlaybackProgressStore.load(context, savedUri) else null
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
                val savedThumbnail = libraryEntry?.thumbnailUrl
                var localPreview by remember(savedUri, savedThumbnail) { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(savedUri, savedThumbnail, fileType, fileAvailable, scrolling) {
                    if (scrolling || fileAvailable != true || savedThumbnail != null ||
                        fileType != DownloadedFileType.MEDIA || localPreview != null) return@LaunchedEffect
                    delay(200)
                    localPreview = download.savedUri?.let { LibraryThumbnailStore.loadLocalFrame(context.applicationContext, it) }
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
                        LibraryThumbnailStore.loadApkIcon(context.applicationContext, download.savedUri!!)
                    } else {
                        null
                    }
                }
                HubVideoCard(
                    title = download.displayName.ifBlank { "Downloaded file" },
                    sourceLabel = downloadSourceLabel(download),
                    positionMs = localProgress?.positionMs ?: libraryEntry?.positionMs ?: 0L,
                    durationMs = localProgress?.durationMs ?: libraryEntry?.durationMs ?: 0L,
                    thumbnailUrl = savedThumbnail,
                    localPreview = localPreview.takeIf { fileAvailable == true },
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
        MiowserConfirmationDialog(
            title = "Delete download?",
            message = "Remove only Miowser's record, or delete the downloaded file too?",
            onDismiss = { pendingDelete = null },
            actions = listOf(
                ConfirmationAction("Cancel", { pendingDelete = null }, ConfirmationTone.CAUTION),
                ConfirmationAction("Record only", {
                        pendingDelete = null
                        onDelete(download, false)
                    }, ConfirmationTone.CAUTION),
                ConfirmationAction("File and record", {
                        pendingDelete = null
                        onDelete(download, true)
                    }, ConfirmationTone.CAUTION)
            )
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
private fun LibraryViewState(
    holder: androidx.compose.runtime.saveable.SaveableStateHolder,
    viewKey: String,
    search: String,
    content: @Composable () -> Unit
) {
    if (search.isBlank()) holder.SaveableStateProvider(viewKey, content)
    else key(viewKey, search.trim()) { content() }
}

@Composable
private fun HubLibraryContent(
    cache: HubLibraryCache,
    onIdleChanged: (Boolean) -> Unit,
    hasEntries: Boolean,
    folderVideoCounts: Map<String?, Int>,
    onPlay: (LibraryEntry, LibraryBrowseQuery) -> Unit,
    onRelink: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onSave: (LibraryEntry) -> Unit,
    sort: HubLibrarySort,
    searchQuery: String,
    selectedFolderId: String,
    onFolderSelected: (String) -> Unit,
    onCreateFolder: (String?) -> Unit,
    onDeleteFolder: (LibraryFolder, Boolean) -> Unit,
    libraryView: HubLibraryView,
    modifier: Modifier = Modifier
) {
    var unavailableEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    val gridState = rememberLazyGridState()
    val placement = remember(selectedFolderId, sort, searchQuery) { HubListPlacement() }
    val childFolders = LibraryFolders.entries.filter { folder ->
        folder.parentId == selectedFolderId && selectedFolderId != UNFILED_FOLDER_ID &&
            (searchQuery.isBlank() || folder.name.contains(searchQuery.trim(), ignoreCase = true))
    }.sortedBy { it.id != LibraryFolders.defaultFolderId }
    val selectedFolder = LibraryFolders.entries.firstOrNull { it.id == selectedFolderId }
    val query = remember(selectedFolderId, sort, searchQuery.trim()) {
        LibraryBrowseQuery(selectedFolderId.takeIf { it.isNotEmpty() }, sort, searchQuery.trim())
    }
    val pages = rememberHubLibraryPages(query, gridState, childFolders.size, cache)
    val window = pages.window
    val artworkContext = LocalContext.current.applicationContext
    LaunchedEffect(window, childFolders.size, gridState) {
        snapshotFlow {
            if (gridState.isScrollInProgress) null
            else gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
        }.distinctUntilChanged().collectLatest { lastVisible ->
            if (lastVisible == null) return@collectLatest
            val next = (lastVisible + 1 - childFolders.size).coerceAtLeast(0)
            (next until next + 6).asSequence()
                .mapNotNull { window?.get(it)?.let(MediaLibrary::entryForCard)?.thumbnailUrl }.distinct()
                .forEach { LibraryThumbnailStore.prefetch(artworkContext, it) }
        }
    }
    val layoutKeys = remember(childFolders, window) {
        childFolders.map { "folder-${it.id}" } + window?.ids.orEmpty().map { it.gridKey }
    }
    val reportIdle by rememberUpdatedState(onIdleChanged)
    DisposableEffect(Unit) { onDispose { reportIdle(false) } }
    LaunchedEffect(window != null, gridState.isScrollInProgress) {
        reportIdle(false)
        if (window != null && !gridState.isScrollInProgress) {
            // Let the prepared grid/empty state draw before scheduling optional browser work.
            withFrameNanos { }
            withFrameNanos { }
            reportIdle(true)
        }
    }
    val motion = LocalMotionPolicy.current
    var preparedLayout by remember(placement) { mutableStateOf<List<String>?>(null) }
    val placeItems = remember(layoutKeys, preparedLayout, window != null, motion.reduced, gridState.isScrollInProgress) {
        window != null && placement.update(layoutKeys, !motion.reduced && !gridState.isScrollInProgress)
    }
    // After a snapped structural change has laid out, re-arm the stable nodes for the next
    // manual edit. One frame per layout change, never a frame loop or an action-start delay.
    LaunchedEffect(layoutKeys, motion.reduced) {
        if (!motion.reduced) withFrameNanos { }
        preparedLayout = layoutKeys
    }
    Column(modifier = modifier) {
        LibraryFolderStrip(selectedFolderId, onFolderSelected, onCreateFolder, onDeleteFolder)
        if (selectedFolder?.parentId != null) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                    .padding(horizontal = UiSpacing.m),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryFolders.lineage(selectedFolder.id).forEachIndexed { index, folder ->
                    if (index > 0) Text(" / ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { onFolderSelected(folder.id) }) { Text(folder.name, maxLines = 1) }
                }
            }
        }
        if (pages.failed) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                TextButton(onClick = { pages.retry++ }) { Text("Retry loading Library") }
            }
        } else if (!hasEntries && childFolders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(UiSpacing.xxl),
                contentAlignment = Alignment.Center
            ) {
                ExpressiveEmptyState(
                    title = "Your Library is empty",
                    description = "Online videos opened in Miowser's player will appear here.",
                    icon = Icons.Rounded.VideoLibrary
                )
            }
        } else if (window == null) {
            Spacer(Modifier.weight(1f))
        } else if (window.count == 0 && childFolders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(UiSpacing.xxl),
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
                columns = if (libraryView == HubLibraryView.GRID)
                    GridCells.Adaptive(minSize = HubLibraryTokens.gridMinCell) else GridCells.Fixed(1),
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("library_grid"),
                contentPadding = PaddingValues(start = HubLibraryTokens.gridPadding,
                    end = HubLibraryTokens.gridPadding, bottom = HubLibraryTokens.gridBottomClearance),
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.m)
            ) {
                items(childFolders, key = { "folder-${it.id}" }, contentType = { "folder-card" }) { folder ->
                    LibrarySubfolderCard(
                        folder = folder,
                        videoCount = folderVideoCounts[folder.id] ?: 0,
                        onOpen = { onFolderSelected(folder.id) },
                        onCreateSubfolder = { onCreateFolder(folder.id) },
                        onDeleteFolder = onDeleteFolder,
                        listMode = libraryView == HubLibraryView.LIST,
                        modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                            placementSpec = if (placeItems) motion.spec(MotionTokens.selection) else null)
                    )
                }
                items(window.count, key = { window[it]?.gridKey ?: "pending:$it" }, contentType = { "library-card" }) { index ->
                    val id = window[index]
                    val entry = if (id == null) null else observeLibraryCard(id)
                    if (entry == null || id == null) {
                        LibraryCardPlaceholder(listMode = libraryView == HubLibraryView.LIST)
                    } else {
                        ManagedLibraryCard(
                            entry = entry,
                            listMode = libraryView == HubLibraryView.LIST,
                            onPlay = { selected -> onPlay(selected, query) },
                            onRemove = {
                                onRemove(it)
                                if (MediaLibrary.entryForCard(id) == null) placement.removedByUser(id.gridKey)
                            },
                            onSave = onSave,
                            onUnavailable = { unavailableEntry = it },
                            deferArtwork = { gridState.isScrollInProgress },
                            modifier = Modifier.animateItem(fadeInSpec = null, fadeOutSpec = null,
                                placementSpec = if (placeItems) motion.spec(MotionTokens.selection) else null),
                            onMovedOut = { placement.removedByUser(id.gridKey) }
                        )
                    }
                }
            }
        }
    }

    unavailableEntry?.let { entry ->
        MiowserConfirmationDialog(
            title = "File unavailable",
            message = "Miowser can no longer open this file. If it is still on the device, locate it to reconnect this Library entry.",
            onDismiss = { unavailableEntry = null },
            actions = listOf(
                ConfirmationAction("Keep", { unavailableEntry = null }),
                ConfirmationAction("Locate file", {
                    unavailableEntry = null
                    onRelink(entry)
                }),
                ConfirmationAction("Remove record", {
                    unavailableEntry = null
                    onRemove(entry)
                }, ConfirmationTone.CAUTION)
            )
        )
    }
}

@Composable
internal fun observeLibraryCard(id: LibraryCardId): LibraryEntry? {
    var entry by remember(id) { mutableStateOf(MediaLibrary.entryForCard(id)) }
    DisposableEffect(id) {
        val stop = MediaLibrary.observeCard(id) { entry = it }
        onDispose(stop)
    }
    return entry
}

@Composable
internal fun LibraryCardPlaceholder(listMode: Boolean) {
    val color = MaterialTheme.colorScheme.surfaceContainer
    if (listMode) {
        Spacer(Modifier.fillMaxWidth().height(HubVideoCardTokens.listArtworkHeight + UiSpacing.s * 2)
            .clip(MaterialTheme.shapes.large).background(color))
    } else {
        Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).background(color)) {
            Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh))
            Spacer(Modifier.height(UiControlSize.touchTarget + UiSpacing.compact))
        }
    }
}

@Composable
private fun ManagedLibraryCard(
    entry: LibraryEntry,
    listMode: Boolean,
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

    HubVideoCard(
        listMode = listMode,
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
