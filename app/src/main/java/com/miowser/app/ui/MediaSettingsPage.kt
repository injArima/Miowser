package com.miowser.app.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.miowser.app.ui.theme.AppThemeMode
import com.miowser.app.ui.theme.AppThemePreference
import com.miowser.app.ui.theme.ExpressiveTokens
import com.miowser.app.ui.theme.UiSpacing
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MediaSettingsPage(
    hasSavedMedia: Boolean,
    onClearLibrary: () -> Unit,
    onOpenDirectories: () -> Unit,
    onOpenSubtitles: () -> Unit,
    onOpenCrashReports: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings = AppSettings.current
    var showPermissions by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var orientation by remember { mutableStateOf(PlayerDisplayPreferenceStore.orientation(context)) }
    var aspectRatio by remember {
        val saved = PlayerDisplayPreferenceStore.scaleMode(context)
        mutableStateOf(PlayerScaleMode.entries.firstOrNull { it.name == saved } ?: PlayerScaleMode.FIT)
    }
    var brightness by remember { mutableStateOf(PlayerDisplayPreferenceStore.brightness(context)) }

    SimplePageScaffold(title = "Media settings", onBack = onBack) { modifier ->
        Column(
            modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(UiSpacing.l),
            verticalArrangement = Arrangement.spacedBy(ExpressiveTokens.sectionGap)
        ) {
            SettingsSection("Media library") {
                NavigationRow("Media library directories", "Link, relink, or unlink folders in Device Videos.",
                    onClick = onOpenDirectories)
                SectionDivider()
                SettingRow("Device video discovery",
                    "Find videos in device storage and linked folders. You can still open individual files when this is off.",
                    settings.deviceDiscoveryEnabled) { enabled ->
                    AppSettings.update(context) { it.copy(deviceDiscoveryEnabled = enabled) }
                }
                if (settings.deviceDiscoveryEnabled) {
                    SectionDivider()
                    SettingRow("Auto rescan",
                        "Check for changes while Device Videos is open. Opening it and manual refresh also check for updates.",
                        settings.deviceAutoRescanEnabled) { enabled ->
                        AppSettings.update(context) { it.copy(deviceAutoRescanEnabled = enabled) }
                    }
                }
            }
            SettingsSection("Video") {
                SettingRow("Background playback",
                    "Keep headphone mode playing when Miowser is minimized or the screen is off.",
                    settings.backgroundPlaybackEnabled) { enabled ->
                    AppSettings.update(context) { it.copy(backgroundPlaybackEnabled = enabled) }
                }
                SectionDivider()
                OptionBlock("Screen orientation", "Choose how both players open. You can change orientation during playback.") {
                    ChoiceRow(PlayerOrientationMode.entries, orientation, { it.label }) { mode ->
                        orientation = mode
                        PlayerDisplayPreferenceStore.saveOrientation(context, mode)
                    }
                }
            }
            SettingsSection("Network") {
                OptionBlock("Simultaneous download connections",
                    "More connections may speed up large downloads on fast servers. Fewer can help on slower connections.") {
                    ChoiceRow(DownloadThreadCount.entries, settings.downloadThreadCount, { it.label }) { count ->
                        AppSettings.update(context) { it.copy(downloadThreadCount = count) }
                    }
                }
            }
            SettingsSection("Permissions") {
                NavigationRow("Media permissions", "Review device video access and open Android's app permissions.") {
                    showPermissions = true
                }
            }
            SettingsSection("History") {
                val description = when (settings.libraryBehavior) {
                    LibraryBehavior.AUTOMATIC -> "Save played media and progress automatically."
                    LibraryBehavior.MANUAL_ONLY -> "Keep only media you add or import, and update its progress."
                    LibraryBehavior.DISABLED -> "Hide the Miowser Library and keep existing entries for later."
                }
                OptionBlock("Playback history", description) {
                    ChoiceRow(LibraryBehavior.entries, settings.libraryBehavior, { it.label }) { behavior ->
                        AppSettings.update(context) { it.copy(libraryBehavior = behavior) }
                    }
                }
                if (settings.libraryBehavior.isAvailable || settings.deviceDiscoveryEnabled) {
                    SectionDivider()
                    SettingRow("Resume video playback",
                        "Start a video from its last saved position when available.",
                        settings.resumeSavedVideos) { enabled ->
                        AppSettings.update(context) { it.copy(resumeSavedVideos = enabled) }
                    }
                }
                if (settings.libraryBehavior.isAvailable && hasSavedMedia) {
                    SectionDivider()
                    NavigationRow("Clear Library", "Remove saved Library entries and playback progress.", destructive = true) {
                        confirmClear = true
                    }
                }
            }
            Text("Extra settings", style = MaterialTheme.typography.titleMedium)
            SettingsSection("Interface") {
                OptionBlock("Appearance", "Choose the theme for the Hub and browser.") {
                    ChoiceRow(AppThemeMode.entries, AppThemePreference.mode, { it.label }) { mode ->
                        AppThemePreference.set(context, mode)
                    }
                }
                SectionDivider()
                SettingRow("Reduce motion", "Use simpler transitions. Android's remove-animations setting is always respected.",
                    settings.reduceMotion) { enabled ->
                    AppSettings.update(context) { it.copy(reduceMotion = enabled) }
                }
            }
            SettingsSection("Video display") {
                OptionBlock("Aspect ratio", "Set how video fills the screen. Changes made in either player become the new default.") {
                    ChoiceRow(PlayerScaleMode.entries, aspectRatio, { it.label }) { mode ->
                        aspectRatio = mode
                        PlayerDisplayPreferenceStore.saveScaleMode(context, mode.name)
                    }
                }
                SectionDivider()
                OptionBlock("Brightness", "Use system brightness or set a starting level for both players.") {
                    val choices = listOf(false, true)
                    ChoiceRow(choices, brightness != null, { if (it) "Custom" else "System" }) { custom ->
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
            SettingsSection("Subtitles") {
                NavigationRow("Subtitle appearance", "Set the font, size, colors, outline, opacity, and position for both players.",
                    onClick = onOpenSubtitles)
            }
            SettingsSection("Advanced") {
                Text("Optional features", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = UiSpacing.l, top = UiSpacing.l))
                SettingRow("Browse source while playing", "Keep the player open and paused while you browse its source page.",
                    settings.companionBrowsingEnabled) { enabled ->
                    AppSettings.update(context) { it.copy(companionBrowsingEnabled = enabled) }
                }
                SectionDivider()
                Text("Diagnostics", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = UiSpacing.l, top = UiSpacing.l))
                SettingRow("Save crash diagnostics", "Keep redacted crash reports on this device.",
                    settings.crashDiagnosticsEnabled) { enabled ->
                    AppSettings.update(context) { it.copy(crashDiagnosticsEnabled = enabled) }
                }
                SectionDivider()
                NavigationRow("Crash reports", "View, copy, share, or clear reports stored on this device.",
                    onClick = onOpenCrashReports)
            }
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
    SimplePageScaffold(title = "Subtitle appearance", onBack = onBack) { modifier ->
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
