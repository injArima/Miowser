package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.SiteProtectionMode
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(
    host: String?,
    security: SiteSecurity,
    siteMode: SiteProtectionMode,
    openPageLinksHere: Boolean,
    mediaMinimumOverride: Int?,
    globalMediaMinimum: Int,
    canInterceptPopups: Boolean,
    onSiteModeChanged: (SiteProtectionMode) -> Unit,
    onOpenPageLinksHereChanged: (Boolean) -> Unit,
    onMediaMinimumChanged: (Int?) -> Unit,
    onDismiss: () -> Unit
) {
    var mediaMinimumDraft by remember(host, mediaMinimumOverride) {
        mutableFloatStateOf((mediaMinimumOverride ?: globalMediaMinimum).toFloat())
    }
    val draftSeconds = (mediaMinimumDraft / 30f).roundToInt() * 30
    ModalBottomSheet(onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() },
        shape = MiowserBottomSheetShape) {
        BoxWithConstraints {
        Column(Modifier.fillMaxWidth()
            .heightIn(max = maxHeight * 0.78f)
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(host ?: "This page", style = MaterialTheme.typography.titleLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(security.label, style = MaterialTheme.typography.titleMedium,
                color = when (security) {
                    SiteSecurity.SECURE -> MaterialTheme.colorScheme.primary
                    SiteSecurity.INSECURE, SiteSecurity.CERTIFICATE_ERROR,
                    SiteSecurity.CERTIFICATE_BYPASSED -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                })
            Text(security.description, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            if (host != null) {
                Text("Site settings", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 14.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Open page-created links here", style = MaterialTheme.typography.bodyLarge)
                        Text(if (canInterceptPopups)
                            "Keep links this site opens in new windows in the current tab."
                            else "Enable pop-ups in Browser settings to use this option.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = openPageLinksHere, onCheckedChange = onOpenPageLinksHereChanged,
                        enabled = canInterceptPopups)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Minimum media length", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            if (mediaMinimumOverride == null) "Use Browser setting (${siteLengthLabel(globalMediaMinimum)})"
                            else if (draftSeconds == 0) "Off for this site"
                            else "${siteLengthLabel(draftSeconds)} for this site",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = mediaMinimumOverride != null, onCheckedChange = { enabled ->
                        onMediaMinimumChanged(if (enabled) globalMediaMinimum else null)
                    })
                }
                if (mediaMinimumOverride != null) {
                    Slider(
                        value = mediaMinimumDraft,
                        onValueChange = { mediaMinimumDraft = it },
                        onValueChangeFinished = { onMediaMinimumChanged(draftSeconds) },
                        valueRange = 0f..1_800f,
                        steps = 59
                    )
                    Text("Unknown-length media is hidden when a minimum is enabled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Ad blocker for this site", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SiteProtectionMode.entries.forEach { mode ->
                        FilterChip(selected = mode == siteMode, onClick = { onSiteModeChanged(mode) },
                            label = { Text(mode.label) })
                    }
                }
            }
        }
    }
    }
}

private fun siteLengthLabel(seconds: Int): String = if (seconds <= 0) "Off"
else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
