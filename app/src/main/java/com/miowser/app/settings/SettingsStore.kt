package com.miowser.app.settings

import android.content.Context

private const val PREFS_NAME = "settings"
private const val KEY_AD_BLOCK_ENABLED = "ad_block_enabled"
private const val KEY_HOME_PAGE_URL = "home_page_url"
private const val KEY_HOME_PAGE_MODE = "home_page_mode"
private const val KEY_SEARCH_ENGINE = "search_engine"
private const val KEY_RESTORE_TABS_ON_STARTUP = "restore_tabs_on_startup"
private const val KEY_NEW_TAB_CONFIRMATION = "new_tab_confirmation"
private const val KEY_DOWNLOAD_THREAD_COUNT = "download_thread_count"
private const val KEY_JAVASCRIPT_ENABLED = "javascript_enabled"
private const val KEY_COOKIES_ENABLED = "cookies_enabled"
private const val KEY_POPUPS_ENABLED = "popups_enabled"
private const val KEY_GRABBER_MINIMUM_DURATION_SECONDS = "grabber_minimum_duration_seconds"
private const val KEY_AUTO_DELETE_INACTIVE_TABS = "auto_delete_inactive_tabs"

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
        return Settings(
            adBlockEnabled = prefs.getBoolean(KEY_AD_BLOCK_ENABLED, defaults.adBlockEnabled),
            homePageUrl = prefs.getString(KEY_HOME_PAGE_URL, defaults.homePageUrl)
                ?.takeIf { it.isNotBlank() } ?: defaults.homePageUrl,
            homePageMode = savedHomePageMode ?: defaults.homePageMode,
            searchEngine = savedEngine ?: defaults.searchEngine,
            restoreTabsOnStartup = prefs.getBoolean(KEY_RESTORE_TABS_ON_STARTUP, defaults.restoreTabsOnStartup),
            newTabConfirmation = savedNewTabConfirmation ?: defaults.newTabConfirmation,
            downloadThreadCount = savedThreadCount ?: defaults.downloadThreadCount,
            javaScriptEnabled = prefs.getBoolean(KEY_JAVASCRIPT_ENABLED, defaults.javaScriptEnabled),
            cookiesEnabled = prefs.getBoolean(KEY_COOKIES_ENABLED, defaults.cookiesEnabled),
            popupsEnabled = prefs.getBoolean(KEY_POPUPS_ENABLED, defaults.popupsEnabled),
            grabberMinimumDurationSeconds = prefs.getInt(
                KEY_GRABBER_MINIMUM_DURATION_SECONDS, defaults.grabberMinimumDurationSeconds
            ).coerceIn(0, 1_800),
            autoDeleteInactiveTabs = prefs.getBoolean(
                KEY_AUTO_DELETE_INACTIVE_TABS, defaults.autoDeleteInactiveTabs
            )
        )
    }

    fun save(context: Context, settings: Settings) {
        // commit(), not apply() — see DownloadStore's matching doc comment for why: apply()'s
        // async write can be lost to a process death before it lands.
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AD_BLOCK_ENABLED, settings.adBlockEnabled)
            .putString(KEY_HOME_PAGE_URL, settings.homePageUrl)
            .putString(KEY_HOME_PAGE_MODE, settings.homePageMode.name)
            .putString(KEY_SEARCH_ENGINE, settings.searchEngine.name)
            .putBoolean(KEY_RESTORE_TABS_ON_STARTUP, settings.restoreTabsOnStartup)
            .putString(KEY_NEW_TAB_CONFIRMATION, settings.newTabConfirmation.name)
            .putString(KEY_DOWNLOAD_THREAD_COUNT, settings.downloadThreadCount.name)
            .putBoolean(KEY_JAVASCRIPT_ENABLED, settings.javaScriptEnabled)
            .putBoolean(KEY_COOKIES_ENABLED, settings.cookiesEnabled)
            .putBoolean(KEY_POPUPS_ENABLED, settings.popupsEnabled)
            .putInt(KEY_GRABBER_MINIMUM_DURATION_SECONDS, settings.grabberMinimumDurationSeconds.coerceIn(0, 1_800))
            .putBoolean(KEY_AUTO_DELETE_INACTIVE_TABS, settings.autoDeleteInactiveTabs)
            .commit()
    }
}
