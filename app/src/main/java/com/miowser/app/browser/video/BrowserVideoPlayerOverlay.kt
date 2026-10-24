package com.miowser.app.browser.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.player.PlayerBufferedTimeline
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
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val motion = LocalMotionPolicy.current
    var visible by remember { mutableStateOf(true) }
    var speedMenu by remember { mutableStateOf(false) }
    var scrub by remember { mutableStateOf<Long?>(null) }
    var interaction by remember { mutableIntStateOf(0) }
    var rotationLocked by remember { mutableStateOf(false) }
    val orientationOwner = remember { booleanArrayOf(false) }
    DisposableEffect(controller, activity) {
        val original = activity?.requestedOrientation
        controller.setRichUi(true)
        onDispose {
            controller.setRichUi(false)
            if (orientationOwner[0] && activity != null && original != null) activity.requestedOrientation = original
        }
    }
    LaunchedEffect(visible, video.playing, speedMenu, scrub, state.pendingSeekMs, state.message, interaction) {
        if (!video.playing || state.message != null) visible = true
        if (visible && video.playing && !speedMenu && scrub == null && state.pendingSeekMs == null && state.message == null) {
            delay(3_500)
            visible = false
        }
    }
    fun act(action: () -> Unit) { interaction++; controller.clearMessage(); action() }

    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
        // This tap surface is a sibling, never an ancestor which consumes control gestures.
        Box(Modifier.fillMaxSize().semantics { contentDescription = if (visible) "Hide player controls" else "Show player controls" }
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                interaction++; visible = !visible
            })
        AnimatedVisibility(visible = visible, enter = fadeIn(motion.spec(MotionTokens.controlsEnter)),
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
                                RoundPlayerButton(Icons.Rounded.Speed, "Playback speed", { interaction++; speedMenu = true })
                            }
                            MiowserDropdownMenu(speedMenu, onDismissRequest = { speedMenu = false }) {
                                listOf(0.5, 0.75, 1.0, 1.25, 1.5, 2.0).forEach { speed ->
                                    MiowserMenuItem(text = { Text("${speed}×") }, selected = speed == video.speed,
                                        leadingIcon = { Icon(Icons.Rounded.Speed, null) }, onClick = {
                                            speedMenu = false; act { controller.setSpeed(speed) }
                                        })
                                }
                            }
                        }
                    }
                    state.message?.let { Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall, maxLines = 2) }
                }
            }
        }
    }
}

private tailrec fun Context.videoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.videoActivity()
    else -> null
}
