package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.Role
import com.miowser.app.library.device.DeviceMediaAccess
import com.miowser.app.ui.AppButtonStyle
import com.miowser.app.ui.ExpressiveBottomSheet
import com.miowser.app.ui.ExpressiveEmptyState
import com.miowser.app.ui.ExpressiveSectionCard
import com.miowser.app.ui.ExpressiveSheetHeading
import com.miowser.app.ui.MiowserActionButton
import com.miowser.app.ui.theme.ExpressiveTokens
import com.miowser.app.ui.theme.UiSpacing

@Composable
internal fun LibraryOptionsSheet(
    description: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpressiveBottomSheet(onDismissRequest = onDismiss) {
        ExpressiveSheetHeading("Library settings", description)
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(ExpressiveTokens.itemGap),
                content = content
            )
        }
    }
}

@Composable
internal fun LibraryOptionsGroup(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    ExpressiveSectionCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
            Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.m),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            content()
        }
    }
}

@Composable
internal fun LibraryOptionAction(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    ListItem(
        leadingContent = { Icon(icon, contentDescription = null, tint = tint) },
        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = tint) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled, role = Role.Button, onClick = onClick)
    ) { Text(label, color = tint) }
}

@Composable
internal fun DeviceLibraryEmptyState(
    title: String,
    access: DeviceMediaAccess,
    onLinkFolder: () -> Unit,
    onAllowAccess: () -> Unit
) {
    Column(Modifier.verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(UiSpacing.s)) {
        ExpressiveEmptyState(
            title = title,
            description = if (access == DeviceMediaAccess.FULL) "Link another folder to include its videos."
                else "Link a folder or grant access to device videos.",
            icon = Icons.Rounded.VideoLibrary
        )
        MiowserActionButton(style = AppButtonStyle.TONAL, onClick = onLinkFolder) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null)
            Text("Link folder", Modifier.padding(start = UiSpacing.s))
        }
        if (access != DeviceMediaAccess.FULL) {
            MiowserActionButton(style = AppButtonStyle.OUTLINED, onClick = onAllowAccess) {
                Text("Allow all videos")
            }
        }
    }
}
