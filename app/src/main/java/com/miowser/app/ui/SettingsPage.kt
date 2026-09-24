package com.miowser.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.miowser.app.settings.DownloadThreadCount
import com.miowser.app.settings.HomePageMode
import com.miowser.app.settings.NewTabConfirmation
import com.miowser.app.settings.SearchEngine
import com.miowser.app.settings.Settings

/**
 * Every control here applies immediately via [onUpdate] — a mobile settings page with a
 * separate "Save" step just adds a step users have to remember to take, for state that's cheap
 * to persist on every change (SettingsStore.save is a small synchronous SharedPreferences write).
 */
@Composable
fun SettingsPage(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onOpenBrowsingData: () -> Unit,
    onOpenSitePermissions: () -> Unit,
    onBack: () -> Unit
) {
    // A local draft for the home-page text field: applying onUpdate on every keystroke would
    // persist (and re-resolve elsewhere) a URL that's still mid-edit. Committed when leaving the
    // page instead (onBack below), same as it was committed on sheet-dismiss before this was a page.
    var homePageDraft by remember(settings.homePageUrl) { mutableStateOf(settings.homePageUrl) }
    var minimumDurationDraft by remember(settings.grabberMinimumDurationSeconds) {
        mutableFloatStateOf(settings.grabberMinimumDurationSeconds.toFloat())
    }

    SimplePageScaffold(
        title = "Settings",
        onBack = {
            if (homePageDraft != settings.homePageUrl && homePageDraft.isNotBlank()) {
                onUpdate(settings.copy(homePageUrl = homePageDraft))
            }
            onBack()
        }
    ) { modifier ->
        Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp)) {
            SettingRow("Restore tabs on startup", settings.restoreTabsOnStartup) {
                onUpdate(settings.copy(restoreTabsOnStartup = it))
            }
            SettingRow("Delete inactive tabs after 30 days", settings.autoDeleteInactiveTabs) {
                onUpdate(settings.copy(autoDeleteInactiveTabs = it))
            }
            TextButton(onClick = onOpenBrowsingData) { Text("Clear browsing data") }
            TextButton(onClick = onOpenSitePermissions) { Text("Site permissions") }
            val minimumSeconds = (minimumDurationDraft / 30f).roundToInt() * 30
            Text("Media grabber", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp))
            Text(
                if (minimumSeconds == 0) "Minimum media length: Off"
                else "Minimum media length: ${formatDuration(minimumSeconds)}",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = minimumDurationDraft,
                onValueChange = { minimumDurationDraft = it },
                onValueChangeFinished = {
                    onUpdate(settings.copy(grabberMinimumDurationSeconds = minimumSeconds))
                },
                valueRange = 0f..1_800f,
                steps = 59
            )
            Text(
                "Media with an unknown length is hidden when a minimum is enabled.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text("Website behavior", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 12.dp))
            SettingRow("JavaScript", settings.javaScriptEnabled) {
                onUpdate(settings.copy(javaScriptEnabled = it))
            }
            SettingRow("Cookies", settings.cookiesEnabled) {
                onUpdate(settings.copy(cookiesEnabled = it))
            }
            SettingRow("Page popups and new windows", settings.popupsEnabled) {
                onUpdate(settings.copy(popupsEnabled = it))
            }

            Text("Browser home page", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            FlowRow {
                HomePageMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.homePageMode == mode,
                        onClick = { onUpdate(settings.copy(homePageMode = mode)) },
                        label = { Text(mode.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
            if (settings.homePageMode == HomePageMode.CUSTOM) {
                OutlinedTextField(
                    value = homePageDraft,
                    onValueChange = { homePageDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Custom home URL") },
                    singleLine = true
                )
            }

            Text("Search engine", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            FlowRow {
                SearchEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.searchEngine == engine,
                        onClick = { onUpdate(settings.copy(searchEngine = engine)) },
                        label = { Text(engine.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Text(
                "Confirm before opening a new tab",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            FlowRow {
                NewTabConfirmation.entries.forEach { option ->
                    FilterChip(
                        selected = settings.newTabConfirmation == option,
                        onClick = { onUpdate(settings.copy(newTabConfirmation = option)) },
                        label = { Text(option.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }

            Text(
                "Simultaneous download connections",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
            )
            FlowRow {
                DownloadThreadCount.entries.forEach { option ->
                    FilterChip(
                        selected = settings.downloadThreadCount == option,
                        onClick = { onUpdate(settings.copy(downloadThreadCount = option)) },
                        label = { Text(option.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
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
private fun SettingRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChecked)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Switch(checked = checked, onCheckedChange = null)
        }
    }
}
