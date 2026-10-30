package com.miowser.app.backup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Process
import android.provider.DocumentsContract
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miowser.app.persistence.Persistence
import com.miowser.app.ui.SimplePageScaffold
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.MiowserConfirmationDialog
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

@Composable
fun BackupRestorePage(activeDownloads: Boolean, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var settings by remember { mutableStateOf(BackupSettingsStore.load(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var prepared by remember { mutableStateOf<PreparedBackup?>(null) }
    var finishedRestore by remember { mutableStateOf(false) }

    val latestPrepared by rememberUpdatedState(prepared)
    DisposableEffect(Unit) { onDispose { latestPrepared?.discard() } }
    BackHandler(enabled = busy || prepared != null || finishedRestore) {
        if (!busy && !finishedRestore) { prepared?.discard(); prepared = null }
    }

    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) {
                context.contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                BackupSettingsStore.setDirectory(context, uri)
                BackupSettingsStore.load(context)
            } }.onSuccess { settings = it; message = "Backup folder selected." }
                .onFailure { message = it.message ?: "Could not keep access to this folder." }
            busy = false
        }
    }
    val chooseArchive = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { withContext(Dispatchers.IO) { BackupEngine.inspect(context, uri) } }
                .onSuccess { prepared = it; message = null }
                .onFailure { message = it.message ?: "Could not read this backup." }
            busy = false
        }
    }

    SimplePageScaffold(title = "Backup & restore", onBack = { if (!busy && !finishedRestore) onBack() }) { modifier ->
        Column(modifier.verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Keep your settings, Library references, bookmarks, speed dials, site choices, filter lists, and saved video details.",
                style = MaterialTheme.typography.bodyMedium)
            Text("History, open tabs, private browsing, cookies, login data, and video files are excluded. Backups are not encrypted; choose a private folder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Backup folder", style = MaterialTheme.typography.titleMedium)
            Text(settings.treeUri?.let { uri ->
                runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: uri.toString()
            } ?: "No folder selected", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(onClick = { chooseFolder.launch(settings.treeUri) }, enabled = !busy) {
                Text(if (settings.treeUri == null) "Choose folder" else "Change folder")
            }

            Text("Automatic backup", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BackupInterval.entries.take(3).forEach { option ->
                    FilterChip(enabled = !busy, selected = settings.interval == option,
                        onClick = {
                            scope.launch {
                                busy = true
                                runCatching { withContext(Dispatchers.IO) {
                                    BackupSettingsStore.setInterval(context, option)
                                    BackupSettingsStore.load(context)
                                } }.onSuccess { settings = it }.onFailure { message = it.message }
                                busy = false
                            }
                        }, label = { Text(option.label) })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                BackupInterval.entries.drop(3).forEach { option ->
                    FilterChip(enabled = !busy, selected = settings.interval == option,
                        onClick = {
                            scope.launch {
                                busy = true
                                runCatching { withContext(Dispatchers.IO) {
                                    BackupSettingsStore.setInterval(context, option)
                                    BackupSettingsStore.load(context)
                                } }.onSuccess { settings = it }.onFailure { message = it.message }
                                busy = false
                            }
                        }, label = { Text(option.label) })
                }
            }
            Text("Android may run a scheduled backup later than the selected interval. The latest 10 automatic backups are kept; manual backups are never rotated.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (settings.lastSuccessfulAt > 0) Text("Last backup: ${dateLabel(settings.lastSuccessfulAt)}")
            if (settings.skippedArtwork > 0) Text("Last backup omitted ${settings.skippedArtwork} regenerable artwork files.")
            settings.lastError?.let { Text("Last backup failed: $it", color = MaterialTheme.colorScheme.error) }

            Button(onClick = {
                val tree = settings.treeUri ?: return@Button
                scope.launch {
                    busy = true
                    try { runCatching {
                        withTimeout(20_000) { awaitExistingWrites() }
                        withContext(Dispatchers.IO) { BackupEngine.create(context, tree) }
                    }.onSuccess {
                        message = "Backup saved (${it.fileCount} data files)." +
                            if (it.skippedArtwork > 0) " ${it.skippedArtwork} regenerable artwork files omitted." else ""
                        runCatching { withContext(Dispatchers.IO) {
                            BackupSettingsStore.report(context, null, it.skippedArtwork)
                            BackupSettingsStore.load(context)
                        } }.onSuccess { settings = it }.onFailure {
                            message = "$message Could not save backup status."
                        }
                    }.onFailure {
                        message = it.message ?: "Backup failed."
                        runCatching { withContext(Dispatchers.IO) {
                            BackupSettingsStore.report(context, it.message ?: "Backup failed")
                            BackupSettingsStore.load(context)
                        } }.onSuccess { settings = it }
                    }
                    } finally { busy = false }
                }
            }, enabled = settings.treeUri != null && !busy) { Text("Back up now") }

            Text("Restore", style = MaterialTheme.typography.titleMedium)
            Text("Restore replaces saved app data. Downloaded files stay on the device. Miowser reconnects restored records by filename in the saved download folder; Android will ask you to grant a custom folder again after reinstalling.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (activeDownloads) Text("Finish or pause active downloads before restoring.",
                color = MaterialTheme.colorScheme.error)
            OutlinedButton(onClick = { chooseArchive.launch(arrayOf("*/*")) },
                enabled = !busy && !activeDownloads) { Text("Choose backup file") }
            if (busy) CircularProgressIndicator()
            message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        }
    }

    prepared?.let { backup ->
        MiowserConfirmationDialog(
            title = "Restore this backup?",
            message = "Created ${dateLabel(backup.summary.createdAt)}. It contains ${backup.summary.fileCount} data files. Current settings and saved records will be replaced; downloaded media will not be deleted. Miowser will close after restoring so the data reloads when you reopen it.",
            onDismiss = { backup.discard(); prepared = null },
            actions = listOf(
                ConfirmationAction("Cancel", { backup.discard(); prepared = null }, ConfirmationTone.CAUTION),
                ConfirmationAction("Restore", {
                prepared = null
                scope.launch {
                    busy = true
                    runCatching {
                        withTimeout(20_000) { awaitExistingWrites() }
                        withContext(Dispatchers.IO) { BackupEngine.restore(context, backup) }
                    }.onSuccess { finishedRestore = true; message = null }
                        .onFailure {
                            backup.discard()
                            message = it.message ?: "Restore failed."
                            if (it is RestoreNeedsRestartException) finishedRestore = true
                        }
                    busy = false
                }
            }, ConfirmationTone.CAUTION)
            )
        )
    }
    if (finishedRestore) AlertDialog(onDismissRequest = {},
        title = { Text("Restore complete") },
        text = { Text(message ?: "Miowser will close now. Reopen it to load your restored data.") },
        confirmButton = { TextButton(onClick = {
            var owner: Context? = context
            while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
            (owner as? Activity)?.finishAffinity()
            Process.killProcess(Process.myPid())
        }) { Text("Close Miowser") } })
}

private suspend fun awaitExistingWrites() {
    suspendCancellableCoroutine<Unit> { continuation ->
        Persistence.afterWrites { if (continuation.isActive) continuation.resume(Unit) }
    }
}

private fun dateLabel(timestamp: Long): String = DateFormat.getDateTimeInstance().format(Date(timestamp))
