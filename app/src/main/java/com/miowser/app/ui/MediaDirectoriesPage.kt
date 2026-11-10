package com.miowser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.miowser.app.library.device.DeviceSource
import com.miowser.app.library.device.DeviceVideoCatalog
import com.miowser.app.library.device.DeviceCatalogSession
import com.miowser.app.library.device.LinkedDeviceFolder
import com.miowser.app.ui.theme.ExpressiveTokens
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun MediaDirectoriesPage(onBack: () -> Unit) {
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
            message = "Videos from ${folder.label} will be removed from Device Videos. The files will stay in the folder.",
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
