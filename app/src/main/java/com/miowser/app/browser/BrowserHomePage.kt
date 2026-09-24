package com.miowser.app.browser

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.theme.ExpressiveTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

/** Internal address kept in WebView history while the native homepage is drawn over it. */
const val BROWSER_HOME_URL = "about:blank#miowser-home"

fun isBrowserHomeUrl(url: String?): Boolean = url == BROWSER_HOME_URL

@Composable
fun BrowserHomePage(
    speedDials: List<SpeedDial>,
    folders: List<SpeedDialFolder>,
    sort: SpeedDialSort,
    focusRequestKey: Int,
    searchEngineName: String,
    isPrivate: Boolean = false,
    onSearch: (String) -> Unit,
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
    modifier: Modifier = Modifier
) {
    var searchText by rememberSaveable { mutableStateOf("") }
    var currentFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var editingDial by remember { mutableStateOf<SpeedDial?>(null) }
    var editorFolderId by remember { mutableStateOf<Long?>(null) }
    var showDialEditor by remember { mutableStateOf(false) }
    var movingDialIds by remember { mutableStateOf<Set<Long>?>(null) }
    var selectedDialIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBulkDeleteConfirmation by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var folderBeingRenamed by remember { mutableStateOf<SpeedDialFolder?>(null) }
    var showFolderEditor by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val searchFocusRequester = remember { FocusRequester() }
    val currentFolder = folders.firstOrNull { it.id == currentFolderId }
    val visibleDials = sortSpeedDials(speedDials.filter { it.folderId == currentFolderId }, sort)

    LaunchedEffect(focusRequestKey) {
        if (focusRequestKey > 0) {
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
                OutlinedTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(searchFocusRequester),
                    placeholder = { Text("Search with $searchEngineName or enter address") },
                    leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { submitSearch() })
                )
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
                    IconButton(onClick = { movingDialIds = selectedDialIds }) {
                        Icon(Icons.Rounded.DriveFileMove, contentDescription = "Move selected speed dials")
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
                canMove = folders.isNotEmpty() || entry.folderId != null,
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
                onMove = { movingDialIds = setOf(entry.id) },
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

    movingDialIds?.let { ids ->
        val selectedEntries = speedDials.filter { it.id in ids }
        val selectedFolders = selectedEntries.map { it.folderId }.distinct()
        val selectedFolder = selectedFolders.singleOrNull()
        val allInSameFolder = selectedFolders.size == 1
        AlertDialog(
            onDismissRequest = { movingDialIds = null },
            title = { Text("Move to folder") },
            text = {
                Column {
                    FolderDestinationRow(
                        label = "Speed dial home",
                        selected = selectedEntries.isNotEmpty() && allInSameFolder && selectedFolder == null,
                        icon = { Icon(Icons.Rounded.Home, contentDescription = null) }
                    ) {
                        if (ids.size == 1) onMoveSpeedDial(ids.first(), null) else onMoveSpeedDials(ids, null)
                        selectedDialIds = emptySet()
                        movingDialIds = null
                    }
                    folders.forEach { folder ->
                        FolderDestinationRow(
                            label = folder.name,
                            selected = allInSameFolder && selectedFolder == folder.id,
                            icon = { Icon(Icons.Rounded.Folder, contentDescription = null) }
                        ) {
                            if (ids.size == 1) {
                                onMoveSpeedDial(ids.first(), folder.id)
                            } else {
                                onMoveSpeedDials(ids, folder.id)
                            }
                            selectedDialIds = emptySet()
                            movingDialIds = null
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { movingDialIds = null }) { Text("Cancel") } }
        )
    }

    if (showBulkDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showBulkDeleteConfirmation = false },
            title = { Text("Delete ${selectedDialIds.size} speed dials?") },
            text = { Text("This removes the selected shortcuts from the homepage.") },
            confirmButton = {
                TextButton(onClick = {
                    val ids = selectedDialIds
                    showBulkDeleteConfirmation = false
                    selectedDialIds = emptySet()
                    onRemoveSpeedDials(ids)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showBulkDeleteConfirmation = false }) { Text("Cancel") }
            }
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
    canMove: Boolean,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val favicon by produceState<Bitmap?>(initialValue = null, entry.url) {
        value = SpeedDialFaviconLoader.load(entry.url)
    }
    Box {
        CompactDialTile(
            label = defaultSpeedDialTitle(entry.url),
            selected = selected,
            onClick = onClick,
            onLongClick = {
                if (selectionMode) onClick() else menuExpanded = true
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
                    defaultSpeedDialTitle(entry.url).firstOrNull()?.uppercase() ?: "?",
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
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Select") },
                leadingIcon = { Icon(Icons.Rounded.CheckCircle, contentDescription = null) },
                onClick = { menuExpanded = false; onSelect() }
            )
            DropdownMenuItem(
                text = { Text("Edit") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { menuExpanded = false; onEdit() }
            )
            DropdownMenuItem(
                text = { Text("Move to folder") },
                leadingIcon = { Icon(Icons.Rounded.DriveFileMove, contentDescription = null) },
                enabled = canMove,
                onClick = { menuExpanded = false; onMove() }
            )
            DropdownMenuItem(
                text = { Text("Remove") },
                leadingIcon = {
                    Icon(Icons.Rounded.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                },
                onClick = { menuExpanded = false; onRemove() }
            )
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
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Folder, contentDescription = null, modifier = Modifier.size(34.dp))
                if (itemCount > 0) {
                    Text(
                        itemCount.coerceAtMost(99).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.align(Alignment.BottomEnd)
                    )
                }
            }
        }
        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
            DropdownMenuItem(
                text = { Text("Rename") },
                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                onClick = { menuExpanded = false; onRename() }
            )
            DropdownMenuItem(
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
    val tileColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.tertiaryContainer
        else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = tween(ExpressiveTokens.compactMotionMillis),
        label = "Speed dial selection"
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(46.dp),
            shape = MaterialTheme.shapes.medium,
            color = tileColor,
            contentColor = if (selected) {
                MaterialTheme.colorScheme.onTertiaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimaryContainer
            },
            border = if (selected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
        ) {
            Box(contentAlignment = Alignment.Center) { icon() }
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
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        SpeedDialSort.entries.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
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
private fun FolderDestinationRow(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = if (selected) ({ Text("Current location") }) else null,
        leadingContent = icon,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    )
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
                    keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(title, url) })
                )
            }
        },
        confirmButton = { TextButton(enabled = canSave, onClick = { onSave(title, url) }) { Text("Save") } },
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

private object SpeedDialFaviconLoader {
    private val memoryCache = ConcurrentHashMap<String, Bitmap>()

    suspend fun load(pageUrl: String): Bitmap? = withContext(Dispatchers.IO) {
        val faviconUrl = runCatching {
            val page = URI(pageUrl)
            val host = page.host ?: return@runCatching null
            "${page.scheme ?: "https"}://$host/favicon.ico"
        }.getOrNull() ?: return@withContext null
        memoryCache[faviconUrl]?.let { return@withContext it }
        val connection = runCatching { URL(faviconUrl).openConnection() as HttpURLConnection }
            .getOrNull() ?: return@withContext null
        try {
            connection.connectTimeout = 4_000
            connection.readTimeout = 4_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Miowser")
            if (connection.responseCode !in 200..299) return@withContext null
            val bitmap = connection.inputStream.buffered().use(BitmapFactory::decodeStream)
                ?: return@withContext null
            memoryCache[faviconUrl] = bitmap
            bitmap
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
