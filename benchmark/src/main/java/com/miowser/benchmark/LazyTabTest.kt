package com.miowser.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LazyTabTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val target = "com.miowser.app.benchmark"
    private val probe = "$target/com.miowser.app.benchmark.TabControllerProbeActivity"
    private fun node(selector: BySelector) = checkNotNull(device.wait(Until.findObject(selector), 30_000)) { "Missing $selector" }
    private fun text(value: String) = node(By.text(value))
    private fun clear() {
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("pm clear $target")
        device.executeShellCommand("pm grant $target android.permission.POST_NOTIFICATIONS")
    }
    private fun command(action: String) { device.executeShellCommand("am start -W -n $probe --es action $action") }
    private fun overview() { node(By.descStartsWith("Tabs (")).click(); node(By.res("tabs_grid")) }

    @Test fun thirtyRecordsStayDormantAndWarmPagesDoNotReload() {
        clear()
        device.executeShellCommand("am start -W -n $target/com.miowser.app.benchmark.FixtureActivity --ei tabs 30 --ei downloads 200")
        text("Fixture ready: library=500 history=2000 downloads=200 tabs=30")
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -W -n $probe")
        text("Records 30; engines 0")
        command("preferences"); text("Dormant preferences preserved")
        val start = android.os.SystemClock.elapsedRealtime()
        node(By.desc("Browse")).click()
        text("Records 30; engines 1")
        node(By.desc("Tabs (30 open)"))
        android.util.Log.i("Step6Timing", "30-record first browser entry through UI wait: ${android.os.SystemClock.elapsedRealtime() - start} ms")
        overview() // capturing the selected page waits for an actual first draw
        text("Fixture tab 0").click()
        command("remember"); text("Page sentinel saved")
        overview()
        node(By.desc("Close Fixture tab 2")).click()
        text("Records 29; engines 1")
        text("Fixture tab 1").click()
        text("Records 29; engines 2")
        overview(); text("Fixture tab 0").click()
        command("verify"); text("Warm page preserved")
        text("Records 29; engines 2")
    }

    @Test fun closingChildReturnsToDormantOpenerWithoutCreatingOtherEngines() {
        clear()
        device.executeShellCommand("am start -W -n $probe --es mode child")
        text("Records 2; engines 0")
        node(By.desc("Browse")).click(); text("Records 2; engines 1")
        overview(); node(By.desc("Close Child")).click()
        text("Records 1; engines 0")
        device.pressBack()
        text("Records 1; engines 1")
        overview(); text("Parent")
    }

    @Test fun nativeHomeDoesNotCreateHiddenWebView() {
        clear()
        device.executeShellCommand("am start -W -n $probe --es mode home")
        text("Records 1; engines 0")
        node(By.desc("Browse")).click()
        node(By.desc("Tabs (1 open)"))
        text("Records 1; engines 0")
        command("navigate")
        text("Records 1; engines 1")
        overview(); text("Native Home navigation")
    }

    @Test fun sessionRestoreSeesQueuedChangesIncludingEmptySession() {
        clear()
        device.executeShellCommand("am start -W -n $probe --es mode home")
        text("Records 1; engines 0")
        command("queued-session"); text("Queued session preserved")
    }

    @Test fun lazyPrivateTabRemainsExcludedFromHistoryAndSession() {
        clear()
        device.executeShellCommand("am start -W -n $probe --es mode home")
        text("Records 1; engines 0")
        node(By.desc("Browse")).click()
        overview(); text("Private (0)").click()
        node(By.desc("New private tab")).click()
        text("Continue").click()
        text("Records 2; engines 0")
        command("private-navigate")
        text("Records 2; engines 1")
        command("verify-private"); text("Private record excluded")
    }
}
