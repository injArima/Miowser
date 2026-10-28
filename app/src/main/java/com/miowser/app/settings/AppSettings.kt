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

    suspend fun loadAsync(context: Context) {
        com.miowser.app.persistence.Persistence.requireOwner()
        if (loaded) return
        val settings = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { SettingsStore.load(context) }
        if (!loaded) {
            current = settings
            com.miowser.app.diagnostics.CrashDiagnostics.setEnabled(settings.crashDiagnosticsEnabled)
            loaded = true
        }
    }

    fun load(context: Context) {
        com.miowser.app.persistence.Persistence.requireOwner()
        if (loaded) return
        current = SettingsStore.load(context)
        com.miowser.app.diagnostics.CrashDiagnostics.setEnabled(current.crashDiagnosticsEnabled)
        loaded = true
    }

    fun update(context: Context, transform: (Settings) -> Settings) {
        load(context)
        val updated = transform(current)
        current = updated
        com.miowser.app.diagnostics.CrashDiagnostics.setEnabled(updated.crashDiagnosticsEnabled)
        SettingsStore.save(context.applicationContext, updated)
    }
}
