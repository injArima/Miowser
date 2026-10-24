package com.miowser.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PersistenceDurabilityTest {
    @Test fun acknowledgedBulkEditsSurviveProcessRestart() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val target = "com.miowser.app.benchmark"
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("pm clear $target")
        device.executeShellCommand("pm grant $target android.permission.POST_NOTIFICATIONS")
        device.executeShellCommand("am start -W -n $target/com.miowser.app.benchmark.FixtureActivity --ei tabs 10 --ei downloads 200")
        assertNotNull(device.wait(Until.findObject(By.text("Fixture ready: library=500 history=2000 downloads=200 tabs=10")), 30_000))
        device.executeShellCommand("am force-stop $target")
        val probe = "$target/com.miowser.app.benchmark.PersistenceProbeActivity"
        device.executeShellCommand("am start -W -n $probe")
        assertNotNull(device.wait(Until.findObject(By.text("Step 4 changes saved")), 60_000))
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -W -n $probe --ez verify true")
        assertNotNull(device.wait(Until.findObject(By.text("Step 4 restart verified")), 30_000))
    }
}
