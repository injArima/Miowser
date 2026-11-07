package com.miowser.app.browser

import android.content.res.Configuration
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.miowser.app.browser.video.NativeVideoAssistantDisplay
import com.miowser.app.ui.theme.UiSpacing

/** A second normal tab stays interactive while the player behind this pane is paused. */
@Composable
internal fun CompanionBrowserPane(tab: Tab, onNavigate: (String) -> Unit, onClose: () -> Unit,
    showVideoHint: Boolean, modifier: Modifier = Modifier) {
    BackHandler(onBack = onClose)
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val paneShape = if (landscape) RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp)
        else RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    Surface(
        modifier = modifier.then(if (landscape) Modifier.fillMaxWidth(0.58f).fillMaxHeight()
            else Modifier.fillMaxWidth().fillMaxHeight(0.72f)).safeDrawingPadding(),
        shape = paneShape,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 12.dp
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = UiSpacing.s, vertical = UiSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
                IconButton(onClick = { tab.liveWebView?.takeIf { it.canGoBack() }?.goBack() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Page back")
                }
                IconButton(onClick = { tab.liveWebView?.takeIf { it.canGoForward() }?.goForward() }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = "Page forward")
                }
                OutlinedTextField(
                    value = tab.addressBarText,
                    onValueChange = { tab.addressBarText = it },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { onNavigate(tab.addressBarText.text) }),
                    label = { Text("Source page") }
                )
                IconButton(onClick = tab::reload) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Reload source page")
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.Close, contentDescription = "Close source page")
                }
            }
            if (showVideoHint) {
                Text("Tap the video button on the page to open it in Miowser",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = UiSpacing.m, vertical = UiSpacing.xs),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall)
            }
            key(tab.id) { AndroidView(modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { context ->
                    val engine = tab.ensureWebView()
                    FrameLayout(context).apply {
                        (engine.parent as? ViewGroup)?.removeView(engine)
                        addView(engine, FrameLayout.LayoutParams(-1, -1))
                        val display = NativeVideoAssistantDisplay(context)
                        addView(display, FrameLayout.LayoutParams(-1, -1))
                        tab.attachVideoAssistantDisplay(engine, display)
                    }
                },
                onRelease = { host ->
                    (0 until host.childCount).mapNotNull { host.getChildAt(it) as? NativeVideoAssistantDisplay }
                        .forEach(tab::detachVideoAssistantDisplay)
                    host.removeAllViews()
                }) }
        }
    }
}
