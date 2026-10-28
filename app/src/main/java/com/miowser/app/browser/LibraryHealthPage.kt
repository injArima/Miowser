package com.miowser.app.browser

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.library.FailedVideoRecord
import com.miowser.app.library.LibraryDuplicateGroup
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibraryFolder
import com.miowser.app.library.LibraryHealthReport
import com.miowser.app.library.LibraryHealthScanner
import com.miowser.app.library.MediaLibrary
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.MiowserConfirmationDialog
import com.miowser.app.ui.SimplePageScaffold
import kotlinx.coroutines.launch

private const val MAX_VISIBLE_ISSUES = 20

@Composable
internal fun LibraryHealthPage(
    entries: List<LibraryEntry>,
    folders: List<LibraryFolder>,
    onBack: () -> Unit,
    onLocate: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit,
    onOpenSource: (String) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var refresh by remember { mutableIntStateOf(0) }
    var report by remember { mutableStateOf<LibraryHealthReport?>(null) }
    var confirmDuplicates by remember { mutableStateOf(false) }

    LaunchedEffect(entries, folders, refresh) {
        report = null
        report = LibraryHealthScanner.scan(context.applicationContext, entries, folders)
    }

    SimplePageScaffold(
        title = "Library health",
        onBack = onBack,
        actions = {
            IconButton(onClick = { refresh++ }, enabled = report != null) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Scan Library again")
            }
        }
    ) { modifier ->
        Box(modifier.fillMaxSize()) {
            val current = report
            if (current == null) {
                Column(
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator()
                    Text("Checking Library records and local files…")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        HealthSummary(current)
                    }

                    if (current.inaccessibleStorageEntries.isNotEmpty()) item {
                        EntryIssueSection(
                            title = "Storage access needed",
                            description = "These files moved, were deleted, or lost Android's document permission.",
                            icon = Icons.Rounded.Storage,
                            entries = current.inaccessibleStorageEntries,
                            onLocate = onLocate,
                            onRemove = onRemove
                        )
                    }

                    if (current.missingLocalFiles.isNotEmpty()) item {
                        EntryIssueSection(
                            title = "Missing local files",
                            description = "Miowser cannot read these files at their saved locations.",
                            icon = Icons.Rounded.LinkOff,
                            entries = current.missingLocalFiles,
                            onLocate = onLocate,
                            onRemove = onRemove
                        )
                    }

                    if (current.brokenThumbnailEntries.isNotEmpty()) item {
                        HealthActionSection(
                            title = "Broken thumbnail cache",
                            count = current.brokenThumbnailEntries.size,
                            description = "The saved preview files are missing. Clearing their references lets Miowser generate fresh frames.",
                            icon = Icons.Rounded.BrokenImage,
                            action = "Rebuild previews",
                            onAction = {
                                val count = MediaLibrary.clearThumbnails(
                                    context,
                                    current.brokenThumbnailEntries.mapTo(HashSet(), LibraryEntry::identityKey)
                                )
                                refresh++
                                scope.launch { snackbar.showSnackbar("Queued $count previews for regeneration") }
                            }
                        )
                    }

                    if (current.orphanedFolderEntries.isNotEmpty()) item {
                        HealthActionSection(
                            title = "Folder references need repair",
                            count = current.orphanedFolderEntries.size,
                            description = "These videos point to folders that no longer exist. They can be safely moved to Unfiled.",
                            icon = Icons.Rounded.FolderOff,
                            action = "Move to Unfiled",
                            onAction = {
                                val count = MediaLibrary.moveOrphanedEntriesToUnfiled(
                                    context, folders.mapTo(HashSet(), LibraryFolder::id)
                                )
                                refresh++
                                scope.launch { snackbar.showSnackbar("Moved $count records to Unfiled") }
                            }
                        )
                    }

                    if (current.duplicateGroups.isNotEmpty()) item {
                        DuplicateSection(current.duplicateGroups, onConsolidate = { confirmDuplicates = true })
                    }

                    if (current.remoteEntriesWithoutSourcePage.isNotEmpty()) item {
                        RemoteWithoutSourceSection(current.remoteEntriesWithoutSourcePage, onRemove)
                    }

                    if (current.recentRemoteFailures.isNotEmpty()) item {
                        FailedRemoteSection(current.recentRemoteFailures, onOpenSource)
                    }
                }
            }
            SnackbarHost(
                hostState = snackbar,
                modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp)
            )
        }
    }

    if (confirmDuplicates) {
        val extras = report?.duplicateGroups?.sumOf { it.entries.size - 1 } ?: 0
        MiowserConfirmationDialog(
            title = "Consolidate duplicates?",
            message = "Miowser will keep the most complete copy of each exact duplicate and remove $extras extra ${if (extras == 1) "record" else "records"}. You can undo this afterward.",
            onDismiss = { confirmDuplicates = false },
            actions = listOf(
                ConfirmationAction("Cancel", { confirmDuplicates = false }, ConfirmationTone.CAUTION),
                ConfirmationAction("Consolidate", {
                    confirmDuplicates = false
                    val removed = MediaLibrary.consolidateExactDuplicates(context)
                    refresh++
                    scope.launch {
                        val result = snackbar.showSnackbar(
                            "Removed ${removed.size} duplicate ${if (removed.size == 1) "record" else "records"}",
                            actionLabel = "Undo"
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            MediaLibrary.restoreDuplicateRecords(context, removed)
                            refresh++
                        }
                    }
                }, ConfirmationTone.CAUTION)
            )
        )
    }
}

@Composable
private fun HealthSummary(report: LibraryHealthReport) {
    val healthy = report.issueCount == 0
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = if (healthy) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.tertiaryContainer
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (healthy) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                contentDescription = null,
                modifier = Modifier.size(32.dp)
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (healthy) "Library looks healthy" else "${report.issueCount} items need attention",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "Checked ${report.scannedEntries} ${if (report.scannedEntries == 1) "video" else "videos"}. Remote links are only flagged after a real playback failure.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun HealthActionSection(
    title: String,
    count: Int,
    description: String,
    icon: ImageVector,
    action: String,
    onAction: () -> Unit
) {
    HealthCard(title, "$count", description, icon) {
        Button(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun EntryIssueSection(
    title: String,
    description: String,
    icon: ImageVector,
    entries: List<LibraryEntry>,
    onLocate: (LibraryEntry) -> Unit,
    onRemove: (LibraryEntry) -> Unit
) {
    HealthCard(title, entries.size.toString(), description, icon) {
        Column {
            entries.take(MAX_VISIBLE_ISSUES).forEachIndexed { index, entry ->
                if (index > 0) HorizontalDivider()
                Column(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(entry.title.ifBlank { "Untitled video" }, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                    Text(entry.sourceLabel, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                        overflow = TextOverflow.Ellipsis)
                    Row(Modifier.align(Alignment.End)) {
                        TextButton(onClick = { onLocate(entry) }) { Text("Locate") }
                        TextButton(onClick = { onRemove(entry) }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
            HiddenIssueCount(entries.size)
        }
    }
}

@Composable
private fun DuplicateSection(groups: List<LibraryDuplicateGroup>, onConsolidate: () -> Unit) {
    val extras = groups.sumOf { it.entries.size - 1 }
    HealthCard(
        title = "Exact duplicate records",
        count = extras.toString(),
        description = "These records point to exactly the same media. Consolidation keeps the most complete copy.",
        icon = Icons.Rounded.ContentCopy
    ) {
        groups.take(MAX_VISIBLE_ISSUES).forEach { group ->
            Text(
                "${group.entries.size} copies · ${group.entries.first().title.ifBlank { "Untitled video" }}",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }
        HiddenIssueCount(groups.size)
        Button(onClick = onConsolidate, modifier = Modifier.align(Alignment.End)) {
            Text("Consolidate")
        }
    }
}

@Composable
private fun RemoteWithoutSourceSection(entries: List<LibraryEntry>, onRemove: (LibraryEntry) -> Unit) {
    HealthCard(
        title = "No recovery page",
        count = entries.size.toString(),
        description = "If these remote links expire, Miowser has no website page to revisit for a fresh source.",
        icon = Icons.Rounded.LinkOff
    ) {
        entries.take(MAX_VISIBLE_ISSUES).forEach { entry ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(entry.title.ifBlank { "Untitled video" }, modifier = Modifier.weight(1f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                TextButton(onClick = { onRemove(entry) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }
        }
        HiddenIssueCount(entries.size)
    }
}

@Composable
private fun FailedRemoteSection(records: List<FailedVideoRecord>, onOpenSource: (String) -> Unit) {
    HealthCard(
        title = "Recent remote source failures",
        count = records.size.toString(),
        description = "These sources failed during real playback. Reopen their pages to detect a fresh video link.",
        icon = Icons.Rounded.WarningAmber
    ) {
        records.take(MAX_VISIBLE_ISSUES).forEach { record ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(record.videoName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(record.domain, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onOpenSource(record.pageUrl) }) { Text("Open page") }
            }
        }
        HiddenIssueCount(records.size)
    }
}

@Composable
private fun HealthCard(
    title: String,
    count: String,
    description: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp),
                    tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(count, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun HiddenIssueCount(total: Int) {
    val remaining = total - MAX_VISIBLE_ISSUES
    if (remaining > 0) {
        Text("+$remaining more", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp))
    }
}
