package com.miowser.app.browser

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Keeps the WebView file chooser callback owned by its tab, including retirement and dismissal. */
internal class BrowserFileSelection {
    var ownerId by mutableStateOf<Long?>(null)
        private set
    private var callback: ValueCallback<Array<Uri>>? = null
    private var awaitingResult = false

    fun start(tabId: Long, next: ValueCallback<Array<Uri>>): Boolean {
        if (awaitingResult) { next.onReceiveValue(null); return false }
        ownerId = tabId
        callback = next
        awaitingResult = true
        return true
    }

    fun complete(resultCode: Int, data: Intent?, ownPackage: String) {
        awaitingResult = false
        val current = callback
        callback = null
        ownerId = null
        val selected = if (resultCode == Activity.RESULT_OK) runCatching {
            WebChromeClient.FileChooserParams.parseResult(resultCode, data)
        }.getOrNull()?.takeIf { uris ->
            uris.isNotEmpty() && uris.size <= 100 && uris.all { uri ->
                uri.scheme == "content" && uri.authority?.startsWith(ownPackage) != true
            }
        } else null
        current?.onReceiveValue(selected)
    }

    fun cancelFor(tabId: Long) { if (ownerId == tabId) cancel() }

    fun launchFailed(tabId: Long) {
        if (ownerId == tabId) {
            awaitingResult = false
            cancel()
        }
    }

    fun cancel() {
        val current = callback
        callback = null
        ownerId = null
        current?.onReceiveValue(null)
    }
}
