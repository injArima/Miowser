package com.miowser.app.browser

import android.content.Context

/** Crash marker: if process death skips the close path, clear shared website data on next launch. */
object PrivateSessionStore {
    private const val PREFS = "private_session"
    private const val KEY_ACTIVE = "active"

    fun wasInterrupted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_ACTIVE, false)

    fun setActive(context: Context, active: Boolean): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVE, active).commit()

    /** Queue on the UI owner; opening a private tab still waits for durable completion. */
    fun saveActive(context: Context, active: Boolean): java.util.concurrent.CompletableFuture<Boolean> {
        val app = context.applicationContext
        return com.miowser.app.persistence.Persistence.submit("private-session") { setActive(app, active) }
    }
}
