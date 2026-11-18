package com.miowser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.miowser.app.ui.AppText as Text
import com.miowser.app.ui.theme.AppColorTheme
import com.miowser.app.ui.theme.AppThemeMode
import com.miowser.app.ui.theme.AppThemePreference
import com.miowser.app.ui.theme.ThemePickerTokens
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing

/** Shared picker; palette and light/dark mode are saved independently and applied immediately. */
@Composable
internal fun ThemePalettePicker(onDismissRequest: () -> Unit) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val maximumListHeight = LocalConfiguration.current.screenHeightDp.dp * ThemePickerTokens.listHeightFraction
    ExpressiveBottomSheet(onDismissRequest = onDismissRequest) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth()
                .weight(1f, fill = false)
                .heightIn(max = maximumListHeight)
                .clipToBounds()
                .sheetVerticalEdgeFade(listState, MaterialTheme.colorScheme.surface)
                .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ThemePickerTokens.choiceGap)
        ) {
            item(key = "heading", contentType = "heading") {
                ExpressiveSheetHeading(
                    title = "Theme",
                    description = "${AppThemePreference.colorTheme.label} · ${AppThemePreference.mode.label}"
                )
            }
            item(key = "mode", contentType = "mode") {
                Column(
                    modifier = Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(ThemePickerTokens.choiceGap)
                ) {
                    ThemePickerSectionHeading("Appearance")
                    AppThemeMode.entries.forEach { mode ->
                        ThemePickerChoice(
                            label = mode.label,
                            selected = AppThemePreference.mode == mode,
                            onClick = { AppThemePreference.set(context, mode) }
                        )
                    }
                }
            }
            item(key = "palettes", contentType = "heading") {
                ThemePickerSectionHeading("Color theme")
            }
            items(AppColorTheme.entries, key = { it.name }, contentType = { "palette" }) { palette ->
                ThemePickerChoice(
                    label = palette.label,
                    selected = AppThemePreference.colorTheme == palette,
                    onClick = { AppThemePreference.setColorTheme(context, palette) },
                    preview = { ThemePaletteSwatches(palette.swatches) }
                )
            }
        }
        TextButton(
            onClick = onDismissRequest,
            modifier = Modifier.fillMaxWidth().heightIn(min = ThemePickerTokens.minimumHeight)
        ) { Text("Done") }
    }
}

@Composable
private fun ThemePickerSectionHeading(label: String) {
    Text(
        label,
        modifier = Modifier.padding(vertical = UiSpacing.s).semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ThemePickerChoice(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    preview: (@Composable () -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
            else MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
                .heightIn(min = ThemePickerTokens.minimumHeight)
                .padding(ThemePickerTokens.rowPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(UiSpacing.m)
        ) {
            preview?.invoke()
            // Let names wrap and rows grow with the user's font size.
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.size(UiIconSize.standard), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null)
            }
        }
    }
}

/** Catalog source colors are decorative previews, never text or control colors. */
@Composable
private fun ThemePaletteSwatches(swatches: List<Color>) {
    Row(
        modifier = Modifier.size(ThemePickerTokens.previewWidth, ThemePickerTokens.previewHeight)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        swatches.forEach { color ->
            Box(Modifier.weight(1f).fillMaxHeight().background(color))
        }
    }
}
