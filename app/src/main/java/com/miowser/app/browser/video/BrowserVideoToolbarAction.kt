package com.miowser.app.browser.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.miowser.app.R
import com.miowser.app.browser.Tab
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Fallback stays outside page content and preserves all six existing navigation actions. */
@Composable
internal fun BrowserVideoToolbarAction(tab: Tab?, enabled: Boolean, onArmed: (String?) -> Unit) {
    if (tab == null || !enabled || tab.isShowingHome || tab.pageError != null) return
    val targets = remember(tab) { tab.liveVideoSessions.map(::browserVideoToolbarTarget).distinctUntilChanged() }
    val target by targets.collectAsState(initial = null)
    DisposableEffect(tab, target) {
        onDispose { tab.cancelBrowserVideoToolbar() }
    }
    val current = target ?: return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(horizontal = 12.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onArmed(tab.armBrowserVideoToolbar(current)) }) {
                Icon(painterResource(R.drawable.ic_miowser_mark), contentDescription = null,
                    modifier = Modifier.size(24.dp))
                Text(stringResource(R.string.browser_video_open), Modifier.padding(start = 8.dp))
            }
        }
    }
}
