package com.miowser.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.FilterSubscription
import com.miowser.app.adblock.FilterListProgress
import com.miowser.app.adblock.FilterListStage
import java.io.ByteArrayOutputStream
import java.text.DateFormat
import java.util.Date

private const val MAX_IMPORTED_RULE_BYTES = 256 * 1024

@Composable
fun AdBlockPage(
    enabled: Boolean,
    subscriptions: List<FilterSubscription>,
    manualRules: String,
    isRefreshing: Boolean,
    isRefreshingAll: Boolean,
    isAddingLists: Boolean,
    bulkStatus: String?,
    listProgress: Map<String, FilterListProgress>,
    currentPageUrl: String,
    protectionSummary: () -> String,
    onSiteModeChanged: (String, com.miowser.app.adblock.SiteProtectionMode) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onAddSubscription: (name: String, url: String) -> Unit,
    onAddSubscriptions: (String) -> Unit,
    onSubscriptionEnabledChange: (id: String, enabled: Boolean) -> Unit,
    onRefreshSubscription: (id: String) -> Unit,
    onRenameSubscription: (id: String, name: String) -> Unit,
    onRemoveSubscription: (id: String) -> Unit,
    onRefreshAll: () -> Unit,
    onSaveManualRules: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val filterStatus by com.miowser.app.adblock.AdBlock.engine.loadStatus.collectAsState()
    val importScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<FilterSubscription?>(null) }
    var showCustomRulesGuide by remember { mutableStateOf(false) }
    var bulkDraft by rememberSaveable { mutableStateOf("") }
    var summaryVersion by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    val siteHost = com.miowser.app.adblock.SiteProtection.host(currentPageUrl)
    val siteRevision = com.miowser.app.adblock.SiteProtection.revision
    val siteMode = remember(siteHost, siteRevision) { siteHost?.let(com.miowser.app.adblock.SiteProtection::mode) }
    val summary = remember(summaryVersion, currentPageUrl) { protectionSummary() }
    var manualDraft by remember(manualRules) { mutableStateOf(manualRules) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        importScope.launch {
        runCatching { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val output = ByteArrayOutputStream()
                val buffer = ByteArray(16 * 1024)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_IMPORTED_RULE_BYTES) error("Custom rules are limited to 256 KB; add larger lists as subscriptions")
                    output.write(buffer, 0, read)
                }
                output.toString(Charsets.UTF_8.name())
            } ?: error("Could not open the selected file")
        } }.onSuccess { imported ->
            val combined = listOf(manualDraft.trim(), imported.trim()).filter(String::isNotBlank).joinToString("\n")
            if (combined.toByteArray(Charsets.UTF_8).size > MAX_IMPORTED_RULE_BYTES)
                onMessage("Combined custom rules exceed 256 KB")
            else { manualDraft = combined; onMessage("Filter file imported - tap Save to apply") }
        }.onFailure { onMessage(it.message ?: "Could not import filter file") }
        }
    }

    SimplePageScaffold(title = "Ad blocker", onBack = onBack) { modifier ->
        LazyColumn(
            modifier = modifier.imePadding(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Block ads and trackers", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (enabled) filterStatus else "All filtering is off",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = enabled, onCheckedChange = onEnabledChange)
                    }
                }
            }

            item {
                SectionHeading(
                    title = "Default protection",
                    supporting = "Peter Lowe's list, EasyList and EasyPrivacy update in the background. Basic safeguards protect new requests while they load."
                )
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Protection for this site", fontWeight = FontWeight.SemiBold)
                        if (siteHost == null) Text("Open a regular web page to change its protection.")
                        else {
                            Text(siteHost)
                            Text("Changes reload this page. Strict adds extra ad-element hiding; use Standard or Off if a site breaks.", style = MaterialTheme.typography.bodySmall)
                            com.miowser.app.adblock.SiteProtectionMode.entries.forEach { mode ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    androidx.compose.material3.RadioButton(selected = mode == siteMode,
                                        enabled = enabled, onClick = { onSiteModeChanged(siteHost, mode) })
                                    Text(mode.label)
                                }
                            }
                            Text("Standard and Strict block windows opened without a user gesture. Off restores the browser's normal popup settings.", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(summary, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { summaryVersion++ }) { Text("Refresh blocking summary") }
                    }
                }
            }

            items(subscriptions.filter(FilterSubscription::isBuiltIn), key = { it.id }) { subscription ->
                SubscriptionRow(
                    subscription = subscription,
                    onEnabledChange = {},
                    onRefresh = { onRefreshSubscription(subscription.id) },
                    onRename = { renameTarget = subscription },
                    onRemove = {},
                    allowControls = false,
                    refreshEnabled = !isRefreshing && !isAddingLists,
                    progress = listProgress[subscription.id]
                )
            }

            item {
                FilledTonalButton(
                    onClick = onRefreshAll,
                    enabled = !isRefreshing && !isAddingLists,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRefreshingAll) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    }
                    Text(if (isRefreshingAll) "Refreshing all lists..." else "Refresh all lists")
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeading(
                    title = "Your filter lists",
                    supporting = "Your enabled lists stay enabled. Lists are compiled and cached in the background, with overlapping rules stored once. Download and parsing errors are reported without changing your switches."
                )
            }

            val customSubscriptions = subscriptions.filterNot(FilterSubscription::isBuiltIn)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add lists in bulk", fontWeight = FontWeight.SemiBold)
                        Text("Paste one URL per line, up to 50 saved lists. New lists are enabled and processed in the background. Duplicate URLs are skipped. Downloads over 32 MB are rejected with an error.",
                            style = MaterialTheme.typography.bodySmall)
                        OutlinedTextField(
                            value = bulkDraft,
                            onValueChange = {
                                if (it.length <= 100_000) bulkDraft = it
                                else onMessage("Paste up to 50 filter-list URLs at a time")
                            },
                            label = { Text("Filter-list URLs") },
                            placeholder = { Text("https://example.org/ads.txt\nhttps://example.org/privacy.txt") },
                            minLines = 4,
                            maxLines = 8,
                            enabled = !isAddingLists,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { onAddSubscriptions(bulkDraft) },
                            enabled = bulkDraft.isNotBlank() && !isAddingLists && !isRefreshing,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (isAddingLists) "Adding lists…" else "Add and apply lists") }
                        bulkStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
            if (customSubscriptions.isEmpty()) {
                item {
                    Text(
                        "No additional lists yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(customSubscriptions, key = { it.id }) { subscription ->
                    SubscriptionRow(
                        subscription = subscription,
                        onEnabledChange = { onSubscriptionEnabledChange(subscription.id, it) },
                        onRefresh = { onRefreshSubscription(subscription.id) },
                        onRename = { renameTarget = subscription },
                        onRemove = { onRemoveSubscription(subscription.id) },
                        allowControls = true,
                        refreshEnabled = !isRefreshing && !isAddingLists,
                        progress = listProgress[subscription.id]
                    )
                }
            }

            item {
                OutlinedButton(onClick = { showAddDialog = true }, enabled = !isAddingLists && !isRefreshing, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text("Add filter-list URL")
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeading(
                    title = "Custom rules",
                    supporting = "Paste one EasyList/Adblock Plus network or cosmetic rule per line, or import a plain-text filter file. Changes apply to new or reloaded pages."
                )
                OutlinedTextField(
                    value = manualDraft,
                    onValueChange = { manualDraft = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp)
                        .padding(top = 8.dp),
                    placeholder = { Text("||ads.example.com^\nexample.com##.sponsored") },
                    minLines = 7
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("text/plain", "application/octet-stream")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Import")
                    }
                    Button(
                        onClick = { onSaveManualRules(manualDraft) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                        Text("Save")
                    }
                }
            }
            item {
                TextButton(onClick = {
                    keyboardController?.hide()
                    showCustomRulesGuide = true
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.AutoMirrored.Rounded.HelpOutline, contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp))
                    Text("How to use custom rules")
                }
            }
        }
    }

    if (showCustomRulesGuide) {
        CustomRulesGuideDialog(onDismiss = { showCustomRulesGuide = false })
    }

    if (showAddDialog) {
        AddSubscriptionDialog(
            onAdd = { name, url ->
                showAddDialog = false
                onAddSubscription(name, url)
            },
            onDismiss = { showAddDialog = false }
        )
    }
    renameTarget?.let { target ->
        RenameSubscriptionDialog(
            subscription = target,
            onRename = { name ->
                renameTarget = null
                onRenameSubscription(target.id, name)
            },
            onDismiss = { renameTarget = null }
        )
    }
}

@Composable
private fun CustomRulesGuideDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("How to use custom rules") },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Put one rule on each line. Lines beginning with ! are notes. Start with a rule for one site so it cannot hide content elsewhere.")
                Text("Hide an element on one site:", fontWeight = FontWeight.SemiBold)
                Text("example.com##.ad-banner", fontFamily = FontFamily.Monospace)
                Text("Hide an element on every site:", fontWeight = FontWeight.SemiBold)
                Text("##ins.adsbygoogle", fontFamily = FontFamily.Monospace)
                Text("Block requests to a domain and its subdomains:", fontWeight = FontWeight.SemiBold)
                Text("||ads.example.com^", fontFamily = FontFamily.Monospace)
                Text("If a global hiding rule breaks a site, remove it or add an exception:",
                    fontWeight = FontWeight.SemiBold)
                Text("example.com#@#ins.adsbygoogle", fontFamily = FontFamily.Monospace)
                Text("Replace example.com and the selector with the site and element you mean. Tap Save, then reload the page. Avoid broad selectors such as .ad; they can hide normal content. Scriptlets and procedural filters are not supported.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Got it") } }
    )
}

@Composable
private fun SectionHeading(title: String, supporting: String) {
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            supporting,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SubscriptionRow(
    subscription: FilterSubscription,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
    allowControls: Boolean,
    refreshEnabled: Boolean,
    progress: FilterListProgress?
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(subscription.name, fontWeight = FontWeight.Medium)
                    Text(
                        subscription.url,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (allowControls) {
                    Switch(
                        checked = subscription.enabled,
                        onCheckedChange = onEnabledChange
                    )
                } else {
                    Icon(
                        Icons.Rounded.Lock,
                        contentDescription = "Required default list",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val status = when (progress?.stage) {
                FilterListStage.QUEUED -> "Queued for download"
                FilterListStage.DOWNLOADING -> "Downloading ${filterByteLabel(progress.downloadedBytes)}" +
                    (progress.totalBytes?.let { " of ${filterByteLabel(it)}" } ?: "")
                FilterListStage.CHECKING -> "Checking downloaded rules"
                FilterListStage.WAITING_TO_APPLY -> "Waiting to apply protection"
                FilterListStage.APPLYING -> "Applying protection"
                FilterListStage.READY -> "Protection updated"
                FilterListStage.FAILED -> "Failed: ${progress.error ?: "Could not update this list"}"
                null -> when {
                subscription.lastError != null && (subscription.isBuiltIn || subscription.lastUpdatedAt > 0L) ->
                    "Refresh failed: ${subscription.lastError}. Last working copy remains active."
                subscription.lastError != null -> "Could not load: ${subscription.lastError}"
                subscription.lastUpdatedAt == 0L -> "Bundled copy active - online refresh pending"
                else -> {
                    val rules = subscription.networkRuleCount + subscription.cosmeticRuleCount
                    "Updated ${formatDate(subscription.lastUpdatedAt)} - $rules rules"
                }
                }
            }
            Text(
                status.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (progress?.stage == FilterListStage.FAILED ||
                    (progress == null && subscription.lastError != null)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp)
            )
            if (progress != null) {
                val fraction = when (progress.stage) {
                    FilterListStage.QUEUED -> 0f
                    FilterListStage.DOWNLOADING ->
                        progress.totalBytes?.takeIf { it > 0 }?.let {
                            (progress.downloadedBytes.toFloat() / it).coerceIn(0f, 1f)
                        }
                    FilterListStage.FAILED -> 0f
                    FilterListStage.READY -> 1f
                    else -> null
                }
                val barModifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                val barColor = if (progress.stage == FilterListStage.FAILED)
                    MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                if (fraction == null) LinearProgressIndicator(modifier = barModifier, color = barColor)
                else LinearProgressIndicator(progress = { fraction }, modifier = barModifier, color = barColor)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onRefresh, enabled = refreshEnabled) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh ${subscription.name}")
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Rounded.Edit, contentDescription = "Rename ${subscription.name}")
                }
                if (allowControls) {
                    IconButton(onClick = onRemove) {
                        Icon(
                            Icons.Rounded.Delete,
                            contentDescription = "Remove ${subscription.name}",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RenameSubscriptionDialog(
    subscription: FilterSubscription,
    onRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember(subscription.id) { mutableStateOf(subscription.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename filter list") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("List name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onRename(name.trim()) },
                enabled = name.trim().isNotEmpty() && name.trim() != subscription.name) {
                Text("Rename")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun AddSubscriptionDialog(onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add filter list") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Filter-list URL") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(name, url) }, enabled = url.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun formatDate(timestamp: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(timestamp))

private fun filterByteLabel(bytes: Long): String = when {
    bytes >= 1024L * 1024 -> "${bytes / (1024L * 1024)}.${(bytes % (1024L * 1024)) * 10 / (1024L * 1024)} MB"
    else -> "${bytes / 1024} KB"
}
