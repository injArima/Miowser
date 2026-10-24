package com.miowser.app.player

import android.content.Context

internal const val MAX_SUBTITLE_DELAY_MS = 3_600_000L

/** Align the selected cue's start with the paused video frame. */
internal fun subtitleDelayForCue(playbackPositionMs: Long, cueStartTimeUs: Long): Long =
    (playbackPositionMs - cueStartTimeUs / 1_000L)
        .coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS)

internal enum class CaptionVisualPreset(val label: String, val sample: String) {
    STANDARD("Standard", "This is a standard caption."),
    WHITE_TRANSLUCENT("Translucent background", "White on a translucent background"),
    WHITE_OUTLINE("Black outline", "White with a black outline"),
    WHITE_SHADOW("Black shadow", "White with a black shadow"),
    YELLOW_SHADOW("Yellow with shadow", "Yellow with a black shadow"),
    YELLOW_TRANSLUCENT("Yellow on translucent", "Yellow on a translucent background")
}

internal enum class CaptionFontChoice(val label: String) {
    SYSTEM("System"),
    HELVETICA_LIKE("Helvetica · Arimo substitute"),
    ROBOTO("Roboto"),
    AVENIR_LIKE("Avenir · Nunito Sans substitute"),
    FREDOKA_ONE("Fredoka One"),
    LOBSTER("Lobster")
}

/** Global caption appearance; a video does not need to be saved in the library to use it. */
internal data class PlayerSubtitleStyle(
    val size: Float = 1f,
    val bottomPadding: Float = 0.12f,
    val preset: CaptionVisualPreset = CaptionVisualPreset.WHITE_OUTLINE,
    val font: CaptionFontChoice = CaptionFontChoice.SYSTEM,
    val delayMs: Long = 0L,
    val bold: Boolean = false,
    val textColor: Int? = null,
    val textOpacity: Int = 100,
    val outlineEnabled: Boolean? = null,
    val outlineColor: Int = android.graphics.Color.BLACK
)

internal fun PlayerSubtitleStyle.resolvedTextColor(): Int = textColor ?: when (preset) {
    CaptionVisualPreset.YELLOW_SHADOW, CaptionVisualPreset.YELLOW_TRANSLUCENT -> android.graphics.Color.YELLOW
    else -> android.graphics.Color.WHITE
}

internal fun PlayerSubtitleStyle.resolvedOutlineEnabled(): Boolean =
    outlineEnabled ?: (preset == CaptionVisualPreset.WHITE_OUTLINE)

/** A positive delay shows a cue later than its VTT timestamp. */
internal fun PlayerSubtitleStyle.vttPositionMs(playbackPositionMs: Long): Long =
    (playbackPositionMs - delayMs).coerceAtLeast(0L)

internal object PlayerSubtitleStyleStore {
    private const val PREFS = "player_subtitle_style"

    fun load(context: Context): PlayerSubtitleStyle = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).run {
        PlayerSubtitleStyle(
            size = getFloat("size", 1f).coerceIn(0.7f, 1.6f),
            bottomPadding = getFloat("bottom_padding", 0.12f).coerceIn(0.03f, 0.35f),
            preset = getString("preset", null)
                ?.let { runCatching { CaptionVisualPreset.valueOf(it) }.getOrNull() }
                ?: legacyPreset(
                    yellow = getBoolean("yellow_text", false),
                    background = getBoolean("background", false),
                    shadow = getBoolean("shadow", false)
                ),
            font = getString("font", null)
                ?.let { runCatching { CaptionFontChoice.valueOf(it) }.getOrNull() }
                ?: CaptionFontChoice.SYSTEM,
            delayMs = getLong("delay_ms", 0L).coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS),
            bold = getBoolean("bold", false),
            textColor = if (contains("text_color")) getInt("text_color", android.graphics.Color.WHITE) else null,
            textOpacity = getInt("text_opacity", 100).coerceIn(10, 100),
            outlineEnabled = if (contains("outline_enabled")) getBoolean("outline_enabled", false) else null,
            outlineColor = getInt("outline_color", android.graphics.Color.BLACK)
        )
    }

    fun save(context: Context, style: PlayerSubtitleStyle) {
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submitSnapshot("subtitle-style") {
        val editor = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat("size", style.size.coerceIn(0.7f, 1.6f))
            .putFloat("bottom_padding", style.bottomPadding.coerceIn(0.03f, 0.35f))
            .putString("preset", style.preset.name)
            .putString("font", style.font.name)
            .putLong("delay_ms", style.delayMs.coerceIn(-MAX_SUBTITLE_DELAY_MS, MAX_SUBTITLE_DELAY_MS))
            .putBoolean("bold", style.bold)
            .putInt("text_opacity", style.textOpacity.coerceIn(10, 100))
            .putInt("outline_color", style.outlineColor)
        if (style.textColor == null) editor.remove("text_color") else editor.putInt("text_color", style.textColor)
        if (style.outlineEnabled == null) editor.remove("outline_enabled")
        else editor.putBoolean("outline_enabled", style.outlineEnabled)
        editor.commit()
        }
    }
}

internal fun legacyPreset(yellow: Boolean, background: Boolean, shadow: Boolean): CaptionVisualPreset = when {
    yellow && background -> CaptionVisualPreset.YELLOW_TRANSLUCENT
    yellow -> CaptionVisualPreset.YELLOW_SHADOW
    background -> CaptionVisualPreset.WHITE_TRANSLUCENT
    shadow -> CaptionVisualPreset.WHITE_SHADOW
    else -> CaptionVisualPreset.WHITE_OUTLINE
}
