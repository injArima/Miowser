package com.miowser.app.player

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.MiowserDropdownMenu
import com.miowser.app.ui.MiowserMenuItem
import kotlin.math.roundToLong

/** Shared original player controls. No playback engine or clock is created by this UI. */
@Composable
internal fun BoxScope.PlayerChromeScrims(viewportHeight: Dp, locked: Boolean) {
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(minOf(144.dp, viewportHeight * 0.32f))
        .background(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.60f),
            0.55f to Color.Black.copy(alpha = 0.30f), 1f to Color.Transparent)))
    if (!locked) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(minOf(256.dp, viewportHeight * 0.53f))
        .background(Brush.verticalGradient(0f to Color.Transparent,
            0.35f to Color.Black.copy(alpha = 0.34f), 1f to Color.Black.copy(alpha = 0.72f))))
}

@Composable
internal fun PlayerHoldSpeedIndicator(modifier: Modifier = Modifier) {
    Text("2×", color = Color.White,
        style = MaterialTheme.typography.titleMedium.copy(shadow = Shadow(Color.Black, Offset.Zero, 8f)),
        modifier = modifier.background(Color.Black.copy(alpha = 0.60f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 6.dp))
}

internal enum class PlayerScaleMode(val label: String) { FIT("Fit"), ZOOM("Zoom"), FILL("Fill") }

@Composable
internal fun PlayerLoadingSpinner(modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = Color.White,
            strokeWidth = 3.5.dp
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerTopBar(
    isLandscape: Boolean,
    rotationLocked: Boolean,
    playbackSpeed: Float,
    onRotate: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onDownload: (() -> Unit)?,
    onPip: (() -> Unit)?,
    onOpenPlaylist: (() -> Unit)?,
    playlistFocus: FocusRequester,
    playlistPosition: Int,
    playlistSize: Int,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top)
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        val compact = maxWidth < 340.dp
        val gap = if (compact) 0.dp else 4.dp
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            if (onDownload != null) {
                RoundPlayerButton(Icons.Rounded.Download, "Download video", onDownload)
                Spacer(Modifier.width(gap))
            }
            if (onPip != null) {
                RoundPlayerButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture", onPip)
                Spacer(Modifier.width(gap))
            }
            RotationPlayerButton(
                isLandscape = isLandscape,
                locked = rotationLocked,
                onRotate = onRotate,
                onToggleLock = onToggleRotationLock
            )
            Spacer(Modifier.width(gap))
            if (playbackSpeed != 1f && !compact) {
                PlaybackSpeedIndicator(playbackSpeed)
                Spacer(Modifier.width(gap))
            }
            RoundPlayerButton(Icons.Rounded.Lock, "Lock controls", onLock)
            Spacer(Modifier.width(gap))
            if (onOpenPlaylist != null) {
                RoundPlayerButton(Icons.AutoMirrored.Rounded.PlaylistPlay,
                    "Playlist: $playlistPosition of $playlistSize", onOpenPlaylist,
                    Modifier.focusRequester(playlistFocus))
                Spacer(Modifier.width(gap))
            }
            RoundPlayerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close player", onDismiss)
        }
    }
}

@Composable
private fun PlaybackSpeedIndicator(speed: Float) {
    Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
        Text(
            text = formatSpeed(speed),
            color = Color.White,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    playlistActive: Boolean,
    onPreviousVideo: (() -> Unit)?,
    onNextVideo: (() -> Unit)?,
    onReplay: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier,
    seekEnabled: Boolean = true,
    showSeekControls: Boolean = true
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(if (playlistActive) 8.dp else 40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showSeekControls) PlayerTransportButton(Icons.Rounded.Replay10, "Rewind 10 seconds", onReplay,
            enabled = seekEnabled,
            size = if (playlistActive) 48.dp else 56.dp,
            iconSize = if (playlistActive) 31.dp else 36.dp)
        if (playlistActive) PlayerTransportButton(
            Icons.Rounded.SkipPrevious, "Previous video", { onPreviousVideo?.invoke() },
            enabled = onPreviousVideo != null, size = 40.dp, iconSize = 27.dp)
        Surface(
            shape = CircleShape,
            color = Color.Transparent,
            contentColor = Color.White,
            modifier = Modifier
                .size(64.dp)
                .testTag("player_play_pause")
                .clip(CircleShape)
                .clickable(enabled = LocalPlayerControlsActive.current, onClick = onPlayPause)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    PlayerLoadingSpinner()
                } else {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
        if (playlistActive) PlayerTransportButton(
            Icons.Rounded.SkipNext, "Next video", { onNextVideo?.invoke() },
            enabled = onNextVideo != null, size = 40.dp, iconSize = 27.dp)
        if (showSeekControls) PlayerTransportButton(Icons.Rounded.Forward10, "Forward 10 seconds", onForward,
            enabled = seekEnabled,
            size = if (playlistActive) 48.dp else 56.dp,
            iconSize = if (playlistActive) 31.dp else 36.dp)
    }
}

@Composable
private fun PlayerTransportButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    size: Dp = 56.dp,
    iconSize: Dp = 36.dp
) {
    Surface(
        onClick = onClick,
        enabled = enabled && LocalPlayerControlsActive.current,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (enabled) Color.White else Color.White.copy(alpha = 0.35f),
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(iconSize))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlayerBufferedTimeline(
    positionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Slider(
        value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat(),
        onValueChange = { onScrub(it.roundToLong()) },
        onValueChangeFinished = onScrubFinished,
        valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
        enabled = durationMs > 0 && LocalPlayerControlsActive.current,
        modifier = Modifier.fillMaxWidth().height(38.dp),
        thumb = {
            // Keep Slider's gestures and accessibility; paint the visible thumb with the track
            // so both always share the same vertical center and progress calculation.
            Spacer(Modifier.size(1.dp))
        },
        track = { state ->
            Canvas(Modifier.fillMaxWidth().height(12.dp)) {
                val duration = durationMs.takeIf { it > 0L } ?: 1L
                val played = (state.value / duration).coerceIn(0f, 1f)
                // Media3 reports the contiguous playable buffer. Disk cache may contain disjoint
                // ranges, so the timeline must not imply that the entire cache is seekable at once.
                val buffered = (bufferedPositionMs.toFloat() / duration).coerceIn(played, 1f)
                val lineHeight = 6.dp.toPx()
                val lineTop = (size.height - lineHeight) / 2f
                val lineRadius = CornerRadius(lineHeight / 2f)
                val thumbRadius = 5.dp.toPx()
                val lineLeft = thumbRadius
                val lineWidth = (size.width - thumbRadius * 2f).coerceAtLeast(0f)
                val lineStart = Offset(lineLeft, lineTop)
                drawRoundRect(Color.White.copy(alpha = 0.42f), topLeft = lineStart,
                    size = Size(lineWidth, lineHeight), cornerRadius = lineRadius)
                if (buffered > 0f) drawRoundRect(
                    accent.copy(alpha = 0.5f),
                    topLeft = lineStart,
                    size = Size(lineWidth * buffered, lineHeight),
                    cornerRadius = lineRadius
                )
                if (played > 0f) drawRoundRect(
                    accent,
                    topLeft = lineStart,
                    size = Size(lineWidth * played, lineHeight),
                    cornerRadius = lineRadius
                )
                drawCircle(accent, radius = thumbRadius,
                    center = Offset(lineLeft + lineWidth * played, size.height / 2f))
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun RotationPlayerButton(
    isLandscape: Boolean,
    locked: Boolean,
    onRotate: () -> Unit,
    onToggleLock: () -> Unit
) {
    val icon = when {
        !locked -> Icons.Rounded.ScreenRotation
        isLandscape -> Icons.Rounded.ScreenLockLandscape
        else -> Icons.Rounded.ScreenLockPortrait
    }
    val description = if (locked) {
        "Rotation locked. Tap to rotate, hold to unlock"
    } else {
        "Rotate screen. Hold to lock rotation"
    }
    Surface(
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = if (locked) MaterialTheme.colorScheme.primary else Color.White,
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(enabled = LocalPlayerControlsActive.current,
                onClick = onRotate, onLongClick = onToggleLock)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
internal fun RoundPlayerButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        enabled = LocalPlayerControlsActive.current,
        shape = CircleShape,
        color = Color.Transparent,
        contentColor = Color.White,
        modifier = modifier.size(48.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(25.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerOptionButton(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onLongClickLabel: String? = null,
    onLongClick: (() -> Unit)? = null,
    testId: String = "player_option",
    onClick: () -> Unit
) {
    Box(
        modifier = modifier.testTag(testId)
            .size(48.dp)
            .clip(CircleShape)
            .combinedClickable(
                enabled = enabled && LocalPlayerControlsActive.current,
                onClick = onClick,
                onLongClickLabel = onLongClickLabel,
                onLongClick = onLongClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (enabled) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.Black.copy(alpha = 0.28f),
                modifier = Modifier.size(26.dp).offset(y = 1.dp)
                    .blur(2.dp, BlurredEdgeTreatment.Unbounded)
            )
        }
        Icon(
            icon,
            contentDescription = label,
            tint = if (enabled) Color.White else Color(0xFF9C9C9C),
            modifier = Modifier.size(26.dp)
        )
    }
}

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerBottomChrome(
    title: String, sourceLabel: String, qualityLabel: String?, isLandscape: Boolean,
    scaleMode: PlayerScaleMode, playbackSpeed: Float, layoutKey: Any,
    scaleMenuExpanded: Boolean, onScaleMenuChange: (Boolean) -> Unit, onScaleSelected: (PlayerScaleMode) -> Unit,
    speedMenuExpanded: Boolean, onSpeedMenuChange: (Boolean) -> Unit, onSpeedSelected: (Float) -> Unit,
    subtitleFocus: FocusRequester, subtitleLabel: String, subtitlesEnabled: Boolean, onOpenSubtitles: (() -> Unit)?,
    audioLabel: String, audioEnabled: Boolean, onOpenAudio: (() -> Unit)?,
    sourceButtonLabel: String, sourcesEnabled: Boolean, onOpenSources: (() -> Unit)?,
    onAdjustmentsRequested: () -> Unit,
    timelineContent: @Composable () -> Unit,
    timerContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    extraSpeedOptions: @Composable ColumnScope.() -> Unit = {},
    message: String? = null
) {
    var timerWidthPx by remember(layoutKey) { mutableIntStateOf(0) }
    val timerWidth = maxOf(with(LocalDensity.current) { timerWidthPx.toDp() }, 110.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom)
            )
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 24.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            color = Color.White,
            style = if (isLandscape) MaterialTheme.typography.titleLarge
                else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = listOfNotNull(sourceLabel.takeIf { it.isNotBlank() }, qualityLabel)
                .joinToString("  ·  "),
            color = Color.White.copy(alpha = 0.78f),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 1.dp, bottom = 1.dp)
        )
        timelineContent()
        Box(modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Row(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth()
                    .padding(end = timerWidth + 8.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (onOpenSubtitles != null) PlayerOptionButton(
                    Icons.Rounded.Subtitles,
                    subtitleLabel,
                    testId = "player_subtitles",
                    modifier = Modifier.focusRequester(subtitleFocus),
                    enabled = subtitlesEnabled,
                    onClick = onOpenSubtitles
                )
                if (onOpenAudio != null) PlayerOptionButton(
                    Icons.Rounded.Audiotrack,
                    audioLabel,
                    enabled = audioEnabled,
                    onClick = onOpenAudio
                )
                if (onOpenSources != null) PlayerOptionButton(
                    Icons.Rounded.Tune,
                    sourceButtonLabel,
                    enabled = sourcesEnabled,
                    onClick = onOpenSources
                )
                Box {
                    PlayerOptionButton(Icons.Rounded.Speed, formatSpeed(playbackSpeed)) {
                        onSpeedMenuChange(true)
                    }
                    MiowserDropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { onSpeedMenuChange(false) }
                    ) {
                        MiowserMenuItem(text = { Text("Brightness & volume") },
                            leadingIcon = { Icon(Icons.Rounded.Tune, contentDescription = null) },
                            onClick = onAdjustmentsRequested)
                        extraSpeedOptions()
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            MiowserMenuItem(
                                text = { Text(formatSpeed(speed)) },
                                selected = speed == playbackSpeed,
                                leadingIcon = { Icon(Icons.Rounded.Speed, contentDescription = null) },
                                onClick = { onSpeedSelected(speed) }
                            )
                        }
                    }
                }
                Box {
                    PlayerOptionButton(
                        icon = Icons.Rounded.FitScreen,
                        label = "Aspect ratio: ${scaleMode.label}",
                        onLongClickLabel = "Show aspect ratio options",
                        onLongClick = { onScaleMenuChange(true) },
                        onClick = {
                            onScaleSelected(PlayerScaleMode.entries[(scaleMode.ordinal + 1) % PlayerScaleMode.entries.size])
                        }
                    )
                    MiowserDropdownMenu(
                        expanded = scaleMenuExpanded,
                        onDismissRequest = { onScaleMenuChange(false) }
                    ) {
                        PlayerScaleMode.entries.forEach { mode ->
                            MiowserMenuItem(
                                text = { Text(mode.label) },
                                selected = mode == scaleMode,
                                leadingIcon = { Icon(Icons.Rounded.FitScreen, contentDescription = null) },
                                onClick = { onScaleSelected(mode) }
                            )
                        }
                    }
                }
            }
            timerContent(Modifier.align(Alignment.CenterEnd).onSizeChanged { if (it.width > timerWidthPx) timerWidthPx = it.width })
        }
        if (message != null) Text(message, color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall, maxLines = 2,
            modifier = Modifier.fillMaxWidth())
    }
}

@Composable
internal fun PlayerTimer(positionMs: Long, durationMs: Long, modifier: Modifier, live: Boolean = false) {
    var showRemaining by remember { mutableStateOf(false) }
    Box(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(10.dp))
            .clickable(enabled = LocalPlayerControlsActive.current && !live, onClickLabel = if (showRemaining) "Show total duration" else "Show time remaining") {
                showRemaining = !showRemaining
            }.padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterEnd
    ) {
        Text(text = if (live) "Live" else formatPlayerTimer(positionMs, durationMs, showRemaining), color = Color.White,
            style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}
