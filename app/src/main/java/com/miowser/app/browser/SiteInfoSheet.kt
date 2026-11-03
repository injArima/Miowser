package com.miowser.app.browser

import android.net.http.SslCertificate
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.adblock.ElementRuleAction
import com.miowser.app.adblock.SiteElementRule
import com.miowser.app.adblock.SiteProtectionMode
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.ExpressiveSectionCard
import com.miowser.app.ui.MiowserBottomSheetShape
import java.text.DateFormat
import kotlin.math.roundToInt

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
    elementRules: List<SiteElementRule>,
    onSiteModeChanged: (SiteProtectionMode) -> Unit,
    onOpenPageLinksHereChanged: (Boolean) -> Unit,
    onMediaMinimumChanged: (Int?) -> Unit,
    onElementRuleActionChanged: (SiteElementRule, ElementRuleAction) -> Unit,
    onRevertElementRule: (SiteElementRule) -> Unit,
    onRevertAllElementRules: () -> Unit,
    onClearCache: () -> Unit,
    onHardReload: () -> Unit,
    onDismiss: () -> Unit
) {
    var mediaMinimumDraft by remember(host, mediaMinimumOverride) {
        mutableFloatStateOf((mediaMinimumOverride ?: globalMediaMinimum).toFloat())
    }
    val draftSeconds = (mediaMinimumDraft / 30f).roundToInt() * 30

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { CompactSheetDragHandle() },
        shape = MiowserBottomSheetShape,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        BoxWithConstraints {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.9f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DashboardHeader(host = host, security = security, onDismiss = onDismiss)

                if (host != null) {
                    ExpressiveSectionCard(Modifier.fillMaxWidth()) {
                        DashboardSectionTitle("Site settings")
                        Spacer(Modifier.size(12.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DashboardActionButton(
                                label = "Clear cache",
                                icon = Icons.Rounded.DeleteSweep,
                                onClick = onClearCache,
                                modifier = Modifier.weight(1f)
                            )
                            DashboardActionButton(
                                label = "Hard reload",
                                icon = Icons.Rounded.Refresh,
                                onClick = onHardReload,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Text(
                            "Cache is shared across tabs. Hard reload clears cached assets, then reloads this page.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                        )
                        DashboardDivider()
                        DashboardToggle(
                            title = "Open page-created links here",
                            description = if (canInterceptPopups) {
                                "Keep links this site opens in new windows in the current tab."
                            } else {
                                "Enable pop-ups in Browser settings to use this option."
                            },
                            checked = openPageLinksHere,
                            enabled = canInterceptPopups,
                            onCheckedChange = onOpenPageLinksHereChanged
                        )
                        DashboardDivider()
                        DashboardToggle(
                            title = "Minimum media length",
                            description = when {
                                mediaMinimumOverride == null ->
                                    "Use Browser setting (${siteLengthLabel(globalMediaMinimum)})"
                                draftSeconds == 0 -> "Off for this site"
                                else -> "${siteLengthLabel(draftSeconds)} for this site"
                            },
                            checked = mediaMinimumOverride != null,
                            onCheckedChange = { enabled ->
                                onMediaMinimumChanged(if (enabled) globalMediaMinimum else null)
                            }
                        )
                        if (mediaMinimumOverride != null) {
                            Slider(
                                value = mediaMinimumDraft,
                                onValueChange = { mediaMinimumDraft = it },
                                onValueChangeFinished = { onMediaMinimumChanged(draftSeconds) },
                                valueRange = 0f..1_800f,
                                steps = 59
                            )
                            Text(
                                "Unknown-length media is hidden when a minimum is enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    ExpressiveSectionCard(Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DashboardSectionTitle("Page element changes")
                            if (elementRules.isNotEmpty()) {
                                Text(
                                    "${elementRules.size} active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.size(8.dp))
                        if (elementRules.isEmpty()) {
                            Text(
                                "No elements have been hidden or deleted on this site.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            elementRules.forEachIndexed { index, rule ->
                                ElementRuleRow(
                                    rule = rule,
                                    onActionChanged = { onElementRuleActionChanged(rule, it) },
                                    onRevert = { onRevertElementRule(rule) }
                                )
                                if (index != elementRules.lastIndex) DashboardDivider()
                            }
                            TextButton(
                                onClick = onRevertAllElementRules,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            ) {
                                Text("Revert all page changes", color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    ExpressiveSectionCard(Modifier.fillMaxWidth()) {
                        DashboardSectionTitle("Ad blocker for this site")
                        Text(
                            "Choose how strongly Miowser protects this website.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            SiteProtectionMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = mode == siteMode,
                                    onClick = { onSiteModeChanged(mode) },
                                    label = {
                                        Text(if (mode == SiteProtectionMode.OFF) "Off" else mode.label)
                                    }
                                )
                            }
                        }
                        Text(
                            siteProtectionDescription(siteMode),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }

                ExpressiveSectionCard(Modifier.fillMaxWidth()) {
                    DashboardSectionTitle("Connection")
                    Spacer(Modifier.size(8.dp))
                    DashboardDetail(
                        "HTTPS",
                        when (security) {
                            SiteSecurity.SECURE -> "Connected securely"
                            SiteSecurity.CHECKING -> "Checking the current connection"
                            SiteSecurity.INSECURE -> "Loaded over HTTP; HTTPS support is unknown"
                            SiteSecurity.CERTIFICATE_ERROR,
                            SiteSecurity.CERTIFICATE_BYPASSED -> "Certificate verification failed"
                            SiteSecurity.OTHER -> "No website loaded"
                        }
                    )
                    DashboardDivider()
                    DashboardDetail("HSTS", "Unavailable from Android WebView")
                    Text(
                        "An HTTPS connection alone does not show whether this site uses HSTS.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                ExpressiveSectionCard(Modifier.fillMaxWidth()) {
                    DashboardSectionTitle("Certificate")
                    Spacer(Modifier.size(8.dp))
                    if (security == SiteSecurity.SECURE && certificate != null) {
                        DashboardDetail("Issued to", certificate.issuedTo?.dName.orEmpty().ifBlank { "Unknown" })
                        DashboardDivider()
                        DashboardDetail("Issued by", certificate.issuedBy?.dName.orEmpty().ifBlank { "Unknown" })
                        DashboardDivider()
                        DashboardDetail(
                            "Valid from",
                            certificate.validNotBeforeDate?.let {
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
                            } ?: "Unknown"
                        )
                        DashboardDivider()
                        DashboardDetail(
                            "Valid until",
                            certificate.validNotAfterDate?.let {
                                DateFormat.getDateInstance(DateFormat.MEDIUM).format(it)
                            } ?: "Unknown"
                        )
                    } else {
                        Text(
                            "Verified certificate details are unavailable for this page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ExpressiveSectionCard(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DashboardSectionTitle("Protection on this page")
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                if (adBlockEnabled && siteMode != SiteProtectionMode.OFF) "Real-time" else "Off",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                    Row(
                        Modifier.fillMaxWidth().padding(top = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ProtectionStat(
                            label = "Ads & trackers",
                            value = protectionStats.blockedRequests,
                            detail = "blocked",
                            modifier = Modifier.weight(1f)
                        )
                        ProtectionStat(
                            label = "Pop-ups",
                            value = protectionStats.blockedPopups,
                            detail = "prevented",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        if (adBlockEnabled && siteMode != SiteProtectionMode.OFF) {
                            "Counts include requests blocked by filter lists. Individual blocks are not classified as ads or trackers."
                        } else {
                            "Ad blocking is off for this page."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                    if (protectionStats.topBlockedHosts.isNotEmpty()) {
                        DashboardDivider()
                        Text(
                            "Most blocked hosts",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        val largestCount = protectionStats.topBlockedHosts.maxOf { it.second }.coerceAtLeast(1)
                        protectionStats.topBlockedHosts.forEach { (blockedHost, count) ->
                            BlockedHostRow(
                                host = blockedHost,
                                count = count,
                                progress = count.toFloat() / largestCount
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHeader(host: String?, security: SiteSecurity, onDismiss: () -> Unit) {
    val statusColor = securityStatusColor(security)
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(15.dp),
            color = statusColor.copy(alpha = 0.14f)
        ) {
            Icon(
                Icons.Rounded.Shield,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.padding(11.dp).size(22.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Privacy dashboard", style = MaterialTheme.typography.titleLarge)
            Text(
                "Connection and site controls",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.Rounded.Close, contentDescription = "Close privacy dashboard")
        }
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(8.dp),
                shape = RoundedCornerShape(50),
                color = statusColor
            ) {}
            Text(
                host ?: "This page",
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Surface(
                shape = RoundedCornerShape(50),
                color = statusColor.copy(alpha = 0.14f)
            ) {
                Text(
                    security.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    maxLines = 1
                )
            }
        }
    }
    Text(
        security.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun DashboardSectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun DashboardActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, maxLines = 1)
    }
}

@Composable
private fun DashboardToggle(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun ElementRuleRow(
    rule: SiteElementRule,
    onActionChanged: (ElementRuleAction) -> Unit,
    onRevert: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Text(
                rule.selector,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp)
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ElementRuleAction.entries.forEach { action ->
                FilterChip(
                    selected = rule.action == action,
                    onClick = { onActionChanged(action) },
                    label = { Text(if (action == ElementRuleAction.HIDE) "Hide" else "Delete") }
                )
            }
            TextButton(onClick = onRevert) {
                Text("Revert", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun DashboardDetail(label: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ProtectionStat(
    label: String,
    value: Int,
    detail: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 6.dp)) {
                Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
                Text(
                    detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 5.dp, bottom = 3.dp)
                )
            }
        }
    }
}

@Composable
private fun BlockedHostRow(host: String, count: Int, progress: Float) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    host,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    count.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
            )
        }
    }
}

@Composable
private fun DashboardDivider() {
    HorizontalDivider(
        Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
    )
}

@Composable
private fun securityStatusColor(security: SiteSecurity): Color = when (security) {
    SiteSecurity.SECURE -> MaterialTheme.colorScheme.primary
    SiteSecurity.INSECURE,
    SiteSecurity.CERTIFICATE_ERROR,
    SiteSecurity.CERTIFICATE_BYPASSED -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun siteProtectionDescription(mode: SiteProtectionMode): String = when (mode) {
    SiteProtectionMode.STANDARD ->
        "Blocks common trackers and intrusive requests while prioritizing site compatibility."
    SiteProtectionMode.STRICT ->
        "Uses stronger blocking that may affect some site features."
    SiteProtectionMode.OFF ->
        "Allows requests on this site until protection is enabled again."
}

private fun siteLengthLabel(seconds: Int): String = if (seconds <= 0) "Off"
else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
