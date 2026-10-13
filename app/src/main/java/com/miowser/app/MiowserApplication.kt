package com.miowser.app

import android.app.Application
import com.miowser.app.backup.BackupEngine

class MiowserApplication : Application() {
    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        // Normally just checks for the private journal. Exceptional interrupted-restore recovery
        // must precede Activity, service and WorkManager repository reads.
        BackupEngine.recoverInterruptedRestore(this)
    }
}
