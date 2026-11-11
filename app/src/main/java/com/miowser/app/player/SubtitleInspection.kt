package com.miowser.app.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.ui.SubtitleView
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.SubtitlePanelTokens
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.theme.UiControlSize

/** Only the visible inspection panel observes the player clock. Paused playback sleeps. */
@Composable
internal fun rememberSubtitlePlaybackPosition(player: Player): State<Long?> {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    return produceState<Long?>(player.currentPosition, player, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            observePlayerClock(player, intervalMs = 100) { value = player.currentPosition }
        }
    }
}

/** Use the playback subtitle renderer for both the settings sample and image-cue previews. */
@OptIn(UnstableApi::class)
@Composable
internal fun SubtitleCuePreview(style: PlayerSubtitleStyle, cues: List<Cue>, modifier: Modifier) {
    val context = LocalContext.current
    val typeface = remember(context, style.font) {
        captionFontResource(style.font)?.let { ResourcesCompat.getFont(context, it) }
    }
    val positioned = remember(cues, style.bottomPadding) { positionSubtitleCues(cues, style.bottomPadding) }
    AndroidView(modifier = modifier, factory = { SubtitleView(it) }, update = { view ->
        applyPlayerSubtitleStyle(view, style, typeface)
        view.setFractionalTextSize(SubtitlePanelTokens.previewTextFraction * style.size)
        view.setCues(positioned)
    }, onRelease = { it.setCues(emptyList()) })
}

@OptIn(UnstableApi::class)
@Composable
internal fun PlayerSubtitleInspectPanel(
    style: PlayerSubtitleStyle,
    chosen: DetectedMedia?,
    timeline: List<CuesWithTiming>,
    error: String?,
    ready: Boolean,
    playbackPositionMs: State<Long?>,
    subtitleAdjustAnchorMs: Long?,
    onAlignSubtitleToCue: (Long) -> Unit,
    onDelayChange: (Long) -> Unit,
    onBeginAdjust: () -> Unit,
    onCancelAdjust: () -> Unit,
    modifier: Modifier,
    embeddedSubtitleLabel: String? = null
) {
    val entries = remember(timeline) { timeline.filter { it.cues.isNotEmpty() } }
    val currentIndex by remember(entries, style.delayMs, playbackPositionMs) {
        derivedStateOf {
            playbackPositionMs.value?.let { subtitleCueIndexAt(entries, style.vttPositionMs(it)) } ?: -1
        }
    }
    val list = rememberLazyListState()
    val dragged by list.interactionSource.collectIsDraggedAsState()
    var followPlayback by remember(chosen?.url, embeddedSubtitleLabel, timeline) { mutableStateOf(true) }
    var previewIndex by remember(chosen?.url, embeddedSubtitleLabel, timeline) { mutableIntStateOf(-1) }
    var followedOnce by remember(timeline) { mutableStateOf(false) }
    var wasAdjusting by remember { mutableStateOf(false) }
    val reducedMotion = LocalMotionPolicy.current.reduced
    LaunchedEffect(dragged) {
        if (dragged) followPlayback = false
    }
    LaunchedEffect(subtitleAdjustAnchorMs) {
        if (subtitleAdjustAnchorMs != null) followPlayback = false
        else if (wasAdjusting) followPlayback = true
        wasAdjusting = subtitleAdjustAnchorMs != null
    }
    LaunchedEffect(currentIndex) { previewIndex = -1 }
    LaunchedEffect(entries, currentIndex, followPlayback, subtitleAdjustAnchorMs) {
        if (!followPlayback || subtitleAdjustAnchorMs != null) return@LaunchedEffect
        if (currentIndex < 0) {
            if (!followedOnce && entries.isNotEmpty()) list.scrollToItem(0)
            return@LaunchedEffect
        }
        val target = (currentIndex - 1).coerceAtLeast(0)
        if (!followedOnce || reducedMotion) list.scrollToItem(target)
        else if (list.layoutInfo.visibleItemsInfo.none { it.index == currentIndex }) list.animateScrollToItem(target)
        followedOnce = true
    }
    val hasTrack = chosen != null || embeddedSubtitleLabel != null
    val accent = subtitleOverlayAccent()
    val preview = entries.getOrNull(if (previewIndex >= 0) previewIndex else currentIndex)?.cues.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
        Text(when {
            error != null -> error
            !hasTrack -> "Choose a subtitle track to inspect"
            !ready -> "Loading subtitle…"
            entries.isEmpty() -> "This track has no subtitle lines."
            else -> embeddedSubtitleLabel ?: chosen?.let(::detectedSubtitleLabel).orEmpty()
        }, style = MaterialTheme.typography.bodySmall, color = PlayerUiColors.content.copy(alpha = 0.7f),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        PlayerSubtitleTimingControls(style.delayMs, onDelayChange,
            subtitleAdjustAnchorMs != null, hasTrack && ready && entries.isNotEmpty(),
            onBeginAdjust, onCancelAdjust, Modifier.fillMaxWidth())
        if (subtitleAdjustAnchorMs != null) {
            Text("Tap the line that belongs at ${formatPlayerTime(subtitleAdjustAnchorMs)}",
                style = MaterialTheme.typography.bodySmall, color = accent)
        } else if (!followPlayback && ready && entries.isNotEmpty()) {
            TextButton(onClick = { followPlayback = true; previewIndex = -1 }) { Text("Follow playback", color = accent) }
        }
        if (preview.any { it.bitmap != null }) {
            SubtitleCuePreview(style, preview, Modifier.fillMaxWidth().height(SubtitlePanelTokens.inspectionPreviewHeight)
                .clip(MaterialTheme.shapes.medium).background(PlayerUiColors.subtitlePreview))
        }
        if (hasTrack && ready) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("subtitle_cue_list"), state = list) {
                items(entries.size, key = { it }) { index ->
                    val entry = entries[index]
                    val current = index == currentIndex
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(when {
                            current -> accent.copy(alpha = 0.18f)
                            index == previewIndex -> PlayerUiColors.content.copy(alpha = 0.08f)
                            else -> PlayerUiColors.panel
                        })
                        .semantics { selected = current }.testTag("subtitle_cue_$index")
                        .clickable {
                            previewIndex = index
                            followPlayback = false
                            if (subtitleAdjustAnchorMs != null) onAlignSubtitleToCue(entry.startTimeUs)
                        }.padding(UiSpacing.s)) {
                        Text(formatPlayerTime(entry.startTimeUs / 1_000L),
                            style = MaterialTheme.typography.labelSmall, color = accent)
                        Text(entry.cues.joinToString("\n") { it.text?.toString() ?: "Image subtitle" },
                            style = MaterialTheme.typography.bodyMedium, color = PlayerUiColors.content)
                    }
                    HorizontalDivider(color = PlayerUiColors.content.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun PlayerSubtitleTimingControls(
    delayMs: Long,
    onDelayChange: (Long) -> Unit,
    adjusting: Boolean,
    canAdjust: Boolean,
    onBeginAdjust: () -> Unit,
    onCancelAdjust: () -> Unit,
    modifier: Modifier
) {
    Column(modifier) {
        PlayerSubtitleDelayControl(delayMs, onDelayChange)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = if (adjusting) onCancelAdjust else onBeginAdjust,
                enabled = adjusting || canAdjust,
                colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) {
                Text(if (adjusting) "Cancel adjustment" else "Adjust timing")
            }
            TextButton(onClick = { onDelayChange(0L) },
                colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) {
                Text("Reset delay")
            }
        }
    }
}

@Composable
private fun PlayerSubtitleDelayControl(delayMs: Long, onChange: (Long) -> Unit) {
    var input by remember { mutableStateOf(delayMs.toString()) }
    LaunchedEffect(delayMs) {
        if (input.toLongOrNull() != delayMs) input = delayMs.toString()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("Subtitle Delay", style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((delayMs - 50L).coerceAtLeast(-MAX_SUBTITLE_DELAY_MS)) },
            modifier = Modifier.width(UiControlSize.compact).semantics { contentDescription = "Decrease subtitle delay by 50 milliseconds" },
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) { Text("−") }
        BasicTextField(
            value = input,
            onValueChange = { edited ->
                if (edited.length <= 8 && edited.matches(Regex("-?\\d*"))) {
                    input = edited
                    edited.toLongOrNull()?.let { onChange(it.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)) }
                }
            },
            modifier = Modifier.width(SubtitlePanelTokens.delayFieldWidth)
                .semantics { contentDescription = "Subtitle delay in milliseconds" }, singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = PlayerUiColors.content, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = Brush.verticalGradient(listOf(PlayerUiColors.content, PlayerUiColors.content))
        )
        Text("ms", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange((delayMs + 50L).coerceAtMost(MAX_SUBTITLE_DELAY_MS)) },
            modifier = Modifier.width(UiControlSize.compact).semantics { contentDescription = "Increase subtitle delay by 50 milliseconds" },
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) { Text("+") }
    }
}
