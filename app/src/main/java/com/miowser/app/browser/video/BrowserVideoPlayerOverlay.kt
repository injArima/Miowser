package com.miowser.app.browser.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.miowser.app.MainActivity
import com.miowser.app.player.*
import com.miowser.app.ui.MiowserMenuItem
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MiowserPlayerTheme
import com.miowser.app.ui.theme.MotionTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Original player UI backed by the retained HTML video. This route never creates ExoPlayer. */
@Composable
internal fun BrowserVideoPlayerOverlay(controller: BrowserPlaybackControls, title: String,
    onExit: () -> Unit, sourceLabel: String = "", backgroundPlaybackEnabled: Boolean = true,
    onAudioOnlyChanged: (Boolean) -> Unit = {}) {
    val presentation = remember(controller) { controller.state.map(::browserVideoPresentation).distinctUntilChanged() }
    val state by presentation.collectAsState(browserVideoPresentation(controller.state.value))
    MiowserPlayerTheme(darkTheme = true) {
        if (state.phase == BrowserVideoPhase.UNAVAILABLE) {
            val host = LocalContext.current.videoActivity() as? MainActivity
            if (host?.playerInPictureInPicture != true) BrowserVideoRecoveryBanner(
                state.message ?: "The video connection is unavailable.", onExit)
        } else state.owner?.let { owner ->
            key(owner) {
                BrowserVideoControlsContent(controller, state, title, sourceLabel, onExit,
                    backgroundPlaybackEnabled, onAudioOnlyChanged)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BrowserVideoControlsContent(controller: BrowserPlaybackControls, state: BrowserVideoPresentation,
    title: String, sourceLabel: String, onExit: () -> Unit, backgroundPlaybackEnabled: Boolean,
    onAudioOnlyChanged: (Boolean) -> Unit) {
    val video = state.video ?: return
    val owner = state.owner ?: return
    val context = LocalContext.current
    val activity = context.videoActivity()
    val host = activity as? MainActivity
    val inPip = host?.playerInPictureInPicture == true && host.browserVideoPipOwner == owner
    val supportsPip = remember(activity) { host?.packageManager?.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) == true }
    val audio = remember(activity) { activity?.getSystemService(AudioManager::class.java) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val motion = LocalMotionPolicy.current
    var visible by remember { mutableStateOf(true) }
    var speedMenu by remember { mutableStateOf(false) }
    var scaleMenu by remember { mutableStateOf(false) }
    var adjustments by remember { mutableStateOf(false) }
    var audioPanel by remember { mutableStateOf(false) }
    var guidanceVisible by remember { mutableStateOf(false) }
    val overlays = remember { PlayerOverlayState() }
    val subtitleFocus = remember { FocusRequester() }
    val playlistFocus = remember { FocusRequester() }
    val focusScope = rememberCoroutineScope()
    var locked by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<PlayerGestureFeedback?>(null) }
    var feedbackSequence by remember { mutableIntStateOf(0) }
    val speedHold = remember { BrowserVideoSpeedHold() }
    var holdingSpeed by remember { mutableStateOf(false) }
    val timeline = remember { BrowserVideoTimelineInteraction() }
    var scrubbing by remember { mutableStateOf(false) }
    var interaction by remember { mutableIntStateOf(0) }
    var rotationLocked by remember { mutableStateOf(false) }
    var pendingRotation by remember { mutableStateOf<Boolean?>(null) }
    val modal = overlays.isPresent || adjustments || audioPanel || guidanceVisible
    val latestAudioOnly by rememberUpdatedState(state.audioOnly)

    LaunchedEffect(state.audioOnly) { onAudioOnlyChanged(state.audioOnly) }
    DisposableEffect(controller, state.audioOnly, title, backgroundPlaybackEnabled) {
        if (state.audioOnly && backgroundPlaybackEnabled) {
            BrowserPlaybackService.attach(context.applicationContext, controller, title)
        }
        onDispose {
            if (state.audioOnly && backgroundPlaybackEnabled) {
                BrowserPlaybackService.detach(context.applicationContext, controller)
            }
        }
    }

    DisposableEffect(controller, activity) {
        val original = activity?.requestedOrientation
        val originalBrightness = activity?.window?.attributes?.screenBrightness
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        PlayerDisplayPreferenceStore.brightness(context)?.let { saved ->
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply { screenBrightness = saved }
            }
        }
        controller.setRichUi(true)
        onDispose {
            speedHold.stop(controller)
            if (latestAudioOnly) controller.setAudioOnly(false)
            onAudioOnlyChanged(false)
            controller.setRichUi(false)
            if (activity != null && original != null) activity.requestedOrientation = original
            if (originalBrightness != null) activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
        }
    }
    LaunchedEffect(activity, landscape, pendingRotation, rotationLocked) {
        if (pendingRotation == landscape) {
            delay(300)
            activity?.requestedOrientation = if (rotationLocked) ActivityInfo.SCREEN_ORIENTATION_LOCKED
                else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            pendingRotation = null
        }
    }
    LaunchedEffect(controller, owner, state.controlsEnabled, video.sourceRevision) {
        if (state.controlsEnabled) {
            val saved = PlayerDisplayPreferenceStore.scaleMode(context)
            BrowserVideoScale.entries.firstOrNull { it.name == saved }?.let(controller::setScale)
        }
    }
    BackHandler(enabled = locked && !inPip && state.controlsEnabled && !modal) { locked = false; visible = true }
    BackHandler(enabled = guidanceVisible) { guidanceVisible = false }
    LaunchedEffect(state.phase, video.sourceRevision, inPip) {
        speedMenu = false; scaleMenu = false; adjustments = false; audioPanel = false
        guidanceVisible = false
        overlays.clear(); scrubbing = false; feedback = null; speedHold.stop(controller); holdingSpeed = false
        if (!state.controlsEnabled) locked = false
    }
    LaunchedEffect(video.captions.isEmpty(), video.audioTracks.size > 1) {
        if (video.captions.isEmpty()) overlays.clear()
        if (video.audioTracks.size <= 1) audioPanel = false
    }
    LaunchedEffect(feedbackSequence) { if (feedback != null) { delay(1_000); feedback = null } }
    LaunchedEffect(guidanceVisible) {
        if (guidanceVisible) {
            delay(7_000)
            guidanceVisible = false
        }
    }
    LaunchedEffect(visible, video.playing, video.buffering, speedMenu, scaleMenu, modal, scrubbing,
        state.seeking, state.message, interaction, state.phase) {
        if (!locked && (!video.playing || state.message != null)) visible = true
        if (visible && video.playing && !video.buffering && !speedMenu && !scaleMenu && !modal &&
            !scrubbing && !state.seeking && state.message == null && state.controlsEnabled) {
            delay(4_000); visible = false
        }
    }
    fun act(action: () -> Unit) { visible = true; interaction++; controller.clearMessage(); action() }
    fun closeMenus() { speedMenu = false; scaleMenu = false }
    fun skip(deltaMs: Long) {
        val origin = controller.state.value.session?.state?.positionMs ?: return
        act { controller.skip(deltaMs) }
        val target = controller.state.value.pendingSeekMs ?: origin
        feedback = PlayerGestureFeedback(if (deltaMs < 0) PlayerGestureKind.SEEK_BACKWARD else PlayerGestureKind.SEEK_FORWARD,
            formatPlayerTime(target), seekDeltaMs = target - origin)
        feedbackSequence++
    }

    if (!inPip) PlayerInteractionLayers(
        blockPlayerInput = modal,
        playerContent = {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val viewportHeight = maxHeight
                if (state.audioOnly) Box(Modifier.fillMaxSize().background(Color.Black))
                if (state.controlsEnabled) PlayerGestureSurface(locked, visible, landscape,
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
                        // Gesture seeking has its own compact feedback. Do not wake the full
                        // chrome; an ordinary single tap remains the explicit show/hide action.
                        controller.clearMessage()
                        controller.seekTo(window.startMs + target)
                    } }, onFeedback = { feedback = it; feedbackSequence++ },
                    onBrightnessCommitted = { PlayerDisplayPreferenceStore.saveBrightness(context, it) },
                    canSeek = state.canSeek, volumeEnabled = audio?.isVolumeFixed != true,
                    onVolumeInteraction = { if (controller.state.value.session?.state?.muted == true) controller.setMuted(false) })
                AnimatedVisibility(visible && state.controlsEnabled,
                    enter = fadeIn(motion.spec(MotionTokens.controlsEnter)),
                    exit = fadeOut(motion.spec(MotionTokens.controlsExit)), modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalPlayerControlsActive provides (visible && state.controlsEnabled && !modal)) {
                        Box(Modifier.fillMaxSize().playerFocusGate(!visible || !state.controlsEnabled || modal)) {
                            PlayerChromeScrims(viewportHeight, locked)
                            if (locked) RoundPlayerButton(Icons.Rounded.LockOpen, "Unlock controls", { locked = false },
                                Modifier.align(Alignment.TopEnd)
                                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)).padding(20.dp))
                            else {
                                PlayerTopBar(landscape, rotationLocked, video.speed.toFloat(),
                                    onRotate = {
                                        interaction++; pendingRotation = !landscape
                                        activity?.requestedOrientation = if (landscape) ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    }, onToggleRotationLock = {
                                        interaction++; pendingRotation = null; rotationLocked = !rotationLocked
                                        activity?.requestedOrientation = if (rotationLocked) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                                    }, onDownload = null,
                                    onCaution = {
                                        closeMenus()
                                        visible = true
                                        interaction++
                                        guidanceVisible = true
                                    },
                                    onPip = if (!state.audioOnly && supportsPip) ({
                                        closeMenus(); speedHold.stop(controller)
                                        if (host?.enterBrowserVideoPictureInPicture(owner, video.width, video.height) != true)
                                            Toast.makeText(activity, "Picture in picture is unavailable. Check the app's PiP permission.", Toast.LENGTH_SHORT).show()
                                    }) else null,
                                    audioOnly = state.audioOnly,
                                    onToggleAudioOnly = {
                                        closeMenus()
                                        act { controller.setAudioOnly(!state.audioOnly) }
                                    },
                                    onOpenPlaylist = null, playlistFocus = playlistFocus, playlistPosition = 0, playlistSize = 0,
                                    onLock = { locked = true; closeMenus() }, onDismiss = onExit, modifier = Modifier.align(Alignment.TopCenter))
                                PlayerCenterControls(video.playing, video.buffering, false, null, null,
                                    onReplay = { skip(-10_000) }, onPlayPause = { act(controller::playPause) },
                                    onForward = { skip(10_000) }, modifier = Modifier.align(Alignment.Center), seekEnabled = state.canSeek,
                                    showSeekControls = state.canSeek)
                                PlayerBottomChrome(title.ifBlank { "Video" }, sourceLabel, video.height.takeIf { it > 0 }?.let { "${it}p" },
                                    landscape, PlayerScaleMode.valueOf(video.scale.name), video.speed.toFloat(), owner,
                                    scaleMenu, { scaleMenu = it; interaction++ }, { mode ->
                                        scaleMenu = false
                                        PlayerDisplayPreferenceStore.saveScaleMode(context, mode.name)
                                        act { controller.setScale(BrowserVideoScale.valueOf(mode.name)) }
                                    },
                                    speedMenu, { speedMenu = it; interaction++ }, { rate -> speedMenu = false; act { controller.setSpeed(rate.toDouble()) } },
                                    subtitleFocus, "Subtitles", true, if (video.captions.isNotEmpty()) ({ closeMenus(); overlays.open(PlayerOverlay.SUBTITLES); interaction++ }) else null,
                                    "Audio tracks", true, if (video.audioTracks.size > 1) ({ closeMenus(); audioPanel = true; interaction++ }) else null,
                                    "Quality", false, null,
                                    onAdjustmentsRequested = { closeMenus(); adjustments = true; interaction++ },
                                    timelineContent = { BrowserVideoTimeline(controller, timeline) { if (scrubbing != it) { scrubbing = it; interaction++ } } },
                                    timerContent = { BrowserVideoTimer(controller, timeline, it) }, modifier = Modifier.align(Alignment.BottomCenter),
                                    extraSpeedOptions = {
                                        MiowserMenuItem(text = { Text(if (video.muted) "Unmute" else "Mute") },
                                            leadingIcon = { Icon(if (video.muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null) },
                                            onClick = { speedMenu = false; act { controller.setMuted(!video.muted) } })
                                    }, message = state.message)
                            }
                        }
                    }
                }
                if (!locked && !modal) feedback?.let {
                    PlayerGestureFeedbackOverlay(it, when (it.kind) {
                        PlayerGestureKind.BRIGHTNESS -> Modifier.align(Alignment.CenterStart).padding(start = 28.dp)
                        PlayerGestureKind.VOLUME -> Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
                        PlayerGestureKind.SEEK_BACKWARD,
                        PlayerGestureKind.SEEK_FORWARD -> Modifier.align(Alignment.TopCenter)
                            .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                            .padding(top = if (landscape) 64.dp else 100.dp)
                        else -> Modifier.align(Alignment.Center)
                    })
                }
                if (holdingSpeed && video.speed == 2.0 && state.controlsEnabled && !locked && !modal) {
                    PlayerHoldSpeedIndicator(Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                        .padding(top = 16.dp))
                }
                if (state.phase == BrowserVideoPhase.RECONNECTING) BrowserVideoRecoveryBanner("Reconnecting player controls…", onExit)
            }
        }, overlayContent = {
            if (guidanceVisible) {
                val bubbleInteraction = remember { MutableInteractionSource() }
                Box(Modifier.fillMaxSize().clickable(
                    interactionSource = bubbleInteraction,
                    indication = null
                ) { guidanceVisible = false })
                BrowserPlayerGuidanceBubble(
                    Modifier.align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .padding(top = 62.dp, end = 16.dp)
                )
            }
            if (adjustments && state.controlsEnabled) PlayerAdjustmentsSheet(activity, { adjustments = false },
                volumeEnabled = audio?.isVolumeFixed != true,
                onVolumeInteraction = { if (controller.state.value.session?.state?.muted == true) controller.setMuted(false) })
            if (audioPanel && state.controlsEnabled) PlayerTrackSheet("Audio", { audioPanel = false }) { listModifier ->
                LazyColumn(listModifier) {
                    state.message?.let { item("error") { Text(it, color = MaterialTheme.colorScheme.error) } }
                    itemsIndexed(video.audioTracks, key = { _, track -> track.id }) { index, track ->
                        PlayerTrackChoiceRow(track.displayLabel("Audio", index), track.language, track.selected,
                            { act { controller.selectAudio(track.id) } })
                    }
                }
            }
            PlayerOverlayHost(overlays, onClosed = {
                visible = true
                val revision = overlays.revision
                focusScope.launch {
                    withFrameNanos { }
                    val current = controller.state.value
                    if (overlays.revision == revision && !overlays.isPresent && owner.matches(current) &&
                        current.controlsEnabled && current.session?.state?.captions?.isNotEmpty() == true) {
                        runCatching { subtitleFocus.requestFocus() }
                    }
                }
            }) { _, panelModifier, initialFocus ->
                PlayerSubtitleWorkspace({ overlays.dismiss() }, panelModifier, initialFocus,
                    tracks = { contentModifier, _ ->
                        LazyColumn(contentModifier) {
                            state.message?.let { item("error") { Text(it, color = MaterialTheme.colorScheme.error) } }
                            item("off") { PlayerSubtitleChoiceRow("Off", video.captions.none { it.selected }, { act { controller.selectCaption(null) } }) }
                            itemsIndexed(video.captions, key = { _, track -> track.id }) { index, track ->
                                PlayerSubtitleChoiceRow(track.displayLabel("Subtitle", index), track.selected, { act { controller.selectCaption(track.id) } })
                            }
                        }
                    })
            }
        })
}

@Composable
private fun BrowserPlayerGuidanceBubble(modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier.widthIn(max = 360.dp).clickable(
            interactionSource = interaction,
            indication = null,
            onClick = {}
        ),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF2D291E).copy(alpha = 0.96f),
        contentColor = Color.White,
        border = BorderStroke(1.dp, Color(0xFFFFD875).copy(alpha = 0.72f)),
        shadowElevation = 8.dp
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = Color(0xFFFFD875),
                modifier = Modifier.size(24.dp)
            )
            Text(
                "For the best Miowser experience, open this video from the Media page and play it " +
                    "with the rich player. DRM-protected videos must remain in the WebView player.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun BrowserVideoTimeline(controller: BrowserPlaybackControls, timeline: BrowserVideoTimelineInteraction,
    onScrubbing: (Boolean) -> Unit) {
    val state by controller.state.collectAsState()
    val video = state.session?.state ?: return
    val window = video.browserSeekWindow() ?: return
    val notify by rememberUpdatedState(onScrubbing)
    DisposableEffect(controller) { onDispose { timeline.clear(); notify(false) } }
    LaunchedEffect(video.sourceRevision, state.controlsEnabled) { timeline.clear(); notify(false) }
    PlayerBufferedTimeline((timeline.scrubPositionMs ?: state.pendingSeekMs ?: video.positionMs) - window.startMs,
        (video.buffered.firstOrNull { video.positionMs in it.startMs..it.endMs }?.endMs ?: video.positionMs) - window.startMs,
        window.durationMs,
        onScrub = { timeline.update(state, window.startMs + it); notify(true) },
        onScrubFinished = {
            timeline.targetFor(controller.state.value)?.let(controller::seekTo)
            timeline.clear(); notify(false)
        })
}

@Composable
private fun BrowserVideoTimer(controller: BrowserPlaybackControls, timeline: BrowserVideoTimelineInteraction, modifier: Modifier) {
    val state by controller.state.collectAsState()
    val video = state.session?.state ?: return
    PlayerTimer(timeline.scrubPositionMs ?: state.pendingSeekMs ?: video.positionMs, video.durationMs ?: 0, modifier, live = video.durationMs == null)
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BrowserVideoRecoveryBanner(message: String, onExit: () -> Unit) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing)
        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
        .background(Color.Black.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = Color.White, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        RoundPlayerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Return to website", onExit)
    }
}

private fun BrowserMediaTrack.displayLabel(kind: String, index: Int): String = label.ifBlank { language.ifBlank { "$kind ${index + 1}" } }

private tailrec fun Context.videoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.videoActivity()
    else -> null
}
