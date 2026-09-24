package com.miowser.app.browser

import android.content.Context

private const val PREFS_NAME = "hub_settings"
private const val KEY_LIBRARY_SORT = "library_sort"
private const val KEY_DOWNLOAD_SECTION = "download_section"

data class HubSettings(
    val librarySort: HubLibrarySort = HubLibrarySort.RECENT,
    val defaultDownloadSection: HubDownloadSection = HubDownloadSection.DOWNLOADING
)

internal fun decodeHubSettings(librarySort: String?, downloadSection: String?): HubSettings =
    HubSettings(
        librarySort = librarySort?.let { value ->
            runCatching { HubLibrarySort.valueOf(value) }.getOrNull()
        } ?: HubLibrarySort.RECENT,
        defaultDownloadSection = downloadSection?.let { value ->
            runCatching { HubDownloadSection.valueOf(value) }.getOrNull()
        } ?: HubDownloadSection.DOWNLOADING
    )

/** Hub-only preferences; deliberately separate from the browser's SettingsStore. */
object HubSettingsStore {
    fun load(context: Context): HubSettings {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return decodeHubSettings(
            librarySort = prefs.getString(KEY_LIBRARY_SORT, null),
            downloadSection = prefs.getString(KEY_DOWNLOAD_SECTION, null)
        )
    }

    fun save(context: Context, settings: HubSettings) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(KEY_LIBRARY_SORT, settings.librarySort.name)
            .putString(KEY_DOWNLOAD_SECTION, settings.defaultDownloadSection.name)
            .commit()
    }
}
