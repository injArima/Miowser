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

    var mode by mutableStateOf(AppThemeMode.SYSTEM)
        private set

    fun load(context: Context) {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, null)
        mode = AppThemeMode.entries.firstOrNull { it.name == saved } ?: AppThemeMode.SYSTEM
    }

    fun set(context: Context, selected: AppThemeMode) {
        mode = selected
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_MODE, selected.name).apply()
    }
}
