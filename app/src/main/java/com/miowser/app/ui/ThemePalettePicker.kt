package com.miowser.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Wallpaper
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.miowser.app.ui.AppText as Text
import com.miowser.app.ui.theme.AppColorTheme
import com.miowser.app.ui.theme.AppThemeMode
import com.miowser.app.ui.theme.AppThemePreference
import com.miowser.app.ui.theme.ThemePickerTokens
import com.miowser.app.ui.theme.UiIconSize
import com.miowser.app.ui.theme.UiSpacing
import com.miowser.app.ui.theme.familyOrderedSwatches

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
            item(key = "palette-gallery", contentType = "gallery") {
                ThemePaletteGallery()
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
    onClick: () -> Unit
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
            // Let names wrap and rows grow with the user's font size.
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Box(Modifier.size(UiIconSize.standard), contentAlignment = Alignment.Center) {
                if (selected) Icon(Icons.Rounded.Check, contentDescription = null)
            }
        }
    }
}

/** Equal-height cards grow with font scale and the palette with the most swatches. */
@Composable
private fun ThemePaletteGallery() {
    val context = LocalContext.current
    val initialScroll = with(LocalDensity.current) {
        ((ThemePickerTokens.cardWidth + ThemePickerTokens.cardGap) * AppThemePreference.colorTheme.ordinal)
            .roundToPx()
    }
    val scrollState = rememberScrollState(initialScroll)
    Row(
        modifier = Modifier.fillMaxWidth()
            .horizontalScroll(scrollState)
            .height(IntrinsicSize.Min)
            .padding(vertical = UiSpacing.xs)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ThemePickerTokens.cardGap)
    ) {
        AppColorTheme.entries.forEach { palette ->
            val selected = AppThemePreference.colorTheme == palette
            val containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow
            Surface(
                modifier = Modifier.width(ThemePickerTokens.cardWidth).fillMaxHeight(),
                shape = MaterialTheme.shapes.large,
                color = containerColor,
                contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                    else MaterialTheme.colorScheme.onSurface,
                border = BorderStroke(
                    if (selected) ThemePickerTokens.selectedBorderWidth else ThemePickerTokens.borderWidth,
                    if (selected) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxHeight()
                        .heightIn(min = ThemePickerTokens.cardMinimumHeight)
                        .selectable(selected = selected, role = Role.RadioButton,
                            onClick = { AppThemePreference.setColorTheme(context, palette) })
                        .padding(ThemePickerTokens.cardPadding),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(UiSpacing.m, Alignment.CenterVertically)
                ) {
                    if (palette == AppColorTheme.WALLPAPER) {
                        Box(Modifier.size(ThemePickerTokens.swatchSize + ThemePickerTokens.swatchSeparatorWidth * 2),
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.Wallpaper, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    } else {
                        ThemePaletteSwatches(palette.familyOrderedSwatches(), containerColor)
                    }
                    Text(palette.label, modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

/** Front-to-back circular swatches; card-colored rims separate each overlap. */
@Composable
private fun ThemePaletteSwatches(swatches: List<Color>, separatorColor: Color) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(-ThemePickerTokens.swatchOverlap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        swatches.forEachIndexed { index, color ->
            Box(Modifier
                .size(ThemePickerTokens.swatchSize + ThemePickerTokens.swatchSeparatorWidth * 2)
                // The first family stays fully circular in front of the following colors.
                // Row handles start/end placement for both LTR and RTL layouts.
                .zIndex((swatches.size - index).toFloat())
                .clip(ThemePickerTokens.swatchShape)
                .background(separatorColor)
                .padding(ThemePickerTokens.swatchSeparatorWidth)
                .clip(ThemePickerTokens.swatchShape)
                .background(color))
        }
    }
}
