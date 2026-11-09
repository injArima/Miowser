package com.miowser.app

import android.app.Application
import com.miowser.app.backup.BackupEngine
import com.miowser.app.diagnostics.CrashDiagnostics
import com.miowser.app.diagnostics.ProcessExitRecovery
import com.miowser.app.library.LibraryThumbnailStore
import com.miowser.app.settings.SettingsStore

class MiowserApplication : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // Restore recovery must finish before even the minimal Settings read or migration.
        BackupEngine.recoverInterruptedRestore(this)
        CrashDiagnostics.install(this, SettingsStore.crashDiagnosticsEnabled(this))
        ProcessExitRecovery.initialize(this)
    }

    override fun onCreate() {
        super.onCreate()
        ProcessExitRecovery.recover(this)
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        LibraryThumbnailStore.trimMemory(level)
    }
}
