package com.miowser.app.browser

import android.content.Context
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import com.miowser.app.downloader.DownloadQueue
import com.miowser.app.history.HistoryLog
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.LibraryThumbnailStore

data class BrowsingDataSelection(
    val history: Boolean = true,
    val cookiesAndSiteStorage: Boolean = true,
    val cache: Boolean = true,
    val formData: Boolean = true,
    val sitePermissions: Boolean = true,
    val downloadRecords: Boolean = false,
    val libraryRecords: Boolean = false
) {
    fun hasSelection(): Boolean = history || cookiesAndSiteStorage || cache || formData ||
        sitePermissions || downloadRecords || libraryRecords
}

/** Clear only selected categories. Downloaded files are never deleted by this screen. */
object BrowsingData {
    fun clear(context: Context, tabs: List<Tab>, selected: BrowsingDataSelection, onFinished: () -> Unit) {
        if (selected.history) {
            HistoryLog.clear(context)
            tabs.forEach { it.webView.clearHistory() }
        }
        if (selected.cache) {
            if (tabs.isNotEmpty()) tabs.first().webView.clearCache(true)
            else WebView(context).apply { clearCache(true); destroy() }
        }
        if (selected.formData) {
            tabs.forEach { it.webView.clearFormData() }
            WebViewDatabase.getInstance(context).clearFormData()
        }
        if (selected.sitePermissions) {
            SitePermissionStore.clear(context)
            GeolocationPermissions.getInstance().clearAll()
        }
        if (selected.downloadRecords) DownloadQueue.downloads.toList().forEach {
            DownloadQueue.delete(context, it, deleteFile = false)
        }
        if (selected.libraryRecords) {
            MediaLibrary.clear(context)
            LibraryThumbnailStore.pruneAsync(context, emptyList())
        }
        if (selected.cookiesAndSiteStorage) {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                onFinished()
            }
        } else {
            onFinished()
        }
    }

    /** The lighter private-session mode shares WebView storage, so both entry and exit purge it. */
    fun clearSharedPrivateSessionData(context: Context, tabs: List<Tab>, onFinished: () -> Unit) {
        clear(
            context, tabs,
            BrowsingDataSelection(
                history = false,
                cookiesAndSiteStorage = true,
                cache = true,
                formData = true,
                sitePermissions = false,
                downloadRecords = false,
                libraryRecords = false
            ),
            onFinished
        )
    }
}
