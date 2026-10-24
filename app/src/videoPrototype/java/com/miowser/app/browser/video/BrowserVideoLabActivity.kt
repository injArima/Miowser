package com.miowser.app.browser.video

import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.miowser.app.player.PlayerBufferedTimeline
import com.miowser.app.player.PlayerCenterControls
import com.miowser.app.player.formatPlayerTimer
import com.miowser.app.ui.theme.MiowserPlayerTheme
import java.util.Locale

/** Separate launcher entry in debug/development only. It ignores all incoming URLs/extras. */
class BrowserVideoLabActivity : ComponentActivity() {
    private lateinit var controller: BrowserVideoLabController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        controller = BrowserVideoLabController(this)
        setContent {
            MiowserPlayerTheme(darkTheme = true) {
                BrowserVideoLabScreen(controller, onClose = ::finish)
                val fullscreen = controller.customView != null
                DisposableEffect(fullscreen) {
                    val insets = WindowCompat.getInsetsController(window, window.decorView)
                    if (fullscreen) {
                        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                        insets.hide(WindowInsetsCompat.Type.systemBars())
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose {
                        if (fullscreen) {
                            insets.show(WindowInsetsCompat.Type.systemBars())
                            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        }
                    }
                }
            }
        }
    }

    override fun onStart() { super.onStart(); controller.onStart() }
    override fun onStop() { controller.onStop(); super.onStop() }
    override fun onDestroy() { controller.close(); super.onDestroy() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BrowserVideoLabScreen(controller: BrowserVideoLabController, onClose: () -> Unit) {
    val customView = controller.customView
    BackHandler(enabled = customView != null) { controller.exitFullscreen() }
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Keep this exact WebView attached throughout fullscreen; never key/recreate/load it here.
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close Video Lab", tint = Color.White)
                }
                Text("Video Assistant · Step 1", color = Color.White)
            }
            FlowRow(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BrowserVideoFixture.entries.forEach { fixture ->
                    FilterChip(selected = controller.fixture == fixture, onClick = { controller.load(fixture) },
                        label = { Text(fixture.label) })
                }
            }
            Text("Offline prototype. Play the clip, seek ahead, then tap Open prototype in the page.",
                color = Color.White, modifier = Modifier.padding(12.dp))
            AndroidView(factory = { controller.webView }, modifier = Modifier.fillMaxWidth().weight(1f))
            LabDiagnostics(controller, Modifier.padding(12.dp))
        }
        if (customView != null) {
            AndroidView(factory = { context ->
                FrameLayout(context).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    (customView.parent as? ViewGroup)?.removeView(customView)
                    addView(customView, FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                }
            }, modifier = Modifier.fillMaxSize(), onRelease = { host -> host.removeAllViews() })
            LabFullscreenControls(controller)
        }
    }
}

@Composable
private fun LabFullscreenControls(controller: BrowserVideoLabController) {
    val sample = controller.sample
    var visible by remember(controller.customView) { mutableStateOf(true) }
    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(Modifier.align(Alignment.TopStart).background(Color.Black.copy(alpha = 0.65f)),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = controller::exitFullscreen) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Return to web video", tint = Color.White)
            }
            TextButton(onClick = { visible = !visible }) {
                Text(if (visible) "Hide controls" else "Show controls")
            }
        }
        if (visible && sample != null) {
            PlayerCenterControls(isPlaying = sample.playing, isBuffering = sample.buffering,
                playlistActive = false, onPreviousVideo = null, onNextVideo = null,
                onReplay = { controller.skip(-10_000) }, onPlayPause = controller::playPause,
                onForward = { controller.skip(10_000) }, modifier = Modifier.align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.45f)))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.75f)).padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (sample.seekable) {
                    var scrub by remember(sample.documentId) { mutableStateOf<Long?>(null) }
                    // The shared timeline starts at zero. Offset it for non-zero seek windows.
                    val start = requireNotNull(sample.seekStartMs)
                    val end = requireNotNull(sample.seekEndMs)
                    PlayerBufferedTimeline((scrub ?: sample.positionMs) - start,
                        (sample.bufferedMs - start).coerceAtLeast(0), end - start,
                        onScrub = { scrub = start + it },
                        onScrubFinished = { scrub?.let(controller::seek); scrub = null })
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatPlayerTimer(sample.positionMs, sample.durationMs, false), color = Color.White)
                    TextButton(onClick = {
                        controller.setSpeed(if (sample.speed < 1.25) 1.5 else 1.0)
                    }) { Text(String.format(Locale.ROOT, "%.1f×", sample.speed)) }
                }
                LabDiagnostics(controller)
            }
        }
    }
}

/** Timeline updates stay in this leaf and the controls, rather than rebuilding the WebView host. */
@Composable
private fun LabDiagnostics(controller: BrowserVideoLabController, modifier: Modifier = Modifier) {
    Column(modifier) {
        controller.notice?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        controller.sample?.let { sample ->
            Text("${sample.width}×${sample.height} · ${sample.sourceKind} · " +
                "source changes ${sample.sourceChanges} · metadata loads ${sample.metadataLoads}",
                color = Color.White, style = MaterialTheme.typography.bodySmall)
            Text("Session ${sample.documentId.take(8)} · " +
                formatPlayerTimer(sample.positionMs, sample.durationMs, false),
                color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
        controller.entrySample?.let { entry ->
            val exit = controller.exitSample
            Text("Entered at ${formatPlayerTimer(entry.positionMs, entry.durationMs, false)}" +
                if (exit != null) " · returned at ${formatPlayerTimer(exit.positionMs, exit.durationMs, false)}" else "",
                color = Color.White, style = MaterialTheme.typography.bodySmall)
        }
    }
}
