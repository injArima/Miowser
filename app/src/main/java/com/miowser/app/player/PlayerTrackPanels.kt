package com.miowser.app.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.CompactSheetDragHandle
import com.miowser.app.ui.MiowserBottomSheetShape

/** Original subtitle workspace, with unsupported sections omitted rather than disabled. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerSubtitleWorkspace(
    onDismiss: () -> Unit, modifier: Modifier = Modifier, initialFocus: FocusRequester? = null,
    tracks: @Composable (Modifier, () -> Unit) -> Unit,
    inspect: (@Composable (Modifier) -> Unit)? = null,
    settings: (@Composable (Modifier, () -> Unit) -> Unit)? = null
) {
    var section by remember { mutableIntStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize().then(modifier)) {
        val landscape = maxWidth > maxHeight
        val sections = buildList { add("Tracks"); if (inspect != null) add("Inspect"); if (settings != null) add("Settings") }
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            Column(Modifier.fillMaxSize().testTag("subtitle_workspace")
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .playerPanelPointerShield().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Subtitles", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss, modifier = initialFocus?.let { Modifier.focusRequester(it) } ?: Modifier) {
                        Icon(Icons.Rounded.Close, "Close subtitles")
                    }
                }
                if (!landscape && sections.size > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    sections.forEachIndexed { index, label ->
                        TextButton(onClick = { section = index }, colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) {
                            Text(label, color = if (section == index) subtitleOverlayAccent() else Color.White)
                        }
                    }
                }
                val openInspect = { if (!landscape && inspect != null) section = sections.indexOf("Inspect") }
                if (landscape) Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Column(Modifier.weight(if (sections.size == 1) 1f else 0.28f).fillMaxHeight()) {
                        Text("Available tracks", style = MaterialTheme.typography.labelLarge)
                        tracks(Modifier.fillMaxSize(), openInspect)
                    }
                    if (inspect != null) Column(Modifier.weight(0.34f).fillMaxHeight()) {
                        Text("Inspect", style = MaterialTheme.typography.labelLarge); inspect(Modifier.fillMaxSize())
                    }
                    if (settings != null) Column(Modifier.weight(0.38f).fillMaxHeight()) {
                        Text("Subtitle Settings", style = MaterialTheme.typography.labelLarge); settings(Modifier.fillMaxSize(), openInspect)
                    }
                } else {
                    val contentModifier = Modifier.fillMaxWidth().weight(1f)
                    when (sections.getOrElse(section) { "Tracks" }) {
                        "Inspect" -> inspect?.invoke(contentModifier)
                        "Settings" -> settings?.invoke(contentModifier, openInspect)
                        else -> tracks(contentModifier, openInspect)
                    }
                }
            }
        }
    }
}

/** Original audio/source sheet container, independent of the track provider. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerTrackSheet(title: String, onDismiss: () -> Unit, content: @Composable (Modifier) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = { CompactSheetDragHandle() }, containerColor = Color(0xFF151313),
        contentColor = Color.White, shape = MiowserBottomSheetShape) {
        BoxWithConstraints {
            val maxSheetContentHeight = maxHeight * 0.58f
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp, vertical = 2.dp))
                }
                content(Modifier.fillMaxWidth().heightIn(max = maxSheetContentHeight))
                Spacer(Modifier.size(6.dp))
            }
        }
    }
}

@Composable
internal fun subtitleOverlayAccent(): Color {
    val primary = MaterialTheme.colorScheme.primary
    return if (primary.luminance() < 0.48f) lerp(primary, Color.White, 0.58f) else primary
}

@Composable
internal fun PlayerSubtitleChoiceRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    detail: String? = null
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 9.dp)) {
        Text(label, color = if (selected) subtitleOverlayAccent() else Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.65f), maxLines = 2)
    }
}

@Composable
internal fun PlayerTrackChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    onInspect: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 7.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = when {
                    selected -> MaterialTheme.colorScheme.primary
                    !enabled -> Color.White.copy(alpha = 0.6f)
                    else -> Color.White
                }
            )
            if (detail.isNotBlank()) {
                Text(
                    detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.66f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (onInspect != null) {
            IconButton(onClick = onInspect) {
                Icon(Icons.Rounded.Visibility, contentDescription = "Inspect subtitle",
                    tint = Color.White)
            }
        }
        if (selected) {
            Icon(
                Icons.Rounded.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
