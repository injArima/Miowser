package com.miowser.app.ui

import android.content.Context

/** Remembers whether the first-run guide has already been dismissed or completed. */
object OnboardingStore {
    private const val PREFS = "onboarding"
    private const val KEY_COMPLETED = "guide_completed"

    fun shouldShow(context: Context): Boolean {
        val app = context.applicationContext
        val preferences = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (preferences.getBoolean(KEY_COMPLETED, false)) return false

        // This feature was added after Miowser already had users. Do not interrupt an existing
        // installation merely because its older build never had an onboarding preference.
        val packageInfo = runCatching { app.packageManager.getPackageInfo(app.packageName, 0) }.getOrNull()
        val isUpdatedInstall = packageInfo != null && packageInfo.lastUpdateTime > packageInfo.firstInstallTime
        if (isUpdatedInstall) {
            preferences.edit().putBoolean(KEY_COMPLETED, true).apply()
            return false
        }
        return true
    }

    fun complete(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_COMPLETED, true)
            .apply()
    }
}
