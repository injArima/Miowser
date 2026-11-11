package com.miowser.app.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.miowser.app.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.miowser.app.library.device.DEVICE_LINK
import com.miowser.app.library.device.DEVICE_MEDIA
import com.miowser.app.library.device.DeviceMediaAccess
import com.miowser.app.library.device.DeviceSource
import com.miowser.app.library.device.DeviceVideoCatalog
import com.miowser.app.library.device.DeviceCatalogSession
import com.miowser.app.library.device.LinkedDeviceFolder
import com.miowser.app.library.device.deviceFolderKey
import com.miowser.app.library.device.isDeviceFolderExcluded
import com.miowser.app.settings.AppSettings
import com.miowser.app.ui.theme.ExpressiveTokens
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MediaDirectoriesPage(onBack: () -> Unit) {
    val status = DeviceCatalogSession.status
    var manageLinks by rememberSaveable { mutableStateOf(false) }
    if (status.access == DeviceMediaAccess.FULL && !manageLinks) {
        val storage = status.sources.filter { it.kind == DEVICE_MEDIA && it.id in status.visible }
        DeviceStorageDirectories(storage, status.revision, onBack,
            onManageLinks = if (status.sources.any { it.kind == DEVICE_LINK }) ({ manageLinks = true }) else null)
    } else {
        val back: () -> Unit = if (manageLinks) ({ manageLinks = false }) else onBack
        BackHandler(enabled = manageLinks, onBack = back)
        LinkedMediaDirectories(onBack = back)
    }
}

@Composable
private fun DeviceStorageDirectories(
    storage: List<DeviceSource>,
    revision: Long,
    onBack: () -> Unit,
    onManageLinks: (() -> Unit)?
) {
    val context = LocalContext.current
    val app = context.applicationContext
    val exclusions = AppSettings.current.excludedDeviceFolders
    var sourceId by rememberSaveable { mutableStateOf<String?>(null) }
    // Save each opened path explicitly because older Android versions index absolute paths.
    var parents by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    val parent = parents.lastOrNull().orEmpty()
    val source = storage.firstOrNull { it.id == sourceId }
    var directories by remember(sourceId, parent) { mutableStateOf<List<String>?>(null) }
    var failed by remember(sourceId, parent) { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(source, parent, revision, retry) {
        if (source == null) return@LaunchedEffect
        failed = false
        try {
            directories = DeviceVideoCatalog.childDirectories(app, source.id, parent)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            failed = true
        }
    }
    fun goBack() {
        when {
            parents.isNotEmpty() -> parents = ArrayList(parents.dropLast(1))
            sourceId != null -> sourceId = null
            else -> onBack()
        }
    }
    BackHandler(enabled = sourceId != null, onBack = ::goBack)

    fun setIncluded(id: String, folder: String, included: Boolean) {
        val key = deviceFolderKey(id, folder)
        AppSettings.update(context) { settings ->
            settings.copy(excludedDeviceFolders = if (included) settings.excludedDeviceFolders - key
                else settings.excludedDeviceFolders + key)
        }
    }

    SimplePageScaffold(
        title = parent.substringAfterLast('/').ifEmpty { source?.label ?: "Media library folders" },
        onBack = ::goBack
    ) { modifier ->
        key(sourceId, parent) {
            LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(UiSpacing.l),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                if (sourceId == null) {
                    item {
                        Text("Storages", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    item {
                        Text("Folders are included automatically. Uncheck a folder to hide its videos and subfolders from Device.",
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (storage.isEmpty()) item { Text("No storage is available in the library.") }
                    items(storage, key = { it.id }) { volume ->
                        val included = !isDeviceFolderExcluded(exclusions, volume.id, "")
                        DeviceDirectoryRow(volume.label, included, true, storage = true,
                            onOpen = { sourceId = volume.id },
                            onIncludedChange = { setIncluded(volume.id, "", it) })
                    }
                    if (onManageLinks != null) item {
                        MiowserActionButton(style = AppButtonStyle.TEXT, onClick = onManageLinks) { Text("Linked folders") }
                    }
                } else if (source == null) {
                    item { Text("This storage is unavailable.") }
                } else {
                    item {
                        Text(listOf(source.label, parent).filter { it.isNotEmpty() }.joinToString(" / "),
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    val shown = directories
                    if (failed) item {
                        MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { retry++ }) { Text("Retry loading folders") }
                    } else if (shown == null) item { MiowserLoadingIndicator() }
                    else if (shown.isEmpty()) item { Text("No subfolders.") }
                    val parentIncluded = !isDeviceFolderExcluded(exclusions, source.id, parent)
                    items(shown.orEmpty(), key = { it }) { path ->
                        DeviceDirectoryRow(path.substringAfterLast('/'),
                            included = !isDeviceFolderExcluded(exclusions, source.id, path),
                            enabled = parentIncluded, storage = false,
                            onOpen = { parents = ArrayList(parents + path) },
                            onIncludedChange = { setIncluded(source.id, path, it) })
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceDirectoryRow(
    label: String,
    included: Boolean,
    enabled: Boolean,
    storage: Boolean,
    onOpen: () -> Unit,
    onIncludedChange: (Boolean) -> Unit
) {
    Surface(shape = MaterialTheme.shapes.large) {
        ListItem(
            supportingContent = if (!included) ({ Text("Excluded from Device") }) else null,
            leadingContent = { Icon(if (storage) Icons.Rounded.Storage else Icons.Rounded.Folder, null) },
            trailingContent = {
                Checkbox(checked = included, enabled = enabled, onCheckedChange = onIncludedChange,
                    modifier = Modifier.semantics { contentDescription = "Include $label" })
            },
            modifier = Modifier.clickable(onClickLabel = "Browse $label", onClick = onOpen),
            colors = ListItemDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) { Text(label) }
    }
}

@Composable
private fun LinkedMediaDirectories(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val scope = rememberCoroutineScope()
    var folders by remember { mutableStateOf<List<LinkedDeviceFolder>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var relinkId by rememberSaveable { mutableStateOf<String?>(null) }
    var unlink by remember { mutableStateOf<DeviceSource?>(null) }

    suspend fun refresh() {
        folders = DeviceVideoCatalog.linkedSources(app)
    }

    fun updateFolders(failure: String, action: suspend () -> Unit) {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                action()
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = failure
            } finally {
                busy = false
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val replacing = relinkId
        relinkId = null
        if (uri != null) updateFolders("Couldn’t link this folder. Choose it again.") {
            DeviceCatalogSession.refresh(DeviceVideoCatalog.link(app, uri, replacing))
        }
    }

    LaunchedEffect(lifecycleState) {
        if (lifecycleState == Lifecycle.State.RESUMED && !busy) {
            try {
                refresh()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Couldn’t load linked folders."
            }
        }
    }

    SimplePageScaffold(title = "Media library directories", onBack = onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.l),
            verticalArrangement = Arrangement.spacedBy(ExpressiveTokens.sectionGap)
        ) {
            Text("Link folders to include their videos in Device Videos. Miowser keeps the files in their original folders.",
                style = MaterialTheme.typography.bodyMedium)
            MiowserActionButton(enabled = !busy, onClick = {
                relinkId = null
                picker.launch(null)
            }) { Text("Link folder") }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            val currentFolders = folders
            if (busy || (currentFolders == null && error == null)) MiowserLoadingIndicator()
            if (currentFolders?.isEmpty() == true) Text("No folders linked.")
            currentFolders.orEmpty().forEach { (folder, hasReadAccess) ->
                ExpressiveSectionCard(Modifier.fillMaxWidth()) {
                    Text(folder.label, style = MaterialTheme.typography.titleMedium)
                    Text(if (hasReadAccess) "Read access granted" else "Folder access needed",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                        MiowserActionButton(style = AppButtonStyle.OUTLINED, enabled = !busy, onClick = {
                            relinkId = folder.id
                            picker.launch(null)
                        }) { Text("Relink") }
                        MiowserActionButton(style = AppButtonStyle.CAUTION_OUTLINED, enabled = !busy,
                            onClick = { unlink = folder }) { Text("Unlink") }
                    }
                }
            }
        }
    }

    unlink?.let { folder ->
        MiowserConfirmationDialog(
            title = "Unlink folder?",
            message = "Remove the link to ${folder.label} from Device Videos. Files stay in their original folder.",
            onDismiss = { unlink = null },
            actions = listOf(
                ConfirmationAction("Cancel", { unlink = null }, ConfirmationTone.CAUTION),
                ConfirmationAction("Unlink", {
                    unlink = null
                    updateFolders("Couldn’t unlink this folder.") {
                        DeviceVideoCatalog.unlink(app, folder.id)
                        DeviceCatalogSession.folderRemoved()
                    }
                }, ConfirmationTone.CAUTION)
            )
        )
    }
}
