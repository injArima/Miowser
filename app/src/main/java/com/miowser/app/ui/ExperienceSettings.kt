@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.miowser.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.miowser.app.settings.*
import com.miowser.app.ui.theme.ExperienceTokens
import com.miowser.app.ui.theme.ExpressiveTokens
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.AppText as Text

/** Shared by More and the master Settings destination reachable without the hub. */
@Composable
internal fun ExperienceSettingsContent(
    onCustomize: () -> Unit,
    onMedia: () -> Unit,
    onBrowser: () -> Unit,
    onBackup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings = AppSettings.current
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(ExpressiveTokens.pageGutter), verticalArrangement = Arrangement.spacedBy(ExpressiveTokens.sectionGap)) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            ExperienceCard(settings, onCustomize)
        }
        SettingsSection("Settings") {
            if (settings.browserAvailable && settings.experiencePriority == ExperiencePriority.BROWSER_FIRST) {
                NavigationRow("Browser settings", "Browsing, tabs, privacy, and websites", onClick = onBrowser)
            }
            NavigationRow("Media settings", if (settings.deviceLibraryAvailable)
                "Device folders, playback, downloads, and appearance" else "Playback, subtitles, downloads, and appearance", onClick = onMedia)
            if (settings.browserAvailable && settings.experiencePriority != ExperiencePriority.BROWSER_FIRST) {
                NavigationRow("Browser settings", "Browsing, tabs, privacy, and websites", onClick = onBrowser)
            }
            NavigationRow("Backup & restore", "Save app data or restore a previous backup.", onClick = onBackup)
        }
    }
}

@Composable
private fun ExperienceCard(settings: Settings, onCustomize: () -> Unit) {
    Surface(modifier = Modifier.widthIn(max = ExperienceTokens.cardMaxWidth).fillMaxWidth()
        .aspectRatio(ExperienceTokens.cardAspectRatio).settingsSearchAnchor("Customize Miowser")
        .testTag("experience_card"), shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
        Column(Modifier.fillMaxSize().padding(ExperienceTokens.cardPadding)) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.l)) {
                Icon(Icons.Rounded.Tune, null, tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(ExperienceTokens.choiceIconSize))
                Text(settings.experienceTitle, style = MaterialTheme.typography.headlineSmall)
                Text(if (settings.experienceCustomized) settings.experienceSummary else
                    "Welcome to Miowser. A video player with a browser alongside it—or a browser with more possibilities. Choose what belongs in your app.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MiowserActionButton(onClick = onCustomize, style = AppButtonStyle.TEXT,
                modifier = Modifier.align(Alignment.End).testTag("experience_customize")) {
                Text(if (settings.experienceCustomized) "Customize again" else "Customize")
            }
        }
    }
}

@Composable
internal fun MasterSettingsPage(onCustomize: () -> Unit, onMedia: () -> Unit,
    onBrowser: () -> Unit, onBackup: () -> Unit, onBack: () -> Unit) {
    SimplePageScaffold("Settings", onBack) { modifier ->
        ExperienceSettingsContent(onCustomize, onMedia, onBrowser, onBackup, modifier)
    }
}

/** Draft choices survive recreation, but preferences change only after Review → Apply. */
@Composable
internal fun CustomizeExperiencePage(onApply: (AppExperience, ExperiencePriority, LibraryBehavior) -> Unit,
    onCancel: () -> Unit) {
    val saved = AppSettings.current
    var step by rememberSaveable { mutableStateOf(ExperienceStep.INTRO) }
    var mode by rememberSaveable { mutableStateOf(saved.experience) }
    var priority by rememberSaveable { mutableStateOf(saved.experiencePriority) }
    var history by rememberSaveable { mutableStateOf(saved.libraryBehavior) }
    fun back() { step.previous(mode)?.let { step = it } ?: onCancel() }
    BackHandler(onBack = ::back)
    val preview = saved.withExperience(mode, priority, history)
    SimplePageScaffold("Customize Miowser", ::back) { modifier ->
        Column(modifier.fillMaxSize()) {
            key(step) {
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    .padding(ExpressiveTokens.pageGutter), verticalArrangement = Arrangement.spacedBy(UiSpacing.l)) {
                    Text(when (step) {
                        ExperienceStep.INTRO -> "Make Miowser feel like yours"
                        ExperienceStep.PRIORITY -> "Which experience should lead?"
                        ExperienceStep.FEATURES -> "What would you like to use?"
                        ExperienceStep.HISTORY -> "How should your watch catalog work?"
                        ExperienceStep.REVIEW -> "Your Miowser setup"
                    }, style = MaterialTheme.typography.headlineSmall)
                    when (step) {
                        ExperienceStep.INTRO -> Text("Exploring Miowser first can help you decide. Try the browser, players, device library, and subtitle tools—or customize now. You can change these choices at any time.")
                        ExperienceStep.PRIORITY -> {
                            Text("Choose what opens first when you use both, and which settings come first. This choice doesn't disable features.")
                            SegmentedListGroup {
                                ExperienceChoice("Browser first", "A browser with a video player alongside", priority == ExperiencePriority.BROWSER_FIRST) { priority = ExperiencePriority.BROWSER_FIRST }
                                ExperienceChoice("Player first", "A video player with a browser alongside", priority == ExperiencePriority.PLAYER_FIRST) { priority = ExperiencePriority.PLAYER_FIRST }
                            }
                        }
                        ExperienceStep.FEATURES -> SegmentedListGroup {
                            ExperienceChoice("Browser only", "Browser, Rich player, WebView player, and subtitle tools. Hide the device library.", mode == AppExperience.BROWSER_ONLY) { mode = AppExperience.BROWSER_ONLY }
                            ExperienceChoice("Player only", "Device videos and downloads. Hide the browser and online watch catalog.", mode == AppExperience.PLAYER_ONLY) { mode = AppExperience.PLAYER_ONLY }
                            ExperienceChoice("Both", "The complete Miowser experience: browser, device videos, and downloads.", mode == AppExperience.BOTH) { mode = AppExperience.BOTH }
                        }
                        ExperienceStep.HISTORY -> {
                            Text("This applies to online videos. Private browsing is excluded. Existing records are kept when the catalog is hidden.")
                            SegmentedListGroup {
                                ExperienceChoice("Automatic", "Save online videos when played and remember their progress.", history == LibraryBehavior.AUTOMATIC) { history = LibraryBehavior.AUTOMATIC }
                                ExperienceChoice("Manual", "Save only the videos you choose, and update their progress.", history == LibraryBehavior.MANUAL_ONLY) { history = LibraryBehavior.MANUAL_ONLY }
                                ExperienceChoice("Disabled", "Hide the watch catalog. Keep your records for later.", history == LibraryBehavior.DISABLED) { history = LibraryBehavior.DISABLED }
                            }
                        }
                        ExperienceStep.REVIEW -> {
                            Text(preview.experienceTitle, style = MaterialTheme.typography.titleLarge)
                            Text(preview.experienceSummary)
                            if (mode != AppExperience.BOTH) Text("Your ${priority.label.lowercase()} preference is remembered for when you use both.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Hidden sections keep their data and preferences. Downloads and shared settings stay available. Nothing changes until you apply this setup.")
                        }
                    }
                }
            }
            FlowRow(Modifier.fillMaxWidth().padding(ExpressiveTokens.pageGutter).navigationBarsPadding(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                MiowserActionButton(onClick = onCancel, style = AppButtonStyle.TEXT) {
                    Text(if (step == ExperienceStep.INTRO) "Explore first" else "Cancel")
                }
                MiowserActionButton(onClick = {
                    if (step == ExperienceStep.REVIEW) onApply(mode, priority, history) else step = step.next(mode)
                }, modifier = Modifier.testTag("experience_continue")) {
                    Text(when (step) {
                        ExperienceStep.INTRO -> "Customize now"
                        ExperienceStep.REVIEW -> "Apply setup"
                        else -> "Continue"
                    })
                }
            }
        }
    }
}

@Composable
private fun ExperienceChoice(title: String, detail: String, selected: Boolean, onClick: () -> Unit) {
    ListItem(onClick = onClick, selected = selected, shapes = segmentedListShapes(),
        modifier = Modifier.segmentedListItem(nativeSurface = true).testTag("experience_choice_$title"),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        trailingContent = { if (selected) Icon(Icons.Rounded.Check, "Selected") },
        supportingContent = { Text(detail) }) { Text(title) }
}
