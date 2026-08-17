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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
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

/** The app's full-page destinations, each reached via [OverflowMenuSheet] or (for Bookmarks) BottomBar's own button. */
enum class Screen { DOWNLOADS, HISTORY, BOOKMARKS, SETTINGS }

/**
 * The hamburger button's menu — a grid of icon+label tiles rather than a plain dropdown list,
 * concept borrowed from Viowser's own overflow menu (not its exact styling). Populated only with
 * destinations that actually exist right now: Bookmarks already has its own BottomBar button, so
 * it isn't duplicated here. As more "crucial components" arrive later (per the same request that
 * moved these off transient sheets and onto real pages), they slot in as additional grid items.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverflowMenuSheet(
    activeDownloadCount: Int,
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
                    Triple(Screen.DOWNLOADS, "Downloads", Icons.Default.KeyboardArrowDown),
                    Triple(Screen.HISTORY, "History", Icons.Default.List),
                    Triple(Screen.SETTINGS, "Settings", Icons.Default.Settings)
                )
            ) { (screen, label, icon) ->
                MenuTile(
                    icon = icon,
                    label = label,
                    badgeCount = if (screen == Screen.DOWNLOADS) activeDownloadCount else 0,
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
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.size(56.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                if (badgeCount > 0) {
                    BadgedBox(badge = { Badge { Text(badgeCount.coerceAtMost(99).toString()) } }) {
                        Icon(icon, contentDescription = null)
                    }
                } else {
                    Icon(icon, contentDescription = null)
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
