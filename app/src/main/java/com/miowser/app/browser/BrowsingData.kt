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
    /** WebView's HTTP cache is shared by every tab; Android exposes no per-origin cache purge. */
    fun clearCache(context: Context, tabs: List<Tab>) {
        val existing = tabs.firstNotNullOfOrNull { it.liveWebView }
        if (existing != null) existing.clearCache(true)
        else WebView(context).apply { clearCache(true); destroy() }
    }

    fun clear(context: Context, tabs: List<Tab>, selected: BrowsingDataSelection, onFinished: () -> Unit) {
        fun finish() {
            if (selected.history || selected.downloadRecords || selected.libraryRecords) {
                com.miowser.app.persistence.Persistence.afterWrites(onFinished)
            } else onFinished()
        }
        if (selected.history) {
            HistoryLog.clear(context)
            tabs.forEach { it.liveWebView?.clearHistory() }
        }
        if (selected.cache) clearCache(context, tabs)
        if (selected.formData) {
            tabs.forEach { it.liveWebView?.clearFormData() }
            WebViewDatabase.getInstance(context).clearFormData()
        }
        if (selected.sitePermissions) {
            SitePermissionStore.clear(context)
            GeolocationPermissions.getInstance().clearAll()
        }
        if (selected.downloadRecords) DownloadQueue.deleteAll(context, DownloadQueue.downloads.toList())
        if (selected.libraryRecords) {
            MediaLibrary.clear(context)
            LibraryThumbnailStore.pruneAsync(context, emptyList())
        }
        if (selected.cookiesAndSiteStorage) {
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies {
                CookieManager.getInstance().flush()
                finish()
            }
        } else {
            finish()
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
