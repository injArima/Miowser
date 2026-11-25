package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.miowser.app.downloader.*
import com.miowser.app.ui.AppText as Text
import com.miowser.app.ui.theme.UiSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadConnectionDialog(download: Download, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var selected by remember(download.id) { mutableIntStateOf(download.connectionLimit) }
    val editable = download.status in listOf(DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED, DownloadStatus.CANCELED)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Connections for this download") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.m)) {
                Text(download.displayName, style = MaterialTheme.typography.bodyMedium)
                Text("The site limit and six shared background slots also apply. Pause a running download before changing its connections.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                    listOf(0, 1, 2, 4, 6).forEach { value ->
                        FilterChip(selected = selected == value, enabled = editable,
                            onClick = { selected = value }, label = { Text(if (value == 0) "Default" else value.toString()) })
                    }
                }
            }
        }, confirmButton = {
            TextButton(enabled = editable, onClick = {
                DownloadQueue.setConnectionLimit(context, download, selected)
                onDismiss()
            }) { Text("Apply") }
        }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DownloadVerificationDialog(download: Download, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    var hashes by remember(download.id) { mutableStateOf<FileHashes?>(null) }
    var failure by remember(download.id) { mutableStateOf<String?>(null) }
    var expected by remember(download.id) { mutableStateOf("") }
    val uri = download.savedUri
    LaunchedEffect(uri) {
        try {
            hashes = withContext(Dispatchers.IO) {
                val job = currentCoroutineContext()
                checkNotNull(uri) { "The saved file is unavailable." }
                context.contentResolver.openInputStream(uri)?.use { input -> FileVerification.hash(input) { job.ensureActive() } }
                    ?: error("Could not read the saved file. Reconnect its storage and try again.")
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) { failure = error.message ?: "Could not verify this file." }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Verify saved file") }, text = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(UiSpacing.m)) {
            Text(download.displayName, style = MaterialTheme.typography.titleSmall)
            if (hashes == null && failure == null) {
                androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("Reading the saved file and calculating checksums…")
            }
            failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            hashes?.let { result ->
                Text("Read ${formatTransferBytes(result.bytes)} from the saved file")
                SelectionContainer { Text("SHA-256\n${result.sha256}\n\nMD5\n${result.md5}", style = MaterialTheme.typography.bodySmall) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                    TextButton(onClick = { clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", result.sha256)) }) { Text("Copy SHA-256") }
                    TextButton(onClick = { clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("MD5", result.md5)) }) { Text("Copy MD5") }
                }
                OutlinedTextField(value = expected, onValueChange = { expected = it.take(128) },
                    label = { Text("Expected SHA-256 or MD5") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (expected.isNotBlank()) {
                    val match = result.matches(expected)
                    Text(when (match) {
                        true -> "Checksum matches"
                        false -> "Checksum does not match. Check the expected hash and the downloaded file."
                        null -> "Enter a 64-character SHA-256 or 32-character MD5 hexadecimal checksum."
                    }, color = if (match == false) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                } else Text("Compare with a checksum from the file's publisher to confirm its integrity.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}
