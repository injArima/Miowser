package com.miowser.app.browser

import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import kotlinx.coroutines.launch

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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.libraryDomain
import com.miowser.app.ui.ExpressiveBottomSheet
import com.miowser.app.ui.ExpressiveSheetHeading
import com.miowser.app.ui.MiowserMenuDivider
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.MiowserDropdownMenu

internal enum class FolderCreationMode { MANUAL, WEBSITE }
internal const val UNFILED_FOLDER_ID = ""

@Composable
internal fun LibraryFolderStrip(
    selectedId: String,
    onSelected: (String) -> Unit,
    onCreateFolder: (FolderCreationMode, String?) -> Unit
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
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f).horizontalScroll(folderScrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (defaultFolder == null) {
                UnfiledFolderChip(selectedId == UNFILED_FOLDER_ID, isDefault = true) {
                    selectFolder(UNFILED_FOLDER_ID)
                }
            }
            defaultFolder?.let { folder ->
                FolderTabChip(
                    folder = folder,
                    selected = selectedId in defaultBranch,
                    isDefault = true,
                    onSelected = { selectFolder(folder.id) },
                    onRename = { renaming = folder; editedName = folder.name; error = null },
                    onRules = { editingRules = folder },
                    onDefault = { LibraryFolders.setDefault(context, folder.id) },
                    onDelete = { deleting = folder }
                )
            }
            if (defaultFolder != null) {
                UnfiledFolderChip(selectedId == UNFILED_FOLDER_ID, isDefault = false) {
                    selectFolder(UNFILED_FOLDER_ID)
                }
            }
            LibraryFolders.entries.filter { it.parentId == null && it.id != defaultFolder?.id }.forEach { folder ->
                FolderTabChip(
                    folder = folder,
                    selected = selectedRootId == folder.id && selectedId !in defaultBranch,
                    isDefault = false,
                    onSelected = { selectFolder(folder.id) },
                    onRename = { renaming = folder; editedName = folder.name; error = null },
                    onRules = { editingRules = folder },
                    onDefault = { LibraryFolders.setDefault(context, folder.id) },
                    onDelete = { deleting = folder }
                )
            }
        }
        Box {
            IconButton(onClick = { createMenuOpen = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Library folder")
            }
            MiowserDropdownMenu(expanded = createMenuOpen, onDismissRequest = { createMenuOpen = false }) {
                MiowserMenuItem(text = { Text(if (parentId == null) "Manual folder" else "Subfolder") },
                    leadingIcon = { Icon(Icons.Rounded.CreateNewFolder, contentDescription = null) }, onClick = {
                    createMenuOpen = false
                    onCreateFolder(FolderCreationMode.MANUAL, parentId)
                })
                MiowserMenuItem(text = { Text(if (parentId == null) "Website folder" else "Website subfolder") },
                    leadingIcon = { Icon(Icons.Rounded.Language, contentDescription = null) }, onClick = {
                    createMenuOpen = false
                    onCreateFolder(FolderCreationMode.WEBSITE, parentId)
                })
                MiowserMenuDivider()
                MiowserMenuItem(text = { Text("Default folder") },
                    leadingIcon = { Icon(Icons.Rounded.Star, contentDescription = null) }, onClick = {
                    createMenuOpen = false
                    choosingDefault = true
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
            confirmButton = { TextButton(onClick = {
                if (LibraryFolders.rename(context, folder.id, editedName)) renaming = null
                else error = "Enter a unique name in this folder"
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${folder.name}?") },
            text = { Text(if (folder.parentId == null) {
                "Videos move to Unfiled. Subfolders move to the Library root."
            } else {
                "Videos and subfolders move to ${LibraryFolders.entries.firstOrNull { it.id == folder.parentId }?.name ?: "their parent"}."
            }) },
            confirmButton = { TextButton(onClick = {
                MediaLibrary.relocateFolder(context, folder.id, folder.parentId)
                LibraryFolders.remove(context, folder.id)
                onSelected(folder.parentId ?: UNFILED_FOLDER_ID)
                deleting = null
            }) { Text("Delete folder") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
    if (choosingDefault) {
        LibraryDefaultFolderSheet(
            onSelected = { LibraryFolders.setDefault(context, it); choosingDefault = false },
            onDismiss = { choosingDefault = false }
        )
    }
    editingRules?.let { folder ->
        LibraryFolderRuleEditor(folder.id, onDismiss = { editingRules = null })
    }
}

@Composable
private fun UnfiledFolderChip(selected: Boolean, isDefault: Boolean, onSelected: () -> Unit) {
    FilterChip(selected = selected, onClick = onSelected, label = {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (isDefault) Icon(Icons.Rounded.Star, contentDescription = "Default folder", modifier = Modifier.size(16.dp))
            Text("Unfiled")
        }
    })
}

@Composable
private fun FolderTabChip(
    folder: LibraryFolder,
    selected: Boolean,
    isDefault: Boolean,
    onSelected: () -> Unit,
    onRename: () -> Unit,
    onRules: () -> Unit,
    onDefault: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember(folder.id) { mutableStateOf(false) }
    LaunchedEffect(selected) { if (!selected) menuOpen = false }
    FilterChip(
        selected = selected,
        onClick = onSelected,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    Icon(Icons.Rounded.MoreVert, contentDescription = "${folder.name} folder options", modifier = Modifier.size(20.dp))
                }
                MiowserDropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MiowserMenuItem(text = { Text("Rename folder") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onRename() })
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
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp),
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
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                if (isUnfiled) Icons.Rounded.FolderOpen else Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (isDefault) Icon(Icons.Rounded.Star, contentDescription = "Default folder",
                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
internal fun LibraryFolderCreationFlow(mode: FolderCreationMode, parentId: String?, onFinished: () -> Unit) {
    val routingScope = androidx.compose.runtime.rememberCoroutineScope()
    val context = LocalContext.current
    var name by remember(mode) { mutableStateOf("") }
    var domainInput by remember(mode) { mutableStateOf("") }
    var pendingDomain by remember(mode) { mutableStateOf<String?>(null) }
    var error by remember(mode) { mutableStateOf<String?>(null) }

    val domain = pendingDomain
    if (domain != null) {
        fun create(includeExisting: Boolean) {
            val folder = LibraryFolders.add(context, name.ifBlank { domain }, domain, parentId)
            if (folder == null) {
                error = "Use a unique name in this folder"
                pendingDomain = null
                return
            }
            routingScope.launch {
                try {
                    if (includeExisting) MediaLibrary.applyRuleReorganization(context, folder.id)
                    onFinished()
                } catch (failure: IllegalStateException) { error = failure.message; pendingDomain = null }
            }
        }
        AlertDialog(
            onDismissRequest = onFinished,
            title = { Text("Add videos from $domain") },
            text = { Text("New videos from this website will go into this folder. Include matching videos currently in its source folder?") },
            confirmButton = { TextButton(onClick = { create(true) }) { Text("Include existing") } },
            dismissButton = { TextButton(onClick = { create(false) }) { Text("New videos only") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onFinished,
            title = { Text(if (parentId != null) "Create subfolder" else if (mode == FolderCreationMode.MANUAL) "Manual folder" else "Website folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(name, { name = it }, label = {
                        Text(if (mode == FolderCreationMode.MANUAL) "Folder name" else "Folder name (optional)")
                    }, singleLine = true)
                    if (mode == FolderCreationMode.WEBSITE) {
                        OutlinedTextField(domainInput, { domainInput = it }, label = { Text("Website domain or URL") }, singleLine = true)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = { TextButton(onClick = {
                if (mode == FolderCreationMode.MANUAL) {
                    if (LibraryFolders.add(context, name, parentId = parentId) != null) onFinished()
                    else error = "Enter a unique name in this folder"
                } else {
                    val parsed = libraryDomain(domainInput)
                    val desiredName = name.ifBlank { parsed.orEmpty() }.trim()
                    error = when {
                        parsed == null -> "Enter a valid website domain"
                        LibraryFolders.entries.any { it.parentId == parentId && it.name.equals(desiredName, ignoreCase = true) } ->
                            "Use a unique name in this folder"
                        LibraryFolders.entries.any { parsed in it.websiteDomains } ->
                            "This website already has a folder rule"
                        else -> null
                    }
                    if (error == null) pendingDomain = parsed
                }
            }) { Text(if (mode == FolderCreationMode.MANUAL) "Create" else "Continue") } },
            dismissButton = { TextButton(onClick = onFinished) { Text("Cancel") } }
        )
    }
}

@Composable
internal fun LibrarySubfolderCard(
    folder: LibraryFolder,
    videoCount: Int,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var editingRules by remember { mutableStateOf(false) }
    var editedName by remember(folder.id) { mutableStateOf(folder.name) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(
        onClick = onOpen,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Rounded.Folder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp)
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
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "$videoCount ${if (videoCount == 1) "video" else "videos"}" +
                    if (LibraryFolders.defaultFolderId == folder.id) " · Default" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
        confirmButton = { TextButton(onClick = {
            if (LibraryFolders.rename(context, folder.id, editedName)) renaming = false
            else error = "Enter a unique name in this folder"
        }) { Text("Save") } },
        dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } }
    )
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("Delete ${folder.name}?") },
        text = { Text("Videos and subfolders move to ${LibraryFolders.entries.firstOrNull { it.id == folder.parentId }?.name ?: "their parent"}.") },
        confirmButton = { TextButton(onClick = {
            MediaLibrary.relocateFolder(context, folder.id, folder.parentId)
            LibraryFolders.remove(context, folder.id)
            deleting = false
        }) { Text("Delete folder") } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } }
    )
    if (editingRules) LibraryFolderRuleEditor(folder.id, onDismiss = { editingRules = false })
}
