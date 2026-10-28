package com.miowser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.miowser.app.settings.DownloadThreadCount
import com.miowser.app.settings.HomePageMode
import com.miowser.app.settings.InactiveTabRetention
import com.miowser.app.settings.NewTabConfirmation
import com.miowser.app.settings.SearchEngine
import com.miowser.app.settings.Settings
import com.miowser.app.settings.isValidCustomSearchUrl
import kotlin.math.roundToInt

/** Browser preferences apply immediately; editable URL templates have explicit Save actions. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPage(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onOpenBrowsingData: () -> Unit,
    onOpenSitePermissions: () -> Unit,
    onOpenBackupRestore: () -> Unit,
    onBack: () -> Unit
) {
    var homePageDraft by remember(settings.homePageUrl) { mutableStateOf(settings.homePageUrl) }
    var customSearchDraft by remember(settings.customSearchUrl) { mutableStateOf(settings.customSearchUrl) }
    var minimumDurationDraft by remember(settings.grabberMinimumDurationSeconds) {
        mutableFloatStateOf(settings.grabberMinimumDurationSeconds.toFloat())
    }
    var pendingRetention by remember { mutableStateOf<InactiveTabRetention?>(null) }
    val minimumSeconds = (minimumDurationDraft / 5f).roundToInt() * 5

    pendingRetention?.let { period ->
        MiowserConfirmationDialog(
            title = "Delete archived tabs after ${period.label}?",
            message = "Archived inactive tabs older than ${period.label} will be permanently deleted now and during future cleanups. Open tabs are unaffected.",
            onDismiss = { pendingRetention = null },
            actions = listOf(
                ConfirmationAction("Cancel", { pendingRetention = null }, ConfirmationTone.CAUTION),
                ConfirmationAction("Enable deletion", {
                    onUpdate(settings.copy(inactiveTabRetention = period))
                    pendingRetention = null
                }, ConfirmationTone.CAUTION)
            )
        )
    }

    val leaveSettings = {
        if (homePageDraft.isNotBlank() && homePageDraft != settings.homePageUrl) {
            onUpdate(settings.copy(homePageUrl = homePageDraft))
        }
        onBack()
    }
    SimplePageScaffold(title = "Settings", onBack = leaveSettings) { modifier ->
        Column(
            modifier = modifier.verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection("General") {
                OptionBlock("Search engine", "Used for searches from the address bar and browser home.") {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SearchEngine.entries.forEach { engine ->
                            FilterChip(
                                selected = settings.searchEngine == engine,
                                onClick = { onUpdate(settings.copy(searchEngine = engine)) },
                                label = { Text(engine.label) }
                            )
                        }
                    }
                    if (settings.searchEngine == SearchEngine.CUSTOM) {
                        val valid = isValidCustomSearchUrl(customSearchDraft)
                        OutlinedTextField(
                            value = customSearchDraft,
                            onValueChange = { customSearchDraft = it },
                            label = { Text("Search URL") },
                            supportingText = { Text("Use one %s where the search terms belong. HTTPS only.") },
                            isError = !valid,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { onUpdate(settings.copy(customSearchUrl = customSearchDraft)) },
                            enabled = valid && customSearchDraft != settings.customSearchUrl
                        ) { Text("Save custom search") }
                        Description("Remote suggestions are unavailable for custom search engines.")
                    }
                    if (settings.searchEngine == SearchEngine.BRAVE) {
                        Description("Brave Search requires an API key for autocomplete, so suggestions are unavailable.")
                    }
                }
                SectionDivider()
                OptionBlock("Browser home page", "Choose the page shown when opening a new browser tab.") {
                    ChoiceRow(HomePageMode.entries, settings.homePageMode, { it.label }) {
                        onUpdate(settings.copy(homePageMode = it))
                    }
                    if (settings.homePageMode == HomePageMode.CUSTOM) {
                        OutlinedTextField(
                            value = homePageDraft,
                            onValueChange = { homePageDraft = it },
                            label = { Text("Custom home URL") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TextButton(
                            onClick = { onUpdate(settings.copy(homePageUrl = homePageDraft)) },
                            enabled = homePageDraft.isNotBlank() && homePageDraft != settings.homePageUrl
                        ) { Text("Save home page") }
                    }
                }
            }

            SettingsSection("Tabs") {
                SettingRow("Restore tabs on startup", "Reopen regular tabs when Miowser starts again.",
                    settings.restoreTabsOnStartup) {
                    onUpdate(settings.copy(restoreTabsOnStartup = it))
                }
                SectionDivider()
                OptionBlock("Delete archived inactive tabs", "Keep archived tabs until you remove them, or delete them after a chosen period.") {
                    ChoiceRow(InactiveTabRetention.entries, settings.inactiveTabRetention, { it.label }) { period ->
                        if (period == InactiveTabRetention.NEVER) {
                            onUpdate(settings.copy(inactiveTabRetention = period))
                        } else if (period != settings.inactiveTabRetention) pendingRetention = period
                    }
                }
                SectionDivider()
                OptionBlock("Confirm before opening a new tab", "Choose when page-created tabs need your approval.") {
                    ChoiceRow(NewTabConfirmation.entries, settings.newTabConfirmation, { it.label }) {
                        onUpdate(settings.copy(newTabConfirmation = it))
                    }
                }
            }

            SettingsSection("Privacy & security") {
                NavigationRow("Clear browsing data", "Choose history, cookies, and other data to erase.",
                    destructive = true, onClick = onOpenBrowsingData)
                SectionDivider()
                NavigationRow("Site permissions", "Review access granted to websites.", onClick = onOpenSitePermissions)
                SectionDivider()
                SettingRow("Cookies", "Allow websites to remember sign-ins and preferences.", settings.cookiesEnabled) {
                    onUpdate(settings.copy(cookiesEnabled = it))
                }
                SectionDivider()
                SettingRow("Allow popups", "Let websites open new windows; tab confirmation still applies.", settings.popupsEnabled) {
                    onUpdate(settings.copy(popupsEnabled = it))
                }
            }

            SettingsSection("Downloads") {
                OptionBlock("Minimum media length", "Hide detected media shorter than this. Unknown lengths are hidden when a minimum is set.") {
                    Text(if (minimumSeconds == 0) "Off" else formatDuration(minimumSeconds),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                    ChoiceRow(listOf(0, 60, 300, 600), minimumSeconds,
                        { if (it == 0) "Off" else formatDuration(it) }) { seconds ->
                        minimumDurationDraft = seconds.toFloat()
                        onUpdate(settings.copy(grabberMinimumDurationSeconds = seconds))
                    }
                    Slider(
                        value = minimumDurationDraft,
                        onValueChange = { minimumDurationDraft = it },
                        onValueChangeFinished = {
                            onUpdate(settings.copy(grabberMinimumDurationSeconds = minimumSeconds))
                        },
                        valueRange = 0f..1_800f,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        thumb = {
                            Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                        }
                    )
                    Description("Fine tune from Off to 30 minutes in 5-second increments.")
                }
                SectionDivider()
                OptionBlock("Simultaneous download connections", "More connections may help large files on fast servers, but can slow or strain other servers.") {
                    ChoiceRow(DownloadThreadCount.entries, settings.downloadThreadCount, { it.label }) {
                        onUpdate(settings.copy(downloadThreadCount = it))
                    }
                }
            }

            SettingsSection("Media") {
                SettingRow(
                    "Background playback",
                    "Allow headphone mode to keep playing when Miowser is minimized or the screen is off.",
                    settings.backgroundPlaybackEnabled
                ) {
                    onUpdate(settings.copy(backgroundPlaybackEnabled = it))
                }
            }

            SettingsSection("Advanced") {
                SettingRow("JavaScript", "Run website scripts. Turning this off can break many sites.",
                    settings.javaScriptEnabled) {
                    onUpdate(settings.copy(javaScriptEnabled = it))
                }
            }
            SettingsSection("Data") {
                NavigationRow("Backup & restore", "Save your browser data or restore a previous backup.",
                    onClick = onOpenBackupRestore)
            }
            SettingsSection("Accessibility") {
                SettingRow("Reduce motion", "Use simpler transitions. Android's remove-animations setting is always respected.",
                    settings.reduceMotion) {
                    onUpdate(settings.copy(reduceMotion = it))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return when {
        seconds == 0 -> "$minutes min"
        minutes == 0 -> "$seconds sec"
        else -> "$minutes min $seconds sec"
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp))
        Surface(shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            modifier = Modifier.fillMaxWidth()) {
            Column(content = content)
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun Description(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun OptionBlock(title: String, description: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Description(description)
        content()
    }
}

@Composable
private fun <T> ChoiceRow(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(selected = selected == option, onClick = { onSelect(option) },
                label = { Text(label(option)) })
        }
    }
}

@Composable
private fun SettingRow(label: String, description: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Description(description)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NavigationRow(label: String, description: String, destructive: Boolean = false, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge,
                color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            Description(description)
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null,
            tint = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
