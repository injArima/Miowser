package com.miowser.app.browser.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.pm.PackageManager
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.player.PlayerBufferedTimeline
import com.miowser.app.MainActivity
import com.miowser.app.player.PlayerAdjustmentsSheet
import com.miowser.app.player.PlayerGestureSurface
import com.miowser.app.player.PlayerGestureFeedback
import com.miowser.app.player.PlayerGestureFeedbackOverlay
import com.miowser.app.player.PlayerGestureKind
import com.miowser.app.player.LocalPlayerControlsActive
import com.miowser.app.player.PlayerCenterControls
import com.miowser.app.player.RotationPlayerButton
import com.miowser.app.player.RoundPlayerButton
import com.miowser.app.player.formatPlayerTimer
import com.miowser.app.ui.MiowserDropdownMenu
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MiowserPlayerTheme
import com.miowser.app.ui.theme.MotionTokens
import kotlinx.coroutines.delay

/** A sibling of the retained custom video View. Ordinary website fullscreen has no owner here. */
@Composable
internal fun BrowserVideoPlayerOverlay(controller: BrowserPlaybackControls, title: String, onExit: () -> Unit) {
    val state by controller.state.collectAsState()
    val session = state.session ?: return
    key(session.id, state.attempt) {
        MiowserPlayerTheme(darkTheme = true) {
            BrowserVideoControlsContent(controller, state, title, onExit)
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BrowserVideoControlsContent(controller: BrowserPlaybackControls, state: BrowserVideoPlaybackState,
    title: String, onExit: () -> Unit) {
    val session = state.session ?: return
    val video = session.state
    val activity = LocalContext.current.videoActivity()
    val host = activity as? MainActivity
    val owner = remember(session.id, state.attempt) { BrowserVideoPipOwner(session.id, state.attempt) }
    val inPip = host?.playerInPictureInPicture == true && host.browserVideoPipOwner == owner
    val supportsPip = remember(activity) { host?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true }
    val audio = remember(activity) { activity?.getSystemService(AudioManager::class.java) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val motion = LocalMotionPolicy.current
    var visible by remember { mutableStateOf(true) }
    var menu by remember { mutableStateOf<String?>(null) }
    var adjustments by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<PlayerGestureFeedback?>(null) }
    var feedbackSequence by remember { mutableIntStateOf(0) }
    var holdingSpeed by remember { mutableStateOf(false) }
    val speedHold = remember { BrowserVideoSpeedHold() }
    var scrub by remember { mutableStateOf<Long?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var rotationLocked by remember { mutableStateOf(false) }
    val orientationOwner = remember { booleanArrayOf(false) }
    DisposableEffect(controller, activity) {
        val original = activity?.requestedOrientation
        val originalBrightness = activity?.window?.attributes?.screenBrightness
        controller.setRichUi(true)
        onDispose {
            speedHold.stop(controller)
            controller.setRichUi(false)
            if (orientationOwner[0] && activity != null && original != null) activity.requestedOrientation = original
            if (originalBrightness != null) activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
        }
    }
    BackHandler(enabled = locked && !inPip) { locked = false; visible = true }
    LaunchedEffect(feedbackSequence) { if (feedback != null) { delay(1_000); feedback = null } }
    LaunchedEffect(inPip) {
        if (inPip) { menu = null; adjustments = false; feedback = null; speedHold.stop(controller); holdingSpeed = false }
    }
    LaunchedEffect(visible, video.playing, menu, adjustments, locked, scrub, state.pendingSeekMs, state.message, interaction) {
        if (!locked && (!video.playing || state.message != null)) visible = true
        if (visible && video.playing && menu == null && !adjustments && scrub == null && state.pendingSeekMs == null && state.message == null) {
            delay(3_500)
            visible = false
        }
    }
    fun act(action: () -> Unit) { interaction++; controller.clearMessage(); action() }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // Gesture surface and controls remain siblings: sheets/buttons own their input.
        if (!inPip) PlayerGestureSurface(locked, visible, landscape,
            positionMs = { controller.state.value.let { s ->
                val v = s.session?.state
                ((s.pendingSeekMs ?: v?.positionMs ?: 0L) - (v?.browserSeekWindow()?.startMs ?: 0L)).coerceAtLeast(0)
            } }, durationMs = { controller.state.value.session?.state?.browserSeekWindow()?.durationMs ?: 0L },
            onSingleTap = { interaction++; visible = !visible },
            onTogglePlayPause = {
                val playing = controller.state.value.session?.state?.playing == true
                act(controller::playPause); !playing
            }, onStartHoldToSpeed = { speedHold.start(controller).also { holdingSpeed = it } },
            onStopHoldToSpeed = { speedHold.stop(controller); holdingSpeed = false },
            onSeekTo = { target -> controller.state.value.session?.state?.browserSeekWindow()?.let { window ->
                act { controller.seekTo(window.startMs + target) }
            } }, onFeedback = { feedback = it; feedbackSequence++ }, onBrightnessCommitted = {},
            canSeek = state.canSeek, volumeEnabled = audio?.isVolumeFixed != true,
            onVolumeInteraction = { if (controller.state.value.session?.state?.muted == true) controller.setMuted(false) })
        if (locked && !inPip) RoundPlayerButton(Icons.Rounded.LockOpen, "Unlock controls", { locked = false; visible = true },
            modifier = Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top)))
        CompositionLocalProvider(LocalPlayerControlsActive provides (visible && !locked && !inPip)) {
        AnimatedVisibility(visible = visible && !locked && !inPip, enter = fadeIn(motion.spec(MotionTokens.controlsEnter)),
            exit = fadeOut(motion.spec(MotionTokens.controlsExit))) {
            Box(Modifier.fillMaxSize()) {
                Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                    .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(title.ifBlank { "Video" }, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium)
                        if (video.videoWidth > 0 && video.videoHeight > 0) Text("${video.videoWidth} × ${video.videoHeight}",
                            color = Color.White.copy(alpha = 0.7f), style = MaterialTheme.typography.labelSmall)
                    }
                    RotationPlayerButton(landscape, rotationLocked,
                        onRotate = {
                            interaction++; orientationOwner[0] = true; rotationLocked = true
                            activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                        }, onToggleLock = {
                            interaction++; orientationOwner[0] = true; rotationLocked = !rotationLocked
                            activity?.requestedOrientation = if (rotationLocked) ActivityInfo.SCREEN_ORIENTATION_LOCKED
                                else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                        })
                    RoundPlayerButton(Icons.Rounded.Lock, "Lock controls", { locked = true; menu = null; adjustments = false })
                    RoundPlayerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Return to website", onExit)
                }
                PlayerCenterControls(video.playing, video.buffering, false, null, null,
                    onReplay = { act { controller.skip(-10_000) } }, onPlayPause = { act(controller::playPause) },
                    onForward = { act { controller.skip(10_000) } },
                    modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(alpha = 0.25f)),
                    seekEnabled = state.canSeek)
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val window = video.browserSeekWindow()
                    if (window != null && window.durationMs > 0) {
                        PlayerBufferedTimeline(
                            (scrub ?: state.pendingSeekMs ?: video.positionMs) - window.startMs,
                            (video.buffered.firstOrNull { video.positionMs in it.startMs..it.endMs }?.endMs ?: video.positionMs) - window.startMs,
                            window.durationMs,
                            onScrub = { scrub = window.startMs + it; interaction++ },
                            onScrubFinished = { scrub?.let { target -> act { controller.seekTo(target) } }; scrub = null })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(if (video.durationMs == null) "Live" else formatPlayerTimer(
                            scrub ?: state.pendingSeekMs ?: video.positionMs, video.durationMs, false),
                            color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.weight(1f))
                        if (state.pendingSeekMs != null) Text("Seeking…", color = Color.White,
                            style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp))
                        Box {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("${video.speed}×", color = Color.White, style = MaterialTheme.typography.labelLarge)
                                RoundPlayerButton(Icons.Rounded.Speed, "Playback speed", { interaction++; menu = "speed" })
                            }
                            MiowserDropdownMenu(menu == "speed", onDismissRequest = { menu = null }) {
                                listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { speed ->
                                    MiowserMenuItem(text = { Text("${speed}×") }, selected = speed == video.speed,
                                        leadingIcon = { Icon(Icons.Rounded.Speed, null) }, onClick = {
                                            menu = null; act { controller.setSpeed(speed) }
                                        })
                                }
                            }
                        }
                    }
                    FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        RoundPlayerButton(if (video.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                            if (video.muted) "Unmute" else "Mute", { act { controller.setMuted(!video.muted) } })
                        RoundPlayerButton(Icons.Rounded.Tune, "Brightness and volume", { interaction++; adjustments = true })
                        Box {
                            RoundPlayerButton(Icons.Rounded.AspectRatio, "Video size: ${video.scale.label}", { interaction++; menu = "scale" })
                            MiowserDropdownMenu(menu == "scale", { menu = null }) {
                                BrowserVideoScale.entries.forEach { scale ->
                                    MiowserMenuItem(text = { Text(scale.label) }, selected = video.scale == scale,
                                        onClick = { menu = null; act { controller.setScale(scale) } })
                                }
                            }
                        }
                        if (video.captions.isNotEmpty()) Box {
                            RoundPlayerButton(Icons.Rounded.Subtitles, "Subtitles", { interaction++; menu = "captions" })
                            MiowserDropdownMenu(menu == "captions", { menu = null }) {
                                MiowserMenuItem(text = { Text("Off") }, selected = video.captions.none { it.selected },
                                    onClick = { menu = null; act { controller.selectCaption(null) } })
                                video.captions.forEachIndexed { index, track ->
                                    MiowserMenuItem(text = { Text(track.displayLabel("Subtitle", index), maxLines = 2) }, selected = track.selected,
                                        onClick = { menu = null; act { controller.selectCaption(track.id) } })
                                }
                            }
                        }
                        if (video.audioTracks.size > 1) Box {
                            RoundPlayerButton(Icons.Rounded.Audiotrack, "Audio tracks", { interaction++; menu = "audio" })
                            MiowserDropdownMenu(menu == "audio", { menu = null }) {
                                video.audioTracks.forEachIndexed { index, track ->
                                    MiowserMenuItem(text = { Text(track.displayLabel("Audio", index), maxLines = 2) }, selected = track.selected,
                                        onClick = { menu = null; act { controller.selectAudio(track.id) } })
                                }
                            }
                        }
                        if (supportsPip) RoundPlayerButton(Icons.Rounded.PictureInPictureAlt, "Picture in picture", {
                            menu = null; adjustments = false; speedHold.stop(controller); holdingSpeed = false
                            if (host?.enterBrowserVideoPictureInPicture(owner, video.videoWidth, video.videoHeight) != true) {
                                Toast.makeText(activity, "Picture in picture is unavailable. Check the app's PiP permission.", Toast.LENGTH_SHORT).show()
                            }
                        })
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                }
            }
        }
        }
        if (!inPip && !locked) feedback?.let {
            PlayerGestureFeedbackOverlay(it, when (it.kind) {
                PlayerGestureKind.BRIGHTNESS -> Modifier.align(Alignment.CenterStart).padding(start = 28.dp)
                PlayerGestureKind.VOLUME -> Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
                else -> Modifier.align(Alignment.Center)
            })
        }
        if (holdingSpeed && !inPip && !locked) Text("2× playback", color = Color.White,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp).background(Color.Black.copy(alpha = 0.65f)).padding(8.dp))
    }
    if (adjustments && !inPip && !locked) PlayerAdjustmentsSheet(activity, { adjustments = false },
        onBrightnessCommitted = {}, volumeEnabled = audio?.isVolumeFixed != true,
        onVolumeInteraction = { if (controller.state.value.session?.state?.muted == true) controller.setMuted(false) })
}

private fun BrowserMediaTrack.displayLabel(kind: String, index: Int): String =
    label.ifBlank { language.ifBlank { "$kind ${index + 1}" } }

private tailrec fun Context.videoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.videoActivity()
    else -> null
}
