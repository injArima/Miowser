package com.miowser.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * The real browser toolbar: Back, Forward, Home, Bookmarks, Tabs, and an overflow (hamburger)
 * menu — the standard mobile-browser bottom-bar layout, plain icon buttons rather than a labeled
 * [androidx.compose.material3.NavigationBar] (which is meant for 3-5 top-level destinations with
 * room for labels; six icons need the compactness a plain Row gives).
 *
 * Back/Forward reflect real WebView history state via [canGoBack]/[canGoForward] — disabled
 * (dimmed, non-clickable) exactly when there's nowhere to go, the same state the hardware/gesture
 * back button already reacts to (see BrowserScreen's BackHandler), so the on-screen button and
 * the system gesture never disagree about whether "back" is available.
 *
 * Bookmarks and Menu are plain actions, not toggles — Bookmarks navigates straight to its own
 * full page (see [Screen]) rather than opening a popup first, and Menu opens
 * [OverflowMenuSheet], which is where destinations that don't get their own bar button live. Tabs
 * is the one button left with a persistent "currently open" highlight, since it's still a sheet
 * (see BrowserScreen's TabsSheet) rather than a full page.
 */
@Composable
fun BottomBar(
    canGoBack: Boolean,
    canGoForward: Boolean,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onHome: () -> Unit,
    onBookmarks: () -> Unit,
    tabCount: Int,
    tabsSelected: Boolean,
    onTabs: () -> Unit,
    activeDownloadCount: Int,
    onMenu: () -> Unit
) {
    Surface(tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(onClick = onBack, enabled = canGoBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForward, enabled = canGoForward) {
                Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = onHome) {
                Icon(Icons.Default.Home, contentDescription = "Home")
            }
            IconButton(onClick = onBookmarks) {
                Icon(Icons.Outlined.Star, contentDescription = "Bookmarks")
            }
            IconButton(onClick = onTabs) {
                TabCountIcon(
                    count = tabCount,
                    tint = if (tabsSelected) MaterialTheme.colorScheme.primary else LocalContentColor.current
                )
            }
            IconButton(onClick = onMenu) {
                if (activeDownloadCount > 0) {
                    BadgedBox(badge = { Badge { Text(activeDownloadCount.coerceAtMost(99).toString()) } }) {
                        Icon(Icons.Default.Menu, contentDescription = "More ($activeDownloadCount downloads active)")
                    }
                } else {
                    Icon(Icons.Default.Menu, contentDescription = "More")
                }
            }
        }
    }
}

/** A rounded-square outline with the open-tab count inside — the standard mobile-browser "tabs" icon; no ready-made one exists in material-icons-core. */
@Composable
private fun TabCountIcon(count: Int, tint: Color) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .border(width = 2.dp, color = tint, shape = RoundedCornerShape(5.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.coerceAtMost(99).toString(),
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}
