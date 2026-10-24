package com.miowser.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val target = "com.miowser.app.benchmark"
    private fun waitText(text: String) = device.wait(Until.findObject(By.text(text)), 30_000).also { assertNotNull(text, it) }!!
    private fun clear() {
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("pm clear $target")
        device.executeShellCommand("pm grant $target android.permission.POST_NOTIFICATIONS")
    }
    @Test fun storageErrorRetryAndRecreationPreservePopulatedData() {
        clear()
        device.executeShellCommand("am start -W -n $target/com.miowser.app.benchmark.FixtureActivity --ei tabs 30 --ei downloads 1000")
        waitText("Fixture ready: library=500 history=2000 downloads=1000 tabs=30")
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -W -n $target/com.miowser.app.benchmark.StartupProbeActivity")
        waitText("Couldn’t load your saved data.")
        waitText("Repair test storage").click()
        waitText("Startup retry verified; recreate").click()
        device.waitForIdle()
        waitText("Startup retry verified; recreate")
    }

    @Test fun emptyHubThenBrowserCanOpen() {
        clear()
        device.executeShellCommand("am start -W -n $target/com.miowser.app.MainActivity")
        val browse = device.wait(Until.findObject(By.desc("Browse")), 30_000)
        assertNotNull(browse)
        browse!!.click()
        assertNotNull(device.wait(Until.findObject(By.descStartsWith("Tabs (")), 30_000))
    }

    @Test fun recoveryReadsStayOffMainAndLateResultsRespectCancelAndDelete() {
        clear()
        device.executeShellCommand("am start -W -n $target/com.miowser.app.benchmark.StartupBarrierProbeActivity")
        waitText("Asynchronous recovery verified")
    }

    @Test fun localHubCanLoadBeforePrivateCleanupButCookieUseWaits() {
        clear()
        device.executeShellCommand("am start -W -n $target/com.miowser.app.benchmark.StartupBarrierProbeActivity --ez privacy true")
        waitText("Private startup barrier verified")
    }

    @Test fun populatedHubPausePreservesTabsBeforeFirstBrowserEntry() {
        clear()
        device.executeShellCommand("am start -W -n $target/com.miowser.app.benchmark.FixtureActivity --ei tabs 30 --ei downloads 1000")
        waitText("Fixture ready: library=500 history=2000 downloads=1000 tabs=30")
        device.executeShellCommand("am force-stop $target")
        val start = android.os.SystemClock.elapsedRealtime()
        device.executeShellCommand("am start -W -n $target/com.miowser.app.MainActivity")
        waitText("Fixture video 000")
        android.util.Log.i("Step5Timing", "Hub launch through shell and UI wait: ${android.os.SystemClock.elapsedRealtime() - start} ms")
        device.pressHome()
        device.waitForIdle()
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -W -n $target/com.miowser.app.MainActivity")
        val browse = device.wait(Until.findObject(By.desc("Browse")), 30_000)!!
        val entry = android.os.SystemClock.elapsedRealtime()
        browse.click()
        val tabs = device.wait(Until.findObject(By.desc("Tabs (30 open)")), 60_000)
        assertNotNull(tabs)
        android.util.Log.i("Step5Timing", "First browser entry, 30 tabs, UI wait: ${android.os.SystemClock.elapsedRealtime() - entry} ms")
        tabs!!.click()
        waitText("Fixture tab 0")
    }
}
