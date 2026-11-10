package com.miowser.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.miowser.app.library.device.DeviceMediaAccess
import com.miowser.app.library.device.deviceMediaAccess
import com.miowser.app.library.device.deviceVideoPermissions
import com.miowser.app.player.PlayerDisplayPreferenceStore
import com.miowser.app.player.PlayerOrientationMode
import com.miowser.app.player.PlayerScaleMode
import com.miowser.app.player.PlayerSubtitleStyleStore
import com.miowser.app.player.SubtitleAppearanceControls
import com.miowser.app.settings.AppSettings
import com.miowser.app.settings.DownloadThreadCount
import com.miowser.app.settings.LibraryBehavior
import com.miowser.app.ui.theme.ActionButtonTokens
import com.miowser.app.ui.theme.AppThemeMode
import com.miowser.app.ui.theme.AppThemePreference
import com.miowser.app.ui.theme.UiSpacing
import kotlin.math.roundToInt

@Composable
internal fun MediaSettingsPage(
    hasSavedMedia: Boolean,
    onClearLibrary: () -> Unit,
    onOpenDirectories: () -> Unit,
    onOpenInterface: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenAdvanced: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = AppSettings.current
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var orientation by remember { mutableStateOf(PlayerDisplayPreferenceStore.orientation(context)) }

    MediaSettingsLayout("Media settings", onBack) {
        MediaSettingsSection("Media library") {
            NavigationRow("Media library folders", "Choose folders to include in Device Videos.",
                onClick = onOpenDirectories)
            SettingRow("Device video discovery",
                "Find videos in device storage and linked folders. You can still open individual files when this is off.",
                settings.deviceDiscoveryEnabled) { enabled ->
                AppSettings.update(context) { it.copy(deviceDiscoveryEnabled = enabled) }
            }
            if (settings.deviceDiscoveryEnabled) {
                SettingRow("Auto rescan", "Check for changes while Device Videos is open.",
                    settings.deviceAutoRescanEnabled) { enabled ->
                    AppSettings.update(context) { it.copy(deviceAutoRescanEnabled = enabled) }
                }
            }
        }
        MediaSettingsSection("Video") {
            SettingRow("Background playback",
                "Keep headphone mode playing when Miowser is minimized or the screen is off.",
                settings.backgroundPlaybackEnabled) { enabled ->
                AppSettings.update(context) { it.copy(backgroundPlaybackEnabled = enabled) }
            }
            MediaChoiceRow("Screen orientation", "Choose how both players open.",
                PlayerOrientationMode.entries, orientation, { it.label }) { mode ->
                orientation = mode
                PlayerDisplayPreferenceStore.saveOrientation(context, mode)
            }
        }
        MediaSettingsSection("Network") {
            MediaChoiceRow("Simultaneous download connections",
                "More connections may speed up large downloads on fast servers. Fewer can help on slower connections.",
                DownloadThreadCount.entries, settings.downloadThreadCount, { it.label }) { count ->
                AppSettings.update(context) { it.copy(downloadThreadCount = count) }
            }
        }
        MediaSettingsSection("Permissions") {
            NavigationRow("Media permissions", "Review device video access and Android app permissions.") {
                showPermissions = true
            }
        }
        MediaSettingsSection("History") {
            MediaChoiceRow("Playback history",
                "Automatic saves played media and progress. Manual only keeps entries you add or import. Disabled hides the Library and keeps your entries.",
                LibraryBehavior.entries, settings.libraryBehavior, { it.label }) { behavior ->
                AppSettings.update(context) { it.copy(libraryBehavior = behavior) }
            }
            if (settings.libraryBehavior.isAvailable || settings.deviceDiscoveryEnabled) {
                SettingRow("Resume video playback", "Start a video from its last saved position when available.",
                    settings.resumeSavedVideos) { enabled ->
                    AppSettings.update(context) { it.copy(resumeSavedVideos = enabled) }
                }
            }
            if (settings.libraryBehavior.isAvailable && hasSavedMedia) {
                NavigationRow("Clear Library", "Remove saved Library entries and playback progress.", destructive = true) {
                    confirmClear = true
                }
            }
        }
        MediaSettingsSection("Extra settings", divider = false) {
            MediaCategoryRow("Interface", Icons.Rounded.Palette, onOpenInterface)
            MediaCategoryRow("Video", Icons.Rounded.Movie, onOpenVideo)
            MediaCategoryRow("Subtitles", Icons.Rounded.Subtitles, onOpenSubtitles)
            MediaCategoryRow("Advanced", Icons.Rounded.Build, onOpenAdvanced)
        }
    }

    if (showPermissions) MediaPermissionsDialog(onDismiss = { showPermissions = false })
    if (confirmClear) {
        ClearLibraryConfirmationDialog(
            onDismiss = { confirmClear = false },
            onClear = onClearLibrary
        )
    }
}

@Composable
internal fun MediaInterfaceSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    MediaSettingsLayout("Interface", onBack) {
        MediaChoiceRow("Appearance", "Choose the theme for the Hub and browser.",
            AppThemeMode.entries, AppThemePreference.mode, { it.label }) { mode ->
            AppThemePreference.set(context, mode)
        }
        SectionDivider()
        SettingRow("Reduce motion", "Use simpler transitions. Android's remove-animations setting is always respected.",
            AppSettings.current.reduceMotion) { enabled ->
            AppSettings.update(context) { it.copy(reduceMotion = enabled) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaVideoSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var aspectRatio by remember {
        val saved = PlayerDisplayPreferenceStore.scaleMode(context)
        mutableStateOf(PlayerScaleMode.entries.firstOrNull { it.name == saved } ?: PlayerScaleMode.FIT)
    }
    var brightness by remember { mutableStateOf(PlayerDisplayPreferenceStore.brightness(context)) }

    MediaSettingsLayout("Video", onBack) {
        MediaChoiceRow("Aspect ratio", "Set how video fills the screen in both players.",
            PlayerScaleMode.entries, aspectRatio, { it.label }) { mode ->
            aspectRatio = mode
            PlayerDisplayPreferenceStore.saveScaleMode(context, mode.name)
        }
        SectionDivider()
        OptionBlock("Brightness", "Use system brightness or set a starting level for both players.") {
            ChoiceRow(listOf(false, true), brightness != null, { if (it) "Custom" else "System" }) { custom ->
                val value = if (custom) brightness ?: 0.5f else null
                brightness = value
                PlayerDisplayPreferenceStore.saveBrightness(context, value)
            }
            brightness?.let { value ->
                Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
                val slider = rememberSliderState(value = value, trackRange = 0.01f..1f)
                Slider(
                    state = slider,
                    onValueChange = {
                        slider.value = it
                        brightness = it
                    },
                    onValueChangeFinished = { PlayerDisplayPreferenceStore.saveBrightness(context, slider.value) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun MediaAdvancedSettingsPage(onOpenCrashReports: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = AppSettings.current
    MediaSettingsLayout("Advanced", onBack) {
        MediaSettingsSection("Optional features") {
            SettingRow("Browse source while playing", "Keep the player open and paused while you browse its source page.",
                settings.companionBrowsingEnabled) { enabled ->
                AppSettings.update(context) { it.copy(companionBrowsingEnabled = enabled) }
            }
        }
        MediaSettingsSection("Diagnostics", divider = false) {
            SettingRow("Save crash diagnostics", "Keep redacted crash reports on this device.",
                settings.crashDiagnosticsEnabled) { enabled ->
                AppSettings.update(context) { it.copy(crashDiagnosticsEnabled = enabled) }
            }
            NavigationRow("Crash reports", "View, copy, share, or clear reports stored on this device.",
                onClick = onOpenCrashReports)
        }
    }
}

@Composable
private fun MediaSettingsLayout(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SimplePageScaffold(title = title, onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = UiSpacing.l),
            content = content)
    }
}

@Composable
private fun MediaSettingsSection(
    title: String,
    divider: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = UiSpacing.l, top = UiSpacing.xl, bottom = UiSpacing.xs)
                .semantics { heading() })
        content()
        if (divider) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun MediaCategoryRow(label: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick)
    ) { Text(label) }
}

@Composable
private fun <T> MediaChoiceRow(
    title: String,
    description: String,
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    var showChoices by rememberSaveable { mutableStateOf(false) }
    NavigationRow(title, label(selected)) { showChoices = true }
    if (showChoices) {
        AlertDialog(
            onDismissRequest = { showChoices = false },
            title = { Text(title) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(description, modifier = Modifier.padding(bottom = UiSpacing.s))
                    Column(Modifier.selectableGroup()) {
                        options.forEach { option ->
                            Row(
                                Modifier.fillMaxWidth()
                                    .selectable(selected = option == selected, role = Role.RadioButton, onClick = {
                                        onSelect(option)
                                        showChoices = false
                                    })
                                    .heightIn(min = ActionButtonTokens.minHeight),
                                horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(selected = option == selected, onClick = null)
                                Text(label(option))
                            }
                        }
                    }
                }
            },
            confirmButton = {
                MiowserActionButton(style = AppButtonStyle.TEXT, onClick = { showChoices = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
internal fun ClearLibraryConfirmationDialog(onDismiss: () -> Unit, onClear: () -> Unit) {
    MiowserConfirmationDialog(
        title = "Clear Library?",
        message = "This removes all playback records from the Library. Downloaded files will not be deleted.",
        onDismiss = onDismiss,
        actions = listOf(
            ConfirmationAction("Cancel", onDismiss, ConfirmationTone.CAUTION),
            ConfirmationAction("Clear", {
                onDismiss()
                onClear()
            }, ConfirmationTone.CAUTION)
        )
    )
}

@Composable
internal fun MediaSubtitleSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(PlayerSubtitleStyleStore.load(context)) }
    SimplePageScaffold(title = "Subtitles", onBack = onBack) { modifier ->
        Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.l),
            verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
            SubtitleAppearanceControls(style, onChange = { updated ->
                style = updated
                PlayerSubtitleStyleStore.save(context, updated)
            })
        }
    }
}

@Composable
private fun MediaPermissionsDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var access by remember { mutableStateOf(deviceMediaAccess(context)) }
    val requestAccess = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        access = deviceMediaAccess(context)
    }
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { access = deviceMediaAccess(context) }
    }
    val status = when (access) {
        DeviceMediaAccess.FULL -> "All device videos"
        DeviceMediaAccess.SELECTED -> "Selected videos only"
        DeviceMediaAccess.NONE -> "No device video access"
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Media permissions") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(UiSpacing.m)) {
                Text(status, style = MaterialTheme.typography.titleSmall)
                Text("Linked folders have separate access. You can review them in Media library directories.")
                if (access != DeviceMediaAccess.FULL) {
                    MiowserActionButton(style = AppButtonStyle.TONAL,
                        onClick = { requestAccess.launch(deviceVideoPermissions()) }) {
                        Text(if (access == DeviceMediaAccess.SELECTED) "Change video access" else "Request video access")
                    }
                }
                MiowserActionButton(style = AppButtonStyle.TEXT, onClick = {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)))
                }) { Text("Android app settings") }
            }
        },
        confirmButton = { MiowserActionButton(style = AppButtonStyle.TEXT, onClick = onDismiss) { Text("Done") } }
    )
}
