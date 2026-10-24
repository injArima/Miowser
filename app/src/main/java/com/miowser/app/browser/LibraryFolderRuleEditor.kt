package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miowser.app.library.LibraryFolders
import com.miowser.app.library.LibraryRuleOperator
import com.miowser.app.library.LibraryTitleRule
import com.miowser.app.library.LibraryTitleSource
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.libraryDomain
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.MiowserConfirmationDialog

private enum class FolderRuleKind { WEBSITE, TITLE }

@Composable
internal fun LibraryFolderRuleEditor(folderId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember(folderId) { mutableStateOf(false) }
    val folder = LibraryFolders.entries.firstOrNull { it.id == folderId } ?: return
    var kind by remember(folderId) { mutableStateOf(
        when {
            folder.titleRule != null -> FolderRuleKind.TITLE
            folder.websiteDomains.isNotEmpty() -> FolderRuleKind.WEBSITE
            else -> null
        }
    ) }
    val domains = remember(folderId) { mutableStateListOf<String>().apply {
        addAll(folder.websiteDomains.ifEmpty { listOf("") })
    } }
    val phrases = remember(folderId) { mutableStateListOf<String>().apply {
        addAll(folder.titleRule?.phrases.orEmpty().ifEmpty { listOf("") })
    } }
    var source by remember(folderId) { mutableStateOf(folder.titleRule?.source) }
    var websiteOperator by remember(folderId) { mutableStateOf(
        folder.websiteOperator.takeIf { folder.websiteDomains.size > 1 }
    ) }
    var titleOperator by remember(folderId) { mutableStateOf(
        folder.titleRule?.operator?.takeIf { folder.titleRule.phrases.size > 1 }
    ) }
    var choosingOperator by remember(folderId) { mutableStateOf(false) }
    var error by remember(folderId) { mutableStateOf<String?>(null) }
    var reorganizationCount by remember(folderId) { mutableStateOf<Int?>(null) }

    fun saveRule() {
        error = null
        val saved = when (kind) {
            FolderRuleKind.WEBSITE -> {
                val parsed = domains.map { libraryDomain(it) }
                error = when {
                    parsed.any { it == null } -> "Enter a domain in every field"
                    parsed.distinct().size != parsed.size -> "Remove duplicate domains"
                    parsed.any { domain -> LibraryFolders.entries.any {
                        it.id != folderId && domain in it.websiteDomains
                    } } -> "A domain already belongs to another folder"
                    else -> null
                }
                if (error == null) LibraryFolders.replaceWebsiteRule(
                    context, folderId, parsed.filterNotNull(), websiteOperator ?: LibraryRuleOperator.ANY
                ) else false
            }
            FolderRuleKind.TITLE -> {
                val cleaned = phrases.map(String::trim)
                error = when {
                    source == null -> "Choose which title to check"
                    cleaned.any(String::isBlank) -> "Enter a phrase in every field"
                    cleaned.distinctBy(String::lowercase).size != cleaned.size -> "Remove duplicate phrases"
                    else -> null
                }
                if (error == null) LibraryFolders.replaceTitleRule(context, folderId,
                    LibraryTitleRule(source!!, titleOperator ?: LibraryRuleOperator.ANY, cleaned))
                else false
            }
            null -> false
        }
        if (!saved) {
            if (error == null) error = "Could not save this rule"
            return
        }
        busy = true
        scope.launch {
            try {
                val count = MediaLibrary.countRuleReorganization(context, folderId)
                if (count > 0) reorganizationCount = count else onDismiss()
            } catch (failure: IllegalStateException) { error = failure.message }
            finally { busy = false }
        }
    }

    val count = reorganizationCount
    if (count != null) {
        MiowserConfirmationDialog(
            onDismiss = onDismiss,
            title = "Organize existing videos?",
            message = error ?: "$count video${if (count == 1) "" else "s"} in the source folder match the current rules. Manual moves are kept.",
            actions = listOf(
                ConfirmationAction("Keep where they are", onDismiss),
                ConfirmationAction("Move videos", {
                busy = true
                scope.launch {
                    try { MediaLibrary.applyRuleReorganization(context, folderId); onDismiss() }
                    catch (failure: IllegalStateException) { error = failure.message }
                    finally { busy = false }
                }
            }, enabled = !busy)
            )
        )
    } else if (choosingOperator) {
        AlertDialog(
            onDismissRequest = { choosingOperator = false },
            title = { Text("How should the fields match?") },
            text = { Text("Choose one operator for every field in this rule.") },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        if (kind == FolderRuleKind.WEBSITE) {
                            websiteOperator = LibraryRuleOperator.ALL
                            if (domains.size == 1) domains.add("")
                        } else {
                            titleOperator = LibraryRuleOperator.ALL
                            if (phrases.size == 1) phrases.add("")
                        }
                        choosingOperator = false
                    }) { Text("Contains all of (AND)") }
                    TextButton(onClick = {
                        if (kind == FolderRuleKind.WEBSITE) {
                            websiteOperator = LibraryRuleOperator.ANY
                            if (domains.size == 1) domains.add("")
                        } else {
                            titleOperator = LibraryRuleOperator.ANY
                            if (phrases.size == 1) phrases.add("")
                        }
                        choosingOperator = false
                    }) { Text("Contains any of (OR)") }
                }
            },
            dismissButton = { TextButton(onClick = { choosingOperator = false }) { Text("Cancel") } }
        )
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Folder rule · ${folder.name}") },
            text = {
                Column(
                    modifier = Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Choose one rule type", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == FolderRuleKind.WEBSITE,
                            onClick = { kind = FolderRuleKind.WEBSITE; error = null },
                            label = { Text("Website") })
                        FilterChip(selected = kind == FolderRuleKind.TITLE,
                            onClick = { kind = FolderRuleKind.TITLE; error = null },
                            label = { Text("Title") })
                    }
                    when (kind) {
                        FolderRuleKind.WEBSITE -> {
                            Text("Match page or media URL hosts. AND requires every domain to appear across those hosts.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RuleFields(domains, "Domain or URL") { error = null }
                            RuleAddField(domains, websiteOperator,
                                onChooseOperator = { choosingOperator = true }, onAdd = { domains.add("") },
                                onError = { error = it })
                            OperatorLabel(websiteOperator)
                        }
                        FolderRuleKind.TITLE -> {
                            Text("Check", style = MaterialTheme.typography.bodySmall)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(
                                    LibraryTitleSource.MEDIA to "Media",
                                    LibraryTitleSource.PAGE to "Page",
                                    LibraryTitleSource.COMBINED to "Both"
                                ).forEach { (option, label) ->
                                    FilterChip(selected = source == option, onClick = { source = option; error = null },
                                        label = { Text(label) })
                                }
                            }
                            Text("Whole words and phrases; capitalization and punctuation do not matter.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            RuleFields(phrases, "Word or phrase") { error = null }
                            RuleAddField(phrases, titleOperator,
                                onChooseOperator = { choosingOperator = true }, onAdd = { phrases.add("") },
                                onError = { error = it })
                            OperatorLabel(titleOperator)
                        }
                        null -> Unit
                    }
                    if (folder.titleRule != null && kind == FolderRuleKind.WEBSITE ||
                        folder.websiteDomains.isNotEmpty() && kind == FolderRuleKind.TITLE) {
                        Text("Saving replaces this folder's current rule.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    if (folder.titleRule != null || folder.websiteDomains.isNotEmpty()) {
                        TextButton(onClick = {
                            LibraryFolders.replaceTitleRule(context, folderId, null)
                            onDismiss()
                        }) { Text("Remove rule") }
                    }
                }
            },
            confirmButton = { TextButton(enabled = !busy && kind != null, onClick = ::saveRule) { Text("Save rule") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}

@Composable
private fun RuleFields(values: SnapshotStateList<String>, label: String, onChange: () -> Unit) {
    values.indices.forEach { index ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = values[index],
                onValueChange = { values[index] = it; onChange() },
                label = { Text(if (values.size == 1) label else "$label ${index + 1}") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (values.size > 1) IconButton(onClick = { values.removeAt(index); onChange() }) {
                Icon(Icons.Rounded.Close, contentDescription = "Remove field ${index + 1}")
            }
        }
    }
}

@Composable
private fun RuleAddField(
    values: SnapshotStateList<String>, operator: LibraryRuleOperator?,
    onChooseOperator: () -> Unit, onAdd: () -> Unit, onError: (String) -> Unit
) {
    TextButton(onClick = {
        if (values.any(String::isBlank)) onError("Fill the current field first")
        else if (operator == null) onChooseOperator()
        else onAdd()
    }) {
        Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
        Text("Add another field", modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun OperatorLabel(operator: LibraryRuleOperator?) {
    if (operator != null) {
        Text(if (operator == LibraryRuleOperator.ALL) "Contains all of (AND)" else "Contains any of (OR)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
