package com.miowser.app.player

import android.content.Context

/** Player-only display choices, shared across videos and restored on the next player opening. */
internal object PlayerDisplayPreferenceStore {
    private const val PREFS = "player_display_preferences"
    private const val SCALE_MODE = "scale_mode"
    private const val ORIENTATION = "orientation"
    private const val BRIGHTNESS = "brightness"
    private const val PLAYBACK_SPEED = "playback_speed"

    fun playbackSpeed(context: Context): Float? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getFloat(PLAYBACK_SPEED, Float.NaN)
            .takeIf(::validPlayerPlaybackSpeed)

    fun savePlaybackSpeed(context: Context, value: Float) {
        if (!validPlayerPlaybackSpeed(value)) return
        // A video switch can happen before the ordered disk writer runs. apply() publishes
        // this small preference immediately and lets Android write it asynchronously.
        com.miowser.app.persistence.RestoreWriteFence.write {
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putFloat(PLAYBACK_SPEED, value).apply()
        }
    }

    fun scaleMode(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(SCALE_MODE, null)

    fun saveScaleMode(context: Context, name: String) {
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("player-display") {
        app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(SCALE_MODE, name).commit()
        }
    }

    fun orientation(context: Context): PlayerOrientationMode {
        val name = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ORIENTATION, null)
        return PlayerOrientationMode.entries.firstOrNull { it.name == name } ?: PlayerOrientationMode.AUTOMATIC
    }

    fun saveOrientation(context: Context, mode: PlayerOrientationMode) {
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("player-display") {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ORIENTATION, mode.name).commit()
        }
    }

    fun brightness(context: Context): Float? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(BRIGHTNESS, -1f).takeIf { it in 0.01f..1f }

    fun saveBrightness(context: Context, value: Float?) {
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("player-display") {
            val editor = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            if (value == null) editor.remove(BRIGHTNESS)
            else editor.putFloat(BRIGHTNESS, value.coerceIn(0.01f, 1f))
            editor.commit()
        }
    }
}
