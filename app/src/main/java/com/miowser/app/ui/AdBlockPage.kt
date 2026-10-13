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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.FilterSubscription
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
    isAddingLists: Boolean,
    bulkStatus: String?,
    currentPageUrl: String,
    protectionSummary: () -> String,
    onSiteModeChanged: (String, com.miowser.app.adblock.SiteProtectionMode) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onAddSubscription: (name: String, url: String) -> Unit,
    onAddSubscriptions: (String) -> Unit,
    onSubscriptionEnabledChange: (id: String, enabled: Boolean) -> Unit,
    onRefreshSubscription: (id: String) -> Unit,
    onRemoveSubscription: (id: String) -> Unit,
    onRefreshAll: () -> Unit,
    onSaveManualRules: (String) -> Unit,
    onMessage: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val filterStatus by com.miowser.app.adblock.AdBlock.engine.loadStatus.collectAsState()
    val importScope = rememberCoroutineScope()
    var showAddDialog by remember { mutableStateOf(false) }
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
                    supporting = "Basic safeguards start immediately. Saved filter lists load in the background and take effect on subsequent requests. No extra lists are required."
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

            item {
                DefaultProtectionRow(
                    name = "Miowser safeguards",
                    detail = "Immediate fallback rules built into the app",
                    active = enabled
                )
            }

            items(subscriptions.filter(FilterSubscription::isBuiltIn), key = { it.id }) { subscription ->
                SubscriptionRow(
                    subscription = subscription,
                    onEnabledChange = {},
                    onRefresh = { onRefreshSubscription(subscription.id) },
                    onRemove = {},
                    allowControls = false
                )
            }

            item {
                FilledTonalButton(
                    onClick = onRefreshAll,
                    enabled = !isRefreshing && !isAddingLists,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 8.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    }
                    Text(if (isRefreshing) "Refreshing lists..." else "Refresh all lists")
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                SectionHeading(
                    title = "Your filter lists",
                    supporting = "Your enabled lists stay enabled. Lists are compiled and cached in the background, with overlapping rules stored once. Download and engine-capacity errors are reported without changing your switches."
                )
            }

            val customSubscriptions = subscriptions.filterNot(FilterSubscription::isBuiltIn)
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Add lists in bulk", fontWeight = FontWeight.SemiBold)
                        Text("Paste one URL per line, up to 50 saved lists. New lists are enabled and processed in the background. Duplicate URLs are skipped. Downloads over 8 MB are rejected with an error.",
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
                        onRemove = { onRemoveSubscription(subscription.id) },
                        allowControls = true
                    )
                }
            }

            item {
                OutlinedButton(onClick = { showAddDialog = true }, enabled = !isAddingLists, modifier = Modifier.fillMaxWidth()) {
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
        }
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
private fun DefaultProtectionRow(name: String, detail: String, active: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, fontWeight = FontWeight.Medium)
                Text(
                    if (active) detail else "$detail - inactive",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Rounded.Lock,
                contentDescription = "Required default protection",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SubscriptionRow(
    subscription: FilterSubscription,
    onEnabledChange: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit,
    allowControls: Boolean
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

            val status = when {
                subscription.lastError != null && (subscription.isBuiltIn || subscription.lastUpdatedAt > 0L) ->
                    "Refresh failed: ${subscription.lastError}. Last working copy remains active."
                subscription.lastError != null -> "Could not load: ${subscription.lastError}"
                subscription.lastUpdatedAt == 0L -> "Bundled copy active - online refresh pending"
                else -> {
                    val rules = subscription.networkRuleCount + subscription.cosmeticRuleCount
                    "Updated ${formatDate(subscription.lastUpdatedAt)} - $rules rules"
                }
            }
            Text(
                status.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = if (subscription.lastError != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Refresh ${subscription.name}")
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
