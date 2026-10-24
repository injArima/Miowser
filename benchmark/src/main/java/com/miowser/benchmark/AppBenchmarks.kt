package com.miowser.benchmark

import android.content.Intent
import androidx.benchmark.macro.*
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

private const val PACKAGE = "com.miowser.app.benchmark"
private const val TIMEOUT = 30_000L

private fun androidx.test.uiautomator.UiDevice.requireNode(selector: androidx.test.uiautomator.BySelector) =
    checkNotNull(wait(Until.findObject(selector), TIMEOUT)) { "Missing UI node: $selector" }

private fun seed() {
    val device = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val args = InstrumentationRegistry.getArguments()
    val tabs = (args.getString("tabs") ?: "10").toInt().also { require(it in listOf(1, 10, 30)) }
    val downloads = (args.getString("downloads") ?: "200").toInt().also { require(it in listOf(200, 1000)) }
    val library = (args.getString("library") ?: "500").toInt().also { require(it in listOf(500, 1000, 5000)) }
    val server = args.getString("fixture_server")
    require(server == null || server == "http://127.0.0.1:8765")
    val active = args.getString("active_transfers") == "true"
    val optional = if (server != null) " --es fixture_server $server --ez active_transfers $active" else ""
    device.executeShellCommand("am force-stop $PACKAGE")
    device.executeShellCommand("pm grant $PACKAGE android.permission.POST_NOTIFICATIONS")
    device.executeShellCommand("am start -W -n $PACKAGE/com.miowser.app.benchmark.FixtureActivity --ei tabs $tabs --ei downloads $downloads --ei library $library$optional")
    device.requireNode(By.text("Fixture ready: library=$library history=2000 downloads=$downloads tabs=$tabs"))
    device.executeShellCommand("am force-stop $PACKAGE")
}

private fun MacrobenchmarkScope.openHub() {
    startActivityAndWait(Intent().setClassName(PACKAGE, "com.miowser.app.MainActivity"))
    device.requireNode(By.res("library_grid"))
    device.requireNode(By.text("Fixture video 000"))
}

private fun MacrobenchmarkScope.startFixtureTransfersIfRequested() {
    if (InstrumentationRegistry.getArguments().getString("active_transfers") == "true") {
        device.executeShellCommand("am start -W -n $PACKAGE/com.miowser.app.benchmark.ActiveTransferFixtureActivity")
        device.requireNode(By.text("Fixture transfer requested"))
        startActivityAndWait(Intent().setClassName(PACKAGE, "com.miowser.app.MainActivity"))
    }
}

private fun androidx.test.uiautomator.UiDevice.switchHubDestinations(repetitions: Int) {
    repeat(repetitions) {
        requireNode(By.res("hub_nav_downloads")).click()
        requireNode(By.res("downloads_list"))
        requireNode(By.res("hub_nav_more")).click()
        requireNode(By.res("hub_more"))
        requireNode(By.res("hub_nav_library")).click()
        requireNode(By.res("library_grid"))
    }
}

private fun androidx.test.uiautomator.UiDevice.switchDownloadLists(repetitions: Int) {
    requireNode(By.res("hub_nav_downloads")).click()
    repeat(repetitions) {
        requireNode(By.res("hub_downloads_downloaded")).click()
        requireNode(By.res("downloaded_grid"))
        requireNode(By.res("hub_downloads_downloading")).click()
        requireNode(By.res("downloads_list"))
    }
    requireNode(By.res("hub_nav_library")).click()
    requireNode(By.res("library_grid"))
}

@RunWith(AndroidJUnit4::class)
class AppBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()
    @Before fun setup() = seed()
    private val iterations get() = (InstrumentationRegistry.getArguments().getString("iterations") ?: "20").toInt().also { require(it > 0) }
    private val compilation: CompilationMode get() = when (InstrumentationRegistry.getArguments().getString("compilation") ?: "None") {
        "None" -> CompilationMode.None()
        "BaselineProfile" -> CompilationMode.Partial(baselineProfileMode = BaselineProfileMode.Require, warmupIterations = 0)
        else -> error("Unsupported compilation policy")
    }

    @Test fun populatedStartup() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(StartupTimingMetric()),
        compilationMode = compilation, startupMode = StartupMode.COLD,
        iterations = iterations, setupBlock = {
            pressHome()
            // A pending WorkManager job can rebind while Android force-stops the prior run.
            // Cancel scheduled background jobs outside the timed launch, then terminate it.
            device.executeShellCommand("cmd jobscheduler cancel -u current $PACKAGE")
            if (android.os.Build.VERSION.SDK_INT >= 34) {
                device.executeShellCommand("cmd jobscheduler cancel -u current -n androidx.work.systemjobscheduler $PACKAGE")
            }
            killProcess()
        }
    ) { openHub() }

    @Test fun libraryScroll() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = { killProcess(); openHub() }
    ) {
        val grid = device.requireNode(By.res("library_grid"))
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(3) { grid.fling(Direction.DOWN) }
        repeat(3) { grid.fling(Direction.UP) }
    }

    @Test fun downloadsScroll() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = {
            killProcess(); openHub()
            startFixtureTransfersIfRequested()
            device.requireNode(By.res("hub_nav_downloads")).click()
            device.requireNode(By.res("downloads_list"))
        }
    ) {
        val list = device.requireNode(By.res("downloads_list"))
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) { list.fling(Direction.DOWN) }
        repeat(3) { list.fling(Direction.UP) }
    }

    @Test fun browserAndTabs() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = { killProcess(); openHub() }
    ) {
        device.requireNode(By.desc("Browse")).click()
        // WebView descendants are not currently exposed by the stacked AndroidView host.
        // Use native overview semantics here; inspect the fixture's bridge marker separately.
        device.requireNode(By.res("browser_tabs")).click()
        device.requireNode(By.res("tabs_grid"))
        device.requireNode(By.text("Fixture tab 0"))
        val cards = device.findObjects(By.res("tab_card"))
        check(cards.isNotEmpty())
        // Activate a different live tab when the fixture contains more than one.
        cards[if (cards.size > 1) 1 else 0].click()
        device.requireNode(By.res("browser_tabs"))
    }

    @Test fun hubNavigation() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = {
            killProcess(); openHub(); startFixtureTransfersIfRequested()
            val grid = device.requireNode(By.res("library_grid"))
            grid.setGestureMargin(device.displayWidth / 5)
            grid.fling(Direction.DOWN)
        }
    ) { device.switchHubDestinations(6) }

    @Test fun hubDownloadLists() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = { killProcess(); openHub(); startFixtureTransfersIfRequested() }
    ) { device.switchDownloadLists(6) }

    @Test fun localPlayer() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = { killProcess(); openHub() }
    ) {
        device.requireNode(By.text("Fixture video 000")).click()
        device.requireNode(By.res("player_first_frame"))
    }

    @Test fun tabSwipeReversals() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = {
            killProcess(); openHub()
            device.requireNode(By.desc("Browse")).click()
            device.requireNode(By.res("browser_tabs")).click()
            device.requireNode(By.res("tabs_grid"))
        }
    ) {
        repeat(4) {
            val card = device.requireNode(By.res("tab_card"))
            val bounds = card.visibleBounds
            val x = bounds.centerX()
            val y = bounds.centerY()
            val travel = bounds.width() / 5 // Below the close threshold; should settle back.
            device.swipe(x, y, x + travel, y, 24)
            device.swipe(x, y, x - travel, y, 24)
            device.requireNode(By.res("tabs_grid"))
        }
    }

    @Test fun jumpToTopReversals() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = {
            killProcess(); openHub()
            device.requireNode(By.desc("Browse")).click()
            repeat(3) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 4, 20)
            }
            device.requireNode(By.res("browser_jump_to_top"))
        }
    ) {
        repeat(3) {
            device.requireNode(By.res("browser_jump_to_top")).click()
            repeat(3) {
                device.swipe(device.displayWidth / 2, device.displayHeight * 3 / 4,
                    device.displayWidth / 2, device.displayHeight / 4, 20)
            }
            device.requireNode(By.res("browser_jump_to_top"))
        }
    }

    @Test fun playerControlsAndOverlays() = rule.measureRepeated(
        packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
        compilationMode = compilation, iterations = iterations,
        setupBlock = {
            killProcess(); openHub()
            device.requireNode(By.text("Fixture video 000")).click()
            device.requireNode(By.res("player_first_frame"))
        }
    ) {
        repeat(3) {
            val control = device.findObject(By.res("player_play_pause"))
                ?: run {
                    device.requireNode(By.res("player_first_frame")).click()
                    device.requireNode(By.res("player_play_pause"))
                }
            control.click()
            device.requireNode(By.res("player_subtitles")).click()
            device.requireNode(By.res("subtitle_workspace"))
            device.requireNode(By.desc("Close subtitles")).click()
            device.requireNode(By.descStartsWith("Playlist:")).click()
            device.requireNode(By.desc("Close playlist")).click()
        }
    }

    @Test fun remoteSubtitleControls() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("fixture_server") != null)
        rule.measureRepeated(
            packageName = PACKAGE, metrics = listOf(FrameTimingMetric()),
            compilationMode = compilation, iterations = iterations,
            setupBlock = {
                killProcess(); openHub()
                device.requireNode(By.text("Fixture video 001")).click()
                device.requireNode(By.res("player_first_frame"))
            }
        ) {
            repeat(3) {
                device.requireNode(By.res("player_subtitles")).click()
                device.requireNode(By.res("subtitle_workspace"))
                device.findObject(By.text("Settings"))?.click()
                device.requireNode(By.desc("Increase subtitle delay by 50 milliseconds")).click()
                device.requireNode(By.desc("Close subtitles")).click()
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule val rule = BaselineProfileRule()
    @Before fun setup() = seed()
    @Test fun startup() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) { openHub() }
    @Test fun commonInteractions() = rule.collect(packageName = PACKAGE, includeInStartupProfile = false) {
        openHub()
        device.switchHubDestinations(3)
        device.switchDownloadLists(3)
        val grid = device.requireNode(By.res("library_grid"))
        grid.setGestureMargin(device.displayWidth / 5)
        grid.fling(Direction.DOWN); grid.fling(Direction.UP)
        device.requireNode(By.text("Fixture video 000")).click()
        device.requireNode(By.res("player_first_frame"))
        repeat(3) {
            device.requireNode(By.descStartsWith("Playlist:")).click()
            device.requireNode(By.desc("Close playlist").clazz("android.widget.Button")).click()
        }
        device.pressBack()
        device.requireNode(By.desc("Browse")).click()
        device.requireNode(By.res("browser_tabs")).click()
        device.requireNode(By.res("tabs_grid"))
    }
    @Test fun remotePlaybackAndSubtitleEditing() {
        org.junit.Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("fixture_server") != null)
        rule.collect(packageName = PACKAGE, includeInStartupProfile = false) {
            openHub()
            device.requireNode(By.text("Fixture video 001")).click()
            device.requireNode(By.res("player_first_frame"))
            repeat(3) {
                device.requireNode(By.res("player_subtitles")).click()
                device.requireNode(By.res("subtitle_workspace"))
                device.findObject(By.text("Settings"))?.click()
                device.requireNode(By.desc("Increase subtitle delay by 50 milliseconds")).click()
                device.requireNode(By.desc("Close subtitles").clazz("android.widget.Button")).click()
            }
        }
    }
}
