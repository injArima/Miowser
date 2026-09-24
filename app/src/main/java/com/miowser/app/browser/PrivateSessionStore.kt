package com.miowser.app.browser

import android.content.Context

/** Crash marker: if process death skips the close path, clear shared website data on next launch. */
object PrivateSessionStore {
    private const val PREFS = "private_session"
    private const val KEY_ACTIVE = "active"

    fun wasInterrupted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, active: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active).commit()
    }
}
