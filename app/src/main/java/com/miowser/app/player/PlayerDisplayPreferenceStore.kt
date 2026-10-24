package com.miowser.app.player

import android.content.Context

/** Player-only display choices, shared across videos and restored on the next player opening. */
internal object PlayerDisplayPreferenceStore {
    private const val PREFS = "player_display_preferences"
    private const val SCALE_MODE = "scale_mode"
    private const val BRIGHTNESS = "brightness"

    fun scaleMode(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SCALE_MODE, null)

    fun saveScaleMode(context: Context, name: String) {
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("player-display") {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SCALE_MODE, name).commit()
        }
    }

    fun brightness(context: Context): Float? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(BRIGHTNESS, -1f).takeIf { it in 0.01f..1f }

    fun saveBrightness(context: Context, value: Float) {
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("player-display") {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putFloat(BRIGHTNESS, value.coerceIn(0.01f, 1f)).commit()
        }
    }
}
