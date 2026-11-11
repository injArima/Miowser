package com.miowser.app.browser.video

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.media.AudioManager
import android.os.SystemClock
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.miowser.app.ui.AppText as Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.ResourcesCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.extractor.text.CuesWithTiming
import com.miowser.app.MainActivity
import com.miowser.app.browser.loadSubtitleForPlayback
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.player.*
import com.miowser.app.ui.sheetVerticalEdgeFade
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MiowserPlayerTheme
import com.miowser.app.ui.theme.MotionTokens
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.UiCorners
import com.miowser.app.ui.theme.UiElevation
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing

/** User selections carried only between videos in the same fullscreen browsing session. */
internal class BrowserVideoSessionPreferences {
    var orientation by mutableStateOf<PlayerOrientationMode?>(null)
    var scale: BrowserVideoScale? = null
    var speed: Double? = null
}

/** Own the activity orientation outside the replaceable fullscreen video view. */
@Composable
internal fun BrowserVideoFullscreenOrientation(preferences: BrowserVideoSessionPreferences) {
    val orientation = preferences.orientation ?: return
    val activity = LocalContext.current.videoActivity() ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(activity, lifecycle, preferences) {
        val original = activity.requestedOrientation
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                preferences.orientation?.let { activity.requestedOrientation = it.requestedOrientation }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            activity.requestedOrientation = original
        }
    }
    LaunchedEffect(activity, preferences, orientation) {
        activity.requestedOrientation = orientation.requestedOrientation
    }
}

/** Player UI backed by the retained HTML video. */
@Composable
internal fun BrowserVideoPlayerOverlay(controller: BrowserPlaybackControls, title: String,
    onExit: () -> Unit, preferences: BrowserVideoSessionPreferences,
    sourceLabel: String = "", backgroundPlaybackEnabled: Boolean = true,
    onBrowseSource: (() -> Unit)? = null,
    detectedSubtitles: List<DetectedMedia> = emptyList(), userAgent: String = "Miowser",
    onAudioOnlyChanged: (Boolean) -> Unit = {}) {
    val browserState by controller.state.collectAsState()
    val state = remember(browserState) { browserVideoPresentation(browserState) }
    MiowserPlayerTheme(darkTheme = true) {
        if (state.phase == BrowserVideoPhase.UNAVAILABLE) {
            val host = LocalContext.current.videoActivity() as? MainActivity
            if (host?.playerInPictureInPicture != true) BrowserVideoRecoveryBanner(
                state.message ?: "The video connection is unavailable.", onExit)
        } else state.owner?.let { owner ->
            key(owner) {
                BrowserVideoControlsContent(controller, state, title, sourceLabel, onExit,
                    preferences, backgroundPlaybackEnabled, onBrowseSource, detectedSubtitles, userAgent, onAudioOnlyChanged)
            }
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun BrowserVideoControlsContent(controller: BrowserPlaybackControls, state: BrowserVideoPresentation,
    title: String, sourceLabel: String, onExit: () -> Unit, preferences: BrowserVideoSessionPreferences,
    backgroundPlaybackEnabled: Boolean,
    onBrowseSource: (() -> Unit)?,
    detectedSubtitles: List<DetectedMedia>, userAgent: String,
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
    val orientationMode = preferences.orientation ?: PlayerDisplayPreferenceStore.orientation(context)
    SideEffect {
        if (preferences.orientation == null) preferences.orientation = orientationMode
    }
    val modal = overlays.isPresent || adjustments || audioPanel || guidanceVisible
    val latestAudioOnly by rememberUpdatedState(state.audioOnly)
    var selectedSubtitleUrl by remember { mutableStateOf<String?>(null) }
    var subtitleChoiceMade by remember { mutableStateOf(false) }
    var externalSubtitleCues by remember { mutableStateOf<List<CuesWithTiming>>(emptyList()) }
    var externalSubtitleError by remember { mutableStateOf<String?>(null) }
    var externalSubtitleLoading by remember { mutableStateOf(false) }
    var externalSubtitleLoadAttempt by remember { mutableIntStateOf(0) }
    var subtitleStyle by remember(context) { mutableStateOf(PlayerSubtitleStyleStore.load(context)) }
    var subtitleDelayMs by remember { mutableLongStateOf(subtitleStyle.delayMs) }
    val effectiveSubtitleStyle = subtitleStyle.copy(delayMs = subtitleDelayMs)
    var subtitleAdjustAnchorMs by remember { mutableStateOf<Long?>(null) }
    var resumeAfterSubtitleAdjust by remember { mutableStateOf(false) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(context, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) subtitleStyle = PlayerSubtitleStyleStore.load(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    val subtitleTypeface = remember(context, subtitleStyle.font) {
        captionFontResource(subtitleStyle.font)?.let { ResourcesCompat.getFont(context, it) }
    }
    val subtitleAvailable = video.captions.isNotEmpty() || detectedSubtitles.isNotEmpty()
    val selectedDetectedSubtitle = detectedSubtitles.firstOrNull { it.url == selectedSubtitleUrl }

    fun finishSubtitleAdjustment() {
        if (subtitleAdjustAnchorMs == null) return
        subtitleAdjustAnchorMs = null
        if (resumeAfterSubtitleAdjust) controller.playPause()
        resumeAfterSubtitleAdjust = false
    }
    fun beginSubtitleAdjustment() {
        if (selectedDetectedSubtitle == null || externalSubtitleCues.none { it.cues.isNotEmpty() }) return
        val playback = controller.state.value
        subtitleAdjustAnchorMs = browserSubtitlePositionMs(playback, owner, SystemClock.elapsedRealtime())
            ?: return
        resumeAfterSubtitleAdjust = playback.session?.state?.playing == true
        if (resumeAfterSubtitleAdjust) controller.pause()
    }
    fun alignSubtitleToCue(cueStartTimeUs: Long) {
        val anchorMs = subtitleAdjustAnchorMs ?: return
        subtitleDelayMs = subtitleDelayForCue(anchorMs, cueStartTimeUs)
        finishSubtitleAdjustment()
    }
    LaunchedEffect(overlays.target, subtitleAdjustAnchorMs) {
        if (subtitleAdjustAnchorMs != null && overlays.target != PlayerOverlay.SUBTITLES) {
            finishSubtitleAdjustment()
        }
    }

    LaunchedEffect(detectedSubtitles, video.captions.isEmpty()) {
        if (selectedSubtitleUrl != null && detectedSubtitles.none { it.url == selectedSubtitleUrl }) {
            finishSubtitleAdjustment()
            selectedSubtitleUrl = null
        }
        if (!subtitleChoiceMade && video.captions.isEmpty() && detectedSubtitles.size == 1) {
            selectedSubtitleUrl = detectedSubtitles.single().url
        }
    }
    LaunchedEffect(selectedDetectedSubtitle, userAgent, externalSubtitleLoadAttempt) {
        externalSubtitleCues = emptyList()
        externalSubtitleError = null
        externalSubtitleLoading = false
        val subtitle = selectedDetectedSubtitle ?: return@LaunchedEffect
        externalSubtitleLoading = true
        try {
            val text = loadSubtitleForPlayback(subtitle, userAgent)
            val cues = withContext(Dispatchers.Default) { parseExternalSubtitle(text) }
            if (cues.isEmpty()) externalSubtitleError = "No timed cues were found in this subtitle."
            else externalSubtitleCues = cues
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) {
            externalSubtitleError = error.message ?: "Could not load this subtitle."
        } finally { externalSubtitleLoading = false }
    }

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
        val originalBrightness = activity?.window?.attributes?.screenBrightness
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
            if (originalBrightness != null) activity.window.attributes = activity.window.attributes.apply { screenBrightness = originalBrightness }
        }
    }
    LaunchedEffect(controller, owner, state.controlsEnabled, video.sourceRevision) {
        if (state.controlsEnabled) {
            val saved = PlayerDisplayPreferenceStore.scaleMode(context)
            val scale = preferences.scale ?: BrowserVideoScale.entries.firstOrNull { it.name == saved }
            scale?.let(controller::setScale)
            preferences.speed?.takeIf { it != video.speed }?.let(controller::setSpeed)
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
    LaunchedEffect(subtitleAvailable, video.audioTracks.size > 1) {
        if (!subtitleAvailable) overlays.clear()
        if (video.audioTracks.size <= 1) audioPanel = false
    }
    LaunchedEffect(feedbackSequence) { if (feedback != null) { delay(1_000); feedback = null } }
    LaunchedEffect(guidanceVisible) {
        if (guidanceVisible) {
            delay(7_000)
            guidanceVisible = false
        }
    }
    val playing = state.pendingPlaying ?: video.playing
    LaunchedEffect(playing) { if (!locked && !playing) visible = true }
    LaunchedEffect(state.message) { if (!locked && state.message != null) visible = true }
    LaunchedEffect(visible, playing, video.buffering, speedMenu, scaleMenu, modal, scrubbing,
        state.seeking, state.message, interaction, state.phase) {
        if (visible && playing && !video.buffering && !speedMenu && !scaleMenu && !modal &&
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
                if (state.audioOnly) Box(Modifier.fillMaxSize().background(PlayerUiColors.backdrop))
                if (!state.audioOnly && selectedSubtitleUrl != null && externalSubtitleCues.isNotEmpty()) {
                    BrowserDetectedSubtitleLayer(controller, owner, selectedSubtitleUrl,
                        externalSubtitleCues, effectiveSubtitleStyle, subtitleTypeface)
                }
                if (state.controlsEnabled) PlayerGestureSurface(locked, visible, landscape,
                    positionMs = { controller.state.value.let { s ->
                        val v = s.session?.state
                        ((s.pendingSeekMs ?: v?.positionMs ?: 0L) - (v?.browserSeekWindow()?.startMs ?: 0L)).coerceAtLeast(0)
                    } }, durationMs = { controller.state.value.session?.state?.browserSeekWindow()?.durationMs ?: 0L },
                    onSingleTap = { interaction++; visible = !visible },
                    onTogglePlayPause = {
                        val playing = controller.state.value.let { it.pendingPlaying ?: it.session?.state?.playing } == true
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
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End)).padding(UiSpacing.xl))
                            else {
                                PlayerTopBar(landscape, orientationMode, video.speed.toFloat(),
                                    onOrientationSelected = { interaction++; preferences.orientation = it },
                                    onToggleRotationLock = {
                                        interaction++
                                        preferences.orientation = if (orientationMode.locked) PlayerOrientationMode.AUTOMATIC
                                            else PlayerOrientationMode.lockCurrent(activity)
                                    }, onDownload = null,
                                    onBrowseSource = onBrowseSource,
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
                                    muted = video.muted,
                                    onToggleMute = { closeMenus(); act { controller.setMuted(!video.muted) } },
                                    onLock = { locked = true; closeMenus() }, onDismiss = onExit, modifier = Modifier.align(Alignment.TopCenter))
                                if (visible) PlayerCenterControls(playing,
                                    video.buffering, false, null, null,
                                    onReplay = { skip(-10_000) }, onPlayPause = { act(controller::playPause) },
                                    onForward = { skip(10_000) }, modifier = Modifier.align(Alignment.Center), seekEnabled = state.canSeek,
                                    showSeekControls = state.canSeek)
                                PlayerBottomChrome(title.ifBlank { "Video" }, sourceLabel, video.height.takeIf { it > 0 }?.let { "${it}p" },
                                    landscape, PlayerScaleMode.valueOf(video.scale.name), video.speed.toFloat(), owner,
                                    scaleMenu, { scaleMenu = it; interaction++ }, { mode ->
                                        scaleMenu = false
                                        PlayerDisplayPreferenceStore.saveScaleMode(context, mode.name)
                                        val scale = BrowserVideoScale.valueOf(mode.name)
                                        preferences.scale = scale
                                        act { controller.setScale(scale) }
                                    },
                                    speedMenu, { speedMenu = it; interaction++ }, { rate ->
                                        val speed = rate.toDouble()
                                        preferences.speed = speed
                                        act { controller.setSpeed(speed) }
                                    },
                                    subtitleFocus, "Subtitles", true, if (subtitleAvailable) ({ closeMenus(); overlays.open(PlayerOverlay.SUBTITLES); interaction++ }) else null,
                                    "Audio tracks", true, if (video.audioTracks.size > 1) ({ closeMenus(); audioPanel = true; interaction++ }) else null,
                                    "Quality", false, null,
                                    onAdjustmentsRequested = { closeMenus(); adjustments = true; interaction++ },
                                    timelineContent = { BrowserVideoTimeline(controller, timeline) { if (scrubbing != it) { scrubbing = it; interaction++ } } },
                                    timerContent = { BrowserVideoTimer(controller, timeline, it) }, modifier = Modifier.align(Alignment.BottomCenter),
                                    message = state.message)
                            }
                        }
                    }
                }
                if (!locked && !modal) feedback?.takeUnless { visible &&
                    (it.kind == PlayerGestureKind.PLAY || it.kind == PlayerGestureKind.PAUSE) }?.let {
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
                        .padding(top = UiSpacing.l))
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
                        .padding(top = 62.dp, end = UiSpacing.l)
                )
            }
            if (adjustments && state.controlsEnabled) PlayerAdjustmentsSheet(activity, { adjustments = false },
                volumeEnabled = audio?.isVolumeFixed != true,
                onVolumeInteraction = { if (controller.state.value.session?.state?.muted == true) controller.setMuted(false) })
            if (audioPanel && state.controlsEnabled) PlayerTrackSheet("Audio", { audioPanel = false }) { listModifier ->
                val listState = rememberLazyListState()
                LazyColumn(listModifier.sheetVerticalEdgeFade(listState, PlayerUiColors.panel),
                    state = listState) {
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
                        current.controlsEnabled && subtitleAvailable) {
                        runCatching { subtitleFocus.requestFocus() }
                    }
                }
            }) { _, panelModifier, initialFocus ->
                PlayerSubtitleWorkspace({ overlays.dismiss() }, panelModifier, initialFocus,
                    inspectionTitle = selectedDetectedSubtitle?.let(::detectedSubtitleLabel)
                        ?: video.captions.withIndex().firstOrNull { it.value.selected }?.let {
                            it.value.displayLabel("Subtitle", it.index)
                        },
                    tracks = { contentModifier, inspect ->
                        LazyColumn(contentModifier) {
                            state.message?.let { item("error") { Text(it, color = MaterialTheme.colorScheme.error) } }
                            externalSubtitleError?.let { error ->
                                item("subtitle_error") { Text(error, color = MaterialTheme.colorScheme.error) }
                                item("subtitle_retry") {
                                    TextButton(onClick = { externalSubtitleLoadAttempt++ }) {
                                        Text("Retry subtitle")
                                    }
                                }
                            }
                            if (externalSubtitleLoading) item("subtitle_loading") { Text("Loading subtitle…") }
                            item("off") { PlayerSubtitleChoiceRow("Off", selectedSubtitleUrl == null && video.captions.none { it.selected }, {
                                finishSubtitleAdjustment()
                                subtitleChoiceMade = true; selectedSubtitleUrl = null
                                act { controller.selectCaption(null) }
                            }) }
                            itemsIndexed(video.captions, key = { _, track -> track.id }) { index, track ->
                                PlayerSubtitleChoiceRow(track.displayLabel("Subtitle", index),
                                    selectedSubtitleUrl == null && track.selected, {
                                        finishSubtitleAdjustment()
                                        subtitleChoiceMade = true; selectedSubtitleUrl = null
                                        act { controller.selectCaption(track.id) }
                                    })
                            }
                            if (video.captions.isNotEmpty()) item("website_captions_note") {
                                Text("Website captions use the site's own appearance and timing.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = PlayerUiColors.content.copy(alpha = 0.68f))
                            }
                            if (detectedSubtitles.isNotEmpty()) {
                                item("detected_heading") { Text("Detected subtitle files", style = MaterialTheme.typography.labelLarge) }
                                itemsIndexed(detectedSubtitles, key = { _, item -> item.url }) { _, item ->
                                    PlayerSubtitleChoiceRow(detectedSubtitleLabel(item), selectedSubtitleUrl == item.url, {
                                        finishSubtitleAdjustment()
                                        subtitleChoiceMade = true
                                        if (selectedSubtitleUrl == item.url) externalSubtitleLoadAttempt++
                                        else selectedSubtitleUrl = item.url
                                        if (video.captions.isNotEmpty()) act { controller.selectCaption(null) }
                                        inspect()
                                    }, detail = item.pageUrl.substringAfter("://").substringBefore('/'))
                                }
                            }
                        }
                    }, inspect = { contentModifier ->
                        val position = rememberBrowserSubtitlePlaybackPosition(controller, owner)
                        PlayerSubtitleInspectPanel(effectiveSubtitleStyle, selectedDetectedSubtitle,
                            externalSubtitleCues, externalSubtitleError,
                            !externalSubtitleLoading && externalSubtitleCues.isNotEmpty(),
                            position, subtitleAdjustAnchorMs, ::alignSubtitleToCue,
                            { subtitleDelayMs = it }, ::beginSubtitleAdjustment, ::finishSubtitleAdjustment,
                            contentModifier)
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
        shape = UiCorners.r18,
        color = PlayerUiColors.guidanceSurface.copy(alpha = 0.96f),
        contentColor = PlayerUiColors.content,
        border = BorderStroke(1.dp, PlayerUiColors.caution.copy(alpha = 0.72f)),
        shadowElevation = UiElevation.high
    ) {
        Row(
            Modifier.padding(horizontal = UiSpacing.l, vertical = UiSpacing.comfortable),
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = PlayerUiColors.caution,
                modifier = Modifier.size(UiIconSize.standard)
            )
            Text(
                "For the best Miowser experience, open this video from the Media page and play it " +
                    "with the rich player. DRM-protected videos must remain in the WebView player.",
                style = MaterialTheme.typography.bodyMedium,
                color = PlayerUiColors.content
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
        .background(PlayerUiColors.backdrop.copy(alpha = 0.85f)).padding(horizontal = UiSpacing.m, vertical = UiSpacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(message, color = PlayerUiColors.content, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        RoundPlayerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Return to website", onExit)
    }
}

private fun BrowserMediaTrack.displayLabel(kind: String, index: Int): String = label.ifBlank { language.ifBlank { "$kind ${index + 1}" } }

private tailrec fun Context.videoActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.videoActivity()
    else -> null
}
