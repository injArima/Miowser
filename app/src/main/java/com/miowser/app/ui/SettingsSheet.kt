package com.miowser.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.miowser.app.settings.SearchEngine
import com.miowser.app.settings.Settings

/**
 * Every control here applies immediately via [onUpdate] — a mobile settings sheet with a
 * separate "Save" step just adds a step users have to remember to take, for state that's cheap
 * to persist on every change (SettingsStore.save is a small synchronous SharedPreferences write).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: Settings,
    onUpdate: (Settings) -> Unit,
    onDismiss: () -> Unit
) {
    // A local draft for the home-page text field: applying onUpdate on every keystroke would
    // persist (and re-resolve elsewhere) a URL that's still mid-edit. Committed on blur/dismiss
    // instead, via the LaunchedEffect(Unit)-style flush below.
    var homePageDraft by remember(settings.homePageUrl) { mutableStateOf(settings.homePageUrl) }

    ModalBottomSheet(onDismissRequest = {
        if (homePageDraft != settings.homePageUrl && homePageDraft.isNotBlank()) {
            onUpdate(settings.copy(homePageUrl = homePageDraft))
        }
        onDismiss()
    }) {
        Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
            Text("Settings", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 16.dp))

            SettingRow(label = "Block ads and trackers") {
                Switch(
                    checked = settings.adBlockEnabled,
                    onCheckedChange = { onUpdate(settings.copy(adBlockEnabled = it)) }
                )
            }

            SettingRow(label = "Restore tabs on startup") {
                Switch(
                    checked = settings.restoreTabsOnStartup,
                    onCheckedChange = { onUpdate(settings.copy(restoreTabsOnStartup = it)) }
                )
            }

            Text("Home page", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            OutlinedTextField(
                value = homePageDraft,
                onValueChange = { homePageDraft = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text("Search engine", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp))
            Row {
                SearchEngine.entries.forEach { engine ->
                    FilterChip(
                        selected = settings.searchEngine == engine,
                        onClick = { onUpdate(settings.copy(searchEngine = engine)) },
                        label = { Text(engine.label) },
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(label: String, control: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, modifier = Modifier.weight(1f))
        control()
    }
}
