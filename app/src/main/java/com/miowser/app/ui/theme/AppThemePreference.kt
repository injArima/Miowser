package com.miowser.app.ui.theme

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppThemeMode(val label: String) {
    SYSTEM("System"), LIGHT("Light"), DARK("Dark"), AMOLED("AMOLED")
}

/** A small, app-wide preference so the browser menu can change the theme immediately. */
object AppThemePreference {
    private const val PREFS = "appearance"
    private const val KEY_MODE = "theme_mode"
    private const val KEY_COLOR_THEME = "color_theme"

    var mode by mutableStateOf(AppThemeMode.SYSTEM)
        private set
    var colorTheme by mutableStateOf(AppColorTheme.WALLPAPER)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all
        mode = ThemeSelectionCodec.mode(saved[KEY_MODE])
        colorTheme = ThemeSelectionCodec.palette(saved[KEY_COLOR_THEME])
    }

    fun set(context: Context, selected: AppThemeMode) {
        if (mode == selected) return
        mode = selected
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, selected.name).apply()
    }

    fun setColorTheme(context: Context, selected: AppColorTheme) {
        if (colorTheme == selected) return
        colorTheme = selected
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_COLOR_THEME, selected.name).apply()
    }
}

/** Stable enum identifiers survive label changes; malformed/older preferences keep defaults. */
internal object ThemeSelectionCodec {
    fun mode(saved: Any?): AppThemeMode = AppThemeMode.entries.firstOrNull { it.name == saved }
        ?: AppThemeMode.SYSTEM
    fun palette(saved: Any?): AppColorTheme = AppColorTheme.entries.firstOrNull { it.name == saved }
        ?: AppColorTheme.WALLPAPER
}
