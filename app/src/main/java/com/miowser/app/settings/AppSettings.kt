package com.miowser.app.settings

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * Global settings singleton — mirrors DownloadQueue's pattern: a Compose-observable holder that
 * the UI reads reactively, but that non-Compose code can also safely read off the main thread
 * (e.g. MiowserWebViewClient.shouldInterceptRequest, called by Chromium off-main-thread) since a
 * SnapshotMutableState's plain reads see the latest committed value regardless of which thread
 * set it — the same assumption Download.status already relies on for cross-thread progress
 * updates.
 */
object AppSettings {
    var current by mutableStateOf(Settings())
        private set

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        current = SettingsStore.load(context)
    }

    fun update(context: Context, transform: (Settings) -> Settings) {
        val updated = transform(current)
        current = updated
        SettingsStore.save(context.applicationContext, updated)
    }
}
