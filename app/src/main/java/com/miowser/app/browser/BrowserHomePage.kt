package com.miowser.app.browser

import androidx.activity.compose.BackHandler
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import androidx.compose.animation.animateColorAsState
import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.MiowserConfirmationDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntSize
import com.miowser.app.settings.SearchEngine
import com.miowser.app.ui.MiowserMenuDivider
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.MiowserDropdownMenu

/** Internal address kept in WebView history while the native homepage is drawn over it. */
const val BROWSER_HOME_URL = "about:blank#miowser-home"

fun isBrowserHomeUrl(url: String?): Boolean = url == BROWSER_HOME_URL

@Composable
fun BrowserHomePage(
    speedDials: List<SpeedDial>,
    folders: List<SpeedDialFolder>,
    sort: SpeedDialSort,
    focusRequestKey: Int,
    searchEngine: SearchEngine,
    suggestionsEnabled: Boolean,
    onSearch: (String) -> Unit,
    onSearchSuggestion: (String) -> Unit,
    onOpenSpeedDial: (SpeedDial) -> Unit,
    onSaveSpeedDial: (id: Long?, title: String, url: String, folderId: Long?) -> Unit,
    onMoveSpeedDial: (id: Long, folderId: Long?) -> Unit,
    onMoveSpeedDials: (ids: Set<Long>, folderId: Long?) -> Unit,
    onRemoveSpeedDial: (SpeedDial) -> Unit,
    onRemoveSpeedDials: (Set<Long>) -> Unit,
    onSortSelected: (SpeedDialSort) -> Unit,
    onAddFolder: (String) -> Unit,
    onRenameFolder: (id: Long, name: String) -> Unit,
    onRemoveFolder: (SpeedDialFolder) -> Unit,
    modifier: Modifier = Modifier,
    isPrivate: Boolean = false
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var currentFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingDial by remember { mutableStateOf<SpeedDial?>(null) }
    var editorFolderId by remember { mutableStateOf<Long?>(null) }
    var showDialEditor by remember { mutableStateOf(false) }
    var bulkMoveMenuExpanded by remember { mutableStateOf(false) }
    var selectedDialIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var folderBeingRenamed by remember { mutableStateOf<SpeedDialFolder?>(null) }
    var showFolderEditor by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }
    var searchFieldSize by remember { mutableStateOf(IntSize.Zero) }
    val suggestions = rememberSearchSuggestions(searchText, searchEngine, searchFocused && suggestionsEnabled)
    val searchFieldShape = MaterialTheme.shapes.extraLarge
    LaunchedEffect(suggestionsEnabled) {
        if (!suggestionsEnabled) focusManager.clearFocus()
    }
    val searchFocusRequester = remember { FocusRequester() }
    val lastHandledFocusRequest = remember { mutableIntStateOf(focusRequestKey) }
    val currentFolder = folders.firstOrNull { it.id == currentFolderId }
    val visibleDials = sortSpeedDials(speedDials.filter { it.folderId == currentFolderId }, sort)
    BackHandler(enabled = currentFolderId != null) {
        selectedDialIds = emptySet()
        currentFolderId = null
    }

    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > lastHandledFocusRequest.intValue) {
            lastHandledFocusRequest.intValue = focusRequestKey
            searchFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    fun submitSearch() {
        if (searchText.isBlank()) return
        focusManager.clearFocus()
        onSearch(searchText)
    }

    LazyVerticalGrid(
        // 52dp is compact enough for at least five columns on the narrowest normal phone width,
        // but Adaptive is free to add columns in landscape, split-screen, and wider portrait
        // layouts instead of leaving the grid artificially capped at five.
        columns = GridCells.Adaptive(minSize = 52.dp),
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            },
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(bottom = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(76.dp))
                Text(
                    if (isPrivate) "Private browsing" else "Miowser",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 18.dp)
                )
                Box(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth()
                            .clip(searchFieldShape)
                            .onSizeChanged { searchFieldSize = it }
                            .focusRequester(searchFocusRequester)
                            .onFocusChanged { searchFocused = it.isFocused },
                        placeholder = { Text("Search with ${searchEngine.label} or enter address") },
                        leadingIcon = {
                            Box(
                                modifier = Modifier.size(48.dp).background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                            Color.Transparent
                                        )
                                    )
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Rounded.Search, contentDescription = null)
                            }
                        },
                        trailingIcon = {
                            Box(
                                modifier = Modifier.size(48.dp).background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Transparent,
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        )
                                    )
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                IconButton(
                                    onClick = { submitSearch() },
                                    enabled = searchText.isNotBlank(),
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        Icons.Rounded.Search,
                                        contentDescription = "Go to address or search"
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = searchFieldShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { submitSearch() })
                    )
                    SearchSuggestionPopup(
                        suggestions = suggestions,
                        anchorSize = searchFieldSize,
                        onSelect = { suggestion ->
                            searchText = suggestion
                            focusManager.clearFocus()
                            onSearchSuggestion(suggestion)
                        },
                        onComplete = { suggestion -> searchText = suggestion }
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (selectedDialIds.isNotEmpty()) {
                    Text(
                        "${selectedDialIds.size} selected",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { bulkMoveMenuExpanded = true }) {
                            Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move selected speed dials")
                        }
                        val selectedEntries = speedDials.filter { it.id in selectedDialIds }
                        val selectedFolders = selectedEntries.map(SpeedDial::folderId).distinct()
                        MiowserDropdownMenu(
                            expanded = bulkMoveMenuExpanded,
                            onDismissRequest = { bulkMoveMenuExpanded = false }
                        ) {
                            SpeedDialFolderMenuItems(
                                folders = folders,
                                selectedFolderId = selectedFolders.singleOrNull(),
                                showCurrent = selectedEntries.isNotEmpty() && selectedFolders.size == 1
                            ) { folderId ->
                                bulkMoveMenuExpanded = false
                                onMoveSpeedDials(selectedDialIds, folderId)
                                selectedDialIds = emptySet()
                            }
                        }
                    }
                    IconButton(onClick = { showBulkDeleteConfirmation = true }) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Delete selected speed dials",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    IconButton(onClick = { selectedDialIds = emptySet() }) {
                        Icon(Icons.Rounded.Close, contentDescription = "Exit selection")
                    }
                } else if (currentFolder != null) {
                    IconButton(onClick = { currentFolderId = null }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back to speed dial")
                    }
                    Text(
                        currentFolder.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort speed dials")
                        }
                        SpeedDialSortMenu(sortMenuExpanded, sort, {
                            sortMenuExpanded = false
                        }) { selected ->
                            sortMenuExpanded = false
                            onSortSelected(selected)
                        }
                    }
                } else {
                    Text("Speed dial", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { sortMenuExpanded = true }) {
                            Icon(Icons.Rounded.Sort, contentDescription = "Sort speed dials")
                        }
                        SpeedDialSortMenu(sortMenuExpanded, sort, {
                            sortMenuExpanded = false
                        }) { selected ->
                            sortMenuExpanded = false
                            onSortSelected(selected)
                        }
                    }
                    IconButton(onClick = {
                        folderBeingRenamed = null
                        showFolderEditor = true
                    }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = "Create speed-dial folder")
                    }
                }
            }
        }
        if (currentFolder == null) {
            items(folders, key = { "folder-${it.id}" }) { folder ->
                SpeedDialFolderTile(
                    folder = folder,
                    itemCount = speedDials.count { it.folderId == folder.id },
                    onOpen = {
                        selectedDialIds = emptySet()
                        currentFolderId = folder.id
                    },
                    onRename = {
                        folderBeingRenamed = folder
                        showFolderEditor = true
                    },
                    onRemove = { onRemoveFolder(folder) }
                )
            }
        }
        items(visibleDials, key = SpeedDial::id) { entry ->
            SpeedDialTile(
                entry = entry,
                folders = folders,
                selected = entry.id in selectedDialIds,
                selectionMode = selectedDialIds.isNotEmpty(),
                onClick = {
                    if (selectedDialIds.isNotEmpty()) {
                        selectedDialIds = selectedDialIds.toggle(entry.id)
                    } else {
                        onOpenSpeedDial(entry)
                    }
                },
                onSelect = { selectedDialIds = selectedDialIds + entry.id },
                onEdit = {
                    editingDial = entry
                    editorFolderId = entry.folderId
                    showDialEditor = true
                },
                onMove = { folderId -> onMoveSpeedDial(entry.id, folderId) },
                onRemove = { onRemoveSpeedDial(entry) }
            )
        }
        if (selectedDialIds.isEmpty()) {
            item(key = "add-speed-dial") {
                AddSpeedDialTile {
                    editingDial = null
                    editorFolderId = currentFolderId
                    showDialEditor = true
                }
            }
        }
    }

    if (showDialEditor) {
        SpeedDialEditorDialog(
            existing = editingDial,
            onSave = { title, url ->
                onSaveSpeedDial(editingDial?.id, title, url, editorFolderId)
                showDialEditor = false
            },
            onDismiss = { showDialEditor = false }
        )
    }

    if (showBulkDeleteConfirmation) {
        MiowserConfirmationDialog(
            title = "Delete ${selectedDialIds.size} speed dials?",
            message = "This removes the selected shortcuts from the homepage.",
            onDismiss = { showBulkDeleteConfirmation = false },
            actions = listOf(
                ConfirmationAction("Cancel", { showBulkDeleteConfirmation = false }, ConfirmationTone.CAUTION),
                ConfirmationAction("Delete", {
                    val ids = selectedDialIds
                    showBulkDeleteConfirmation = false
                    selectedDialIds = emptySet()
                    onRemoveSpeedDials(ids)
                }, ConfirmationTone.CAUTION)
            )
        )
    }

    if (showFolderEditor) {
        FolderEditorDialog(
            existing = folderBeingRenamed,
            onSave = { name ->
                folderBeingRenamed?.let { onRenameFolder(it.id, name) } ?: onAddFolder(name)
                showFolderEditor = false
            },
            onDismiss = { showFolderEditor = false }
        )
    }
}

@Composable
private fun SpeedDialTile(
    entry: SpeedDial,
    folders: List<SpeedDialFolder>,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMove: (Long?) -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var moveSubmenuExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val favicon by produceState<Bitmap?>(initialValue = null, entry.url) {
        value = SpeedDialSiteMetadataStore.cachedIcon(context, entry.url)
        val metadata = SpeedDialSiteMetadataStore.fetch(
            context, entry.url, entry.autoTitle && entry.title == defaultSpeedDialTitle(entry.url)
        )
        if (metadata.icon != null) value = metadata.icon
        if (entry.autoTitle && !metadata.title.isNullOrBlank()) {
            SpeedDials.updateAutoTitle(context, entry.url, metadata.title)
        }
    }
    Box {
        CompactDialTile(
            label = entry.title,
            selected = selected,
            onClick = onClick,
            onLongClick = {
                if (selectionMode) onClick() else {
                    moveSubmenuExpanded = false
                    menuExpanded = true
                }
            }
        ) {
            if (favicon != null) {
                Image(
                    bitmap = favicon!!.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(32.dp)
                )
            } else {
                Text(
                    entry.title.firstOrNull()?.uppercase() ?: "?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        if (selected) {
            Icon(
                Icons.Rounded.CheckCircle,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
            )
        }
        MiowserDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false; moveSubmenuExpanded = false }
        ) {
            if (moveSubmenuExpanded) {
                MiowserMenuItem(
                    text = { Text("Back") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null) },
                    onClick = { moveSubmenuExpanded = false }
                )
                MiowserMenuDivider()
                SpeedDialFolderMenuItems(
                    folders = folders,
                    selectedFolderId = entry.folderId,
                    showCurrent = true
                ) { folderId ->
                    menuExpanded = false
                    moveSubmenuExpanded = false
                    onMove(folderId)
                }
            } else {
                MiowserMenuItem(
                    text = { Text("Select") },
                    leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                    onClick = { menuExpanded = false; onSelect() }
                )
                MiowserMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = { menuExpanded = false; onEdit() }
                )
                MiowserMenuItem(
                    text = { Text("Move to folder") },
                    leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
                    trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                    enabled = folders.isNotEmpty() || entry.folderId != null,
                    onClick = { moveSubmenuExpanded = true }
                )
                MiowserMenuDivider()
                MiowserMenuItem(
                    text = { Text("Remove") },
                    leadingIcon = {
                        Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    },
                    onClick = { menuExpanded = false; onRemove() }
                )
            }
        }
    }
}

@Composable
private fun SpeedDialFolderTile(
    folder: SpeedDialFolder,
    itemCount: Int,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        CompactDialTile(folder.name, false, onOpen, { menuExpanded = true }) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(34.dp))
                if (itemCount > 0) {
                    Text(
                        if (itemCount > 99) "99+" else itemCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center).offset(y = 4.dp)
                    )
                }
            }
        }
        MiowserDropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            MiowserMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { menuExpanded = false; onRename() }
            )
            MiowserMenuDivider()
            MiowserMenuItem(
                text = { Text("Remove folder") },
                leadingIcon = {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menuExpanded = false; onRemove() }
            )
        }
    }
}

@Composable
private fun CompactDialTile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: @Composable () -> Unit
) {
    val motion = LocalMotionPolicy.current
    val tileColor = animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = motion.spec(MotionTokens.selection),
        label = "Speed dial selection"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = MaterialTheme.shapes.medium,
            color = Color.Transparent,
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Box(Modifier.fillMaxSize().drawBehind { drawRect(tileColor.value) },
                contentAlignment = Alignment.Center) { icon() }
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
        )
    }
}

@Composable
private fun AddSpeedDialTile(onClick: () -> Unit) {
    CompactDialTile("Add", false, onClick, onClick) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun SpeedDialSortMenu(
    expanded: Boolean,
    selected: SpeedDialSort,
    onDismiss: () -> Unit,
    onSelect: (SpeedDialSort) -> Unit
) {
    MiowserDropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SpeedDialSort.entries.forEach { option ->
            MiowserMenuItem(
                text = { Text(option.label) },
                selected = option == selected,
                trailingIcon = {
                    if (option == selected) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = "Selected")
                    }
                },
                onClick = { onSelect(option) }
            )
        }
    }
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

@Composable
private fun SpeedDialFolderMenuItems(
    folders: List<SpeedDialFolder>,
    selectedFolderId: Long?,
    showCurrent: Boolean,
    onSelect: (Long?) -> Unit
) {
    MiowserMenuItem(
        text = { Text("Speed dial home") },
        leadingIcon = { Icon(Icons.Rounded.Home, contentDescription = null) },
        selected = showCurrent && selectedFolderId == null,
        trailingIcon = if (showCurrent && selectedFolderId == null) {
            { Icon(Icons.Rounded.CheckCircle, contentDescription = "Current location") }
        } else null,
        onClick = { onSelect(null) }
    )
    folders.forEach { folder ->
        MiowserMenuItem(
            text = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
            selected = showCurrent && selectedFolderId == folder.id,
            trailingIcon = if (showCurrent && selectedFolderId == folder.id) {
                { Icon(Icons.Rounded.CheckCircle, contentDescription = "Current location") }
            } else null,
            onClick = { onSelect(folder.id) }
        )
    }
}

@Composable
private fun SpeedDialEditorDialog(
    existing: SpeedDial?,
    onSave: (title: String, url: String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember(existing?.id) { mutableStateOf(existing?.title.orEmpty()) }
    var url by remember(existing?.id) { mutableStateOf(existing?.url.orEmpty()) }
    val canSave = url.isNotBlank()
    val savedTitle = if (existing?.autoTitle == true && title == existing.title) "" else title
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add speed dial" else "Edit speed dial") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Name (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website address") },
                    leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(savedTitle, url) })
                )
            }
        },
        confirmButton = { TextButton(enabled = canSave, onClick = { onSave(savedTitle, url) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun FolderEditorDialog(
    existing: SpeedDialFolder?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(existing?.id) { mutableStateOf(existing?.name.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Create folder" else "Rename folder") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Folder name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (name.isNotBlank()) onSave(name) })
            )
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank(), onClick = { onSave(name) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
