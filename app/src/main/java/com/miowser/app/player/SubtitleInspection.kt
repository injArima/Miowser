package com.miowser.app.player

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.miowser.app.ui.AppText as Text
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.SubtitlePanelTokens
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiSpacing
import kotlin.math.abs

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
    onConfirmSubtitleDelay: (Long) -> Unit,
    onDelayChange: (Long) -> Unit,
    onBeginAdjust: () -> Unit,
    onCancelAdjust: () -> Unit,
    modifier: Modifier,
    embeddedSubtitleLabel: String? = null,
    inspectionTitle: String? = null
) {
    val entries = remember(timeline) { timeline.filter { it.cues.isNotEmpty() } }
    val focus by remember(entries, style.delayMs, playbackPositionMs) {
        derivedStateOf { subtitleInspectionFocus(entries, playbackPositionMs.value?.let(style::vttPositionMs)) }
    }
    val list = rememberLazyListState()
    var followPlayback by remember(chosen?.url, embeddedSubtitleLabel, timeline) { mutableStateOf(true) }
    var previewIndex by remember(chosen?.url, embeddedSubtitleLabel, timeline) { mutableIntStateOf(-1) }
    var pressedIndex by remember(timeline) { mutableIntStateOf(-1) }
    var lastFollowedIndex by remember(timeline) { mutableIntStateOf(-1) }
    val sourceKey = chosen?.url ?: embeddedSubtitleLabel
    var delayDraft by remember(sourceKey, subtitleAdjustAnchorMs, style.delayMs) {
        mutableStateOf(SubtitleDelayDraft(style.delayMs.toString()))
    }
    val motion = LocalMotionPolicy.current
    val viewportHeight by remember { derivedStateOf { list.layoutInfo.viewportSize.height } }
    val fallbackRowHeight = with(LocalDensity.current) { UiControlSize.touchTarget.roundToPx() }
    val anchorRowHeight by remember(focus.anchorIndex) {
        derivedStateOf { list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == focus.anchorIndex }?.size }
    }
    val stopFollowing = remember(chosen?.url, embeddedSubtitleLabel, timeline) { { followPlayback = false } }
    val scrollObserver = remember(stopFollowing) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available != Offset.Zero) stopFollowing()
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(subtitleAdjustAnchorMs) {
        // Finishing an adjustment must not silently re-enable following after manual interaction.
        if (subtitleAdjustAnchorMs != null) followPlayback = false
    }
    LaunchedEffect(focus.activeIndex) { previewIndex = -1 }
    LaunchedEffect(entries, focus.anchorIndex, followPlayback, subtitleAdjustAnchorMs, viewportHeight, anchorRowHeight, motion.reduced) {
        if (!followPlayback || focus.anchorIndex < 0 || viewportHeight <= 0) return@LaunchedEffect
        val target = focus.anchorIndex
        val rowHeight = anchorRowHeight ?: fallbackRowHeight
        val offset = subtitleInspectionScrollOffset(viewportHeight, rowHeight, SubtitlePanelTokens.cueAnchorFraction)
        if (lastFollowedIndex < 0 || motion.reduced || abs(target - lastFollowedIndex) > MAX_ANIMATED_CUE_DISTANCE) {
            list.scrollToItem(target, offset)
        } else {
            list.animateScrollToItem(target, offset)
        }
        lastFollowedIndex = target
    }
    val hasTrack = chosen != null || embeddedSubtitleLabel != null
    val accent = subtitleOverlayAccent()
    val preview = entries.getOrNull(if (previewIndex >= 0) previewIndex else focus.activeIndex)?.cues.orEmpty()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
        Row(Modifier.fillMaxWidth().heightIn(min = UiControlSize.touchTarget), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
            Text(inspectionTitle ?: chosen?.let(::detectedSubtitleLabel) ?: embeddedSubtitleLabel ?: "Choose a subtitle",
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            SubtitleInspectionPlaybackTime(playbackPositionMs)
            if (!followPlayback && hasTrack && ready && entries.isNotEmpty()) {
                FilledTonalIconButton(onClick = {
                    lastFollowedIndex = -1
                    previewIndex = -1
                    followPlayback = true
                }, colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = accent.copy(alpha = SubtitlePanelTokens.controlContainerAlpha).compositeOver(PlayerUiColors.panel),
                    contentColor = accent)) {
                    Icon(Icons.Rounded.Refresh, "Follow playback")
                }
            }
        }
        val status = when {
            error != null -> error
            !hasTrack -> "Choose a subtitle track to inspect"
            !ready -> "Loading subtitle…"
            entries.isEmpty() -> "This track has no subtitle lines."
            else -> null
        }
        if (status != null) Text(status, style = MaterialTheme.typography.bodySmall,
            color = PlayerUiColors.content.copy(alpha = SubtitlePanelTokens.secondaryContentAlpha))
        PlayerSubtitleTimingControls(style.delayMs, delayDraft, { delayDraft = it }, onDelayChange,
            onConfirmSubtitleDelay, subtitleAdjustAnchorMs,
            entries.getOrNull(pressedIndex)?.startTimeUs,
            hasTrack && ready && entries.isNotEmpty(), onBeginAdjust, onCancelAdjust, sourceKey)
        if (preview.any { it.bitmap != null }) {
            SubtitleCuePreview(style, preview, Modifier.fillMaxWidth().height(SubtitlePanelTokens.inspectionPreviewHeight)
                .clip(MaterialTheme.shapes.medium).background(PlayerUiColors.subtitlePreview))
        }
        if (hasTrack && ready) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f).testTag("subtitle_cue_list")
                .nestedScroll(scrollObserver)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyDown && it.key in SUBTITLE_NAVIGATION_KEYS) stopFollowing()
                    false
                }.pointerInput(entries, stopFollowing) {
                    // Observe the down before children, without consuming taps or scrolling.
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        stopFollowing()
                        waitForUpOrCancellation(pass = PointerEventPass.Initial)
                    }
                }, state = list, verticalArrangement = Arrangement.spacedBy(SubtitlePanelTokens.cueGap)) {
                items(entries.size, key = { it }) { index ->
                    val entry = entries[index]
                    val interaction = remember { MutableInteractionSource() }
                    val pressed by interaction.collectIsPressedAsState()
                    LaunchedEffect(pressed) {
                        if (pressed) pressedIndex = index
                        else if (pressedIndex == index) pressedIndex = -1
                    }
                    DisposableEffect(index) {
                        onDispose { if (pressedIndex == index) pressedIndex = -1 }
                    }
                    SegmentedListItem(selected = if (subtitleAdjustAnchorMs != null) delayDraft.cueStartUs == entry.startTimeUs
                        else index == focus.activeIndex, onClick = {
                        previewIndex = index
                        followPlayback = false
                        if (subtitleAdjustAnchorMs != null) delayDraft = delayDraft.selectCue(subtitleAdjustAnchorMs, entry.startTimeUs)
                    }, shapes = ListItemDefaults.segmentedShapes(index, entries.size),
                        // Consume long presses as previews, so releasing cannot confirm or select a cue.
                        onLongClick = if (subtitleAdjustAnchorMs != null) ({ previewIndex = index }) else null,
                        onLongClickLabel = if (subtitleAdjustAnchorMs != null) "Preview delay" else null,
                        modifier = Modifier.fillMaxWidth().testTag("subtitle_cue_$index"),
                        interactionSource = interaction,
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = if (index == previewIndex) accent.copy(alpha = SubtitlePanelTokens.previewCueAlpha)
                                .compositeOver(PlayerUiColors.panel) else PlayerUiColors.panel,
                            contentColor = PlayerUiColors.content, leadingContentColor = accent,
                            selectedContainerColor = accent.copy(alpha = SubtitlePanelTokens.selectedCueAlpha).compositeOver(PlayerUiColors.panel),
                            selectedContentColor = PlayerUiColors.content, selectedLeadingContentColor = accent),
                        contentPadding = PaddingValues(horizontal = UiSpacing.s, vertical = UiSpacing.xs),
                        leadingContent = {
                            Text(formatPlayerTime(entry.startTimeUs / 1_000L),
                                modifier = Modifier.widthIn(min = SubtitlePanelTokens.cueTimeWidth),
                                style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                        }) {
                        Text(entry.cues.joinToString("\n") { it.text?.toString() ?: "Image subtitle" },
                            style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private const val MAX_ANIMATED_CUE_DISTANCE = 3
private val SUBTITLE_NAVIGATION_KEYS = setOf(Key.DirectionDown, Key.DirectionUp, Key.PageDown, Key.PageUp, Key.MoveHome, Key.MoveEnd)

@Composable
private fun SubtitleInspectionPlaybackTime(positionMs: State<Long?>) {
    val seconds by remember(positionMs) { derivedStateOf { positionMs.value?.div(1_000L) } }
    Text(seconds?.let { formatPlayerTime(it * 1_000L) } ?: "--:--",
        modifier = Modifier.semantics { contentDescription = "Video playback time" },
        style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace,
        color = PlayerUiColors.content)
}
