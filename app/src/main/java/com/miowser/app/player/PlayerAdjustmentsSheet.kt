package com.miowser.app.player

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import com.miowser.app.ui.AppText as Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.ExpressiveBottomSheet
import kotlin.math.roundToInt
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.settings.AppSettings

/** Visible, labelled alternatives to the brightness/volume gestures. */
@Composable
internal fun PlayerAdjustmentsSheet(activity: Activity?, onDismiss: () -> Unit,
    onBrightnessCommitted: ((Float) -> Unit)? = null,
    volumeEnabled: Boolean = true,
    onVolumeInteraction: () -> Unit = {}) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var brightness by remember { mutableFloatStateOf(activity?.window?.attributes?.screenBrightness
        ?.takeIf { it > 0 } ?: 0.5f) }
    var volume by remember { mutableFloatStateOf(audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()) }
    ExpressiveBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.l), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Player adjustments", style = MaterialTheme.typography.titleLarge)
            Text("Brightness ${(brightness * 100).roundToInt()}%")
            Slider(value = brightness, valueRange = 0.01f..1f, enabled = activity != null,
                modifier = Modifier.semantics { contentDescription = "Screen brightness" },
                onValueChange = { value ->
                    brightness = value
                    activity?.window?.let { it.attributes = it.attributes.apply { screenBrightness = value } }
                }, onValueChangeFinished = {
                    if (onBrightnessCommitted != null) onBrightnessCommitted(brightness)
                    else PlayerDisplayPreferenceStore.saveBrightness(context, brightness)
                })
            Text("Volume ${(volume * 100 / maxVolume).roundToInt()}%")
            Slider(value = volume, valueRange = 0f..maxVolume.toFloat(), enabled = volumeEnabled,
                modifier = Modifier.semantics { contentDescription = "Media volume" },
                onValueChange = { value ->
                    volume = value; onVolumeInteraction()
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, value.roundToInt(), 0)
                })
            HorizontalDivider(modifier = Modifier.padding(vertical = UiSpacing.s))
            val companionBrowsing = AppSettings.current.companionBrowsingEnabled
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(UiSpacing.m)) {
                Column(Modifier.weight(1f)) {
                    Text("Browse source while playing", style = MaterialTheme.typography.titleSmall)
                    Text("Keep the player open and paused while you browse its source page.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = companionBrowsing, onCheckedChange = { enabled ->
                    AppSettings.update(context) { it.copy(companionBrowsingEnabled = enabled) }
                })
            }
        }
    }
}
