package com.miowser.app.browser

import android.content.Context
import com.miowser.app.persistence.Persistence

private const val PREFS_NAME = "hub_settings"
private const val KEY_LIBRARY_SORT = "library_sort"
private const val KEY_FOLDER_LIBRARY_SORT_PREFIX = "library_sort_folder:"
private const val KEY_DOWNLOAD_SECTION = "download_section"
private const val KEY_DEVICE_FOLDERS = "device_folders"
private const val KEY_DEVICE_LIST = "device_list"
private const val KEY_DEVICE_TITLE_SORT = "device_title_sort"

data class HubSettings(
    val librarySort: HubLibrarySort = HubLibrarySort.RECENT,
    val defaultDownloadSection: HubDownloadSection = HubDownloadSection.DOWNLOADING,
    val deviceFolders: Boolean = false,
    val deviceList: Boolean = false,
    val deviceTitleSort: Boolean = false
)

internal fun decodeLibrarySort(value: String?, fallback: HubLibrarySort = HubLibrarySort.RECENT): HubLibrarySort =
    value?.let { saved -> runCatching { HubLibrarySort.valueOf(saved) }.getOrNull() } ?: fallback

internal fun librarySortPreferenceKey(folderId: String): String =
    KEY_FOLDER_LIBRARY_SORT_PREFIX + folderId

internal fun decodeHubSettings(librarySort: String?, downloadSection: String?): HubSettings =
    HubSettings(
        librarySort = decodeLibrarySort(librarySort),
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
        ).copy(
            deviceFolders = prefs.getBoolean(KEY_DEVICE_FOLDERS, false),
            deviceList = prefs.getBoolean(KEY_DEVICE_LIST, false),
            deviceTitleSort = prefs.getBoolean(KEY_DEVICE_TITLE_SORT, false)
        )
    }

    internal fun saveDeviceView(context: Context, folders: Boolean, list: Boolean, titleSort: Boolean) {
        Persistence.requireOwner()
        val app = context.applicationContext
        Persistence.submitSnapshot("device-library-view") {
            app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_DEVICE_FOLDERS, folders)
                .putBoolean(KEY_DEVICE_LIST, list)
                .putBoolean(KEY_DEVICE_TITLE_SORT, titleSort)
                .commit()
        }
    }

    fun save(context: Context, settings: HubSettings) {
        Persistence.requireOwner()
        com.miowser.app.startup.AppStartup.updateHubSettings(settings)
        val appContext = context.applicationContext
        Persistence.submitSnapshot("hub-settings") {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_LIBRARY_SORT, settings.librarySort.name)
                .putString(KEY_DOWNLOAD_SECTION, settings.defaultDownloadSection.name)
                .commit()
        }
    }

    /**
     * Returns the order saved for this exact Library location. Folder IDs are unique across both
     * primary folders and subfolders; the empty ID is the Unfiled location. The old global order
     * remains the migration fallback until the user chooses an order for this folder.
     */
    fun loadLibrarySort(context: Context, folderId: String): HubLibrarySort {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val legacyDefault = decodeLibrarySort(prefs.getString(KEY_LIBRARY_SORT, null))
        return decodeLibrarySort(
            prefs.getString(librarySortPreferenceKey(folderId), null),
            fallback = legacyDefault
        )
    }

    fun saveLibrarySort(context: Context, folderId: String, sort: HubLibrarySort) {
        Persistence.requireOwner()
        // This is a tiny UI preference. apply() updates SharedPreferences' in-memory state
        // immediately, so switching away and straight back restores the new order without waiting
        // for the persistence queue or a disk write.
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(librarySortPreferenceKey(folderId), sort.name)
            .apply()
    }
}
