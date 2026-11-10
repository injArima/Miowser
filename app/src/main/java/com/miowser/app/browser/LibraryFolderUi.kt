package com.miowser.app.browser

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.MiowserConfirmationDialog
import com.miowser.app.ui.sheetVerticalEdgeFade
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.library.LibraryFolder
import com.miowser.app.library.LibraryFolders
import com.miowser.app.ui.ExpressiveBottomSheet
import com.miowser.app.ui.ExpressiveSheetHeading
import com.miowser.app.ui.MiowserMenuDivider
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.MiowserDropdownMenu
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.AppButtonStyle
import com.miowser.app.ui.MiowserActionButton

internal const val UNFILED_FOLDER_ID = ""

@Composable
internal fun LibraryFolderStrip(
    selectedId: String,
    onSelected: (String) -> Unit,
    onCreateFolder: (String?) -> Unit,
    onDeleteFolder: (LibraryFolder, Boolean) -> Unit
) {
    val context = LocalContext.current
    val view = LocalView.current
    fun selectFolder(id: String) {
        if (selectedId != id) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onSelected(id)
        }
    }
    var renaming by remember { mutableStateOf<LibraryFolder?>(null) }
    var deleting by remember { mutableStateOf<LibraryFolder?>(null) }
    var editingRules by remember { mutableStateOf<LibraryFolder?>(null) }
    var editedName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var createMenuOpen by remember { mutableStateOf(false) }
    var choosingDefault by remember { mutableStateOf(false) }
    var choosingSubfolderParent by remember { mutableStateOf(false) }
    val motion = LocalMotionPolicy.current
    val folderScrollState = rememberScrollState()
    var previousSelection by rememberSaveable { mutableStateOf(selectedId) }
    var previousDefault by rememberSaveable { mutableStateOf(LibraryFolders.defaultFolderId) }
    val selectedRootId = LibraryFolders.rootId(selectedId)
    val parentId = selectedId.takeIf { it != UNFILED_FOLDER_ID }
    val defaultFolder = LibraryFolders.entries.firstOrNull { it.id == LibraryFolders.defaultFolderId }
    val defaultBranch = defaultFolder?.let { LibraryFolders.descendantsIncluding(it.id) }.orEmpty()
    LaunchedEffect(selectedId) {
        if (previousSelection != selectedId && selectedId == UNFILED_FOLDER_ID) folderScrollState.scrollTo(0)
        previousSelection = selectedId
    }
    LaunchedEffect(LibraryFolders.defaultFolderId) {
        if (previousDefault != LibraryFolders.defaultFolderId) {
            if (motion.reduced) folderScrollState.scrollTo(0)
            else folderScrollState.animateScrollTo(0, motion.spec(MotionTokens.selection))
        }
        previousDefault = LibraryFolders.defaultFolderId
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(start = UiSpacing.m, end = UiSpacing.s, top = UiSpacing.xs, bottom = UiSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(folderScrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)
        ) {
            if (defaultFolder == null) {
                UnfiledFolderChip(selectedId == UNFILED_FOLDER_ID, isDefault = true,
                    onSelected = { selectFolder(UNFILED_FOLDER_ID) },
                    onChooseDefault = { choosingDefault = true })
            }
            defaultFolder?.let { folder ->
                FolderTabChip(
                    folder = folder,
                    selected = selectedId in defaultBranch,
                    isDefault = true,
                    onSelected = { selectFolder(folder.id) },
                    onRename = { renaming = folder; editedName = folder.name; error = null },
                    onRules = { editingRules = folder },
                    onCreateSubfolder = { onCreateFolder(folder.id) },
                    onDefault = { LibraryFolders.setDefault(context, folder.id) },
                    onDelete = { deleting = folder }
                )
            }
            if (defaultFolder != null) {
                UnfiledFolderChip(selectedId == UNFILED_FOLDER_ID, isDefault = false,
                    onSelected = { selectFolder(UNFILED_FOLDER_ID) },
                    onChooseDefault = { choosingDefault = true })
            }
            LibraryFolders.entries.filter { it.parentId == null && it.id != defaultFolder?.id }.forEach { folder ->
                FolderTabChip(
                    folder = folder,
                    selected = selectedRootId == folder.id && selectedId !in defaultBranch,
                    isDefault = false,
                    onSelected = { selectFolder(folder.id) },
                    onRename = { renaming = folder; editedName = folder.name; error = null },
                    onRules = { editingRules = folder },
                    onCreateSubfolder = { onCreateFolder(folder.id) },
                    onDefault = { LibraryFolders.setDefault(context, folder.id) },
                    onDelete = { deleting = folder }
                )
            }
        }
        Box {
            FilledTonalIconButton(onClick = { createMenuOpen = true },
                modifier = Modifier.size(UiControlSize.touchTarget),
                shapes = IconButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Library folder")
            }
            MiowserDropdownMenu(expanded = createMenuOpen, onDismissRequest = { createMenuOpen = false }) {
                MiowserMenuItem(text = { Text("Create primary folder") },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) }, onClick = {
                    createMenuOpen = false
                    onCreateFolder(null)
                })
                MiowserMenuItem(text = { Text("Create subfolder") },
                    leadingIcon = { Icon(Icons.Rounded.Folder, contentDescription = null) },
                    enabled = parentId != null || LibraryFolders.entries.isNotEmpty(), onClick = {
                    createMenuOpen = false
                    if (parentId != null) onCreateFolder(parentId)
                    else choosingSubfolderParent = true
                })
            }
        }
    }

    renaming?.let { folder ->
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename folder") },
            text = {
                Column {
                    OutlinedTextField(editedName, { editedName = it }, label = { Text("Name") }, singleLine = true)
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { MiowserActionButton(onClick = {
                if (LibraryFolders.rename(context, folder.id, editedName)) renaming = null
                else error = "Enter a unique name in this folder"
            }) { Text("Save") } },
            dismissButton = { MiowserActionButton(onClick = { renaming = null }, style = AppButtonStyle.CAUTION_OUTLINED) { Text("Cancel") } }
        )
    }
    deleting?.let { folder ->
        FolderDeleteDialog(folder, onDismiss = { deleting = null }, onDelete = { withVideos ->
            deleting = null
            onDeleteFolder(folder, withVideos)
        })
    }
    if (choosingDefault) {
        LibraryDefaultFolderSheet(
            onSelected = { LibraryFolders.setDefault(context, it); choosingDefault = false },
            onDismiss = { choosingDefault = false }
        )
    }
    if (choosingSubfolderParent) {
        ExpressiveBottomSheet(onDismissRequest = { choosingSubfolderParent = false }) {
            ExpressiveSheetHeading("Choose parent folder", "The new folder will appear inside this folder.")
            val listState = rememberLazyListState()
            LazyColumn(state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                    .sheetVerticalEdgeFade(listState, MaterialTheme.colorScheme.surface)) {
                items(LibraryFolders.orderedEntries(), key = LibraryFolder::id) { folder ->
                    FolderChoiceRow(LibraryFolders.path(folder), onClick = {
                        choosingSubfolderParent = false
                        onCreateFolder(folder.id)
                    })
                }
            }
        }
    }
    editingRules?.let { folder ->
        LibraryFolderRuleEditor(folder.id, onDismiss = { editingRules = null })
    }
}

@Composable
private fun UnfiledFolderChip(selected: Boolean, isDefault: Boolean,
    onSelected: () -> Unit, onChooseDefault: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    FilterChip(selected = selected, onClick = onSelected, label = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
            if (isDefault) Icon(Icons.Rounded.Star, contentDescription = "Default folder", modifier = Modifier.size(16.dp))
            Text("Unfiled")
        }
    }, trailingIcon = if (selected && !isDefault) ({ Box {
        IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "Unfiled options")
        }
        MiowserDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MiowserMenuItem(text = { Text("Default folder") },
                leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                onClick = { menuOpen = false; onChooseDefault() })
        }
    } }) else null)
}

@Composable
private fun FolderTabChip(
    folder: LibraryFolder,
    selected: Boolean,
    isDefault: Boolean,
    onSelected: () -> Unit,
    onRename: () -> Unit,
    onRules: () -> Unit,
    onCreateSubfolder: () -> Unit,
    onDefault: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember(folder.id) { mutableStateOf(false) }
    LaunchedEffect(selected) { if (!selected) menuOpen = false }
    FilterChip(
        selected = selected,
        onClick = onSelected,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
                if (isDefault) Icon(Icons.Rounded.Star, contentDescription = "Default folder", modifier = Modifier.size(16.dp))
                Text(
                    if (isDefault && folder.parentId != null) LibraryFolders.path(folder) else folder.name,
                    modifier = Modifier.widthIn(max = 180.dp), maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        },
        trailingIcon = if (selected) ({
            Box {
                Box(
                    modifier = Modifier.size(32.dp).clickable(onClickLabel = "${folder.name} folder options") {
                        menuOpen = true
                    },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "${folder.name} folder options", modifier = Modifier.size(UiIconSize.menu))
                }
                MiowserDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MiowserMenuItem(text = { Text("Rename folder") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() })
                    MiowserMenuItem(text = { Text("Create subfolder") },
                        leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                        onClick = { menuOpen = false; onCreateSubfolder() })
                    MiowserMenuItem(text = { Text("Folder rules") },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) },
                        onClick = { menuOpen = false; onRules() })
                    MiowserMenuItem(
                        text = { Text(if (isDefault) "Default folder" else "Set as default") },
                        selected = isDefault,
                        leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) },
                        onClick = { menuOpen = false; onDefault() }
                    )
                    MiowserMenuDivider()
                    MiowserMenuItem(text = { Text("Delete folder") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() })
                }
            }
        }) else null
    )
}

@Composable
private fun LibraryDefaultFolderSheet(
    onSelected: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val defaultFolder = LibraryFolders.entries.firstOrNull { it.id == LibraryFolders.defaultFolderId }
    ExpressiveBottomSheet(onDismissRequest = onDismiss) {
        ExpressiveSheetHeading("Default folder", "New videos start here before folder rules organize them.")
        val listState = rememberLazyListState()
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)
                .sheetVerticalEdgeFade(listState, MaterialTheme.colorScheme.surface),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            defaultFolder?.let { folder ->
                item(key = folder.id) {
                    FolderChoiceRow(LibraryFolders.path(folder), isDefault = true, onClick = { onSelected(folder.id) })
                }
            }
            item(key = "unfiled") {
                FolderChoiceRow("Unfiled", isUnfiled = true,
                    isDefault = LibraryFolders.defaultFolderId == null, onClick = { onSelected(null) })
            }
            items(LibraryFolders.entries.filter { it.parentId == null && it.id != defaultFolder?.id }, key = LibraryFolder::id) { folder ->
                FolderChoiceRow(
                    LibraryFolders.path(folder),
                    onClick = { onSelected(folder.id) }
                )
            }
        }
    }
}

@Composable
private fun FolderChoiceRow(
    label: String,
    isUnfiled: Boolean = false,
    isDefault: Boolean = false,
    onClick: () -> Unit
) {
    val view = LocalView.current
    Surface(
        onClick = {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
            onClick()
        },
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = UiSpacing.l, vertical = UiSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.m)
        ) {
            Icon(
                if (isUnfiled) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (isDefault) Icon(Icons.Rounded.Star, contentDescription = "Default folder",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(UiIconSize.small))
        }
    }
}

@Composable
internal fun LibraryFolderCreationFlow(parentId: String?, onFinished: () -> Unit) {
    val context = LocalContext.current
    var name by remember(parentId) { mutableStateOf("") }
    var error by remember(parentId) { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onFinished,
        title = { Text(if (parentId == null) "Create primary folder" else "Create subfolder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                OutlinedTextField(name, { name = it }, label = { Text("Folder name") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { MiowserActionButton(onClick = {
            if (LibraryFolders.add(context, name, parentId = parentId) != null) onFinished()
            else error = "Enter a unique name in this folder"
        }) { Text("Create") } },
        dismissButton = { MiowserActionButton(onClick = onFinished, style = AppButtonStyle.CAUTION_OUTLINED) { Text("Cancel") } }
    )
}

@Composable
internal fun LibrarySubfolderCard(
    folder: LibraryFolder,
    videoCount: Int,
    onOpen: () -> Unit,
    onCreateSubfolder: () -> Unit,
    onDeleteFolder: (LibraryFolder, Boolean) -> Unit,
    listMode: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var editingRules by remember { mutableStateOf(false) }
    var editedName by remember(folder.id) { mutableStateOf(folder.name) }
    var error by remember { mutableStateOf<String?>(null) }

    val artwork: @Composable () -> Unit = {
        Box(
            modifier = if (listMode) Modifier.widthIn(min = 136.dp, max = 136.dp).height(84.dp) else Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(UiControlSize.touchTarget)
            )
            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "${folder.name} folder options")
                }
                MiowserDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MiowserMenuItem(text = { Text("Rename folder") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) }, onClick = {
                        menuOpen = false
                        editedName = folder.name
                        error = null
                        renaming = true
                    })
                    MiowserMenuItem(text = { Text("Create subfolder") },
                        leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) },
                        onClick = { menuOpen = false; onCreateSubfolder() })
                    MiowserMenuItem(text = { Text("Folder rules") },
                        leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) }, onClick = {
                        menuOpen = false
                        editingRules = true
                    })
                    MiowserMenuDivider()
                    MiowserMenuItem(text = { Text("Delete folder") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) }, onClick = {
                        menuOpen = false
                        deleting = true
                    })
                }
            }
        }
    }
    val details: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = UiSpacing.m, vertical = UiSpacing.compact)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$videoCount ${if (videoCount == 1) "video" else "videos"}" +
                    if (LibraryFolders.defaultFolderId == folder.id) " · Default" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        if (listMode) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                artwork()
                Box(Modifier.weight(1f)) { details() }
            }
        } else {
            artwork()
            details()
        }
    }

    if (renaming) AlertDialog(
        onDismissRequest = { renaming = false },
        title = { Text("Rename folder") },
        text = {
            Column {
                OutlinedTextField(editedName, { editedName = it }, label = { Text("Name") }, singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { MiowserActionButton(onClick = {
            if (LibraryFolders.rename(context, folder.id, editedName)) renaming = false
            else error = "Enter a unique name in this folder"
        }) { Text("Save") } },
        dismissButton = { MiowserActionButton(onClick = { renaming = false }, style = AppButtonStyle.CAUTION_OUTLINED) { Text("Cancel") } }
    )
    if (deleting) FolderDeleteDialog(folder, onDismiss = { deleting = false }, onDelete = { withVideos ->
        deleting = false
        onDeleteFolder(folder, withVideos)
    })
    if (editingRules) LibraryFolderRuleEditor(folder.id, onDismiss = { editingRules = false })
}

@Composable
private fun FolderDeleteDialog(folder: LibraryFolder, onDismiss: () -> Unit,
    onDelete: (withVideos: Boolean) -> Unit) {
    val destination = LibraryFolders.entries.firstOrNull { it.id == folder.parentId }?.name ?: "Unfiled"
    MiowserConfirmationDialog(
        title = "Remove ${folder.name}?",
        message = "Tap an option to see what it does. Tap the same option again to confirm.",
        onDismiss = onDismiss,
        actions = listOf(
            ConfirmationAction("Cancel", onDismiss, ConfirmationTone.CAUTION),
            ConfirmationAction("Dissolve", { onDelete(false) }, ConfirmationTone.CAUTION,
                firstTapDescription = "Moves videos to $destination and lifts subfolders up one level. Library entries and device files remain. You can undo this."),
            ConfirmationAction("Delete all", { onDelete(true) }, ConfirmationTone.CAUTION,
                firstTapDescription = "Removes this folder, its subfolders, and their Library entries. Device files stay on your device. You can undo this.")
        )
    )
}
