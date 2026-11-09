package com.miowser.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryMigrationTest {
    @Test fun migrationRetriesAndBackupReplacesRoomAfterRestart() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun run(activity: String, arguments: String, ready: String) {
            device.executeShellCommand("am force-stop $PACKAGE")
            device.executeShellCommand("am start -W -n $PACKAGE/com.miowser.app.benchmark.$activity $arguments")
            device.requireNode(By.text(ready))
        }
        run("FixtureActivity", "--ez library_migration true", "Legacy Library fixture ready")
        run("LibraryMigrationProbeActivity", "--es stage edit", "Library migration edits committed")
        run("LibraryMigrationProbeActivity", "--es stage prepare", "Library backup journal staged")
        run("LibraryMigrationProbeActivity", "--es stage verify", "Restored Library verified")
        run("LibraryMigrationProbeActivity", "--es stage verify", "Restored Library verified")
    }
}
