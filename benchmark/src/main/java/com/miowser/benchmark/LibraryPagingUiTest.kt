package com.miowser.benchmark

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryPagingUiTest {
    @Test fun deepRowsKeepTheirPositionAndPlayWithTheFullQueue() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        seed(libraryBaseline = true)
        device.executeShellCommand("am start -W -n $PACKAGE/com.miowser.app.MainActivity")
        device.requireNode(By.res("hub_destination_saved")).click()
        device.requireNode(By.text("Fixture video 000"))
        device.requireNode(By.desc("Search Library")).click()
        val search = device.requireNode(By.res("library_search"))
        search.text = "Fixture video 250"
        search.text = "missing"
        search.text = "Fixture video 300"
        device.requireNode(By.text("Fixture video 300"))
        check(!device.hasObject(By.text("Fixture video 250")))
        device.requireNode(By.desc("Close Library search")).click()
        device.requireNode(By.text("Fixture video 000"))

        val grid = device.requireNode(By.res("library_grid"))
        grid.setGestureMargin(device.displayWidth / 5)
        val video = By.textStartsWith("Fixture video ")
        var deepTitle: String? = null
        repeat(10) {
            if (deepTitle != null) return@repeat
            grid.fling(Direction.DOWN)
            device.requireNode(video)
            deepTitle = device.findObjects(video).sortedBy { it.visibleBounds.top }
                .mapNotNull { it.text }
                .firstOrNull { (it.substringAfterLast(' ').toIntOrNull() ?: 0) >= 96 }
        }
        val title = checkNotNull(deepTitle) { "The fling never reached a video beyond the initial window" }
        device.requireNode(By.res("hub_nav_downloads")).click()
        device.requireNode(By.res("downloads_list"))
        device.requireNode(By.res("hub_nav_library")).click()
        device.requireNode(By.text(title)).click()
        device.requireNode(By.res("player_first_frame"))
        val fixtureSize = InstrumentationRegistry.getArguments().getString("library")?.toInt() ?: 500
        device.requireNode(By.desc("Playlist: ${title.substringAfterLast(' ').toInt() + 1} of ${fixtureSize * 3 / 4}"))
    }
}
