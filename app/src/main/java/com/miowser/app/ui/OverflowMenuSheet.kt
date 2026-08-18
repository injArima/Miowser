package com.miowser.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Every full-page destination in the app. DETECTED, BOOKMARKS, and TABS also have their own
 * dedicated entry points (AddressBar's badge, and BottomBar's Bookmarks/Tabs buttons
 * respectively) — DETECTED is deliberately duplicated into this menu too (a global "what's been
 * found" check independent of that badge ever having appeared), while BOOKMARKS/TABS aren't,
 * since a dedicated bar button is already the more prominent, permanent entry point for those.
 */
enum class Screen { DETECTED, DOWNLOADS, HISTORY, BOOKMARKS, SETTINGS, TABS }

/**
 * The hamburger button's menu — a grid of icon+label tiles rather than a plain dropdown list,
 * concept borrowed from Viowser's own overflow menu (not its exact styling). Populated only with
 * destinations that don't already have a dedicated BottomBar button: Downloads, History, Media
 * (detected on the current tab), Settings. As more "crucial components" arrive later, they slot
 * in as additional grid items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowMenuSheet(
    activeDownloadCount: Int,
    detectedCount: Int,
    onSelect: (Screen) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(4),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            items(
                listOf(
                    Triple(Screen.DOWNLOADS, "Downloads", Icons.Rounded.Download),
                    Triple(Screen.HISTORY, "History", Icons.Rounded.History),
                    Triple(Screen.DETECTED, "Media", Icons.Rounded.VideoLibrary),
                    Triple(Screen.SETTINGS, "Settings", Icons.Rounded.Settings)
                )
            ) { (screen, label, icon) ->
                val badgeCount = when (screen) {
                    Screen.DOWNLOADS -> activeDownloadCount
                    Screen.DETECTED -> detectedCount
                    else -> 0
                }
                MenuTile(
                    icon = icon,
                    label = label,
                    badgeCount = badgeCount,
                    onClick = { onSelect(screen) }
                )
            }
        }
    }
}

@Composable
private fun MenuTile(icon: ImageVector, label: String, badgeCount: Int, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp)
    ) {
        // The standard Material You "tonal icon" pairing (primaryContainer behind,
        // onPrimaryContainer for the glyph) rather than a neutral surfaceVariant circle — these
        // four tiles are the menu's only content, so giving them real color is what makes the
        // sheet look designed rather than a plain list of icons.
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                val tint = MaterialTheme.colorScheme.onPrimaryContainer
                if (badgeCount > 0) {
                    BadgedBox(badge = { Badge { Text(badgeCount.coerceAtMost(99).toString()) } }) {
                        Icon(icon, contentDescription = null, tint = tint)
                    }
                } else {
                    Icon(icon, contentDescription = null, tint = tint)
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
