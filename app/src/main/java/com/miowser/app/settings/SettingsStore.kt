package com.miowser.app.settings

import android.content.Context
import com.miowser.app.persistence.Persistence

private const val PREFS_NAME = "settings"
private const val KEY_APP_EXPERIENCE = "app_experience"
private const val KEY_EXPERIENCE_PRIORITY = "experience_priority"
private const val KEY_EXPERIENCE_CUSTOMIZED = "experience_customized"
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
private const val KEY_LIBRARY_BEHAVIOR = "library_behavior"
private const val KEY_DEVICE_DISCOVERY = "device_video_discovery"
private const val KEY_DEVICE_AUTO_RESCAN = "device_auto_rescan"
private const val KEY_EXCLUDED_DEVICE_FOLDERS = "excluded_device_folders"
private const val KEY_RESUME_SAVED_VIDEOS = "resume_saved_videos"
private const val KEY_BACKGROUND_PLAYBACK_ENABLED = "background_playback_enabled"
private const val KEY_COMPANION_BROWSING_ENABLED = "companion_browsing_enabled"
private const val KEY_CRASH_DIAGNOSTICS_ENABLED = "crash_diagnostics_enabled"
private const val KEY_CRASH_DIAGNOSTICS_MIGRATED = "crash_diagnostics_default_on_v2"

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
            ?.let { name -> runCatching { DownloadThreadCount.valueOf(if (name == "EIGHT") "SIX" else name) }.getOrNull() }
        val savedRetention = prefs.getString(KEY_INACTIVE_TAB_RETENTION, null)
            ?.let { name -> runCatching { InactiveTabRetention.valueOf(name) }.getOrNull() }
        val savedLibraryBehavior = prefs.getString(KEY_LIBRARY_BEHAVIOR, null)
            ?.let { name -> runCatching { LibraryBehavior.valueOf(name) }.getOrNull() }
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
            downloadParallelJobs = prefs.getInt("download_parallel_jobs", 2).coerceIn(1, 4),
            downloadPerHostConnections = prefs.getInt("download_per_host_connections", 4).coerceIn(1, 6),
            downloadRetryAttempts = prefs.getInt("download_retry_attempts", 3).coerceIn(0, 5),
            javaScriptEnabled = prefs.getBoolean(KEY_JAVASCRIPT_ENABLED, defaults.javaScriptEnabled),
            cookiesEnabled = prefs.getBoolean(KEY_COOKIES_ENABLED, defaults.cookiesEnabled),
            popupsEnabled = prefs.getBoolean(KEY_POPUPS_ENABLED, defaults.popupsEnabled),
            grabberMinimumDurationSeconds = prefs.getInt(
                KEY_GRABBER_MINIMUM_DURATION_SECONDS, defaults.grabberMinimumDurationSeconds
            ).coerceIn(0, 1_800),
            inactiveTabRetention = savedRetention ?: legacyRetention,
            reduceMotion = prefs.getBoolean(KEY_REDUCE_MOTION, defaults.reduceMotion),
            libraryBehavior = savedLibraryBehavior ?: defaults.libraryBehavior,
            backgroundPlaybackEnabled = prefs.getBoolean(
                KEY_BACKGROUND_PLAYBACK_ENABLED, defaults.backgroundPlaybackEnabled
            ),
            companionBrowsingEnabled = prefs.getBoolean(
                KEY_COMPANION_BROWSING_ENABLED, defaults.companionBrowsingEnabled
            ),
            crashDiagnosticsEnabled = crashDiagnosticsEnabled(context),
            deviceDiscoveryEnabled = prefs.getBoolean(KEY_DEVICE_DISCOVERY, defaults.deviceDiscoveryEnabled),
            deviceAutoRescanEnabled = prefs.getBoolean(KEY_DEVICE_AUTO_RESCAN, defaults.deviceAutoRescanEnabled),
            excludedDeviceFolders = prefs.getStringSet(KEY_EXCLUDED_DEVICE_FOLDERS, null).orEmpty().toSet(),
            experience = prefs.getString(KEY_APP_EXPERIENCE, null)?.let { runCatching { AppExperience.valueOf(it) }.getOrNull() } ?: defaults.experience,
            experiencePriority = prefs.getString(KEY_EXPERIENCE_PRIORITY, null)?.let { runCatching { ExperiencePriority.valueOf(it) }.getOrNull() } ?: defaults.experiencePriority,
            experienceCustomized = prefs.getBoolean(KEY_EXPERIENCE_CUSTOMIZED, false),
            resumeSavedVideos = prefs.getBoolean(KEY_RESUME_SAVED_VIDEOS, defaults.resumeSavedVideos)
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
                .putInt("download_parallel_jobs", settings.downloadParallelJobs.coerceIn(1, 4))
                .putInt("download_per_host_connections", settings.downloadPerHostConnections.coerceIn(1, 6))
                .putInt("download_retry_attempts", settings.downloadRetryAttempts.coerceIn(0, 5))
                .putBoolean(KEY_JAVASCRIPT_ENABLED, settings.javaScriptEnabled)
                .putBoolean(KEY_COOKIES_ENABLED, settings.cookiesEnabled)
                .putBoolean(KEY_POPUPS_ENABLED, settings.popupsEnabled)
                .putInt(KEY_GRABBER_MINIMUM_DURATION_SECONDS, settings.grabberMinimumDurationSeconds.coerceIn(0, 1_800))
                .putString(KEY_INACTIVE_TAB_RETENTION, settings.inactiveTabRetention.name)
                .putBoolean(KEY_REDUCE_MOTION, settings.reduceMotion)
                .putString(KEY_LIBRARY_BEHAVIOR, settings.libraryBehavior.name)
                .putBoolean(KEY_DEVICE_DISCOVERY, settings.deviceDiscoveryEnabled)
                .putBoolean(KEY_DEVICE_AUTO_RESCAN, settings.deviceAutoRescanEnabled)
                .putStringSet(KEY_EXCLUDED_DEVICE_FOLDERS, settings.excludedDeviceFolders)
                .putString(KEY_APP_EXPERIENCE, settings.experience.name)
                .putString(KEY_EXPERIENCE_PRIORITY, settings.experiencePriority.name)
                .putBoolean(KEY_EXPERIENCE_CUSTOMIZED, settings.experienceCustomized)
                .putBoolean(KEY_RESUME_SAVED_VIDEOS, settings.resumeSavedVideos)
                .putBoolean(KEY_BACKGROUND_PLAYBACK_ENABLED, settings.backgroundPlaybackEnabled)
                .putBoolean(KEY_COMPANION_BROWSING_ENABLED, settings.companionBrowsingEnabled)
                .putBoolean(KEY_CRASH_DIAGNOSTICS_ENABLED, settings.crashDiagnosticsEnabled)
                .putBoolean(KEY_CRASH_DIAGNOSTICS_MIGRATED, true)
                .commit()
        }
    }

    /** Minimal startup read used before the full settings repository is restored. */
    fun crashDiagnosticsEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        // The old default was off and could be written by an unrelated Settings change.
        // Migrate once; subsequent user choices, including Off, remain persistent.
        if (!prefs.getBoolean(KEY_CRASH_DIAGNOSTICS_MIGRATED, false)) {
            prefs.edit().putBoolean(KEY_CRASH_DIAGNOSTICS_ENABLED, true)
                .putBoolean(KEY_CRASH_DIAGNOSTICS_MIGRATED, true).commit()
            return true
        }
        return prefs.getBoolean(KEY_CRASH_DIAGNOSTICS_ENABLED, true)
    }
}
