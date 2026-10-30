package com.miowser.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miowser.app.diagnostics.CrashReportStore
import com.miowser.app.diagnostics.CrashReportSummary
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun CrashDiagnosticsPage(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var revision by remember { mutableIntStateOf(0) }
    val reports = remember(revision) { CrashReportStore.list(context) }
    var selected by remember { mutableStateOf<Pair<CrashReportSummary, String>?>(null) }
    var confirmClear by remember { mutableStateOf(false) }

    SimplePageScaffold(
        title = "Crash reports",
        onBack = onBack,
        actions = {
            IconButton(onClick = { confirmClear = true }, enabled = reports.isNotEmpty()) {
                Icon(Icons.Rounded.DeleteSweep, contentDescription = "Clear crash reports")
            }
        }
    ) { modifier ->
        Box(modifier.fillMaxSize()) {
            if (reports.isEmpty()) {
                ExpressiveEmptyState(
                    title = "No crash reports",
                    description = "When diagnostics are enabled, a redacted report will be kept locally after an app crash.",
                    icon = Icons.Rounded.BugReport,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Text(
                            "Reports stay on this device until you share or clear them. Miowser removes URLs, local paths, page titles, searches, and browsing history.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    items(reports, key = CrashReportSummary::fileName) { report ->
                        CrashReportRow(report) {
                            CrashReportStore.read(context, report.fileName)?.let { selected = report to it }
                        }
                    }
                }
            }
            MiowserSnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }

    selected?.let { (summary, text) ->
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(formatReportDate(summary.createdAt)) },
            text = {
                SelectionContainer {
                    Text(
                        text,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp)
                            .verticalScroll(rememberScrollState()),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { shareReport(context, text) }) { Text("Share") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("Miowser crash report", text))
                        scope.launch { snackbar.showSnackbar("Crash report copied") }
                    }) { Text("Copy") }
                    TextButton(onClick = { selected = null }) { Text("Close") }
                }
            }
        )
    }

    if (confirmClear) {
        MiowserConfirmationDialog(
            title = "Clear crash reports?",
            message = "This removes ${reports.size} local ${if (reports.size == 1) "report" else "reports"}. Diagnostics can keep recording future crashes if enabled.",
            onDismiss = { confirmClear = false },
            actions = listOf(
                ConfirmationAction("Cancel", { confirmClear = false }, ConfirmationTone.CAUTION),
                ConfirmationAction("Clear", {
                    confirmClear = false
                    val removed = CrashReportStore.clear(context)
                    revision++
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            "Crash reports cleared",
                            actionLabel = "Undo"
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            CrashReportStore.restore(context, removed)
                            revision++
                        }
                    }
                }, ConfirmationTone.CAUTION)
            )
        )
    }
}

@Composable
private fun CrashReportRow(report: CrashReportSummary, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.BugReport, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(formatReportDate(report.createdAt), fontWeight = FontWeight.SemiBold)
                Text(
                    formatBytes(report.bytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun shareReport(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Miowser crash report")
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share crash report"))
}

private fun formatReportDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes bytes"
}
