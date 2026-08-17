package com.miowser.app.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The shell every full-page destination (Downloads, History, Bookmarks, Settings, and whatever
 * comes next) shares: a title bar with a back arrow, no bottom browser toolbar — these are real
 * pages you navigate to, not overlays on top of the browser, so nothing browser-chrome-shaped
 * shows while one is open. [onBack] is wired to both the arrow and the hardware/gesture back
 * button by the caller (see BrowserScreen's activeScreen BackHandler).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimplePageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}
