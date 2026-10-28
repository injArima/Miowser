package com.miowser.app.settings

import android.content.Context
import com.miowser.app.persistence.Persistence

private const val PREFS_NAME = "settings"
private const val KEY_AD_BLOCK_ENABLED = "ad_block_enabled"
private const val KEY_HOME_PAGE_URL = "home_page_url"
private const val KEY_HOME_PAGE_MODE = "home_page_mode"
private const val KEY_SEARCH_ENGINE = "search_engine"
private const val KEY_CUSTOM_SEARCH_URL = "custom_search_url"
private const val KEY_RESTORE_TABS_ON_STARTUP = "restore_tabs_on_startup"
private const val KEY_NEW_TAB_CONFIRMATION = "new_tab_confirmation"
private const val KEY_DOWNLOAD_THREAD_COUNT = "download_thread_count"
private const val KEY_JAVASCRIPT_ENABLED = "javascript_enabled"
private const val KEY_COOKIES_ENABLED = "cookies_enabled"
private const val KEY_POPUPS_ENABLED = "popups_enabled"
private const val KEY_GRABBER_MINIMUM_DURATION_SECONDS = "grabber_minimum_duration_seconds"
private const val KEY_AUTO_DELETE_INACTIVE_TABS = "auto_delete_inactive_tabs"
private const val KEY_INACTIVE_TAB_RETENTION = "inactive_tab_retention"
private const val KEY_REDUCE_MOTION = "reduce_motion"
private const val KEY_BACKGROUND_PLAYBACK_ENABLED = "background_playback_enabled"
private const val KEY_CRASH_DIAGNOSTICS_ENABLED = "crash_diagnostics_enabled"

/** Thin SharedPreferences wrapper — one flat record, so plain typed keys rather than a codec. */
object SettingsStore {
    fun load(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = Settings()
        val savedEngine = prefs.getString(KEY_SEARCH_ENGINE, null)
            ?.let { name -> runCatching { SearchEngine.valueOf(name) }.getOrNull() }
        val savedHomePageMode = prefs.getString(KEY_HOME_PAGE_MODE, null)
            ?.let { name -> runCatching { HomePageMode.valueOf(name) }.getOrNull() }
        val savedNewTabConfirmation = prefs.getString(KEY_NEW_TAB_CONFIRMATION, null)
            ?.let { name -> runCatching { NewTabConfirmation.valueOf(name) }.getOrNull() }
        val savedThreadCount = prefs.getString(KEY_DOWNLOAD_THREAD_COUNT, null)
            ?.let { name -> runCatching { DownloadThreadCount.valueOf(name) }.getOrNull() }
        val savedRetention = prefs.getString(KEY_INACTIVE_TAB_RETENTION, null)
            ?.let { name -> runCatching { InactiveTabRetention.valueOf(name) }.getOrNull() }
        val legacyRetention = if (prefs.contains(KEY_AUTO_DELETE_INACTIVE_TABS)) {
            if (prefs.getBoolean(KEY_AUTO_DELETE_INACTIVE_TABS, true)) InactiveTabRetention.DAYS_30
            else InactiveTabRetention.NEVER
        } else defaults.inactiveTabRetention
        return Settings(
            adBlockEnabled = prefs.getBoolean(KEY_AD_BLOCK_ENABLED, defaults.adBlockEnabled),
            homePageUrl = prefs.getString(KEY_HOME_PAGE_URL, defaults.homePageUrl)
                ?.takeIf { it.isNotBlank() } ?: defaults.homePageUrl,
            homePageMode = savedHomePageMode ?: defaults.homePageMode,
            searchEngine = savedEngine ?: defaults.searchEngine,
            customSearchUrl = prefs.getString(KEY_CUSTOM_SEARCH_URL, null)
                ?.takeIf(::isValidCustomSearchUrl) ?: defaults.customSearchUrl,
            restoreTabsOnStartup = prefs.getBoolean(KEY_RESTORE_TABS_ON_STARTUP, defaults.restoreTabsOnStartup),
            newTabConfirmation = savedNewTabConfirmation ?: defaults.newTabConfirmation,
            downloadThreadCount = savedThreadCount ?: defaults.downloadThreadCount,
            javaScriptEnabled = prefs.getBoolean(KEY_JAVASCRIPT_ENABLED, defaults.javaScriptEnabled),
            cookiesEnabled = prefs.getBoolean(KEY_COOKIES_ENABLED, defaults.cookiesEnabled),
            popupsEnabled = prefs.getBoolean(KEY_POPUPS_ENABLED, defaults.popupsEnabled),
            grabberMinimumDurationSeconds = prefs.getInt(
                KEY_GRABBER_MINIMUM_DURATION_SECONDS, defaults.grabberMinimumDurationSeconds
            ).coerceIn(0, 1_800),
            inactiveTabRetention = savedRetention ?: legacyRetention,
            reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, defaults.reduceMotion),
            backgroundPlaybackEnabled = prefs.getBoolean(
                KEY_BACKGROUND_PLAYBACK_ENABLED, defaults.backgroundPlaybackEnabled
            ),
            crashDiagnosticsEnabled = prefs.getBoolean(
                KEY_CRASH_DIAGNOSTICS_ENABLED, defaults.crashDiagnosticsEnabled
            )
        )
    }

    fun save(context: Context, settings: Settings) {
        Persistence.requireOwner()
        val appContext = context.applicationContext
        Persistence.submitSnapshot("settings") {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_AD_BLOCK_ENABLED, settings.adBlockEnabled)
                .putString(KEY_HOME_PAGE_URL, settings.homePageUrl)
                .putString(KEY_HOME_PAGE_MODE, settings.homePageMode.name)
                .putString(KEY_SEARCH_ENGINE, settings.searchEngine.name)
                .putString(KEY_CUSTOM_SEARCH_URL, settings.customSearchUrl)
                .putBoolean(KEY_RESTORE_TABS_ON_STARTUP, settings.restoreTabsOnStartup)
                .putString(KEY_NEW_TAB_CONFIRMATION, settings.newTabConfirmation.name)
                .putString(KEY_DOWNLOAD_THREAD_COUNT, settings.downloadThreadCount.name)
                .putBoolean(KEY_JAVASCRIPT_ENABLED, settings.javaScriptEnabled)
                .putBoolean(KEY_COOKIES_ENABLED, settings.cookiesEnabled)
                .putBoolean(KEY_POPUPS_ENABLED, settings.popupsEnabled)
                .putInt(KEY_GRABBER_MINIMUM_DURATION_SECONDS, settings.grabberMinimumDurationSeconds.coerceIn(0, 1_800))
                .putString(KEY_INACTIVE_TAB_RETENTION, settings.inactiveTabRetention.name)
                .putBoolean(KEY_REDUCE_MOTION, settings.reduceMotion)
                .putBoolean(KEY_BACKGROUND_PLAYBACK_ENABLED, settings.backgroundPlaybackEnabled)
                .putBoolean(KEY_CRASH_DIAGNOSTICS_ENABLED, settings.crashDiagnosticsEnabled)
                .commit()
        }
    }

    /** Minimal startup read used before the full settings repository is restored. */
    fun crashDiagnosticsEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_CRASH_DIAGNOSTICS_ENABLED, false)
}
