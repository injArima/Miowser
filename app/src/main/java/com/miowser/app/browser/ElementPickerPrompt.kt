package com.miowser.app.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.Icon
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiCorners
import com.miowser.app.ui.theme.UiElevation
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.AppButtonStyle
import com.miowser.app.ui.MiowserActionButton

@Composable
fun ElementPickerPrompt(onCancel: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(
        modifier = modifier.fillMaxWidth().padding(horizontal = UiSpacing.l),
        shape = MaterialTheme.shapes.large,
        color = colors.surfaceContainerHigh,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.outlineVariant),
        shadowElevation = UiElevation.high
    ) {
        Row(
            modifier = Modifier.padding(start = UiSpacing.m, end = UiSpacing.s, top = UiSpacing.compact, bottom = UiSpacing.compact),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.m)
        ) {
            Surface(
                shape = UiCorners.r14,
                color = colors.primaryContainer,
                contentColor = colors.onPrimaryContainer
            ) {
                Box(Modifier.size(UiControlSize.action), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.SelectAll, contentDescription = null, modifier = Modifier.size(UiIconSize.standard))
                }
            }
            Column(Modifier.weight(1f)) {
                Text("Choose an element", style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text("Tap a page element to select it", style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant)
            }
            MiowserActionButton(
                onClick = onCancel,
                style = AppButtonStyle.CAUTION
            ) {
                Text("Cancel")
            }
        }
    }
}
