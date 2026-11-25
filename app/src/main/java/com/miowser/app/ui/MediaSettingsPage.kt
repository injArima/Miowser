package com.miowser.app.ui

import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.FolderOpen
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import com.miowser.app.ui.AppText as Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.miowser.app.library.device.DeviceCatalogSession
import com.miowser.app.library.device.DeviceMediaAccess
import com.miowser.app.library.device.deviceMediaAccess
import com.miowser.app.library.device.deviceVideoPermissions
import com.miowser.app.player.PlayerDisplayPreferenceStore
import com.miowser.app.player.PlayerOrientationMode
import com.miowser.app.player.PlayerScaleMode
import com.miowser.app.player.PlayerSubtitleStyleStore
import com.miowser.app.player.SubtitleCuePreview
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import com.miowser.app.settings.AppSettings
import com.miowser.app.settings.DownloadThreadCount
import com.miowser.app.settings.LibraryBehavior
import com.miowser.app.ui.theme.AppThemeMode
import com.miowser.app.ui.theme.AppThemePreference
import com.miowser.app.ui.theme.ExpressiveTokens
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.SubtitlePanelTokens
import androidx.compose.foundation.background
import androidx.compose.ui.platform.testTag
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
        if (settings.deviceLibraryAvailable) SettingsSection("Media library") {
            NavigationRow("Media library folders", "Choose folders to include in Device Videos.",
                leadingIcon = Icons.Rounded.FolderOpen, onClick = onOpenDirectories)
            SettingRow("Device video discovery",
                "Find videos in device storage and linked folders. You can still open individual files when this is off.",
                settings.deviceDiscoveryEnabled) { enabled ->
                AppSettings.update(context) { it.copy(deviceDiscoveryEnabled = enabled) }
                DeviceCatalogSession.setDiscoveryEnabled(AppSettings.current.deviceScanningEnabled)
            }
            if (settings.deviceDiscoveryEnabled) {
                SettingRow("Auto rescan", "Check for new or removed device videos once at app startup.",
                    settings.deviceAutoRescanEnabled) { enabled ->
                    AppSettings.update(context) { it.copy(deviceAutoRescanEnabled = enabled) }
                }
            }
        }
        SettingsSection("Video") {
            SettingRow("Background playback",
                "Keep audio and headphone mode playing when Miowser is minimized or the screen is off.",
                settings.backgroundPlaybackEnabled) { enabled ->
                AppSettings.update(context) { it.copy(backgroundPlaybackEnabled = enabled) }
            }
            MediaChoiceRow("Screen orientation", "Choose how both players open.",
                PlayerOrientationMode.entries, orientation, { it.label }) { mode ->
                orientation = mode
                PlayerDisplayPreferenceStore.saveOrientation(context, mode)
            }
        }
        SettingsSection("Network") {
            MediaChoiceRow("Simultaneous downloads",
                "Choose how many files download at once. Running files finish normally when this limit is lowered.",
                listOf(1, 2, 3, 4), settings.downloadParallelJobs, { it.toString() }) { count ->
                AppSettings.update(context) { it.copy(downloadParallelJobs = count) }
                com.miowser.app.downloader.DownloadQueue.updateLimits()
            }
            MediaChoiceRow("Connections per download",
                "Default for new transfers. Up to six background connections are shared; playback keeps reserved capacity.",
                DownloadThreadCount.entries, settings.downloadThreadCount, { it.label }) { count ->
                AppSettings.update(context) { it.copy(downloadThreadCount = count) }
            }
            MediaChoiceRow("Connections per site",
                "Limit simultaneous connections to one server across downloads. Applies as connections reopen.",
                listOf(1, 2, 3, 4, 6), settings.downloadPerHostConnections, { it.toString() }) { count ->
                AppSettings.update(context) { it.copy(downloadPerHostConnections = count) }
            }
            MediaChoiceRow("Automatic download retries",
                "Retry temporary connection and server errors. Login, unsupported-file and storage errors need your attention. Server wait times are respected.",
                listOf(0, 1, 3, 5), settings.downloadRetryAttempts, { if (it == 0) "Disabled" else it.toString() }) { count ->
                AppSettings.update(context) { it.copy(downloadRetryAttempts = count) }
            }
        }
        if (settings.deviceLibraryAvailable) SettingsSection("Permissions") {
            NavigationRow("Media permissions", "Review device video access and Android app permissions.", leadingIcon = Icons.Rounded.Security) {
                showPermissions = true
            }
        }
        SettingsSection("History") {
            if (settings.browserAvailable) {
                val description = when (settings.libraryBehavior) {
                    LibraryBehavior.AUTOMATIC -> "Save online videos and their progress automatically."
                    LibraryBehavior.MANUAL_ONLY -> "Keep entries you add and update their progress."
                    LibraryBehavior.DISABLED -> "Hide the Miowser Library and keep your entries for later."
                }
                MediaChoiceRow("Playback history", description,
                    LibraryBehavior.entries, settings.libraryBehavior, { it.label }) { behavior ->
                    AppSettings.update(context) { it.copy(libraryBehavior = behavior) }
                }
            }
            if (settings.watchCatalogAvailable || settings.deviceLibraryAvailable) {
                SettingRow("Resume video playback", "Start a video from its last saved position when available.",
                    settings.resumeSavedVideos) { enabled ->
                    AppSettings.update(context) { it.copy(resumeSavedVideos = enabled) }
                }
            }
            if (settings.watchCatalogAvailable && hasSavedMedia) {
                NavigationRow("Clear Library", "Remove saved Library entries and playback progress.", destructive = true, leadingIcon = Icons.Rounded.DeleteSweep) {
                    confirmClear = true
                }
            }
        }
        SettingsSection("Extra settings") {
            MediaCategoryRow("Interface", "Theme, colors, and motion", Icons.Rounded.Palette, onOpenInterface)
            MediaCategoryRow("Video", "Playback layout and controls", Icons.Rounded.Movie, onOpenVideo)
            MediaCategoryRow("Subtitles", "Appearance and preview", Icons.Rounded.Subtitles, onOpenSubtitles)
            MediaCategoryRow("Advanced", "Optional features and diagnostics", Icons.Rounded.Build, onOpenAdvanced)
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
    var showThemePicker by rememberSaveable { mutableStateOf(false) }
    MediaSettingsLayout("Interface", onBack) {
        SettingsSection("Appearance and motion") {
            MediaChoiceRow("Appearance", "Choose System, Light, Dark or AMOLED independently of the color theme.",
                AppThemeMode.entries, AppThemePreference.mode, { it.label }) { mode ->
                AppThemePreference.set(context, mode)
            }
            NavigationRow("Color theme", AppThemePreference.colorTheme.label, leadingIcon = Icons.Rounded.Palette) {
                showThemePicker = true
            }
            SettingRow("Reduce motion", "Use simpler transitions. Android's remove-animations setting is always respected.",
                AppSettings.current.reduceMotion) { enabled ->
                AppSettings.update(context) { it.copy(reduceMotion = enabled) }
            }
        }
    }
    if (showThemePicker) {
        ThemePalettePicker(onDismissRequest = { showThemePicker = false })
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
        SettingsSection("Display") {
            MediaChoiceRow("Aspect ratio", "Set how video fills the screen in both players.",
                PlayerScaleMode.entries, aspectRatio, { it.label }) { mode ->
                aspectRatio = mode
                PlayerDisplayPreferenceStore.saveScaleMode(context, mode.name)
            }
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
}

@Composable
internal fun MediaAdvancedSettingsPage(onOpenCrashReports: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings = AppSettings.current
    MediaSettingsLayout("Advanced", onBack) {
        if (settings.browserAvailable) SettingsSection("Optional features") {
            SettingRow("Browse source while playing", "Keep the player open and paused while you browse its source page.",
                settings.companionBrowsingEnabled) { enabled ->
                AppSettings.update(context) { it.copy(companionBrowsingEnabled = enabled) }
            }
        }
        SettingsSection("Diagnostics") {
            SettingRow("Save crash diagnostics", "Keep redacted crash reports on this device.",
                settings.crashDiagnosticsEnabled) { enabled ->
                AppSettings.update(context) { it.copy(crashDiagnosticsEnabled = enabled) }
            }
            NavigationRow("Crash reports", "View, copy, share, or clear reports stored on this device.",
                leadingIcon = Icons.Rounded.BugReport, onClick = onOpenCrashReports)
        }
    }
}

@Composable
private fun MediaSettingsLayout(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    SimplePageScaffold(title = title, onBack = onBack) { modifier ->
        Column(
            modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(ExpressiveTokens.pageGutter),
            verticalArrangement = Arrangement.spacedBy(ExpressiveTokens.sectionGap),
            content = content
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaCategoryRow(label: String, description: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        onClick = onClick,
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(UiIconSize.standard))
        },
        supportingContent = { Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shapes = segmentedListShapes(),
        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
        modifier = Modifier.settingsSearchAnchor(label).segmentedListItem(nativeSurface = true)
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
    OptionBlock(title, description) {
        ChoiceRow(options, selected, label, onSelect)
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
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun MediaSubtitleSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    var style by remember { mutableStateOf(PlayerSubtitleStyleStore.load(context)) }
    val sample = remember(style.preset) {
        listOf(Cue.Builder().setText(style.preset.sample).build())
    }
    SimplePageScaffold(title = "Subtitles", onBack = onBack) { modifier ->
        BoxWithConstraints(modifier.fillMaxSize().padding(ExpressiveTokens.pageGutter)) {
            val previewHeight = (maxHeight * SubtitlePanelTokens.settingsPreviewHeightFraction).coerceIn(
                SubtitlePanelTokens.settingsPreviewMinHeight, SubtitlePanelTokens.settingsPreviewMaxHeight)
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(UiSpacing.m)) {
                SubtitleCuePreview(style, sample, Modifier.fillMaxWidth().height(previewHeight)
                    .clip(MaterialTheme.shapes.extraLarge).background(PlayerUiColors.subtitlePreview)
                    .testTag("subtitle_appearance_preview"))
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                    SettingsSection("Subtitle appearance") {
                        Column(Modifier.segmentedListItem().padding(ExpressiveTokens.pageGutter),
                            verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
                            SubtitleAppearanceControls(style, onChange = { updated ->
                                style = updated
                                PlayerSubtitleStyleStore.save(context, updated)
                            })
                        }
                    }
                }
            }
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
        DeviceCatalogSession.checkPermissions(context, selectionMayHaveChanged = true)
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
