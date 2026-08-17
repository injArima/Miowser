package com.miowser.app.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

/**
 * The app's global bottom navigation — destinations that make sense from anywhere, as opposed to
 * [com.miowser.app.browser.AddressBar]'s page-contextual actions (e.g. detected media, which is
 * tied to whatever the active tab is currently showing).
 *
 * Every non-Browser item opens as a transient bottom sheet rather than swapping to a full
 * "selected" destination, so there's no persistent selected-item state to track beyond "Browser
 * is always the resting item" — kept intentionally simple rather than building a full
 * destination/enum nav model before there's a real full-screen destination to justify one.
 */
@Composable
fun BottomBar(
    activeDownloadCount: Int,
    onOpenDownloads: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit
) {
    NavigationBar {
        NavigationBarItem(
            selected = true,
            onClick = {},
            icon = { Icon(Icons.Default.Home, contentDescription = null) },
            label = { Text("Browser") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenDownloads,
            icon = {
                if (activeDownloadCount > 0) {
                    BadgedBox(badge = { Badge { Text(activeDownloadCount.coerceAtMost(99).toString()) } }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Downloads ($activeDownloadCount active)")
                    }
                } else {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Downloads")
                }
            },
            label = { Text("Downloads") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenLibrary,
            icon = { Icon(Icons.Default.List, contentDescription = "History and bookmarks") },
            label = { Text("Library") }
        )
        NavigationBarItem(
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            label = { Text("Settings") }
        )
    }
}
