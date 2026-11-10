package com.miowser.benchmark

import android.os.Trace
import androidx.benchmark.macro.ExperimentalMetricApi
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.TraceSectionMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalMetricApi::class)
@RunWith(AndroidJUnit4::class)
class LibraryBaselineBenchmarks {
    @get:Rule val rule = MacrobenchmarkRule()
    @Before fun setup() { seed(libraryBaseline = true) }

    @Test fun coldRows() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(StartupTimingMetric(), observed("P0ColdRows"), appTrace("LibraryProjection")),
        compilationMode = benchmarkCompilation,
        startupMode = StartupMode.COLD,
        iterations = benchmarkIterations,
        setupBlock = { prepareColdLaunch() }
    ) {
        observe("P0ColdRows") { launchAndAwaitRows() }
    }

    @Test fun warmNavigation() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), observed("P0WarmRows"),
            appTrace("LibraryProjection"), appTrace("LibraryProjectionReuse")),
        compilationMode = benchmarkCompilation,
        iterations = benchmarkIterations,
        setupBlock = { prepareWarmReturn() }
    ) {
        observe("P0WarmRows") { returnToLibrary() }
    }

    @Test fun coldArtwork() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(observed("P0ColdArtwork"), appTrace("LibraryArtworkDecode")),
        compilationMode = benchmarkCompilation,
        startupMode = StartupMode.COLD,
        iterations = benchmarkIterations,
        setupBlock = { prepareColdLaunch() }
    ) {
        observe("P0ColdArtwork") {
            launchAndAwaitRows()
            awaitArtwork()
        }
    }

    @Test fun warmArtwork() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(observed("P0WarmArtwork"), appTrace("LibraryArtworkDecode")),
        compilationMode = benchmarkCompilation,
        iterations = benchmarkIterations,
        setupBlock = { prepareWarmReturn() }
    ) {
        observe("P0WarmArtwork") {
            returnToLibrary()
            awaitArtwork()
        }
    }

    @Test fun folderAndSearch() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), observed("P0FolderChange"), observed("P0SearchChange"),
            appTrace("LibraryProjection"), appTrace("LibraryProjectionReuse")),
        compilationMode = benchmarkCompilation,
        iterations = benchmarkIterations,
        setupBlock = { killProcess(); openHub(); awaitArtwork() }
    ) {
        observe("P0FolderChange") {
            device.requireNode(By.text("Fixture Series")).click()
            device.requireNode(By.textStartsWith("Fixture folder video"))
        }
        device.requireNode(By.text("Unfiled")).click()
        device.requireNode(By.text("Fixture video 000"))
        device.requireNode(By.desc("Search Library")).click()
        val search = device.requireNode(By.res("library_search"))
        observe("P0SearchChange") {
            search.text = "Fixture video 250"
            device.requireNode(By.text("Fixture video 250"))
        }
    }

    @Test fun longFling() = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(FrameTimingMetric(), appTrace("LibraryArtworkDecode")),
        compilationMode = benchmarkCompilation,
        iterations = benchmarkIterations,
        setupBlock = { killProcess(); openHub(); awaitArtwork() }
    ) {
        val grid = device.requireNode(By.res("library_grid"))
        grid.setGestureMargin(device.displayWidth / 5)
        repeat(10) { grid.fling(Direction.DOWN) }
        repeat(10) { grid.fling(Direction.UP) }
    }

    @Test fun scanBatchCommit() = measureWrite("import", "LibraryImportBatch", "LibrarySnapshotCommit")

    @Test fun progressCommit() = measureWrite("progress", "LibraryProgressUpdate", "LibraryProgressCommit")

    private fun measureWrite(operation: String, mutationSection: String, commitSection: String) = rule.measureRepeated(
        packageName = PACKAGE,
        metrics = listOf(observed("P0WriteCommitted"), appTrace(mutationSection),
            appTrace(commitSection)),
        compilationMode = benchmarkCompilation,
        iterations = benchmarkIterations,
        setupBlock = {
            seed(libraryBaseline = true, resetLibraryOnly = true)
            device.executeShellCommand(
                "am start -W -n $PACKAGE/com.miowser.app.benchmark.LibraryWriteProbeActivity --es operation $operation"
            )
            device.requireNode(By.text("Library write ready"))
        }
    ) {
        val ready = device.requireNode(By.text("Library write ready"))
        observe("P0WriteCommitted") {
            ready.click()
            device.requireNode(By.text("Library write committed"))
        }
    }

    private fun MacrobenchmarkScope.prepareColdLaunch() {
        pressHome()
        device.executeShellCommand("cmd jobscheduler cancel -u current $PACKAGE")
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            device.executeShellCommand("cmd jobscheduler cancel -u current -n androidx.work.systemjobscheduler $PACKAGE")
        }
        killProcess()
    }

    private fun MacrobenchmarkScope.launchAndAwaitRows() {
        device.executeShellCommand("am start -n $PACKAGE/com.miowser.app.MainActivity")
        device.requireNode(By.res("hub_nav_network")).click()
        device.requireNode(By.text("Fixture video 000"))
    }

    private fun MacrobenchmarkScope.prepareWarmReturn() {
        killProcess()
        openHub()
        awaitArtwork()
        device.requireNode(By.res("hub_nav_downloads")).click()
        device.requireNode(By.res("downloads_list"))
    }

    private fun MacrobenchmarkScope.returnToLibrary() {
        device.requireNode(By.res("hub_nav_network")).click()
        device.requireNode(By.text("Fixture video 000"))
    }

    private fun MacrobenchmarkScope.awaitArtwork() {
        device.requireNode(By.res("library_grid").hasDescendant(By.res("video_card_artwork")))
    }

    private fun observed(name: String) = TraceSectionMetric(
        name, TraceSectionMetric.Mode.Sum, targetPackageOnly = false
    )

    private fun appTrace(name: String) = TraceSectionMetric(name, TraceSectionMetric.Mode.Sum)

    private inline fun observe(name: String, action: () -> Unit) {
        Trace.beginSection(name)
        try {
            action()
        } finally {
            Trace.endSection()
        }
    }
}
