package com.miowser.app.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import com.miowser.app.ui.AppText as Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.miowser.app.player.*
import com.miowser.app.ui.theme.SubtitlePanelTokens
import com.miowser.app.ui.theme.UiControlSize
import com.miowser.app.ui.theme.UiCorners
import com.miowser.app.ui.theme.UiSpacing

@Composable
internal fun SubtitleAppearanceControls(
    style: PlayerSubtitleStyle,
    onChange: (PlayerSubtitleStyle) -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val unselected = MaterialTheme.colorScheme.surfaceContainerHigh
    SubtitleAppearanceStepper(accent, "Font size", "${(style.size * 100).toInt()}%",
        { onChange(style.copy(size = (style.size - 0.1f).coerceAtLeast(0.7f))) },
        { onChange(style.copy(size = (style.size + 0.1f).coerceAtMost(1.6f))) })
    SubtitleAppearanceToggle(accent, secondary, "Bold", style.bold) { onChange(style.copy(bold = it)) }
    Text("Text color", style = MaterialTheme.typography.labelMedium)
    SubtitleAppearanceColors(
        accent,
        listOf(AndroidColor.WHITE, AndroidColor.YELLOW, AndroidColor.CYAN,
            AndroidColor.RED, AndroidColor.GREEN), style.resolvedTextColor()
    ) { onChange(style.copy(textColor = it)) }
    SubtitleAppearanceStepper(accent, "Text opacity", "${style.textOpacity}%",
        { onChange(style.copy(textOpacity = (style.textOpacity - 10).coerceAtLeast(10))) },
        { onChange(style.copy(textOpacity = (style.textOpacity + 10).coerceAtMost(100))) })
    SubtitleAppearanceToggle(accent, secondary, "Outline", style.resolvedOutlineEnabled()) {
        onChange(style.copy(outlineEnabled = it))
    }
    if (style.resolvedOutlineEnabled()) {
        Text("Outline color", style = MaterialTheme.typography.labelMedium)
        SubtitleAppearanceColors(accent, listOf(AndroidColor.BLACK, AndroidColor.WHITE,
            AndroidColor.CYAN, AndroidColor.RED), style.outlineColor) {
            onChange(style.copy(outlineColor = it))
        }
    }
    SubtitleAppearanceStepper(accent, "Bottom offset", "${(style.bottomPadding * 100).toInt()}%",
        { onChange(style.copy(bottomPadding = (style.bottomPadding - 0.01f).coerceAtLeast(0.03f))) },
        { onChange(style.copy(bottomPadding = (style.bottomPadding + 0.01f).coerceAtMost(0.35f))) })
    Text("Caption preset", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
        CaptionVisualPreset.entries.forEach { preset ->
            Surface(onClick = {
                onChange(style.copy(preset = preset, textColor = null, outlineEnabled = null))
            }, shape = UiCorners.r8,
                color = if (style.preset == preset) MaterialTheme.colorScheme.primaryContainer
                    else unselected) {
                Text(preset.label, modifier = Modifier.padding(horizontal = UiSpacing.compact, vertical = UiSpacing.s),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Text("Font", style = MaterialTheme.typography.labelMedium)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(UiSpacing.xs)) {
        CaptionFontChoice.entries.forEach { font ->
            Surface(onClick = { onChange(style.copy(font = font)) },
                shape = UiCorners.r8,
                color = if (style.font == font) MaterialTheme.colorScheme.primaryContainer
                    else unselected) {
                Text(font.label, modifier = Modifier.padding(horizontal = UiSpacing.compact, vertical = UiSpacing.s),
                    style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    TextButton(onClick = { onChange(PlayerSubtitleStyle(delayMs = style.delayMs)) },
        colors = ButtonDefaults.textButtonColors(contentColor = accent)) {
        Text("Reset defaults")
    }
}

@Composable
private fun SubtitleAppearanceStepper(
    accent: Color, label: String, value: String, onDecrease: () -> Unit, onIncrease: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        TextButton(onClick = onDecrease, modifier = Modifier.width(UiControlSize.compact).semantics { contentDescription = "Decrease $label" },
            colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text("−") }
        Text(value, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
            modifier = Modifier.width(SubtitlePanelTokens.valueWidth))
        TextButton(onClick = onIncrease, modifier = Modifier.width(UiControlSize.compact).semantics { contentDescription = "Increase $label" },
            colors = ButtonDefaults.textButtonColors(contentColor = accent)) { Text("+") }
    }
}

@Composable
private fun SubtitleAppearanceToggle(accent: Color, secondary: Color, label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onToggle(!checked) }.padding(vertical = UiSpacing.s),
        verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(if (checked) "On" else "Off", style = MaterialTheme.typography.bodySmall,
            color = if (checked) accent else secondary)
    }
}

@Composable
private fun SubtitleAppearanceColors(accent: Color, colors: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(UiSpacing.compact)) {
        colors.forEach { color ->
            Surface(onClick = { onSelect(color) }, shape = CircleShape,
                border = BorderStroke(if (color == selected) SubtitlePanelTokens.selectedColorBorder else SubtitlePanelTokens.colorBorder,
                    if (color == selected) accent else Color.LightGray),
                color = Color(color), modifier = Modifier.size(SubtitlePanelTokens.colorSize)) {}
        }
    }
}
