package com.miowser.app.player
import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import androidx.compose.ui.platform.testTag

import android.app.Activity
import com.miowser.app.MainActivity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.graphics.Typeface
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.provider.Settings
import android.view.ViewGroup
import android.view.View
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.navigationBarsIgnoringVisibility
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import com.miowser.app.grabber.MediaType
import com.miowser.app.R
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.browser.SubtitleViewer
import com.miowser.app.browser.loadSubtitleForPlayback
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.LibrarySubtitleStore
import com.miowser.app.library.toLibrarySubtitle
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.library.videoQualityHeight
import com.miowser.app.library.videoQualityLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToLong

private const val SEEK_INCREMENT_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val LIBRARY_PROGRESS_MIN_DELTA_MS = 5_000L
private const val PLAYER_TAG = "Miowser/Player"

/** A remote browser stream or a completed local download, plus the metadata shown by the player. */
sealed class PlaybackRequest {
    abstract val title: String
    abstract val sourceLabel: String
    abstract val startPositionMs: Long

    data class Remote(
        val url: String,
        val pageUrl: String,
        val userAgent: String,
        val cookies: String?,
        val mediaType: MediaType,
        val externalSubtitles: List<DetectedMedia> = emptyList(),
        val preferredSubtitleUrl: String? = null,
        override val title: String,
        override val sourceLabel: String,
        val pageTitle: String? = null,
        val videoName: String? = null,
        val requestReferer: String? = null,
        val requestOrigin: String? = null,
        val posterUrl: String? = null,
        val posterCookies: String? = null,
        override val startPositionMs: Long = 0L
    ) : PlaybackRequest()

    data class Local(
        val uri: Uri,
        override val title: String,
        override val sourceLabel: String = "Downloaded",
        val pageUrl: String? = null,
        val pageTitle: String? = null,
        val videoName: String? = null,
        override val startPositionMs: Long = 0L
    ) : PlaybackRequest()
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private val PlayerScaleMode.resizeMode: Int get() = when (this) {
    PlayerScaleMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
    PlayerScaleMode.ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    PlayerScaleMode.FILL -> AspectRatioFrameLayout.RESIZE_MODE_FILL
}

internal enum class PlayerGestureKind { SEEK_BACKWARD, SEEK_FORWARD, PLAY, PAUSE, BRIGHTNESS, VOLUME }

internal val PLAYER_SEEK_BACKWARD_COLOR = Color(0xFFFFAAA5)
internal val PLAYER_SEEK_FORWARD_COLOR = Color(0xFFA8E6B0)

internal data class PlayerGestureFeedback(
    val kind: PlayerGestureKind,
    val label: String,
    val progress: Float? = null,
    val seekDeltaMs: Long? = null
)

/** Player-wide rotation state survives replacement of a playlist item and its loading screen. */
private class PlayerOrientationState {
    var locked by mutableStateOf(false)
    var pendingManualOrientation by mutableStateOf<Int?>(null)
    var relockAfterManualRotation by mutableStateOf(false)
}

/**
 * Miowser's full-screen player shell. Media3 still performs playback, adaptive streaming, and
 * decoding; its stock controller is disabled and this Compose overlay owns the visible controls.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    playlist: List<LibraryEntry> = emptyList(),
    onSelectPlaylistEntry: ((LibraryEntry) -> Unit)? = null,
    onPlayPrevious: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onOpenSourcePage: (() -> Unit)? = null,
    recordInLibrary: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val orientationState = remember { PlayerOrientationState() }
    val initialLocalPlaylistRequest = remember {
        (request as? PlaybackRequest.Local)?.takeIf { playlist.isNotEmpty() }
    }
    val retainedLocalSession = remember {
        mutableStateOf(initialLocalPlaylistRequest?.let { PlaybackSession.create(context, it, null, false) })
    }
    val localPlaylistRequest = (request as? PlaybackRequest.Local)
        ?.takeIf { playlist.isNotEmpty() || retainedLocalSession.value != null }
    var activeLocalRequest by remember { mutableStateOf(initialLocalPlaylistRequest) }
    DisposableEffect(Unit) {
        onDispose { retainedLocalSession.value?.close() }
    }
    LaunchedEffect(localPlaylistRequest) {
        if (localPlaylistRequest == null) {
            retainedLocalSession.value?.close()
            retainedLocalSession.value = null
            activeLocalRequest = null
        } else if (activeLocalRequest != localPlaylistRequest) {
            val session = retainedLocalSession.value
            if (session == null) {
                retainedLocalSession.value = PlaybackSession.create(context, localPlaylistRequest, null, false)
            } else {
                session.replaceLocal(context, localPlaylistRequest)
            }
            activeLocalRequest = localPlaylistRequest
        }
    }
    // A playlist item can briefly show the loading screen. Keep orientation and brightness owned
    // by the player for the whole session, restoring the Hub only when the player actually exits.
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        val previousBrightness = activity?.window?.attributes?.screenBrightness
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        PlayerDisplayPreferenceStore.brightness(context)?.let { saved ->
            activity?.window?.let { window ->
                window.attributes = window.attributes.apply { screenBrightness = saved }
            }
        }
        onDispose {
            if (activity != null && previousOrientation != null) {
                activity.requestedOrientation = previousOrientation
            }
            if (activity != null && previousBrightness != null) {
                activity.window.attributes = activity.window.attributes.apply {
                    screenBrightness = previousBrightness
                }
            }
        }
    }
    var transport by remember(request) { mutableStateOf<RemotePlayerTransport?>(null) }
    var ready by remember(request) { mutableStateOf(request !is PlaybackRequest.Remote) }
    LaunchedEffect(request) {
        val remote = request as? PlaybackRequest.Remote ?: return@LaunchedEffect
        var session: PlayerCacheSession? = null
        try {
            withContext(Dispatchers.IO) {
                session = runCatching { PlayerMediaCache.open(context, remote.url + "|" + remote.userAgent) }.getOrNull()
            }
            transport = RemotePlayerTransport.create(context, remote, session)
            ready = true
            kotlinx.coroutines.awaitCancellation()
        } finally { session?.close() }
    }
    if (ready && (localPlaylistRequest == null ||
            (activeLocalRequest == localPlaylistRequest && retainedLocalSession.value != null))) {
        PlayerContent(request, playlist, onSelectPlaylistEntry, onPlayPrevious, onPlayNext,
            onDownload, onOpenSourcePage, recordInLibrary, onDismiss, transport, orientationState,
            retainedLocalSession.value.takeIf { localPlaylistRequest != null })
    } else {
        BackHandler(onBack = onDismiss)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerLoadingSpinner(Modifier.align(Alignment.Center))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))) {
                Text("Close")
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun PlayerContent(
    request: PlaybackRequest,
    playlist: List<LibraryEntry> = emptyList(),
    onSelectPlaylistEntry: ((LibraryEntry) -> Unit)? = null,
    onPlayPrevious: (() -> Unit)? = null,
    onPlayNext: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onOpenSourcePage: (() -> Unit)? = null,
    recordInLibrary: Boolean = true,
    onDismiss: () -> Unit,
    remoteTransport: RemotePlayerTransport?,
    orientationState: PlayerOrientationState,
    retainedSession: PlaybackSession?
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val inPip = (activity as? MainActivity)?.playerInPictureInPicture == true
    val configuration = LocalConfiguration.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val viewportHeight = with(LocalDensity.current) { viewport.height.toDp() }
    val motion = LocalMotionPolicy.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(request) { mutableStateOf<PlayerErrorPresentation?>(null) }
    var isPlaying by remember(request) { mutableStateOf(false) }
    var isBuffering by remember(request) { mutableStateOf(true) }
    var hasRenderedFirstFrame by remember(request) { mutableStateOf(false) }
    var playbackEnded by remember(request) { mutableStateOf(false) }
    var isScrubbing by remember(request) { mutableStateOf(false) }
    var playbackSpeed by remember(request) { mutableFloatStateOf(1f) }
    var audioOnly by remember(request) { mutableStateOf(false) }
    val backgroundPlaybackEnabled = com.miowser.app.settings.AppSettings.current.backgroundPlaybackEnabled
    var holdToSpeedActive by remember(request) { mutableStateOf(false) }
    var speedBeforeHold by remember(request) { mutableFloatStateOf(1f) }
    var wasPlayingBeforeHold by remember(request) { mutableStateOf(false) }
    var scaleMode by remember(request) {
        mutableStateOf(PlayerScaleMode.entries.firstOrNull {
            it.name == PlayerDisplayPreferenceStore.scaleMode(context)
        } ?: PlayerScaleMode.FIT)
    }
    var playingQualityHeight by remember(request) { mutableStateOf<Int?>(null) }
    var controlsVisible by remember(request) { mutableStateOf(true) }
    val pipTransitionActive = remember { mutableStateOf(false) }
    LaunchedEffect(inPip) { if (!inPip) pipTransitionActive.value = false }
    var controlsLocked by remember(request) { mutableStateOf(false) }
    var controlsActivity by remember(request) { mutableIntStateOf(0) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var scaleMenuExpanded by remember { mutableStateOf(false) }
    var showAdjustments by remember { mutableStateOf(false) }
    val overlays = remember(request) { PlayerOverlayState() }
    val latestOverlays = rememberUpdatedState(overlays)
    val focusScope = rememberCoroutineScope()
    val playlistFocus = remember(request) { FocusRequester() }
    val playlistTriggerAvailable = rememberUpdatedState(playlist.isNotEmpty() && onSelectPlaylistEntry != null)
    val subtitleFocus = remember(request) { FocusRequester() }
    fun openOverlay(panel: PlayerOverlay) {
        speedMenuExpanded = false
        scaleMenuExpanded = false
        showAdjustments = false
        overlays.open(panel)
        controlsVisible = true
        controlsActivity++
    }
    var trackPanel by remember(request) { mutableStateOf<PlayerTrackKind?>(null) }
    var inspectingSubtitle by remember(request) { mutableStateOf<DetectedMedia?>(null) }
    var subtitleStyle by remember { mutableStateOf(PlayerSubtitleStyleStore.load(context)) }
    var subtitleAdjustAnchorMs by remember(request) { mutableStateOf<Long?>(null) }
    var resumeAfterSubtitleAdjust by remember(request) { mutableStateOf(false) }
    var subtitleNotice by remember(request) { mutableStateOf<String?>(null) }
    var subtitleNoticeRevision by remember(request) { mutableIntStateOf(0) }
    fun showSubtitleNotice(message: String) {
        subtitleNotice = message
        subtitleNoticeRevision++
    }
    LaunchedEffect(subtitleNoticeRevision) {
        if (subtitleNotice != null) {
            delay(2_500)
            subtitleNotice = null
        }
    }
    val latestStyleForSave by rememberUpdatedState(subtitleStyle)
    LaunchedEffect(subtitleStyle) {
        delay(250)
        PlayerSubtitleStyleStore.save(context, subtitleStyle)
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) PlayerSubtitleStyleStore.save(context, latestStyleForSave)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            PlayerSubtitleStyleStore.save(context, latestStyleForSave)
        }
    }
    val subtitleTypeface = remember(context, subtitleStyle.font) {
        captionFontResource(subtitleStyle.font)?.let { ResourcesCompat.getFont(context, it) }
    }
    var trackSnapshot by remember(request) { mutableStateOf(PlayerTrackSnapshot()) }
    var gestureFeedback by remember(request) { mutableStateOf<PlayerGestureFeedback?>(null) }
    var gestureFeedbackActivity by remember(request) { mutableIntStateOf(0) }
    var libraryRecorded by remember(request) { mutableStateOf(false) }
    var lastSavedLibraryPositionMs by remember(request) { mutableLongStateOf(-1L) }
    var playerView by remember(request) { mutableStateOf<PlayerView?>(null) }
    var pageSubtitleView by remember(request) { mutableStateOf<SubtitleView?>(null) }
    var midpointCaptureRequested by remember(request) { mutableStateOf(false) }
    var localMidpointExtractionAttempted by remember(request) { mutableStateOf(false) }
    var fallbackCaptureRequested by remember(request) { mutableStateOf(false) }
    var leavingForSourcePage by remember(request) { mutableStateOf(false) }
    val libraryIdentityKey = remember(request) { request.toLibraryEntry().identityKey }
    val nextAtEnd by rememberUpdatedState(onPlayNext)
    LaunchedEffect(request, playbackEnded) {
        if (playbackEnded) nextAtEnd?.invoke()
    }
    var trackPreferences by remember(request, libraryIdentityKey) {
        mutableStateOf(PlayerTrackPreferenceStore.load(context, libraryIdentityKey))
    }
    val effectiveSubtitleStyle = subtitleStyle.copy(
        delayMs = trackPreferences.subtitleDelayMs ?: subtitleStyle.delayMs)
    var restoredTrackKinds by remember(request) { mutableStateOf(emptySet<PlayerTrackKind>()) }
    var attachedExternalSubtitleUrl by remember(request) {
        mutableStateOf((request as? PlaybackRequest.Remote)?.preferredSubtitleUrl
            .orEmpty().ifBlank { externalSubtitleUrlFromSelection(trackPreferences.subtitles).orEmpty() }
            .takeIf { url -> (request as? PlaybackRequest.Remote)?.externalSubtitles?.any { it.url == url } == true })
    }
    var externalSubtitleCues by remember(request) {
        mutableStateOf(emptyList<androidx.media3.extractor.text.CuesWithTiming>())
    }
    var externalSubtitleError by remember(request) { mutableStateOf<String?>(null) }
    var externalSubtitleLoadAttempt by remember(request) { mutableIntStateOf(0) }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val systemBarsController = remember(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    LaunchedEffect(controlsVisible, systemBarsController, inPip) {
        if (controlsVisible && !inPip) {
            systemBarsController?.show(WindowInsetsCompat.Type.systemBars())
        } else {
            systemBarsController?.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    DisposableEffect(systemBarsController) {
        onDispose { systemBarsController?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    LaunchedEffect(gestureFeedbackActivity) {
        if (gestureFeedback != null) {
            delay(900)
            gestureFeedback = null
        }
    }

    // A manual rotate request temporarily pins the requested direction. Once Android reports that
    // the turn completed, return to sensor rotation or re-lock the exact new orientation.
    LaunchedEffect(configuration.orientation, orientationState.pendingManualOrientation,
        orientationState.relockAfterManualRotation) {
        val target = orientationState.pendingManualOrientation ?: return@LaunchedEffect
        if (configuration.orientation == target) {
            delay(300)
            activity?.requestedOrientation = if (orientationState.relockAfterManualRotation) {
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
            orientationState.pendingManualOrientation = null
            orientationState.relockAfterManualRotation = false
        }
    }

    val session = retainedSession ?: remember(request) {
        PlaybackSession.create(context, request, remoteTransport, attachedExternalSubtitleUrl != null)
    }
    val exoPlayer = session.player

    DisposableEffect(exoPlayer) {
        PlayerPlaybackService.attach(context, exoPlayer, request.title)
        onDispose { PlayerPlaybackService.detach(context, exoPlayer) }
    }
    LaunchedEffect(exoPlayer, request.title) {
        PlayerPlaybackService.updateTitle(exoPlayer, request.title)
    }
    LaunchedEffect(exoPlayer, audioOnly) {
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, audioOnly)
            .build()
        PlayerPlaybackService.setAudioOnly(exoPlayer, audioOnly)
    }
    LaunchedEffect(inPip) {
        if (inPip) {
            overlays.clear()
            speedMenuExpanded = false
            scaleMenuExpanded = false
            trackPanel = null
            showAdjustments = false
            inspectingSubtitle = null
        }
    }

    fun finishSubtitleAdjustment() {
        if (subtitleAdjustAnchorMs == null) return
        subtitleAdjustAnchorMs = null
        if (resumeAfterSubtitleAdjust) exoPlayer.play()
        resumeAfterSubtitleAdjust = false
    }

    fun beginSubtitleAdjustment() {
        if (externalSubtitleCues.none { it.cues.isNotEmpty() } || attachedExternalSubtitleUrl == null) return
        resumeAfterSubtitleAdjust = exoPlayer.playWhenReady
        subtitleAdjustAnchorMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        exoPlayer.pause()
        showSubtitleNotice("Adjusting subtitles: tap the matching line")
    }

    fun alignSubtitleToCue(cueStartTimeUs: Long) {
        val anchorMs = subtitleAdjustAnchorMs ?: return
        val delayMs = subtitleDelayForCue(anchorMs, cueStartTimeUs)
        trackPreferences = trackPreferences.copy(subtitleDelayMs = delayMs)
        PlayerTrackPreferenceStore.save(context, libraryIdentityKey, trackPreferences)
        finishSubtitleAdjustment()
        showSubtitleNotice("Subtitle delay set to ${delayMs} ms")
    }

    fun updateSubtitleStyle(updated: PlayerSubtitleStyle) {
        if (updated.delayMs != effectiveSubtitleStyle.delayMs) {
            trackPreferences = trackPreferences.copy(subtitleDelayMs = updated.delayMs)
            PlayerTrackPreferenceStore.save(context, libraryIdentityKey, trackPreferences)
        }
        subtitleStyle = updated.copy(delayMs = subtitleStyle.delayMs)
    }

    LaunchedEffect(overlays.target, trackPanel, subtitleAdjustAnchorMs) {
        if (subtitleAdjustAnchorMs != null && overlays.target != PlayerOverlay.SUBTITLES &&
            trackPanel != PlayerTrackKind.SUBTITLE) finishSubtitleAdjustment()
    }

    val saveLibraryProgress: (Boolean) -> Unit = { force ->
        if (libraryRecorded) {
            val currentPosition = exoPlayer.currentPosition.coerceAtLeast(0L)
            val currentDuration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            if (force || kotlin.math.abs(currentPosition - lastSavedLibraryPositionMs) >= LIBRARY_PROGRESS_MIN_DELTA_MS) {
                MediaLibrary.updateProgress(
                    context = context,
                    identityKey = libraryIdentityKey,
                    positionMs = currentPosition,
                    durationMs = currentDuration
                )
                lastSavedLibraryPositionMs = currentPosition
            }
        }
    }

    fun captureFallbackFrame() {
        if (!recordInLibrary || fallbackCaptureRequested) return
        val existing = MediaLibrary.entries.firstOrNull { it.identityKey == libraryIdentityKey }?.thumbnailUrl
        if (LibraryThumbnailStore.isAvailable(existing)) return
        val view = playerView ?: return
        fallbackCaptureRequested = true
        LibraryThumbnailStore.captureFirstFrame(context, view, libraryIdentityKey) { uri ->
            if (uri != null) {
                val current = MediaLibrary.entries.firstOrNull { it.identityKey == libraryIdentityKey }?.thumbnailUrl
                if (!LibraryThumbnailStore.isMiddleFrame(current)) {
                    MediaLibrary.updateThumbnail(context, libraryIdentityKey, uri)
                }
            } else {
                fallbackCaptureRequested = false
            }
        }
    }

    LaunchedEffect(request, hasRenderedFirstFrame, playerView) {
        if (!hasRenderedFirstFrame || playerView == null) return@LaunchedEffect
        // Avoid the decoder's first transitional/black frame. This uses the existing surface and
        // is cancelled automatically when the playlist item changes.
        delay(750)
        captureFallbackFrame()
    }

    DisposableEffect(exoPlayer, request) {
        fun recordReadyMedia() {
            if (!recordInLibrary || leavingForSourcePage) return
            val readyDuration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            if (!libraryRecorded) {
                val entry = MediaLibrary.recordOpened(
                    context,
                    request.toLibraryEntry(durationMs = readyDuration).copy(
                        qualityHeight = playingQualityHeight,
                        selectedSubtitle = (request as? PlaybackRequest.Remote)?.externalSubtitles
                            ?.firstOrNull { it.url == attachedExternalSubtitleUrl }?.toLibrarySubtitle()
                    )
                )
                libraryRecorded = true
                lastSavedLibraryPositionMs = entry.positionMs
            }
        }

        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo, reason: Int) { remoteTransport?.prefetcher?.cancel() }
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // A network rebuffer also changes isPlaying to false. Persist only an intentional
                // pause here; lifecycle pause, end, and disposal have their own forced saves.
                if (!playing && !exoPlayer.playWhenReady) saveLibraryProgress(true)
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (isBuffering) remoteTransport?.prefetcher?.cancel()
                playbackEnded = state == Player.STATE_ENDED
                if (state == Player.STATE_READY) {
                    recordReadyMedia()
                } else if (state == Player.STATE_ENDED) {
                    saveLibraryProgress(true)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                remoteTransport?.prefetcher?.cancel()
                trackSnapshot = buildPlayerTrackSnapshot(tracks)
            }

            override fun onPlaybackParametersChanged(parameters: androidx.media3.common.PlaybackParameters) {
                playbackSpeed = parameters.speed
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                val height = videoQualityHeight(videoSize.width, videoSize.height) ?: return
                playingQualityHeight = height
                if (recordInLibrary && libraryRecorded && !leavingForSourcePage) {
                    MediaLibrary.updateQuality(context, libraryIdentityKey, height)
                }
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                recordReadyMedia()
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = playerErrorPresentation(error.errorCode)
                controlsVisible = true
                com.miowser.app.diagnostics.SafeLog.debug(PLAYER_TAG, "Playback failed (${error.errorCodeName}): ${error.message}", error)
            }
        }
        exoPlayer.addListener(listener)
        com.miowser.app.diagnostics.DevResourceDiagnostics.listenerAdded()
        trackSnapshot = buildPlayerTrackSnapshot(exoPlayer.currentTracks)
        playbackSpeed = exoPlayer.playbackParameters.speed
        playingQualityHeight = videoQualityHeight(exoPlayer.videoSize.width, exoPlayer.videoSize.height)
        if (exoPlayer.playbackState == Player.STATE_READY) recordReadyMedia()
        onDispose {
            saveLibraryProgress(true)
            exoPlayer.removeListener(listener)
            com.miowser.app.diagnostics.DevResourceDiagnostics.listenerRemoved()
            if (retainedSession == null) session.close()
        }
    }

    // Prefer a lightweight page poster over an early playback frame. The poster is copied into
    // Miowser's bounded cache; an eventual midpoint frame has higher replacement priority.
    LaunchedEffect(request, libraryRecorded, recordInLibrary) {
        val remote = request as? PlaybackRequest.Remote ?: return@LaunchedEffect
        val poster = remote.posterUrl?.takeIf { it.startsWith("https://", true) || it.startsWith("http://", true) }
            ?: return@LaunchedEffect
        if (!recordInLibrary || !libraryRecorded) return@LaunchedEffect
        val existing = MediaLibrary.entries.firstOrNull { it.identityKey == libraryIdentityKey }?.thumbnailUrl
        if (LibraryThumbnailStore.isMiddleFrame(existing)) return@LaunchedEffect
        val headers = buildMap {
            put("User-Agent", remote.userAgent)
            put("Referer", remote.requestReferer ?: remote.pageUrl)
            remote.posterCookies?.takeIf(String::isNotBlank)?.let { put("Cookie", it) }
        }
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            val cancel = LibraryThumbnailStore.cachePoster(
                context.applicationContext, poster, headers, libraryIdentityKey
            ) { uri ->
                if (continuation.isActive) {
                    if (uri != null) MediaLibrary.updateThumbnail(context.applicationContext, libraryIdentityKey, uri)
                    continuation.resumeWith(Result.success(Unit))
                }
            }
            continuation.invokeOnCancellation { cancel() }
        }
    }

    // Local files can be randomly accessed cheaply and reliably. Extract their exact midpoint on
    // the artwork worker; if the platform extractor refuses the file, active-decoder capture below
    // remains the fallback when playback reaches the middle.
    LaunchedEffect(request, libraryRecorded, hasRenderedFirstFrame, recordInLibrary) {
        val local = request as? PlaybackRequest.Local ?: return@LaunchedEffect
        if (!recordInLibrary || !libraryRecorded || !hasRenderedFirstFrame || localMidpointExtractionAttempted)
            return@LaunchedEffect
        val existing = MediaLibrary.entries.firstOrNull { it.identityKey == libraryIdentityKey }?.thumbnailUrl
        if (LibraryThumbnailStore.isMiddleFrame(existing)) return@LaunchedEffect
        localMidpointExtractionAttempted = true
        midpointCaptureRequested = true
        delay(1_000)
        kotlinx.coroutines.suspendCancellableCoroutine<Unit> { continuation ->
            val cancel = LibraryThumbnailStore.captureLocalMiddleFrame(
                context.applicationContext,
                local.uri,
                durationMs = exoPlayer.knownDurationMs(),
                identityKey = libraryIdentityKey
            ) { uri ->
                if (continuation.isActive) {
                    if (uri != null) MediaLibrary.updateThumbnail(context.applicationContext, libraryIdentityKey, uri)
                    else midpointCaptureRequested = false
                    continuation.resumeWith(Result.success(Unit))
                }
            }
            continuation.invokeOnCancellation { cancel() }
        }
    }

    val backgroundAudioOnly = rememberUpdatedState(audioOnly)
    val backgroundPlaybackAllowed = rememberUpdatedState(backgroundPlaybackEnabled)
    DisposableEffect(lifecycleOwner, exoPlayer) {
        var resumeWhenActive = false
        var pauseControlRevision = 0L
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    val enteringOrInPip = pipTransitionActive.value ||
                        (activity as? MainActivity)?.isInPictureInPictureMode == true
                    val keepPlaying = enteringOrInPip ||
                        (backgroundAudioOnly.value && backgroundPlaybackAllowed.value)
                    resumeWhenActive = !keepPlaying && exoPlayer.playWhenReady
                    pauseControlRevision = PlayerPlaybackService.externalControlRevision
                    if (!keepPlaying) exoPlayer.pause()
                    saveLibraryProgress(true)
                }
                Lifecycle.Event.ON_RESUME -> {
                    if (resumeWhenActive &&
                        pauseControlRevision == PlayerPlaybackService.externalControlRevision) exoPlayer.play()
                    resumeWhenActive = false
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    DisposableEffect(remoteTransport, lifecycleOwner) {
        val stop = { remoteTransport?.prefetcher?.cancel(); Unit }
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) stop()
        }
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        var lastTransport: Boolean? = null
        val callback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onLost(network: android.net.Network) { stop() }
            override fun onCapabilitiesChanged(network: android.net.Network, capabilities: android.net.NetworkCapabilities) {
                val wifi = capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                if (lastTransport != null && wifi != lastTransport) stop()
                lastTransport = wifi
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { manager?.unregisterNetworkCallback(callback) }
            stop()
        }
    }
    LaunchedEffect(exoPlayer, remoteTransport) {
        val prefetcher = remoteTransport?.prefetcher ?: return@LaunchedEffect
        while (isActive) {
            val ready = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) &&
                exoPlayer.playbackState == Player.STATE_READY && exoPlayer.isCurrentMediaItemSeekable
            if (ready) {
                val snapshot = PlayerStreamSnapshot(
                    positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                    durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L,
                    bufferedAheadMs = (exoPlayer.bufferedPosition - exoPlayer.currentPosition).coerceAtLeast(0L),
                    videoHeight = exoPlayer.videoFormat?.height ?: 0,
                    videoBitrate = exoPlayer.videoFormat?.bitrate ?: 0,
                    audioLanguage = exoPlayer.audioFormat?.language
                )
                val connection = playerConnection(context)
                val fetched = withContext(Dispatchers.IO) { prefetcher.pass(snapshot, connection) }
                delay(if (fetched) 250L else 5_000L)
            } else delay(5_000L)
        }
    }

    // Page-detected VTT is fetched through the same authenticated path as Inspect. Rendering it
    // outside Media3's merged source avoids losing the track on HLS/DASH manifest transitions.
    LaunchedEffect(request, attachedExternalSubtitleUrl, externalSubtitleLoadAttempt) {
        externalSubtitleCues = emptyList()
        externalSubtitleError = null
        val remote = request as? PlaybackRequest.Remote ?: return@LaunchedEffect
        val subtitle = remote.externalSubtitles.firstOrNull { it.url == attachedExternalSubtitleUrl }
            ?: return@LaunchedEffect
        runCatching {
            val cached = if (recordInLibrary) LibrarySubtitleStore.read(context, libraryIdentityKey, subtitle.url) else null
            val cachedCues = cached?.let { withContext(Dispatchers.Default) { parseExternalSubtitle(it) } }
            if (!cachedCues.isNullOrEmpty()) cachedCues to null else {
                val text = loadSubtitleForPlayback(subtitle, remote.userAgent)
                withContext(Dispatchers.Default) { parseExternalSubtitle(text) } to text
            }
        }.onSuccess { (cues, fetchedText) ->
            if (cues.isEmpty()) externalSubtitleError = "No timed cues were found in this subtitle."
            else {
                externalSubtitleCues = cues
                if (recordInLibrary && fetchedText != null) {
                    LibrarySubtitleStore.save(context, libraryIdentityKey, subtitle.url, fetchedText)
                }
            }
        }.onFailure { error ->
            if (error is kotlinx.coroutines.CancellationException) throw error
            externalSubtitleError = error.message ?: "Could not load this subtitle."
            com.miowser.app.diagnostics.SafeLog.warning(PLAYER_TAG, "Detected subtitle failed to load", error)
        }
    }

    // Remote streams reuse the active decoder so artwork never starts another network stream or
    // codec. Local files reach this only if their cheap exact-midpoint extractor failed.
    LaunchedEffect(exoPlayer, hasRenderedFirstFrame, playerView, midpointCaptureRequested, lifecycleOwner) {
        if (!recordInLibrary || !hasRenderedFirstFrame || midpointCaptureRequested)
            return@LaunchedEffect
        val existing = MediaLibrary.entries.firstOrNull { it.identityKey == libraryIdentityKey }?.thumbnailUrl
        if (LibraryThumbnailStore.isMiddleFrame(existing)) return@LaunchedEffect
        val view = playerView ?: return@LaunchedEffect
        // Media maintenance reads the player directly; it never publishes a UI timeline tick.
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            observePlayerClock(exoPlayer, intervalMs = 500L) {
                val duration = exoPlayer.knownDurationMs()
                val midpoint = duration / 2L
                if (!midpointCaptureRequested && duration > 0L && exoPlayer.currentPosition >= midpoint) {
                    midpointCaptureRequested = true
                    LibraryThumbnailStore.captureFirstFrame(
                        context = context, playerView = view, identityKey = libraryIdentityKey, middle = true
                    ) { uri ->
                        if (uri != null) MediaLibrary.updateThumbnail(context, libraryIdentityKey, uri)
                    }
                }
            }
        }
    }

    fun selectTrack(kind: PlayerTrackKind, selection: PlayerTrackSelection) {
        if (!applyPlayerTrackSelection(exoPlayer, trackSnapshot, kind, selection)) return
        if (kind == PlayerTrackKind.SUBTITLE) {
            finishSubtitleAdjustment()
            attachedExternalSubtitleUrl = null
            if (recordInLibrary) MediaLibrary.updateSubtitle(context, libraryIdentityKey, null)
        }
        val updatedPreferences = trackPreferences.withSelection(kind, selection)
        trackPreferences = updatedPreferences
        PlayerTrackPreferenceStore.save(context, libraryIdentityKey, updatedPreferences)
        restoredTrackKinds = restoredTrackKinds + kind
        if (kind != PlayerTrackKind.SUBTITLE) trackPanel = null
        controlsVisible = true
        controlsActivity++
    }

    fun selectDetectedSubtitle(subtitle: DetectedMedia) {
        finishSubtitleAdjustment()
        val selection = PlayerTrackSelection(PlayerTrackMode.SPECIFIC,
            externalSubtitleSelectionKey(subtitle.url))
        val updatedPreferences = trackPreferences.withSelection(PlayerTrackKind.SUBTITLE, selection)
        trackPreferences = updatedPreferences
        PlayerTrackPreferenceStore.save(context, libraryIdentityKey, updatedPreferences)
        attachedExternalSubtitleUrl = subtitle.url
        if (recordInLibrary) MediaLibrary.updateSubtitle(context, libraryIdentityKey, subtitle.toLibrarySubtitle())
        externalSubtitleLoadAttempt++
        applyPlayerTrackSelection(exoPlayer, trackSnapshot, PlayerTrackKind.SUBTITLE,
            PlayerTrackSelection(PlayerTrackMode.OFF))
        restoredTrackKinds = restoredTrackKinds + PlayerTrackKind.SUBTITLE
        // Keep the subtitle workspace open so selection immediately fills its cue preview.
        controlsVisible = true
        controlsActivity++
    }

    // Track groups may arrive after playback starts (especially live HLS/DASH manifests). Restore
    // each kind independently once it actually exists, instead of taking one early empty snapshot.
    LaunchedEffect(exoPlayer, trackSnapshot, attachedExternalSubtitleUrl) {
        var restored = restoredTrackKinds
        PlayerTrackKind.entries.forEach { kind ->
            if (kind == PlayerTrackKind.SUBTITLE && attachedExternalSubtitleUrl != null) return@forEach
            if (kind in restored || trackSnapshot.options(kind).isEmpty()) return@forEach
            val selection = trackPreferences.selection(kind)
            if (applyPlayerTrackSelection(exoPlayer, trackSnapshot, kind, selection)) {
                restored = restored + kind
            }
        }
        restoredTrackKinds = restored
    }

    LaunchedEffect(
        controlsVisible,
        isPlaying,
        controlsLocked,
        speedMenuExpanded,
        scaleMenuExpanded,
        overlays.isPresent,
        showAdjustments,
        trackPanel,
        isBuffering,
        playbackError,
        controlsActivity,
        isScrubbing,
        lifecycleOwner
    ) {
        if (
            controlsVisible && isPlaying && !isBuffering && !isScrubbing && playbackError == null &&
            !speedMenuExpanded && !scaleMenuExpanded && !overlays.isPresent && !showAdjustments && trackPanel == null
        ) {
            lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                delay(CONTROLS_TIMEOUT_MS)
                if (exoPlayer.isPlaying && exoPlayer.playWhenReady) {
                    controlsVisible = false
                    speedMenuExpanded = false
                    scaleMenuExpanded = false
                }
            }
        }
    }

    val togglePlayback: () -> Boolean = {
        if (exoPlayer.playWhenReady) {
            exoPlayer.pause()
            controlsActivity++ // Cancel a pending auto-hide if pause lands on its timeout.
            false
        } else {
            if (playbackEnded) exoPlayer.seekTo(0L)
            exoPlayer.play()
            true
        }
    }

    val startHoldToSpeed: () -> Boolean = {
        if (holdToSpeedActive || playbackError != null || playbackEnded) {
            false
        } else {
            speedBeforeHold = exoPlayer.playbackParameters.speed
            wasPlayingBeforeHold = exoPlayer.playWhenReady
            exoPlayer.setPlaybackSpeed(2f)
            if (!wasPlayingBeforeHold) exoPlayer.play()
            playbackSpeed = 2f
            holdToSpeedActive = true
            true
        }
    }
    val stopHoldToSpeed: () -> Unit = {
        if (holdToSpeedActive) {
            exoPlayer.setPlaybackSpeed(speedBeforeHold)
            if (!wasPlayingBeforeHold) exoPlayer.pause()
            playbackSpeed = speedBeforeHold
            holdToSpeedActive = false
        }
    }

    PlayerInteractionLayers(
        blockPlayerInput = overlays.isPresent,
        modifier = Modifier.background(Color.Black).onSizeChanged { viewport = it }
            .testTag(if (hasRenderedFirstFrame) "player_first_frame" else "player_loading"),
        playerContent = {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = if (audioOnly) null else exoPlayer
                    useController = false
                    keepScreenOn = !audioOnly && exoPlayer.playWhenReady && playbackError == null
                    resizeMode = scaleMode.resizeMode
                    applyPlayerSubtitleStyle(this, effectiveSubtitleStyle, subtitleTypeface)
                    overlayFrameLayout?.let { overlay ->
                        val layer = SubtitleView(ctx).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            visibility = View.GONE
                        }
                        overlay.addView(layer)
                        pageSubtitleView = layer
                    }
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerView = this
                }
            },
            update = { view ->
                view.importantForAccessibility = if (overlays.isPresent)
                    View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS else View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                view.player = if (audioOnly) null else exoPlayer
                view.resizeMode = scaleMode.resizeMode
                applyPlayerSubtitleStyle(view, effectiveSubtitleStyle, subtitleTypeface)
                view.subtitleView?.visibility = if (attachedExternalSubtitleUrl == null)
                    View.VISIBLE else View.INVISIBLE
                pageSubtitleView?.visibility = if (attachedExternalSubtitleUrl == null)
                    View.GONE else View.VISIBLE
                view.keepScreenOn = !audioOnly && exoPlayer.playWhenReady && playbackError == null
            }
        )
        if (!audioOnly) ExternalSubtitleLayer(exoPlayer, pageSubtitleView, attachedExternalSubtitleUrl,
            externalSubtitleCues, effectiveSubtitleStyle, subtitleTypeface)

        if (!inPip) {

        // This full-video gesture surface sits below the visible controls, so buttons and the
        // timeline keep priority while gestures on the video itself remain available.
        PlayerGestureLayer(
            controlsLocked = controlsLocked,
            controlsVisible = controlsVisible,
            isLandscape = isLandscape,
            player = exoPlayer,
            onSingleTap = {
                controlsVisible = !controlsVisible
                if (controlsVisible) controlsActivity++
            },
            onTogglePlayPause = togglePlayback,
            onStartHoldToSpeed = startHoldToSpeed,
            onStopHoldToSpeed = stopHoldToSpeed,
            onSeekTo = { target ->
                exoPlayer.seekTo(target)
            },
            onFeedback = { feedback ->
                gestureFeedback = feedback
                gestureFeedbackActivity++
            },
            onBrightnessCommitted = { PlayerDisplayPreferenceStore.saveBrightness(context, it) }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            // One visibility transition fades the controls and both scrims together.
            enter = fadeIn(motion.spec(MotionTokens.controlsEnter)),
            exit = fadeOut(motion.spec(MotionTokens.controlsExit)),
            modifier = Modifier.fillMaxSize()
        ) {
            CompositionLocalProvider(LocalPlayerControlsActive provides controlsVisible) {
            Box(modifier = Modifier.fillMaxSize().playerFocusGate(!controlsVisible)) {
                PlayerChromeScrims(viewportHeight, controlsLocked)
                if (controlsLocked) {
                    RoundPlayerButton(
                        icon = Icons.Rounded.LockOpen,
                        contentDescription = "Unlock controls",
                        onClick = { controlsLocked = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(
                                WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top)
                            )
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.End))
                            .padding(20.dp)
                    )
                } else {
                    PlayerTopBar(
                        isLandscape = isLandscape,
                        rotationLocked = orientationState.locked,
                        playbackSpeed = playbackSpeed,
                        onRotate = {
                            val target = if (isLandscape) {
                                Configuration.ORIENTATION_PORTRAIT
                            } else {
                                Configuration.ORIENTATION_LANDSCAPE
                            }
                            orientationState.pendingManualOrientation = target
                            orientationState.relockAfterManualRotation = orientationState.locked
                            activity?.requestedOrientation = if (target == Configuration.ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        },
                        onToggleRotationLock = {
                            orientationState.pendingManualOrientation = null
                            orientationState.relockAfterManualRotation = false
                            val shouldLock = !orientationState.locked
                            orientationState.locked = shouldLock
                            activity?.requestedOrientation = if (shouldLock) {
                                ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            }
                        },
                        onDownload = onDownload,
                        onPip = if (!audioOnly && playbackError == null && activity is MainActivity &&
                            context.packageManager.hasSystemFeature(
                                android.content.pm.PackageManager.FEATURE_PICTURE_IN_PICTURE)) ({
                            val host = activity
                            pipTransitionActive.value = true
                            val size = exoPlayer.videoSize
                            if (!host.enterPlayerPictureInPicture(
                                size.width.takeIf { it > 0 } ?: 16,
                                size.height.takeIf { it > 0 } ?: 9
                            )) pipTransitionActive.value = false
                        }) else null,
                        audioOnly = audioOnly,
                        onToggleAudioOnly = {
                            audioOnly = !audioOnly
                            controlsVisible = true
                            controlsActivity++
                        },
                        onOpenPlaylist = if (playlist.isNotEmpty() && onSelectPlaylistEntry != null) ({
                            trackPanel = null
                            openOverlay(PlayerOverlay.PLAYLIST)
                        }) else null,
                        playlistFocus = playlistFocus,
                        playlistPosition = playlist.indexOfFirst { it.identityKey == libraryIdentityKey } + 1,
                        playlistSize = playlist.size,
                        onLock = { controlsLocked = true },
                        onDismiss = onDismiss,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    PlayerCenterControls(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        playlistActive = playlist.isNotEmpty(),
                        onPreviousVideo = onPlayPrevious,
                        onNextVideo = onPlayNext,
                        onReplay = {
                            val origin = exoPlayer.currentPosition
                            val target = (origin - SEEK_INCREMENT_MS).coerceAtLeast(0L)
                            exoPlayer.seekTo(target)
                            gestureFeedback = PlayerGestureFeedback(PlayerGestureKind.SEEK_BACKWARD,
                                formatPlayerTime(target), seekDeltaMs = target - origin)
                            gestureFeedbackActivity++
                            controlsVisible = true
                            controlsActivity++
                        },
                        onPlayPause = {
                            togglePlayback()
                            controlsVisible = true
                            controlsActivity++
                        },
                        onForward = {
                            val origin = exoPlayer.currentPosition
                            val target = (origin + SEEK_INCREMENT_MS).let {
                                val duration = exoPlayer.knownDurationMs()
                                if (duration > 0L) it.coerceAtMost(duration) else it
                            }
                            exoPlayer.seekTo(target)
                            gestureFeedback = PlayerGestureFeedback(PlayerGestureKind.SEEK_FORWARD,
                                formatPlayerTime(target), seekDeltaMs = target - origin)
                            gestureFeedbackActivity++
                            controlsVisible = true
                            controlsActivity++
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    PlayerBottomControls(
                        request = request,
                        isLandscape = isLandscape,
                        qualityLabel = videoQualityLabel(playingQualityHeight),
                        player = exoPlayer,
                        timelineVisible = controlsVisible && !controlsLocked && trackPanel == null &&
                            !showAdjustments && overlays.rendered != PlayerOverlay.SUBTITLES,
                        scaleMode = scaleMode,
                        playbackSpeed = playbackSpeed,
                        onScrubbingChanged = { scrubbing ->
                            isScrubbing = scrubbing
                            controlsActivity++
                            if (scrubbing) controlsVisible = true
                        },
                        onScaleMenuChange = { if (!it || !overlays.isPresent) scaleMenuExpanded = it },
                        scaleMenuExpanded = scaleMenuExpanded,
                        onScaleSelected = { selected ->
                            scaleMode = selected
                            PlayerDisplayPreferenceStore.saveScaleMode(context, selected.name)
                            scaleMenuExpanded = false
                            controlsVisible = true
                            controlsActivity++
                        },
                        onAdjustmentsRequested = {
                            if (!overlays.isPresent) { speedMenuExpanded = false; showAdjustments = true }
                        },
                        onSpeedMenuChange = { if (!it || !overlays.isPresent) speedMenuExpanded = it },
                        speedMenuExpanded = speedMenuExpanded,
                        onSpeedSelected = { speed ->
                            exoPlayer.setPlaybackSpeed(speed)
                            speedMenuExpanded = false
                            controlsVisible = true
                            controlsActivity++
                        },
                        subtitleFocus = subtitleFocus,
                        subtitleLabel = playerTrackButtonLabel(
                            PlayerTrackKind.SUBTITLE,
                            trackPreferences.subtitles,
                            trackSnapshot
                        ),
                        subtitlesEnabled = trackSnapshot.subtitles.isNotEmpty() ||
                            (request as? PlaybackRequest.Remote)?.externalSubtitles?.isNotEmpty() == true,
                        onOpenSubtitles = {
                            trackPanel = null
                            openOverlay(PlayerOverlay.SUBTITLES)
                        },
                        audioLabel = playerTrackButtonLabel(
                            PlayerTrackKind.AUDIO,
                            trackPreferences.audio,
                            trackSnapshot
                        ),
                        audioEnabled = trackSnapshot.audio.size > 1,
                        onOpenAudio = {
                            if (!overlays.isPresent) {
                                trackPanel = PlayerTrackKind.AUDIO
                                controlsActivity++
                            }
                        },
                        sourceLabel = playerTrackButtonLabel(
                            PlayerTrackKind.VIDEO,
                            trackPreferences.video,
                            trackSnapshot
                        ),
                        sourcesEnabled = trackSnapshot.video.size > 1,
                        onOpenSources = {
                            if (!overlays.isPresent) {
                                trackPanel = PlayerTrackKind.VIDEO
                                controlsActivity++
                            }
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
            }
        }

        gestureFeedback?.let { feedback ->
            PlayerGestureFeedbackOverlay(
                feedback = feedback,
                modifier = when (feedback.kind) {
                    PlayerGestureKind.BRIGHTNESS -> Modifier.align(Alignment.CenterStart).padding(start = 28.dp)
                    PlayerGestureKind.VOLUME -> Modifier.align(Alignment.CenterEnd).padding(end = 28.dp)
                    PlayerGestureKind.SEEK_BACKWARD,
                    PlayerGestureKind.SEEK_FORWARD -> Modifier.align(Alignment.TopCenter)
                        .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                        .padding(top = if (isLandscape) 64.dp else 100.dp)
                    else -> Modifier.align(Alignment.Center)
                }
            )
        }

        if (holdToSpeedActive) {
            PlayerHoldSpeedIndicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top))
                    .padding(top = 16.dp)
            )
        }

        if (isBuffering && (!controlsVisible || controlsLocked) && playbackError == null) {
            PlayerLoadingSpinner(modifier = Modifier.align(Alignment.Center))
        }

        playbackError?.let { error ->
            PlayerErrorCard(
                error = error,
                onOpenSourcePage = if (request is PlaybackRequest.Remote && onOpenSourcePage != null) ({
                    leavingForSourcePage = true
                    exoPlayer.pause()
                    onOpenSourcePage()
                }) else null,
                onRetry = {
                    playbackError = null
                    isBuffering = true
                    playbackEnded = false
                    controlsVisible = true
                    controlsActivity++
                    exoPlayer.prepare()
                    exoPlayer.play()
                },
                onClose = onDismiss,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        } // Non-PiP video controls.
        },
        overlayContent = {
        // These controls stay outside the gate that isolates the video beneath a custom modal.
        if (!inPip) {

        if (showAdjustments) PlayerAdjustmentsSheet(activity, onDismiss = { showAdjustments = false })

        trackPanel?.let { kind ->
            PlayerTrackSelectionSheet(
                kind = kind,
                options = trackSnapshot.options(kind),
                detectedSubtitles = (request as? PlaybackRequest.Remote)?.externalSubtitles.orEmpty(),
                selection = trackPreferences.selection(kind),
                onSelect = { selection -> selectTrack(kind, selection) },
                onSelectDetectedSubtitle = ::selectDetectedSubtitle,
                onInspectSubtitle = { subtitle ->
                    trackPanel = null
                    inspectingSubtitle = subtitle
                },
                subtitleStyle = effectiveSubtitleStyle,
                attachedExternalSubtitleUrl = attachedExternalSubtitleUrl,
                externalSubtitleError = externalSubtitleError,
                externalSubtitleReady = externalSubtitleCues.isNotEmpty(),
                externalSubtitleCues = externalSubtitleCues,
                subtitleAdjustAnchorMs = subtitleAdjustAnchorMs,
                onBeginSubtitleAdjust = ::beginSubtitleAdjustment,
                onCancelSubtitleAdjust = ::finishSubtitleAdjustment,
                onAlignSubtitleToCue = ::alignSubtitleToCue,
                onSubtitleStyleChange = ::updateSubtitleStyle,
                onDismiss = { trackPanel = null }
            )
        }
        inspectingSubtitle?.let { subtitle ->
            SubtitleViewer(subtitle,
                (request as? PlaybackRequest.Remote)?.userAgent ?: "Miowser",
                onDismiss = {
                    inspectingSubtitle = null
                    openOverlay(PlayerOverlay.SUBTITLES)
                })
        }
        PlayerOverlayHost(overlays, onClosed = { panel ->
            val revision = overlays.revision
            focusScope.launch {
                withFrameNanos { }
                if (latestOverlays.value === overlays && !overlays.isPresent && overlays.revision == revision) {
                    when (panel) {
                        PlayerOverlay.PLAYLIST -> if (playlistTriggerAvailable.value) playlistFocus.requestFocus()
                        PlayerOverlay.SUBTITLES -> subtitleFocus.requestFocus()
                    }
                }
            }
        }) { panel, panelModifier, initialFocus ->
            when (panel) {
                PlayerOverlay.PLAYLIST -> PlayerPlaylistPanel(
                    entries = playlist,
                    currentKey = libraryIdentityKey,
                    onSelect = { entry -> overlays.dismiss(); onSelectPlaylistEntry?.invoke(entry) },
                    onPrevious = onPlayPrevious?.let { action -> ({ overlays.dismiss(); action() }) },
                    onNext = onPlayNext?.let { action -> ({ overlays.dismiss(); action() }) },
                    onDismiss = { overlays.dismiss() }, modifier = panelModifier, initialFocus = initialFocus)
                PlayerOverlay.SUBTITLES -> PlayerSubtitleWorkspaceSheet(
                    trackSnapshot.subtitles, (request as? PlaybackRequest.Remote)?.externalSubtitles.orEmpty(),
                    trackPreferences.subtitles, { selectTrack(PlayerTrackKind.SUBTITLE, it) },
                    ::selectDetectedSubtitle, effectiveSubtitleStyle, attachedExternalSubtitleUrl,
                    externalSubtitleError, externalSubtitleCues.isNotEmpty(), externalSubtitleCues,
                    subtitleAdjustAnchorMs, ::beginSubtitleAdjustment, ::finishSubtitleAdjustment,
                    ::alignSubtitleToCue,
                    ::updateSubtitleStyle, { overlays.dismiss() }, panelModifier, initialFocus)
            }
        }
        subtitleNotice?.let { message ->
            PlayerSubtitleNotice(message, Modifier.align(Alignment.BottomCenter))
        }
        }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSubtitleNotice(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .windowInsetsPadding(WindowInsets.navigationBarsIgnoringVisibility.only(WindowInsetsSides.Bottom))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .widthIn(max = 440.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        shadowElevation = 6.dp
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Subtitles, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

internal fun remotePlayerMediaItem(request: PlaybackRequest.Remote): MediaItem =
    MediaItem.Builder()
        .setUri(request.url)
        .setMimeType(request.mediaType.toExoMimeTypeHint())
        .build()

private fun captionFontResource(font: CaptionFontChoice): Int? = when (font) {
    CaptionFontChoice.SYSTEM -> null
    CaptionFontChoice.HELVETICA_LIKE -> R.font.subtitle_arimo
    CaptionFontChoice.ROBOTO -> R.font.subtitle_roboto
    CaptionFontChoice.AVENIR_LIKE -> R.font.subtitle_nunito_sans
    CaptionFontChoice.FREDOKA_ONE -> R.font.subtitle_fredoka_one
    CaptionFontChoice.LOBSTER -> R.font.subtitle_lobster
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun applyPlayerSubtitleStyle(view: PlayerView, style: PlayerSubtitleStyle, typeface: Typeface?) {
    view.subtitleView?.let { applyPlayerSubtitleStyle(it, style, typeface) }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
internal fun applyPlayerSubtitleStyle(view: SubtitleView, style: PlayerSubtitleStyle, typeface: Typeface?) {
    view.apply {
        setApplyEmbeddedStyles(false)
        setApplyEmbeddedFontSizes(false)
        setFractionalTextSize(0.0533f * style.size)
        setBottomPaddingFraction(style.bottomPadding)
        val preset = style.preset
        setStyle(CaptionStyleCompat(
            AndroidColor.argb(
                (style.textOpacity.coerceIn(10, 100) * 255 / 100),
                AndroidColor.red(style.resolvedTextColor()),
                AndroidColor.green(style.resolvedTextColor()),
                AndroidColor.blue(style.resolvedTextColor())
            ),
            when (preset) {
                CaptionVisualPreset.STANDARD -> AndroidColor.BLACK
                CaptionVisualPreset.WHITE_TRANSLUCENT,
                CaptionVisualPreset.YELLOW_TRANSLUCENT -> 0xAA000000.toInt()
                else -> AndroidColor.TRANSPARENT
            },
            AndroidColor.TRANSPARENT,
            when {
                style.resolvedOutlineEnabled() -> CaptionStyleCompat.EDGE_TYPE_OUTLINE
                preset == CaptionVisualPreset.WHITE_SHADOW ||
                    preset == CaptionVisualPreset.YELLOW_SHADOW -> CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW
                else -> CaptionStyleCompat.EDGE_TYPE_NONE
            },
            style.outlineColor,
            if (style.bold) Typeface.create(typeface ?: Typeface.DEFAULT, Typeface.BOLD) else typeface
        ))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerGestureLayer(
    controlsLocked: Boolean,
    controlsVisible: Boolean,
    isLandscape: Boolean,
    player: Player,
    onSingleTap: () -> Unit,
    onTogglePlayPause: () -> Boolean,
    onStartHoldToSpeed: () -> Boolean,
    onStopHoldToSpeed: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFeedback: (PlayerGestureFeedback) -> Unit,
    onBrightnessCommitted: (Float) -> Unit
) {
    PlayerGestureSurface(controlsLocked, controlsVisible, isLandscape,
        positionMs = { player.currentPosition }, durationMs = { player.knownDurationMs() },
        onSingleTap, onTogglePlayPause, onStartHoldToSpeed, onStopHoldToSpeed,
        onSeekTo, onFeedback, onBrightnessCommitted)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlayerGestureSurface(
    controlsLocked: Boolean,
    controlsVisible: Boolean,
    isLandscape: Boolean,
    positionMs: () -> Long,
    durationMs: () -> Long,
    onSingleTap: () -> Unit,
    onTogglePlayPause: () -> Boolean,
    onStartHoldToSpeed: () -> Boolean,
    onStopHoldToSpeed: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onFeedback: (PlayerGestureFeedback) -> Unit,
    onBrightnessCommitted: (Float) -> Unit,
    canSeek: Boolean = true,
    volumeEnabled: Boolean = true,
    onVolumeInteraction: () -> Unit = {}
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val density = LocalDensity.current
    val gestureEdgeInsetPx = with(density) { 24.dp.toPx() }
    val protectedTopPx = WindowInsets.statusBarsIgnoringVisibility.getTop(density).toFloat()
    val navigationBottomPx = WindowInsets.navigationBarsIgnoringVisibility.getBottom(density).toFloat()
    val audioManager = remember(context) { context.getSystemService(AudioManager::class.java) }
    val currentPosition by rememberUpdatedState(positionMs)
    val currentDuration by rememberUpdatedState(durationMs)
    val currentCanSeek by rememberUpdatedState(canSeek)
    val currentVolumeInteraction by rememberUpdatedState(onVolumeInteraction)
    val currentSingleTapHandler by rememberUpdatedState(onSingleTap)
    val currentToggleHandler by rememberUpdatedState(onTogglePlayPause)
    val currentStartHoldToSpeed by rememberUpdatedState(onStartHoldToSpeed)
    val currentStopHoldToSpeed by rememberUpdatedState(onStopHoldToSpeed)
    val currentSeekHandler by rememberUpdatedState(onSeekTo)
    val currentFeedbackHandler by rememberUpdatedState(onFeedback)
    val currentBrightnessCommitHandler by rememberUpdatedState(onBrightnessCommitted)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(controlsLocked, isLandscape, protectedTopPx, navigationBottomPx) {
                var holdStarted = false
                detectTapGestures(
                    onPress = {
                        try {
                            tryAwaitRelease()
                        } finally {
                            if (holdStarted) {
                                currentStopHoldToSpeed()
                                holdStarted = false
                            }
                        }
                    },
                    onLongPress = { offset ->
                        val protectedBottom = if (isLandscape) {
                            navigationBottomPx
                        } else {
                            maxOf(navigationBottomPx, size.height * 0.20f)
                        }
                        if (!controlsLocked && playerGesturePointAllowed(
                                offset.y, size.height.toFloat(), protectedTopPx, protectedBottom
                            )
                        ) {
                            holdStarted = currentStartHoldToSpeed()
                        }
                    },
                    onTap = { offset ->
                        val protectedBottom = if (isLandscape) {
                            navigationBottomPx
                        } else {
                            maxOf(navigationBottomPx, size.height * 0.20f)
                        }
                        if (
                            playerGesturePointAllowed(
                                offset.y,
                                size.height.toFloat(),
                                protectedTopPx,
                                protectedBottom
                            )
                        ) {
                            currentSingleTapHandler()
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!controlsLocked) {
                            val protectedBottom = if (isLandscape) {
                                navigationBottomPx
                            } else {
                                maxOf(navigationBottomPx, size.height * 0.20f)
                            }
                            val action = classifyPlayerDoubleTap(
                                x = offset.x,
                                y = offset.y,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                protectedTop = protectedTopPx,
                                protectedBottom = protectedBottom,
                                isLandscape = isLandscape
                            )
                            when (action) {
                                PlayerDoubleTapAction.SEEK_BACKWARD,
                                PlayerDoubleTapAction.SEEK_FORWARD -> {
                                    val backward = action == PlayerDoubleTapAction.SEEK_BACKWARD
                                    if (!currentCanSeek) return@detectTapGestures
                                    val origin = currentPosition().coerceAtLeast(0L)
                                    val delta = if (backward) -SEEK_INCREMENT_MS else SEEK_INCREMENT_MS
                                    val duration = currentDuration()
                                    val target = (origin + delta).let { candidate ->
                                        if (duration > 0L) candidate.coerceIn(0L, duration)
                                        else candidate.coerceAtLeast(0L)
                                    }
                                    currentSeekHandler(target)
                                    currentFeedbackHandler(
                                        PlayerGestureFeedback(
                                            kind = if (backward) {
                                                PlayerGestureKind.SEEK_BACKWARD
                                            } else {
                                                PlayerGestureKind.SEEK_FORWARD
                                            },
                                            label = formatPlayerTime(target),
                                            seekDeltaMs = target - origin
                                        )
                                    )
                                }

                                PlayerDoubleTapAction.TOGGLE_PLAY_PAUSE -> {
                                    val nowPlaying = currentToggleHandler()
                                    currentFeedbackHandler(
                                        PlayerGestureFeedback(
                                            kind = if (nowPlaying) PlayerGestureKind.PLAY else PlayerGestureKind.PAUSE,
                                            label = if (nowPlaying) "Playing" else "Paused"
                                        )
                                    )
                                }

                                PlayerDoubleTapAction.NONE -> Unit
                            }
                        }
                    }
                )
            }
            .pointerInput(
                controlsLocked,
                controlsVisible,
                isLandscape,
                activity,
                audioManager,
                protectedTopPx,
                navigationBottomPx
            ) {
                if (controlsLocked) return@pointerInput

                var mode = PlayerGestureMode.UNDECIDED
                var totalDrag = Offset.Zero
                var dragStartedAt = Offset.Zero
                var startPosition = 0L
                var pendingSeek: Long? = null
                var startBrightness = 0.5f
                var changedBrightness: Float? = null
                var startVolume = 0
                var maxVolume = 1

                detectDragGestures(
                    onDragStart = { offset ->
                        mode = PlayerGestureMode.UNDECIDED
                        totalDrag = Offset.Zero
                        dragStartedAt = offset
                        startPosition = currentPosition().coerceAtLeast(0L)
                        pendingSeek = null
                        changedBrightness = null

                        val windowBrightness = activity?.window?.attributes?.screenBrightness
                        startBrightness = windowBrightness?.takeIf { it in 0f..1f }
                            ?: runCatching {
                                Settings.System.getInt(
                                    context.contentResolver,
                                    Settings.System.SCREEN_BRIGHTNESS
                                ) / 255f
                            }.getOrDefault(0.5f)

                        if (audioManager != null) {
                            maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        totalDrag += dragAmount
                        if (mode == PlayerGestureMode.UNDECIDED) {
                            val protectedBottom = if (isLandscape) {
                                navigationBottomPx
                            } else {
                                maxOf(navigationBottomPx, size.height * 0.20f)
                            }
                            val tapAction = classifyPlayerDoubleTap(
                                x = dragStartedAt.x,
                                y = dragStartedAt.y,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                protectedTop = protectedTopPx,
                                protectedBottom = protectedBottom,
                                isLandscape = isLandscape
                            )
                            val verticalMode = when (tapAction) {
                                PlayerDoubleTapAction.SEEK_BACKWARD -> PlayerGestureMode.BRIGHTNESS
                                PlayerDoubleTapAction.SEEK_FORWARD -> if (volumeEnabled) PlayerGestureMode.VOLUME else null
                                else -> null
                            }
                            mode = classifyPlayerGesture(
                                dragX = totalDrag.x,
                                dragY = totalDrag.y,
                                startedAtX = dragStartedAt.x,
                                width = size.width.toFloat(),
                                edgeInset = gestureEdgeInsetPx,
                                allowSeek = currentCanSeek && playerSeekGestureAllowed(
                                    y = dragStartedAt.y,
                                    height = size.height.toFloat(),
                                    protectedTop = protectedTopPx,
                                    protectedBottom = protectedBottom,
                                    controlsVisible = controlsVisible,
                                    isLandscape = isLandscape
                                ),
                                verticalMode = verticalMode
                            )
                            if (mode == PlayerGestureMode.VOLUME) currentVolumeInteraction()
                        }
                        if (mode == PlayerGestureMode.UNDECIDED) return@detectDragGestures
                        change.consume()

                        when (mode) {
                            PlayerGestureMode.SEEK -> {
                                val duration = currentDuration()
                                val delta = ((totalDrag.x / size.width.coerceAtLeast(1)) *
                                    gestureSeekSpanMs(duration)).roundToLong()
                                val target = (startPosition + delta).let { candidate ->
                                    if (duration > 0L) candidate.coerceIn(0L, duration)
                                    else candidate.coerceAtLeast(0L)
                                }
                                pendingSeek = target
                                currentFeedbackHandler(
                                    PlayerGestureFeedback(
                                        kind = if (delta < 0L) {
                                            PlayerGestureKind.SEEK_BACKWARD
                                        } else {
                                            PlayerGestureKind.SEEK_FORWARD
                                        },
                                        label = formatPlayerTime(target),
                                        seekDeltaMs = target - startPosition
                                    )
                                )
                            }

                            PlayerGestureMode.BRIGHTNESS -> {
                                val value = (startBrightness -
                                    totalDrag.y / size.height.coerceAtLeast(1)).coerceIn(0.01f, 1f)
                                changedBrightness = value
                                activity?.window?.let { window ->
                                    window.attributes = window.attributes.apply { screenBrightness = value }
                                }
                                currentFeedbackHandler(
                                    PlayerGestureFeedback(
                                        PlayerGestureKind.BRIGHTNESS,
                                        "Brightness ${(value * 100).roundToLong()}%",
                                        value
                                    )
                                )
                            }

                            PlayerGestureMode.VOLUME -> {
                                val value = (startVolume - totalDrag.y /
                                    size.height.coerceAtLeast(1) * maxVolume)
                                    .roundToLong().toInt().coerceIn(0, maxVolume)
                                audioManager?.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
                                currentFeedbackHandler(
                                    PlayerGestureFeedback(
                                        PlayerGestureKind.VOLUME,
                                        "Volume ${(value * 100f / maxVolume).roundToLong()}%",
                                        value.toFloat() / maxVolume
                                    )
                                )
                            }

                            PlayerGestureMode.UNDECIDED -> Unit
                        }
                    },
                    onDragEnd = {
                        if (mode == PlayerGestureMode.SEEK) pendingSeek?.let(currentSeekHandler)
                        changedBrightness?.let(currentBrightnessCommitHandler)
                        pendingSeek = null
                        changedBrightness = null
                    },
                    onDragCancel = {
                        changedBrightness?.let(currentBrightnessCommitHandler)
                        pendingSeek = null
                        changedBrightness = null
                    }
                )
            }
    )
}

@Composable
internal fun PlayerGestureFeedbackOverlay(
    feedback: PlayerGestureFeedback,
    modifier: Modifier = Modifier
) {
    when (feedback.kind) {
        PlayerGestureKind.SEEK_BACKWARD, PlayerGestureKind.SEEK_FORWARD -> {
            val deltaSeconds = ((feedback.seekDeltaMs ?: 0L) / 1_000f).roundToLong()
            val backward = feedback.kind == PlayerGestureKind.SEEK_BACKWARD
            Row(
                modifier = modifier,
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("(${feedback.label})", color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(Color.Black, Offset.Zero, 8f)),
                    fontWeight = FontWeight.SemiBold)
                Text("${if (!backward && deltaSeconds >= 0) "+" else ""}${deltaSeconds}s",
                    color = if (backward) PLAYER_SEEK_BACKWARD_COLOR else PLAYER_SEEK_FORWARD_COLOR,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(Color.Black, Offset.Zero, 8f)),
                    fontWeight = FontWeight.Bold)
            }
        }
        PlayerGestureKind.BRIGHTNESS, PlayerGestureKind.VOLUME -> {
            val progress = feedback.progress?.coerceIn(0f, 1f) ?: 0f
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("${(progress * 100).roundToLong()}", color = Color.White,
                    style = MaterialTheme.typography.titleMedium.copy(
                        shadow = Shadow(Color.Black, Offset.Zero, 8f)),
                    fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier.width(7.dp).height(132.dp)
                        .background(Color.White.copy(alpha = 0.32f), RoundedCornerShape(50)),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(132.dp * progress)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                    )
                }
                Icon(
                    if (feedback.kind == PlayerGestureKind.BRIGHTNESS) Icons.Rounded.Brightness6
                    else Icons.AutoMirrored.Rounded.VolumeUp,
                    contentDescription = feedback.label,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        PlayerGestureKind.PLAY, PlayerGestureKind.PAUSE -> {
            Box(modifier = modifier.size(64.dp), contentAlignment = Alignment.Center) {
                Icon(
                    if (feedback.kind == PlayerGestureKind.PLAY) Icons.Rounded.PlayArrow
                    else Icons.Rounded.Pause,
                    contentDescription = feedback.label,
                    tint = Color.White,
                    modifier = Modifier.size(42.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerErrorCard(
    error: PlayerErrorPresentation,
    onOpenSourcePage: (() -> Unit)?,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.96f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.padding(24.dp).widthIn(max = 440.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(error.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(error.message, style = MaterialTheme.typography.bodyMedium)
            if (onOpenSourcePage != null) {
                OutlinedButton(onClick = onOpenSourcePage, modifier = Modifier.fillMaxWidth()) {
                    Text("Open source page")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onClose) { Text("Close") }
                if (error.canRetry) {
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onRetry) { Text("Try again") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerBottomControls(
    request: PlaybackRequest,
    isLandscape: Boolean,
    qualityLabel: String?,
    player: Player,
    timelineVisible: Boolean,
    scaleMode: PlayerScaleMode,
    playbackSpeed: Float,
    onScrubbingChanged: (Boolean) -> Unit,
    scaleMenuExpanded: Boolean,
    onScaleMenuChange: (Boolean) -> Unit,
    onScaleSelected: (PlayerScaleMode) -> Unit,
    speedMenuExpanded: Boolean,
    onSpeedMenuChange: (Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    subtitleFocus: FocusRequester,
    subtitleLabel: String,
    subtitlesEnabled: Boolean,
    onOpenSubtitles: () -> Unit,
    audioLabel: String,
    audioEnabled: Boolean,
    onOpenAudio: () -> Unit,
    sourceLabel: String,
    sourcesEnabled: Boolean,
    onOpenSources: () -> Unit,
    onAdjustmentsRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val timeline = rememberPlayerTimeline(player, timelineVisible) { onScrubbingChanged(false) }
    val onScrub: (Long) -> Unit = { position ->
        if (timeline.scrubPositionMs == null) onScrubbingChanged(true)
        timeline.scrubPositionMs = position
    }
    val onScrubFinished: () -> Unit = {
        timeline.scrubPositionMs?.let(player::seekTo)
        timeline.scrubPositionMs = null
        timeline.refresh(player)
        onScrubbingChanged(false)
    }
    PlayerBottomChrome(request.title, request.sourceLabel, qualityLabel, isLandscape,
        scaleMode, playbackSpeed, player,
        scaleMenuExpanded, onScaleMenuChange, onScaleSelected,
        speedMenuExpanded, onSpeedMenuChange, onSpeedSelected,
        subtitleFocus, subtitleLabel, subtitlesEnabled, onOpenSubtitles,
        audioLabel, audioEnabled, onOpenAudio, sourceLabel, sourcesEnabled, onOpenSources,
        onAdjustmentsRequested,
        timelineContent = { PlayerTimelineContent(timeline, onScrub, onScrubFinished) },
        timerContent = { PlayerTimerContent(timeline, it) }, modifier = modifier)
}

@Composable
private fun PlayerTimelineContent(timeline: PlayerTimelineState, onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit) {
    val sample = timeline.sample
    PlayerBufferedTimeline(timeline.scrubPositionMs ?: sample.positionMs,
        sample.bufferedPositionMs, sample.durationMs, onScrub, onScrubFinished)
}

@Composable
private fun PlayerTimerContent(timeline: PlayerTimelineState, modifier: Modifier) {
    val sample = timeline.sample
    PlayerTimer(timeline.scrubPositionMs ?: sample.positionMs, sample.durationMs, modifier)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun PlayerSubtitleWorkspaceSheet(
    options: List<PlayerTrackOption>,
    detectedSubtitles: List<DetectedMedia>,
    selection: PlayerTrackSelection,
    onSelect: (PlayerTrackSelection) -> Unit,
    onSelectDetectedSubtitle: (DetectedMedia) -> Unit,
    style: PlayerSubtitleStyle,
    attachedUrl: String?,
    error: String?,
    ready: Boolean,
    timeline: List<CuesWithTiming>,
    subtitleAdjustAnchorMs: Long?,
    onBeginSubtitleAdjust: () -> Unit,
    onCancelSubtitleAdjust: () -> Unit,
    onAlignSubtitleToCue: (Long) -> Unit,
    onStyleChange: (PlayerSubtitleStyle) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialFocus: FocusRequester? = null
) {
    val chosen = detectedSubtitles.firstOrNull { it.url == attachedUrl }
    PlayerSubtitleWorkspace(onDismiss, modifier, initialFocus,
        tracks = { contentModifier, inspect ->
            PlayerSubtitleTrackList(options, detectedSubtitles, selection, chosen,
                error, ready, onSelect, { subtitle -> onSelectDetectedSubtitle(subtitle); inspect() }, contentModifier)
        }, inspect = { contentModifier ->
            PlayerSubtitleInspectPanel(style, chosen, timeline, error, ready,
                subtitleAdjustAnchorMs, onAlignSubtitleToCue, contentModifier)
        }, settings = { contentModifier, inspect ->
            PlayerSubtitleControls(style, onStyleChange, subtitleAdjustAnchorMs != null,
                chosen != null && ready && timeline.any { it.cues.isNotEmpty() },
                { onBeginSubtitleAdjust(); inspect() }, onCancelSubtitleAdjust, contentModifier)
        })
}

@Composable
private fun PlayerSubtitleTrackList(
    options: List<PlayerTrackOption>,
    detectedSubtitles: List<DetectedMedia>,
    selection: PlayerTrackSelection,
    chosen: DetectedMedia?,
    error: String?,
    ready: Boolean,
    onSelect: (PlayerTrackSelection) -> Unit,
    onSelectDetectedSubtitle: (DetectedMedia) -> Unit,
    modifier: Modifier
) {
    LazyColumn(modifier) {
        item("auto") {
            PlayerSubtitleChoiceRow("Auto", selection.mode == PlayerTrackMode.AUTO, onClick = {
                onSelect(PlayerTrackSelection(PlayerTrackMode.AUTO))
            })
        }
        item("off") {
            PlayerSubtitleChoiceRow("Off", selection.mode == PlayerTrackMode.OFF, onClick = {
                onSelect(PlayerTrackSelection(PlayerTrackMode.OFF))
            })
        }
        if (detectedSubtitles.isNotEmpty()) {
            item("subtitle-heading") {
                Text("Detected subtitle files", style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            }
            items(detectedSubtitles, key = { it.url }) { subtitle ->
                PlayerSubtitleChoiceRow(
                    label = detectedSubtitleLabel(subtitle),
                    detail = when {
                        chosen?.url != subtitle.url -> null
                        error != null -> error
                        ready -> null
                        else -> "Loading…"
                    },
                    selected = chosen?.url == subtitle.url,
                    onClick = { onSelectDetectedSubtitle(subtitle) }
                )
            }
        }
        val embedded = options.filter { it.externalSubtitleUrl == null }
        if (embedded.isNotEmpty()) {
            item("embedded-heading") {
                Text("In the stream", style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp))
            }
            items(embedded, key = PlayerTrackOption::key) { option ->
                PlayerSubtitleChoiceRow(option.label,
                    selection.mode == PlayerTrackMode.SPECIFIC && selection.optionKey == option.key,
                    { onSelect(PlayerTrackSelection(PlayerTrackMode.SPECIFIC, option.key)) })
            }
        }
    }
}

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
private fun PlayerSubtitleInspectPanel(
    style: PlayerSubtitleStyle,
    chosen: DetectedMedia?,
    timeline: List<CuesWithTiming>,
    error: String?,
    ready: Boolean,
    subtitleAdjustAnchorMs: Long?,
    onAlignSubtitleToCue: (Long) -> Unit,
    modifier: Modifier
) {
    val context = LocalContext.current
    val typeface = remember(context, style.font) {
        captionFontResource(style.font)?.let { ResourcesCompat.getFont(context, it) }
    }
    val sampleCues = remember {
        listOf(Cue.Builder().setText("The quick brown fox\njumps over the lazy dog.").build())
    }
    val visibleCues = remember(style.bottomPadding) {
        positionSubtitleCues(sampleCues, style.bottomPadding)
    }
    Column(modifier) {
        Text(if (subtitleAdjustAnchorMs != null)
            "Tap the line that belongs at ${formatPlayerTime(subtitleAdjustAnchorMs)}"
            else "Style sample",
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.72f), maxLines = 1,
            overflow = TextOverflow.Ellipsis)
        Box(Modifier.fillMaxWidth(0.86f).height(108.dp).align(Alignment.CenterHorizontally)
            .padding(top = 5.dp)
            .clip(RoundedCornerShape(10.dp)).background(Color(0xFF8C8C8C))) {
            AndroidView(modifier = Modifier.fillMaxSize(), factory = { SubtitleView(it) }, update = { view ->
                applyPlayerSubtitleStyle(view, style, typeface)
                view.setFractionalTextSize(0.12f * style.size)
                view.setCues(visibleCues)
            })
        }
        Text(when {
            chosen == null -> "Choose a file to inspect its cues"
            error != null -> error
            !ready -> "Loading subtitle…"
            else -> detectedSubtitleLabel(chosen)
        }, style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(alpha = 0.7f), modifier = Modifier.padding(vertical = 6.dp))
        val entries = remember(timeline) { timeline.filter { it.cues.isNotEmpty() } }
        if (chosen != null && ready) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(entries, key = { it.startTimeUs }) { entry ->
                    Column(Modifier.fillMaxWidth()
                        .clickable(enabled = subtitleAdjustAnchorMs != null) {
                            onAlignSubtitleToCue(entry.startTimeUs)
                        }.padding(vertical = 5.dp)) {
                        Text(formatPlayerTime(entry.startTimeUs / 1_000L),
                            style = MaterialTheme.typography.labelSmall,
                            color = subtitleOverlayAccent())
                        Text(entry.cues.joinToString("\n") { it.text?.toString().orEmpty() },
                            style = MaterialTheme.typography.bodySmall, color = Color.White)
                    }
                    HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                }
            }
        }
    }
}

@Composable
private fun PlayerSubtitleControls(
    style: PlayerSubtitleStyle,
    onChange: (PlayerSubtitleStyle) -> Unit,
    adjusting: Boolean,
    canAdjust: Boolean,
    onBeginAdjust: () -> Unit,
    onCancelAdjust: () -> Unit,
    modifier: Modifier
) {
    Column(modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PlayerSubtitleDelayControl(style.delayMs) { onChange(style.copy(delayMs = it)) }
        TextButton(onClick = if (adjusting) onCancelAdjust else onBeginAdjust,
            enabled = adjusting || canAdjust,
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) {
            Text(if (adjusting) "Cancel adjustment" else "Adjust Subtitle")
        }
        if (!canAdjust && !adjusting) {
            Text("Select a detected subtitle file to adjust its timing.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.65f))
        }
        PlayerSubtitleStepper("Font size", "${(style.size * 100).toInt()}%",
            { onChange(style.copy(size = (style.size - 0.1f).coerceAtLeast(0.7f))) },
            { onChange(style.copy(size = (style.size + 0.1f).coerceAtMost(1.6f))) })
        PlayerSubtitleToggle("Bold", style.bold) { onChange(style.copy(bold = it)) }
        Text("Text color", style = MaterialTheme.typography.labelMedium)
        PlayerSubtitleColors(
            listOf(AndroidColor.WHITE, AndroidColor.YELLOW, AndroidColor.CYAN,
                AndroidColor.RED, AndroidColor.GREEN), style.resolvedTextColor()
        ) { onChange(style.copy(textColor = it)) }
        PlayerSubtitleStepper("Text opacity", "${style.textOpacity}%",
            { onChange(style.copy(textOpacity = (style.textOpacity - 10).coerceAtLeast(10))) },
            { onChange(style.copy(textOpacity = (style.textOpacity + 10).coerceAtMost(100))) })
        PlayerSubtitleToggle("Outline", style.resolvedOutlineEnabled()) {
            onChange(style.copy(outlineEnabled = it))
        }
        if (style.resolvedOutlineEnabled()) {
            Text("Outline color", style = MaterialTheme.typography.labelMedium)
            PlayerSubtitleColors(listOf(AndroidColor.BLACK, AndroidColor.WHITE,
                AndroidColor.CYAN, AndroidColor.RED), style.outlineColor) {
                onChange(style.copy(outlineColor = it))
            }
        }
        PlayerSubtitleStepper("Bottom offset", "${(style.bottomPadding * 100).toInt()}%",
            { onChange(style.copy(bottomPadding = (style.bottomPadding - 0.01f).coerceAtLeast(0.03f))) },
            { onChange(style.copy(bottomPadding = (style.bottomPadding + 0.01f).coerceAtMost(0.35f))) })
        Text("Caption preset", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CaptionVisualPreset.entries.forEach { preset ->
                Surface(onClick = {
                    onChange(style.copy(preset = preset, textColor = null, outlineEnabled = null))
                }, shape = RoundedCornerShape(8.dp),
                    color = if (style.preset == preset) MaterialTheme.colorScheme.primaryContainer
                        else Color.White.copy(alpha = 0.12f)) {
                    Text(preset.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Text("Font", style = MaterialTheme.typography.labelMedium)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            CaptionFontChoice.entries.forEach { font ->
                Surface(onClick = { onChange(style.copy(font = font)) },
                    shape = RoundedCornerShape(8.dp),
                    color = if (style.font == font) MaterialTheme.colorScheme.primaryContainer
                        else Color.White.copy(alpha = 0.12f)) {
                    Text(font.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        TextButton(onClick = { onChange(PlayerSubtitleStyle()) },
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) {
            Text("Reset defaults")
        }
    }
}

@Composable
private fun PlayerSubtitleStepper(
    label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onDecrease, modifier = Modifier.width(38.dp).semantics { contentDescription = "Decrease $label" },
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) { Text("−") }
        Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.width(56.dp))
        TextButton(onClick = onIncrease, modifier = Modifier.width(38.dp).semantics { contentDescription = "Increase $label" },
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) { Text("+") }
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
            modifier = Modifier.width(38.dp).semantics { contentDescription = "Decrease subtitle delay by 50 milliseconds" },
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) { Text("−") }
        BasicTextField(
            value = input,
            onValueChange = { edited ->
                if (edited.length <= 8 && edited.matches(Regex("-?\\d*"))) {
                    input = edited
                    edited.toLongOrNull()?.let { onChange(it.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)) }
                }
            },
            modifier = Modifier.width(64.dp).semantics { contentDescription = "Subtitle delay in milliseconds" }, singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp, textAlign = TextAlign.Center),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            cursorBrush = Brush.verticalGradient(listOf(Color.White, Color.White))
        )
        Text("ms", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { onChange((delayMs + 50L).coerceAtMost(MAX_SUBTITLE_DELAY_MS)) },
            modifier = Modifier.width(38.dp).semantics { contentDescription = "Increase subtitle delay by 50 milliseconds" },
            colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) { Text("+") }
    }
    TextButton(onClick = { onChange(0L) },
        colors = ButtonDefaults.textButtonColors(contentColor = subtitleOverlayAccent())) {
        Text("Reset delay", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun PlayerSubtitleToggle(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(if (checked) "On" else "Off", style = MaterialTheme.typography.bodySmall,
            color = if (checked) subtitleOverlayAccent() else Color.White.copy(alpha = 0.6f))
    }
}

@Composable
private fun PlayerSubtitleColors(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        colors.forEach { color ->
            Surface(onClick = { onSelect(color) }, shape = CircleShape,
                border = BorderStroke(if (color == selected) 3.dp else 1.dp,
                    if (color == selected) subtitleOverlayAccent() else Color.LightGray),
                color = Color(color), modifier = Modifier.size(30.dp)) {}
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
private fun PlayerTrackSelectionSheet(
    kind: PlayerTrackKind,
    options: List<PlayerTrackOption>,
    detectedSubtitles: List<DetectedMedia>,
    selection: PlayerTrackSelection,
    onSelect: (PlayerTrackSelection) -> Unit,
    onSelectDetectedSubtitle: (DetectedMedia) -> Unit,
    onInspectSubtitle: (DetectedMedia) -> Unit,
    subtitleStyle: PlayerSubtitleStyle,
    attachedExternalSubtitleUrl: String?,
    externalSubtitleError: String?,
    externalSubtitleReady: Boolean,
    externalSubtitleCues: List<CuesWithTiming>,
    subtitleAdjustAnchorMs: Long?,
    onBeginSubtitleAdjust: () -> Unit,
    onCancelSubtitleAdjust: () -> Unit,
    onAlignSubtitleToCue: (Long) -> Unit,
    onSubtitleStyleChange: (PlayerSubtitleStyle) -> Unit,
    onDismiss: () -> Unit
) {
    if (kind == PlayerTrackKind.SUBTITLE) {
        PlayerSubtitleWorkspaceSheet(
            options, detectedSubtitles, selection, onSelect, onSelectDetectedSubtitle,
            subtitleStyle, attachedExternalSubtitleUrl, externalSubtitleError,
            externalSubtitleReady, externalSubtitleCues,
            subtitleAdjustAnchorMs, onBeginSubtitleAdjust, onCancelSubtitleAdjust,
            onAlignSubtitleToCue,
            onSubtitleStyleChange, onDismiss
        )
        return
    }
    PlayerTrackSheet(kind.title, onDismiss) { listModifier ->
        LazyColumn(listModifier) {
                item(key = "auto") {
                    PlayerTrackChoiceRow(
                        label = "Auto",
                        detail = when (kind) {
                            PlayerTrackKind.SUBTITLE -> "Use the stream's default subtitle behavior"
                            PlayerTrackKind.AUDIO -> "Use the stream's default audio"
                            PlayerTrackKind.VIDEO -> "Adapt quality to the connection"
                        },
                        selected = selection.mode == PlayerTrackMode.AUTO,
                        onClick = { onSelect(PlayerTrackSelection(PlayerTrackMode.AUTO)) }
                    )
                }
                if (kind == PlayerTrackKind.SUBTITLE) {
                    item(key = "off") {
                        PlayerTrackChoiceRow(
                            label = "Off",
                            detail = "Hide subtitles",
                            selected = selection.mode == PlayerTrackMode.OFF,
                            onClick = { onSelect(PlayerTrackSelection(PlayerTrackMode.OFF)) }
                        )
                    }
                }
                item(key = "divider") {
                    HorizontalDivider(
                        color = Color.White.copy(alpha = 0.14f),
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
                if (kind == PlayerTrackKind.SUBTITLE && detectedSubtitles.isNotEmpty()) {
                    item(key = "detected-heading") {
                        Text("Detected on page", style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.66f),
                            modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 2.dp))
                    }
                    items(detectedSubtitles, key = { "detected-${it.url}" }) { subtitle ->
                        val option = options.firstOrNull { it.externalSubtitleUrl == subtitle.url }
                        PlayerTrackChoiceRow(
                            label = option?.label ?: detectedSubtitleLabel(subtitle),
                            detail = when {
                                attachedExternalSubtitleUrl != subtitle.url -> "Tap to use with video"
                                externalSubtitleError != null -> externalSubtitleError
                                externalSubtitleReady -> "Playing with video"
                                else -> "Loading subtitles…"
                            },
                            selected = selection.mode == PlayerTrackMode.SPECIFIC &&
                                selection.optionKey == externalSubtitleSelectionKey(subtitle.url),
                            onClick = { onSelectDetectedSubtitle(subtitle) },
                            onInspect = { onInspectSubtitle(subtitle) }
                        )
                    }
                }
                items(options.filter { option ->
                    kind != PlayerTrackKind.SUBTITLE || option.externalSubtitleUrl == null ||
                        detectedSubtitles.none { it.url == option.externalSubtitleUrl }
                }, key = PlayerTrackOption::key) { option ->
                    PlayerTrackChoiceRow(
                        label = option.label,
                        detail = option.detail,
                        selected = selection.mode == PlayerTrackMode.SPECIFIC &&
                            selection.optionKey == option.key,
                        onClick = {
                            onSelect(PlayerTrackSelection(PlayerTrackMode.SPECIFIC, option.key))
                        }
                    )
                }
        }
    }
}

internal fun formatPlayerTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}

internal fun formatPlayerTimer(positionMs: Long, durationMs: Long, showRemaining: Boolean): String {
    val elapsed = formatPlayerTime(positionMs)
    val second = when {
        durationMs <= 0L -> "--:--"
        showRemaining -> "−${formatPlayerTime((durationMs - positionMs).coerceAtLeast(0L))}"
        else -> formatPlayerTime(durationMs)
    }
    return "$elapsed / $second"
}

/** A full-width seek swipe covers 10% of the video, bounded to a useful 1–5 minute range. */
internal fun gestureSeekSpanMs(durationMs: Long): Long =
    if (durationMs > 0L) (durationMs / 10L).coerceIn(60_000L, 300_000L) else 60_000L

internal fun MediaType.toExoMimeTypeHint(): String? = when (this) {
    MediaType.HLS -> MimeTypes.APPLICATION_M3U8
    MediaType.DASH -> MimeTypes.APPLICATION_MPD
    else -> null
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
