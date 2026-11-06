package com.miowser.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.theme.UiIconSize

data class TabNavigationEntry(
    val index: Int,
    val title: String,
    val url: String,
    val favicon: Bitmap?,
    val current: Boolean
)

@Composable
internal fun TabNavigationHistoryMenu(
    expanded: Boolean,
    entries: List<TabNavigationEntry>,
    onDismiss: () -> Unit,
    onSelect: (TabNavigationEntry) -> Unit,
    onOpenHistory: () -> Unit
) {
    MiowserDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 260.dp, max = 320.dp).heightIn(max = 376.dp)
    ) {
        entries.forEach { entry ->
            MiowserMenuItem(
                text = {
                    Text(
                        text = entry.title.ifBlank { displayNavigationUrl(entry.url) },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (entry.current) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { NavigationSiteIcon(entry.url, entry.favicon) },
                trailingIcon = if (entry.current) {
                    { Icon(Icons.Rounded.Check, contentDescription = "Current page") }
                } else null,
                selected = entry.current,
                onClick = { onSelect(entry) }
            )
        }
        MiowserMenuDivider()
        MiowserMenuItem(
            text = { Text("History") },
            leadingIcon = { Icon(Icons.Rounded.History, contentDescription = null) },
            onClick = onOpenHistory
        )
    }
}

@Composable
private fun NavigationSiteIcon(url: String, favicon: Bitmap?) {
    if (url.startsWith("miowser://") || url == "about:blank") {
        Icon(Icons.Rounded.Home, contentDescription = null, modifier = Modifier.size(UiIconSize.menu))
        return
    }
    Box(Modifier.size(UiIconSize.menu), contentAlignment = Alignment.Center) {
        if (favicon != null) {
            Image(
                bitmap = favicon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(UiIconSize.menu),
                contentScale = ContentScale.Fit
            )
        } else {
            Icon(Icons.Rounded.Language, contentDescription = null, modifier = Modifier.size(UiIconSize.menu))
        }
    }
}

private fun displayNavigationUrl(url: String): String = url
    .removePrefix("https://")
    .removePrefix("http://")
    .removePrefix("www.")
    .removeSuffix("/")
