package com.miowser.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import com.miowser.app.ui.AppText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.miowser.app.browser.BrowsingDataSelection
import com.miowser.app.ui.theme.UiSpacing

@Composable
fun BrowsingDataPage(onClear: (BrowsingDataSelection) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf(BrowsingDataSelection()) }
    var confirm by remember { mutableStateOf(false) }
    SimplePageScaffold(title = "Clear browsing data", onBack = onBack) { modifier ->
        Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(UiSpacing.xl)) {
            Text("Choose exactly what to erase", style = MaterialTheme.typography.titleMedium)
            DataChoice("History", selected.history) { selected = selected.copy(history = it) }
            DataChoice("Cookies and website storage", selected.cookiesAndSiteStorage) {
                selected = selected.copy(cookiesAndSiteStorage = it)
            }
            DataChoice("Page cache", selected.cache) { selected = selected.copy(cache = it) }
            DataChoice("WebView form suggestions", selected.formData) { selected = selected.copy(formData = it) }
            DataChoice("Site permission decisions", selected.sitePermissions) {
                selected = selected.copy(sitePermissions = it)
            }
            Text("Saved app records", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = UiSpacing.l))
            Text("Downloaded files stay on your device. These choices remove their app records only.",
                style = MaterialTheme.typography.bodySmall)
            DataChoice("Download records", selected.downloadRecords) {
                selected = selected.copy(downloadRecords = it)
            }
            DataChoice("Library records", selected.libraryRecords) {
                selected = selected.copy(libraryRecords = it)
            }
            MiowserActionButton(onClick = { confirm = true }, enabled = selected.hasSelection(),
                style = AppButtonStyle.CAUTION,
                modifier = Modifier.padding(top = UiSpacing.xl)) { Text("Clear selected data") }
        }
    }
    if (confirm) {
        MiowserConfirmationDialog(
            title = "Clear selected data?",
            message = "This removes the checked categories from Miowser. This cannot be undone.",
            onDismiss = { confirm = false },
            actions = listOf(
                ConfirmationAction("Cancel", { confirm = false }, ConfirmationTone.CAUTION),
                ConfirmationAction("Clear", { confirm = false; onClear(selected) }, ConfirmationTone.CAUTION)
            )
        )
    }
}

@Composable
private fun DataChoice(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Checkbox, onValueChange = onChecked)
        .padding(vertical = UiSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, modifier = Modifier.padding(start = UiSpacing.s))
    }
}
