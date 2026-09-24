package com.miowser.app.browser

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miowser.app.library.LibraryFolder
import com.miowser.app.library.LibraryFolders
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.libraryDomain

internal enum class FolderCreationMode { MANUAL, WEBSITE }
internal const val UNFILED_FOLDER_ID = ""

@Composable
internal fun LibraryFolderStrip(
    selectedId: String?,
    onSelected: (String?) -> Unit,
    onCreateFolder: (FolderCreationMode) -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<LibraryFolder?>(null) }
    var deleting by remember { mutableStateOf<LibraryFolder?>(null) }
    var editedName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var createMenuOpen by remember { mutableStateOf(false) }
    val folderScrollState = rememberScrollState()
    LaunchedEffect(selectedId) {
        if (selectedId == null || selectedId == UNFILED_FOLDER_ID) folderScrollState.scrollTo(0)
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
            FilterChip(selected = selectedId == null, onClick = { onSelected(null) }, label = { Text("All") })
            VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
            FilterChip(
                selected = selectedId == UNFILED_FOLDER_ID,
                onClick = { onSelected(UNFILED_FOLDER_ID) },
                label = { Text("Unfiled") }
            )
            LibraryFolders.entries.forEach { folder ->
                VerticalDivider(modifier = Modifier.height(24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                FilterChip(
                    selected = selectedId == folder.id,
                    onClick = { onSelected(folder.id) },
                    label = { Text(folder.name) }
                )
                if (selectedId == folder.id) {
                    Column {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "${folder.name} folder options")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(text = { Text("Rename folder") }, onClick = {
                                menuOpen = false
                                renaming = folder
                                editedName = folder.name
                                error = null
                            })
                            DropdownMenuItem(text = { Text("Delete folder") }, onClick = {
                                menuOpen = false
                                deleting = folder
                            })
                        }
                    }
                }
            }
        }
        VerticalDivider(modifier = Modifier.height(32.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Box {
            IconButton(onClick = { createMenuOpen = true }) {
                Icon(Icons.Rounded.Add, contentDescription = "Create Library folder")
            }
            DropdownMenu(expanded = createMenuOpen, onDismissRequest = { createMenuOpen = false }) {
                DropdownMenuItem(text = { Text("Manual folder") }, onClick = {
                    createMenuOpen = false
                    onCreateFolder(FolderCreationMode.MANUAL)
                })
                DropdownMenuItem(text = { Text("Website folder") }, onClick = {
                    createMenuOpen = false
                    onCreateFolder(FolderCreationMode.WEBSITE)
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
                else error = "Enter a unique folder name"
            }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
    deleting?.let { folder ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete ${folder.name}?") },
            text = { Text("Videos stay in the Library and move to Unfiled.") },
            confirmButton = { TextButton(onClick = {
                MediaLibrary.unfileFolder(context, folder.id)
                LibraryFolders.remove(context, folder.id)
                onSelected(null)
                deleting = null
            }) { Text("Delete folder") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LibraryFolderPickerSheet(onSelected: (String?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, bottom = 32.dp)) {
            Text("Move to folder", style = MaterialTheme.typography.titleLarge)
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 500.dp)) {
                item(key = "unfiled") {
                    TextButton(onClick = { onSelected(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Unfiled")
                    }
                }
                items(LibraryFolders.entries, key = LibraryFolder::id) { folder ->
                    TextButton(onClick = { onSelected(folder.id) }, modifier = Modifier.fillMaxWidth()) {
                        Text(folder.name)
                    }
                }
            }
        }
    }
}

@Composable
internal fun LibraryFolderCreationFlow(mode: FolderCreationMode, onFinished: () -> Unit) {
    val context = LocalContext.current
    var name by remember(mode) { mutableStateOf("") }
    var domainInput by remember(mode) { mutableStateOf("") }
    var pendingDomain by remember(mode) { mutableStateOf<String?>(null) }
    var error by remember(mode) { mutableStateOf<String?>(null) }

    val domain = pendingDomain
    if (domain != null) {
        fun create(includeExisting: Boolean) {
            val folder = LibraryFolders.add(context, name.ifBlank { domain }, domain)
            if (folder == null) {
                error = "Use a unique folder name"
                pendingDomain = null
                return
            }
            if (includeExisting) MediaLibrary.addExistingFromDomain(context, folder.id, domain)
            onFinished()
        }
        AlertDialog(
            onDismissRequest = onFinished,
            title = { Text("Add videos from $domain") },
            text = { Text("New videos from this website will go into this folder. Include matching videos already in your Library?") },
            confirmButton = { TextButton(onClick = { create(true) }) { Text("Include existing") } },
            dismissButton = { TextButton(onClick = { create(false) }) { Text("New videos only") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onFinished,
            title = { Text(if (mode == FolderCreationMode.MANUAL) "Manual folder" else "Website folder") },
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
                    if (LibraryFolders.add(context, name) != null) onFinished()
                    else error = "Enter a unique folder name"
                } else {
                    val parsed = libraryDomain(domainInput)
                    val desiredName = name.ifBlank { parsed.orEmpty() }.trim()
                    error = when {
                        parsed == null -> "Enter a valid website domain"
                        LibraryFolders.entries.any { it.name.equals(desiredName, ignoreCase = true) } ->
                            "Use a unique folder name"
                        else -> null
                    }
                    if (error == null) pendingDomain = parsed
                }
            }) { Text(if (mode == FolderCreationMode.MANUAL) "Create" else "Continue") } },
            dismissButton = { TextButton(onClick = onFinished) { Text("Cancel") } }
        )
    }
}
