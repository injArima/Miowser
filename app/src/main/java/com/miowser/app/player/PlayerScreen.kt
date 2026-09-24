package com.miowser.app.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Brightness6
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FitScreen
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.ScreenLockLandscape
import androidx.compose.material.icons.rounded.ScreenLockPortrait
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.miowser.app.grabber.MediaType
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.LibraryThumbnailStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

private const val SEEK_INCREMENT_MS = 10_000L
private const val CONTROLS_TIMEOUT_MS = 4_000L
private const val POSITION_POLL_PLAYING_MS = 500L
private const val POSITION_POLL_IDLE_MS = 1_000L
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
        override val title: String,
        override val sourceLabel: String,
        val pageTitle: String? = null,
        val videoName: String? = null,
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

private enum class PlayerScaleMode(val label: String, val resizeMode: Int) {
    FIT("Fit", AspectRatioFrameLayout.RESIZE_MODE_FIT),
    ZOOM("Zoom", AspectRatioFrameLayout.RESIZE_MODE_ZOOM),
    FILL("Fill", AspectRatioFrameLayout.RESIZE_MODE_FILL)
}

private enum class PlayerGestureKind { SEEK_BACKWARD, SEEK_FORWARD, PLAY, PAUSE, BRIGHTNESS, VOLUME }

private data class PlayerGestureFeedback(
    val kind: PlayerGestureKind,
    val label: String,
    val progress: Float? = null
)

/**
 * Miowser's full-screen player shell. Media3 still performs playback, adaptive streaming, and
 * decoding; its stock controller is disabled and this Compose overlay owns the visible controls.
 */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
@Composable
fun PlayerScreen(
    request: PlaybackRequest,
    onDownload: (() -> Unit)? = null,
    onOpenSourcePage: (() -> Unit)? = null,
    recordInLibrary: Boolean = true,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val configuration = LocalConfiguration.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var playbackError by remember(request) { mutableStateOf<PlayerErrorPresentation?>(null) }
    var isPlaying by remember(request) { mutableStateOf(false) }
    var isBuffering by remember(request) { mutableStateOf(true) }
    var hasRenderedFirstFrame by remember(request) { mutableStateOf(false) }
    var playbackEnded by remember(request) { mutableStateOf(false) }
    var positionMs by remember(request) { mutableLongStateOf(0L) }
    var durationMs by remember(request) { mutableLongStateOf(0L) }
    var scrubPositionMs by remember(request) { mutableStateOf<Long?>(null) }
    var playbackSpeed by remember(request) { mutableFloatStateOf(1f) }
    var scaleMode by remember(request) { mutableStateOf(PlayerScaleMode.FIT) }
    var controlsVisible by remember(request) { mutableStateOf(true) }
    var controlsLocked by remember(request) { mutableStateOf(false) }
    var controlsActivity by remember(request) { mutableIntStateOf(0) }
    var rotationLocked by remember(request) { mutableStateOf(false) }
    var pendingManualOrientation by remember(request) { mutableStateOf<Int?>(null) }
    var relockAfterManualRotation by remember(request) { mutableStateOf(false) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    var scaleMenuExpanded by remember { mutableStateOf(false) }
    var trackPanel by remember(request) { mutableStateOf<PlayerTrackKind?>(null) }
    var trackSnapshot by remember(request) { mutableStateOf(PlayerTrackSnapshot()) }
    var gestureFeedback by remember(request) { mutableStateOf<PlayerGestureFeedback?>(null) }
    var gestureFeedbackActivity by remember(request) { mutableIntStateOf(0) }
    var libraryRecorded by remember(request) { mutableStateOf(false) }
    var lastSavedLibraryPositionMs by remember(request) { mutableLongStateOf(-1L) }
    var playerView by remember(request) { mutableStateOf<PlayerView?>(null) }
    var midpointCaptureRequested by remember(request) { mutableStateOf(false) }
    var midpointCaptureFailed by remember(request) { mutableStateOf(false) }
    var fallbackCaptureRequested by remember(request) { mutableStateOf(false) }
    var leavingForSourcePage by remember(request) { mutableStateOf(false) }
    val libraryIdentityKey = remember(request) { request.toLibraryEntry().identityKey }
    var trackPreferences by remember(request, libraryIdentityKey) {
        mutableStateOf(PlayerTrackPreferenceStore.load(context, libraryIdentityKey))
    }
    var restoredTrackKinds by remember(request) { mutableStateOf(emptySet<PlayerTrackKind>()) }

    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val systemBarsController = remember(activity) {
        activity?.window?.let { window ->
            WindowCompat.getInsetsController(window, window.decorView).apply {
                systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }

    LaunchedEffect(controlsVisible, systemBarsController) {
        if (controlsVisible) {
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

    // The player owns orientation only while it is visible. Restoring the activity's previous
    // value on exit keeps the browser and Hub from inheriting a player-only orientation choice.
    DisposableEffect(activity, request) {
        val previousOrientation = activity?.requestedOrientation
        val previousBrightness = activity?.window?.attributes?.screenBrightness
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
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

    // A manual rotate request temporarily pins the requested direction. Once Android reports that
    // the turn completed, return to sensor rotation or re-lock the exact new orientation.
    LaunchedEffect(configuration.orientation, pendingManualOrientation, relockAfterManualRotation) {
        val target = pendingManualOrientation ?: return@LaunchedEffect
        if (configuration.orientation == target) {
            delay(300)
            activity?.requestedOrientation = if (relockAfterManualRotation) {
                ActivityInfo.SCREEN_ORIENTATION_LOCKED
            } else {
                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            }
            pendingManualOrientation = null
            relockAfterManualRotation = false
        }
    }

    val exoPlayer = remember(request) {
        val mediaSourceFactory = when (request) {
            is PlaybackRequest.Remote -> {
                val httpDataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(request.userAgent)
                    .setDefaultRequestProperties(
                        buildMap {
                            put("Referer", request.pageUrl)
                            request.cookies?.let { put("Cookie", it) }
                        }
                    )
                DefaultMediaSourceFactory(httpDataSourceFactory)
            }
            is PlaybackRequest.Local -> DefaultMediaSourceFactory(context)
        }
        val mediaItem = when (request) {
            is PlaybackRequest.Remote -> MediaItem.Builder()
                .setUri(request.url)
                .setMimeType(request.mediaType.toExoMimeTypeHint())
                .build()
            is PlaybackRequest.Local -> MediaItem.fromUri(request.uri)
        }
        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
        ExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                setMediaItem(mediaItem)
                if (request.startPositionMs > 0L) seekTo(request.startPositionMs)
                playWhenReady = true
                prepare()
            }
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

    DisposableEffect(exoPlayer, request) {
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

        fun requestMiddleFrame(duration: Long) {
            if (!recordInLibrary || midpointCaptureRequested) return
            if (request is PlaybackRequest.Remote &&
                request.mediaType in setOf(MediaType.HLS, MediaType.DASH)
            ) return // Adaptive streams get their middle frame from the already-running player.
            val existing = MediaLibrary.entries.firstOrNull { it.identityKey == libraryIdentityKey }?.thumbnailUrl
            if (LibraryThumbnailStore.isMiddleFrame(existing)) return
            midpointCaptureRequested = true
            val mediaUri = when (request) {
                is PlaybackRequest.Remote -> Uri.parse(request.url)
                is PlaybackRequest.Local -> request.uri
            }
            val headers = (request as? PlaybackRequest.Remote)?.let { remote ->
                buildMap {
                    put("User-Agent", remote.userAgent)
                    put("Referer", remote.pageUrl)
                    remote.cookies?.let { put("Cookie", it) }
                }
            }
            LibraryThumbnailStore.captureMiddleFrame(context, mediaUri, headers, duration, libraryIdentityKey) { uri ->
                if (uri != null) MediaLibrary.updateThumbnail(context, libraryIdentityKey, uri)
                else {
                    midpointCaptureFailed = true
                    captureFallbackFrame()
                }
            }
        }

        fun recordReadyMedia() {
            if (!recordInLibrary || leavingForSourcePage) return
            val readyDuration = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            if (!libraryRecorded) {
                val entry = MediaLibrary.recordOpened(
                    context,
                    request.toLibraryEntry(durationMs = readyDuration)
                )
                libraryRecorded = true
                lastSavedLibraryPositionMs = entry.positionMs
            }
            requestMiddleFrame(readyDuration)
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
                // A network rebuffer also changes isPlaying to false. Persist only an intentional
                // pause here; lifecycle pause, end, and disposal have their own forced saves.
                if (!playing && !exoPlayer.playWhenReady) saveLibraryProgress(true)
            }

            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                playbackEnded = state == Player.STATE_ENDED
                if (state == Player.STATE_READY) {
                    recordReadyMedia()
                } else if (state == Player.STATE_ENDED) {
                    saveLibraryProgress(true)
                }
            }

            override fun onTracksChanged(tracks: Tracks) {
                trackSnapshot = buildPlayerTrackSnapshot(tracks)
            }

            override fun onRenderedFirstFrame() {
                hasRenderedFirstFrame = true
                recordReadyMedia()
                // Keep artwork available immediately. Midpoint extraction replaces this image
                // when ready; an older valid thumbnail is left in place meanwhile.
                captureFallbackFrame()
            }

            override fun onPlayerError(error: PlaybackException) {
                isBuffering = false
                playbackError = playerErrorPresentation(error.errorCode)
                controlsVisible = true
                Log.d(PLAYER_TAG, "Playback failed (${error.errorCodeName}): ${error.message}", error)
            }
        }
        exoPlayer.addListener(listener)
        trackSnapshot = buildPlayerTrackSnapshot(exoPlayer.currentTracks)
        if (exoPlayer.playbackState == Player.STATE_READY) recordReadyMedia()
        onDispose {
            saveLibraryProgress(true)
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    DisposableEffect(lifecycleOwner, exoPlayer) {
        var resumeWhenActive = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    resumeWhenActive = exoPlayer.playWhenReady
                    exoPlayer.pause()
                    saveLibraryProgress(true)
                }
                Lifecycle.Event.ON_RESUME -> if (resumeWhenActive) exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(exoPlayer) {
        while (isActive) {
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            durationMs = exoPlayer.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: 0L
            playbackSpeed = exoPlayer.playbackParameters.speed
            delay(if (exoPlayer.isPlaying) POSITION_POLL_PLAYING_MS else POSITION_POLL_IDLE_MS)
        }
    }

    // HLS/DASH usually cannot be sought independently by MediaMetadataRetriever. Capture the
    // visible decoded frame when normal playback passes the midpoint; the earlier fallback stays
    // available if the user stops watching before then.
    LaunchedEffect(positionMs, durationMs, hasRenderedFirstFrame, playerView) {
        val adaptive = request is PlaybackRequest.Remote &&
            request.mediaType in setOf(MediaType.HLS, MediaType.DASH)
        val midpoint = durationMs / 2L
        if (!recordInLibrary || !adaptive || !hasRenderedFirstFrame || durationMs <= 0L ||
            midpointCaptureRequested || positionMs !in midpoint..(midpoint + 2_000L)
        ) return@LaunchedEffect
        val existing = MediaLibrary.entries.firstOrNull { it.identityKey == libraryIdentityKey }?.thumbnailUrl
        if (LibraryThumbnailStore.isMiddleFrame(existing)) return@LaunchedEffect
        val view = playerView ?: return@LaunchedEffect
        midpointCaptureRequested = true
        LibraryThumbnailStore.captureFirstFrame(
            context = context,
            playerView = view,
            identityKey = libraryIdentityKey,
            middle = true
        ) { uri ->
            if (uri != null) MediaLibrary.updateThumbnail(context, libraryIdentityKey, uri)
            else midpointCaptureRequested = false
        }
    }

    fun selectTrack(kind: PlayerTrackKind, selection: PlayerTrackSelection) {
        if (!applyPlayerTrackSelection(exoPlayer, trackSnapshot, kind, selection)) return
        val updatedPreferences = trackPreferences.withSelection(kind, selection)
        trackPreferences = updatedPreferences
        PlayerTrackPreferenceStore.save(context, libraryIdentityKey, updatedPreferences)
        restoredTrackKinds = restoredTrackKinds + kind
        trackPanel = null
        controlsVisible = true
        controlsActivity++
    }

    // Track groups may arrive after playback starts (especially live HLS/DASH manifests). Restore
    // each kind independently once it actually exists, instead of taking one early empty snapshot.
    LaunchedEffect(exoPlayer, trackSnapshot) {
        var restored = restoredTrackKinds
        PlayerTrackKind.entries.forEach { kind ->
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
        trackPanel,
        isBuffering,
        playbackError,
        controlsActivity
    ) {
        if (
            controlsVisible && isPlaying && !isBuffering && playbackError == null &&
            !speedMenuExpanded && !scaleMenuExpanded && trackPanel == null
        ) {
            delay(CONTROLS_TIMEOUT_MS)
            controlsVisible = false
            speedMenuExpanded = false
            scaleMenuExpanded = false
        }
    }

    val togglePlayback: () -> Boolean = {
        if (exoPlayer.playWhenReady) {
            exoPlayer.pause()
            false
        } else {
            if (playbackEnded) exoPlayer.seekTo(0L)
            exoPlayer.play()
            true
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    keepScreenOn = exoPlayer.playWhenReady && playbackError == null
                    resizeMode = scaleMode.resizeMode
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    playerView = this
                }
            },
            update = { view ->
                view.player = exoPlayer
                view.resizeMode = scaleMode.resizeMode
                view.keepScreenOn = exoPlayer.playWhenReady && playbackError == null
            }
        )

        // This full-video gesture surface sits below the visible controls, so buttons and the
        // timeline keep priority while gestures on the video itself remain available.
        PlayerGestureLayer(
            controlsLocked = controlsLocked,
            controlsVisible = controlsVisible,
            isLandscape = isLandscape,
            positionMs = positionMs,
            durationMs = durationMs,
            onSingleTap = {
                controlsVisible = !controlsVisible
                if (controlsVisible) controlsActivity++
            },
            onTogglePlayPause = togglePlayback,
            onSeekTo = { target ->
                exoPlayer.seekTo(target)
                positionMs = target
            },
            onFeedback = { feedback ->
                gestureFeedback = feedback
                gestureFeedbackActivity++
            }
        )

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.72f),
                            0.38f to Color.Transparent,
                            0.64f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.82f)
                        )
                    )
            ) {
                if (controlsLocked) {
                    RoundPlayerButton(
                        icon = Icons.Rounded.LockOpen,
                        contentDescription = "Unlock controls",
                        onClick = { controlsLocked = false },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End)
                            )
                            .padding(20.dp)
                    )
                } else {
                    PlayerTopBar(
                        request = request,
                        isLandscape = isLandscape,
                        rotationLocked = rotationLocked,
                        playbackSpeed = playbackSpeed,
                        onRotate = {
                            val target = if (isLandscape) {
                                Configuration.ORIENTATION_PORTRAIT
                            } else {
                                Configuration.ORIENTATION_LANDSCAPE
                            }
                            pendingManualOrientation = target
                            relockAfterManualRotation = rotationLocked
                            activity?.requestedOrientation = if (target == Configuration.ORIENTATION_LANDSCAPE) {
                                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                            }
                        },
                        onToggleRotationLock = {
                            pendingManualOrientation = null
                            relockAfterManualRotation = false
                            val shouldLock = !rotationLocked
                            rotationLocked = shouldLock
                            activity?.requestedOrientation = if (shouldLock) {
                                ActivityInfo.SCREEN_ORIENTATION_LOCKED
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                            }
                        },
                        onDownload = onDownload,
                        onLock = { controlsLocked = true },
                        onDismiss = onDismiss,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )

                    PlayerCenterControls(
                        isPlaying = isPlaying,
                        isBuffering = isBuffering,
                        onReplay = {
                            exoPlayer.seekTo((exoPlayer.currentPosition - SEEK_INCREMENT_MS).coerceAtLeast(0L))
                            controlsVisible = true
                            controlsActivity++
                        },
                        onPlayPause = {
                            togglePlayback()
                            controlsVisible = true
                            controlsActivity++
                        },
                        onForward = {
                            val target = exoPlayer.currentPosition + SEEK_INCREMENT_MS
                            exoPlayer.seekTo(if (durationMs > 0) target.coerceAtMost(durationMs) else target)
                            controlsVisible = true
                            controlsActivity++
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )

                    if (isBuffering) {
                        PlayerBufferingPill(
                            label = if (hasRenderedFirstFrame) "Buffering…" else "Starting video…",
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(y = if (isLandscape) (-104).dp else (-148).dp)
                        )
                    }

                    PlayerBottomControls(
                        positionMs = scrubPositionMs ?: positionMs,
                        durationMs = durationMs,
                        scaleMode = scaleMode,
                        playbackSpeed = playbackSpeed,
                        onScrub = {
                            if (scrubPositionMs == null) controlsActivity++
                            scrubPositionMs = it
                        },
                        onScrubFinished = {
                            scrubPositionMs?.let(exoPlayer::seekTo)
                            scrubPositionMs = null
                            controlsVisible = true
                            controlsActivity++
                        },
                        onScaleMenuChange = { scaleMenuExpanded = it },
                        scaleMenuExpanded = scaleMenuExpanded,
                        onScaleSelected = { selected ->
                            scaleMode = selected
                            scaleMenuExpanded = false
                            controlsVisible = true
                            controlsActivity++
                        },
                        onSpeedMenuChange = { speedMenuExpanded = it },
                        speedMenuExpanded = speedMenuExpanded,
                        onSpeedSelected = { speed ->
                            exoPlayer.setPlaybackSpeed(speed)
                            speedMenuExpanded = false
                            controlsVisible = true
                            controlsActivity++
                        },
                        subtitleLabel = playerTrackButtonLabel(
                            PlayerTrackKind.SUBTITLE,
                            trackPreferences.subtitles,
                            trackSnapshot
                        ),
                        subtitlesEnabled = trackSnapshot.subtitles.isNotEmpty(),
                        onOpenSubtitles = {
                            trackPanel = PlayerTrackKind.SUBTITLE
                            controlsActivity++
                        },
                        audioLabel = playerTrackButtonLabel(
                            PlayerTrackKind.AUDIO,
                            trackPreferences.audio,
                            trackSnapshot
                        ),
                        audioEnabled = trackSnapshot.audio.size > 1,
                        onOpenAudio = {
                            trackPanel = PlayerTrackKind.AUDIO
                            controlsActivity++
                        },
                        sourceLabel = playerTrackButtonLabel(
                            PlayerTrackKind.VIDEO,
                            trackPreferences.video,
                            trackSnapshot
                        ),
                        sourcesEnabled = trackSnapshot.video.size > 1,
                        onOpenSources = {
                            trackPanel = PlayerTrackKind.VIDEO
                            controlsActivity++
                        },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
            }
        }

        gestureFeedback?.let { feedback ->
            PlayerGestureFeedbackOverlay(
                feedback = feedback,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isBuffering && !controlsVisible && playbackError == null) {
            PlayerBufferingPill(
                label = if (hasRenderedFirstFrame) "Buffering…" else "Starting video…",
                modifier = Modifier.align(Alignment.Center)
            )
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

        trackPanel?.let { kind ->
            PlayerTrackSelectionSheet(
                kind = kind,
                options = trackSnapshot.options(kind),
                selection = trackPreferences.selection(kind),
                onSelect = { selection -> selectTrack(kind, selection) },
                onDismiss = { trackPanel = null }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerGestureLayer(
    controlsLocked: Boolean,
    controlsVisible: Boolean,
    isLandscape: Boolean,
    positionMs: Long,
    durationMs: Long,
    onSingleTap: () -> Unit,
    onTogglePlayPause: () -> Boolean,
    onSeekTo: (Long) -> Unit,
    onFeedback: (PlayerGestureFeedback) -> Unit
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
    val currentSingleTapHandler by rememberUpdatedState(onSingleTap)
    val currentToggleHandler by rememberUpdatedState(onTogglePlayPause)
    val currentSeekHandler by rememberUpdatedState(onSeekTo)
    val currentFeedbackHandler by rememberUpdatedState(onFeedback)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(controlsLocked, isLandscape, protectedTopPx, navigationBottomPx) {
                detectTapGestures(
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
                                    val delta = if (backward) -SEEK_INCREMENT_MS else SEEK_INCREMENT_MS
                                    val duration = currentDuration
                                    val target = (currentPosition + delta).let { candidate ->
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
                                            label = if (backward) "10 seconds back" else "10 seconds forward"
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
                var startVolume = 0
                var maxVolume = 1

                detectDragGestures(
                    onDragStart = { offset ->
                        mode = PlayerGestureMode.UNDECIDED
                        totalDrag = Offset.Zero
                        dragStartedAt = offset
                        startPosition = currentPosition
                        pendingSeek = null

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
                                PlayerDoubleTapAction.SEEK_FORWARD -> PlayerGestureMode.VOLUME
                                else -> null
                            }
                            mode = classifyPlayerGesture(
                                dragX = totalDrag.x,
                                dragY = totalDrag.y,
                                startedAtX = dragStartedAt.x,
                                width = size.width.toFloat(),
                                edgeInset = gestureEdgeInsetPx,
                                allowSeek = playerSeekGestureAllowed(
                                    y = dragStartedAt.y,
                                    height = size.height.toFloat(),
                                    protectedTop = protectedTopPx,
                                    protectedBottom = protectedBottom,
                                    controlsVisible = controlsVisible,
                                    isLandscape = isLandscape
                                ),
                                verticalMode = verticalMode
                            )
                        }
                        if (mode == PlayerGestureMode.UNDECIDED) return@detectDragGestures
                        change.consume()

                        when (mode) {
                            PlayerGestureMode.SEEK -> {
                                val duration = currentDuration
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
                                        label = formatPlayerTime(target)
                                    )
                                )
                            }

                            PlayerGestureMode.BRIGHTNESS -> {
                                val value = (startBrightness -
                                    totalDrag.y / size.height.coerceAtLeast(1)).coerceIn(0.01f, 1f)
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
                        pendingSeek = null
                    },
                    onDragCancel = { pendingSeek = null }
                )
            }
    )
}

@Composable
private fun PlayerGestureFeedbackOverlay(
    feedback: PlayerGestureFeedback,
    modifier: Modifier = Modifier
) {
    val icon = when (feedback.kind) {
        PlayerGestureKind.SEEK_BACKWARD -> Icons.Rounded.Replay10
        PlayerGestureKind.SEEK_FORWARD -> Icons.Rounded.Forward10
        PlayerGestureKind.PLAY -> Icons.Rounded.PlayArrow
        PlayerGestureKind.PAUSE -> Icons.Rounded.Pause
        PlayerGestureKind.BRIGHTNESS -> Icons.Rounded.Brightness6
        PlayerGestureKind.VOLUME -> Icons.AutoMirrored.Rounded.VolumeUp
    }
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(26.dp))
                Text(feedback.label, style = MaterialTheme.typography.titleMedium)
            }
            feedback.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.width(180.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.22f)
                )
            }
        }
    }
}

@Composable
private fun PlayerBufferingPill(label: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color.Black.copy(alpha = 0.72f),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.5.dp
            )
            Text(label, style = MaterialTheme.typography.labelLarge)
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

@Composable
private fun PlayerTopBar(
    request: PlaybackRequest,
    isLandscape: Boolean,
    rotationLocked: Boolean,
    playbackSpeed: Float,
    onRotate: () -> Unit,
    onToggleRotationLock: () -> Unit,
    onDownload: (() -> Unit)?,
    onLock: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = request.title,
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = request.sourceLabel,
                color = Color.White.copy(alpha = 0.72f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (onDownload != null) {
            RoundPlayerButton(Icons.Rounded.Download, "Download video", onDownload)
            Spacer(Modifier.width(10.dp))
        }
        RotationPlayerButton(
            isLandscape = isLandscape,
            locked = rotationLocked,
            onRotate = onRotate,
            onToggleLock = onToggleRotationLock
        )
        Spacer(Modifier.width(10.dp))
        if (playbackSpeed != 1f) {
            PlaybackSpeedIndicator(playbackSpeed)
            Spacer(Modifier.width(10.dp))
        }
        RoundPlayerButton(Icons.Rounded.Lock, "Lock controls", onLock)
        Spacer(Modifier.width(10.dp))
        RoundPlayerButton(Icons.AutoMirrored.Rounded.ArrowBack, "Close player", onDismiss)
    }
}

@Composable
private fun PlaybackSpeedIndicator(speed: Float) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.heightIn(min = 52.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 14.dp)
        ) {
            Text(
                text = formatSpeed(speed),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlayerCenterControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    onReplay: () -> Unit,
    onPlayPause: () -> Unit,
    onForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RoundPlayerButton(Icons.Rounded.Replay10, "Rewind 10 seconds", onReplay, size = 58.dp)
        Surface(
            shape = CircleShape,
            color = Color.White,
            contentColor = Color.Black,
            modifier = Modifier
                .size(76.dp)
                .combinedClickable(onClick = onPlayPause, onDoubleClick = onPlayPause)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (isBuffering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(34.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(42.dp)
                    )
                }
            }
        }
        RoundPlayerButton(Icons.Rounded.Forward10, "Forward 10 seconds", onForward, size = 58.dp)
    }
}

@Composable
private fun PlayerBottomControls(
    positionMs: Long,
    durationMs: Long,
    scaleMode: PlayerScaleMode,
    playbackSpeed: Float,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    scaleMenuExpanded: Boolean,
    onScaleMenuChange: (Boolean) -> Unit,
    onScaleSelected: (PlayerScaleMode) -> Unit,
    speedMenuExpanded: Boolean,
    onSpeedMenuChange: (Boolean) -> Unit,
    onSpeedSelected: (Float) -> Unit,
    subtitleLabel: String,
    subtitlesEnabled: Boolean,
    onOpenSubtitles: () -> Unit,
    audioLabel: String,
    audioEnabled: Boolean,
    onOpenAudio: () -> Unit,
    sourceLabel: String,
    sourcesEnabled: Boolean,
    onOpenSources: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
            )
            .padding(horizontal = 24.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TimeBubble(formatPlayerTime(positionMs))
            TimeBubble(formatPlayerTime(durationMs))
        }
        Slider(
            value = positionMs.coerceIn(0L, durationMs.coerceAtLeast(0L)).toFloat(),
            onValueChange = { onScrub(it.roundToLong()) },
            onValueChangeFinished = onScrubFinished,
            valueRange = 0f..durationMs.coerceAtLeast(1L).toFloat(),
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.34f)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Surface(
            shape = RoundedCornerShape(50),
            color = Color.Black.copy(alpha = 0.58f),
            contentColor = Color.White,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
            modifier = Modifier
                .padding(top = 2.dp)
                .widthIn(max = 760.dp)
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box {
                    PlayerOptionButton(Icons.Rounded.FitScreen, scaleMode.label) {
                        onScaleMenuChange(true)
                    }
                    DropdownMenu(
                        expanded = scaleMenuExpanded,
                        onDismissRequest = { onScaleMenuChange(false) }
                    ) {
                        PlayerScaleMode.entries.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(mode.label) },
                                onClick = { onScaleSelected(mode) }
                            )
                        }
                    }
                }
                Box {
                    PlayerOptionButton(Icons.Rounded.Speed, formatSpeed(playbackSpeed)) {
                        onSpeedMenuChange(true)
                    }
                    DropdownMenu(
                        expanded = speedMenuExpanded,
                        onDismissRequest = { onSpeedMenuChange(false) }
                    ) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(
                                text = { Text(formatSpeed(speed)) },
                                onClick = { onSpeedSelected(speed) }
                            )
                        }
                    }
                }
                PlayerOptionButton(
                    Icons.Rounded.Subtitles,
                    subtitleLabel,
                    enabled = subtitlesEnabled,
                    onClick = onOpenSubtitles
                )
                PlayerOptionButton(
                    Icons.Rounded.Audiotrack,
                    audioLabel,
                    enabled = audioEnabled,
                    onClick = onOpenAudio
                )
                PlayerOptionButton(
                    Icons.Rounded.Tune,
                    sourceLabel,
                    enabled = sourcesEnabled,
                    onClick = onOpenSources
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerTrackSelectionSheet(
    kind: PlayerTrackKind,
    options: List<PlayerTrackOption>,
    selection: PlayerTrackSelection,
    onSelect: (PlayerTrackSelection) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF151313),
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                kind.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp)
            ) {
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
                        modifier = Modifier.padding(vertical = 6.dp)
                    )
                }
                items(options, key = PlayerTrackOption::key) { option ->
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
            Spacer(Modifier.size(14.dp))
        }
    }
}

@Composable
private fun PlayerTrackChoiceRow(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = if (selected) MaterialTheme.colorScheme.primary else Color.White
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RotationPlayerButton(
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
        color = if (locked) MaterialTheme.colorScheme.primaryContainer else Color.Black.copy(alpha = 0.56f),
        contentColor = if (locked) MaterialTheme.colorScheme.onPrimaryContainer else Color.White,
        modifier = Modifier
            .size(52.dp)
            .combinedClickable(onClick = onRotate, onLongClick = onToggleLock)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(25.dp))
        }
    }
}

@Composable
private fun RoundPlayerButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.56f),
        contentColor = Color.White,
        modifier = modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, modifier = Modifier.size(size * 0.48f))
        }
    }
}

@Composable
private fun PlayerOptionButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .alpha(if (enabled) 1f else 0.38f)
            .clickable(enabled = enabled, onClick = onClick)
            .size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            icon,
            contentDescription = label,
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun TimeBubble(text: String) {
    Surface(
        color = Color.Black.copy(alpha = 0.64f),
        contentColor = Color.White,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.26f))
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
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

/** A full-width seek swipe covers 10% of the video, bounded to a useful 1–5 minute range. */
internal fun gestureSeekSpanMs(durationMs: Long): Long =
    if (durationMs > 0L) (durationMs / 10L).coerceIn(60_000L, 300_000L) else 60_000L

private fun formatSpeed(speed: Float): String =
    if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"

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
