package com.miowser.app.settings

import android.content.Context

private const val PREFS_NAME = "settings"
private const val KEY_AD_BLOCK_ENABLED = "ad_block_enabled"
private const val KEY_HOME_PAGE_URL = "home_page_url"
private const val KEY_SEARCH_ENGINE = "search_engine"
private const val KEY_RESTORE_TABS_ON_STARTUP = "restore_tabs_on_startup"

/** Thin SharedPreferences wrapper — one flat record, so plain typed keys rather than a codec. */
object SettingsStore {
    fun load(context: Context): Settings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = Settings()
        val savedEngine = prefs.getString(KEY_SEARCH_ENGINE, null)
            ?.let { name -> runCatching { SearchEngine.valueOf(name) }.getOrNull() }
        return Settings(
            adBlockEnabled = prefs.getBoolean(KEY_AD_BLOCK_ENABLED, defaults.adBlockEnabled),
            homePageUrl = prefs.getString(KEY_HOME_PAGE_URL, defaults.homePageUrl)
                ?.takeIf { it.isNotBlank() } ?: defaults.homePageUrl,
            searchEngine = savedEngine ?: defaults.searchEngine,
            restoreTabsOnStartup = prefs.getBoolean(KEY_RESTORE_TABS_ON_STARTUP, defaults.restoreTabsOnStartup)
        )
    }

    fun save(context: Context, settings: Settings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_AD_BLOCK_ENABLED, settings.adBlockEnabled)
            .putString(KEY_HOME_PAGE_URL, settings.homePageUrl)
            .putString(KEY_SEARCH_ENGINE, settings.searchEngine.name)
            .putBoolean(KEY_RESTORE_TABS_ON_STARTUP, settings.restoreTabsOnStartup)
            .apply()
    }
}
