package com.miowser.app.player

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.view.Surface

/** A fixed mode uses an explicit Android orientation so a screen-off sensor reading cannot unlock it. */
internal enum class PlayerOrientationMode(val label: String, val requestedOrientation: Int) {
    AUTOMATIC("Automatic (sensor)", ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR),
    PORTRAIT("Portrait", ActivityInfo.SCREEN_ORIENTATION_PORTRAIT),
    PORTRAIT_REVERSE("Portrait reverse", ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT),
    LANDSCAPE("Landscape", ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE),
    LANDSCAPE_REVERSE("Landscape reverse", ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE),
    LANDSCAPE_SENSOR("Landscape (sensor)", ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);

    val locked: Boolean get() = this != AUTOMATIC

    companion object {
        fun lockCurrent(activity: Activity?): PlayerOrientationMode {
            if (activity == null) return LANDSCAPE
            val rotation = activity.display?.rotation ?: Surface.ROTATION_0
            val portrait = activity.resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
            val naturalPortrait = if (rotation == Surface.ROTATION_0 || rotation == Surface.ROTATION_180) portrait else !portrait
            return when {
                naturalPortrait && rotation == Surface.ROTATION_90 -> LANDSCAPE
                naturalPortrait && rotation == Surface.ROTATION_270 -> LANDSCAPE_REVERSE
                naturalPortrait && rotation == Surface.ROTATION_180 -> PORTRAIT_REVERSE
                naturalPortrait -> PORTRAIT
                rotation == Surface.ROTATION_90 -> PORTRAIT_REVERSE
                rotation == Surface.ROTATION_270 -> PORTRAIT
                rotation == Surface.ROTATION_180 -> LANDSCAPE_REVERSE
                else -> LANDSCAPE
            }
        }
    }
}
