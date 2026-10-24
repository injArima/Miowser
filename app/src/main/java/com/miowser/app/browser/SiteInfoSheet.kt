package com.miowser.app.browser

import android.net.http.SslCertificate
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
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
import java.text.DateFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteInfoSheet(
    host: String?,
    security: SiteSecurity,
    certificate: SslCertificate?,
    protectionStats: SiteProtectionStats,
    adBlockEnabled: Boolean,
    siteMode: SiteProtectionMode,
    openPageLinksHere: Boolean,
    mediaMinimumOverride: Int?,
    globalMediaMinimum: Int,
    canInterceptPopups: Boolean,
    onSiteModeChanged: (SiteProtectionMode) -> Unit,
    onOpenPageLinksHereChanged: (Boolean) -> Unit,
    onMediaMinimumChanged: (Int?) -> Unit,
    onClearCache: () -> Unit,
    onHardReload: () -> Unit,
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
            Text("Privacy dashboard", style = MaterialTheme.typography.titleLarge)
            Text(host ?: "This page", style = MaterialTheme.typography.titleMedium,
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
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Site settings", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onClearCache, modifier = Modifier.weight(1f)) {
                        Text("Clear browser cache")
                    }
                    OutlinedButton(onClick = onHardReload, modifier = Modifier.weight(1f)) {
                        Text("Hard reload")
                    }
                }
                Text("The browser cache is shared across tabs. Hard reload clears it, then reloads this page.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            DashboardDetail("HTTPS", when (security) {
                SiteSecurity.SECURE -> "Connected securely"
                SiteSecurity.CHECKING -> "Checking the current connection"
                SiteSecurity.INSECURE -> "This page loaded over HTTP; HTTPS support is unknown"
                SiteSecurity.CERTIFICATE_ERROR, SiteSecurity.CERTIFICATE_BYPASSED ->
                    "Certificate verification failed"
                SiteSecurity.OTHER -> "No website loaded"
            })
            DashboardDetail("HSTS", "Not available from Android WebView")
            Text("An HTTPS connection alone does not show whether this site uses HSTS.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)

            Text("Certificate", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 8.dp))
            if (security == SiteSecurity.SECURE && certificate != null) {
                DashboardDetail("Issued to", certificate.issuedTo?.dName.orEmpty().ifBlank { "Unknown" })
                DashboardDetail("Issued by", certificate.issuedBy?.dName.orEmpty().ifBlank { "Unknown" })
                DashboardDetail("Valid from", certificate.validNotBeforeDate?.let {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
                } ?: "Unknown")
                DashboardDetail("Valid until", certificate.validNotAfterDate?.let {
                    DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
                } ?: "Unknown")
            } else {
                Text("Verified certificate details are unavailable for this page.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Protection on this page", style = MaterialTheme.typography.titleMedium)
            DashboardDetail("Ads and trackers blocked", protectionStats.blockedRequests.toString())
            DashboardDetail("Pop-ups blocked", protectionStats.blockedPopups.toString())
            Text(if (adBlockEnabled && siteMode != SiteProtectionMode.OFF)
                "Counts include requests blocked by the filter lists on this page. The engine does not classify each block as an ad or a tracker."
                else "Ad blocking is off for this page.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (protectionStats.topBlockedHosts.isNotEmpty()) {
                Text("Most blocked hosts", style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 4.dp))
                protectionStats.topBlockedHosts.forEach { (blockedHost, count) ->
                    DashboardDetail(blockedHost, count.toString())
                }
            }

        }
    }
    }
}

@Composable
private fun DashboardDetail(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun siteLengthLabel(seconds: Int): String = if (seconds <= 0) "Off"
else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
