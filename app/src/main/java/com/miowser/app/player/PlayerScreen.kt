package com.miowser.app.player

import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.miowser.app.grabber.MediaType

/**
 * Everything PlayerScreen needs to open a real playback session with the right session headers —
 * cookies/UA/referer forwarded the same way MediaProbe and the downloaders already do, so
 * session-authenticated content plays the same as a real page load would have.
 */
data class PlaybackRequest(
    val url: String,
    val pageUrl: String,
    val userAgent: String,
    val cookies: String?,
    val mediaType: MediaType
)

/**
 * Full-screen playback via ExoPlayer (Media3) — HLS/DASH adaptive streaming and direct files are
 * all handled by DefaultMediaSourceFactory once the right extension modules (media3-exoplayer-
 * hls/-dash) are on the classpath; this doesn't need to pick a MediaSource type itself. The MIME
 * type is set explicitly from the grabber's own classification rather than left to
 * extension-guessing from the URL — consistent with this project's "don't trust extensions"
 * pattern elsewhere (MediaUrlClassifier/ManifestSniffer), and necessary here since a redirected
 * or signed URL often doesn't end in .m3u8/.mpd even when it genuinely is one.
 *
 * Scope: playback only — no Picture-in-Picture, no background audio, no casting. Closing this
 * screen releases the player outright rather than keeping it alive in the background.
 */
@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(request: PlaybackRequest, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var errorMessage by remember(request.url) { mutableStateOf<String?>(null) }

    val exoPlayer = remember(request.url) {
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(request.userAgent)
            .setDefaultRequestProperties(
                buildMap {
                    put("Referer", request.pageUrl)
                    request.cookies?.let { put("Cookie", it) }
                }
            )
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(httpDataSourceFactory))
            .build()
            .apply {
                val mediaItem = MediaItem.Builder()
                    .setUri(request.url)
                    .setMimeType(request.mediaType.toExoMimeTypeHint())
                    .build()
                setMediaItem(mediaItem)
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = error.message ?: "Playback failed"
                    }
                })
                playWhenReady = true
                prepare()
            }
    }

    DisposableEffect(exoPlayer) {
        onDispose { exoPlayer.release() }
    }

    // Only the foregrounded player should actually be decoding/using bandwidth — pause (not
    // release) on backgrounding so returning to the app can resume where playback left off,
    // matching the same care already given to WebView tabs' lifecycle in BrowserScreen.
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                }
            }
        )
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Close player", tint = Color.White)
        }
        errorMessage?.let { message ->
            Text(
                text = "Couldn't play this: $message",
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
            )
        }
    }
}

internal fun MediaType.toExoMimeTypeHint(): String? = when (this) {
    MediaType.HLS -> MimeTypes.APPLICATION_M3U8
    MediaType.DASH -> MimeTypes.APPLICATION_MPD
    else -> null // let ExoPlayer infer from extension/Content-Type for direct files
}
