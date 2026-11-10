package com.miowser.benchmark

import android.content.Intent
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.UUID
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 29)
class DeviceCatalogUiTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private fun fixture(action: String, title: String? = null) {
        val flags = (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK).toString(16)
        val titleArgument = if (title == null) "" else " --es fixture_name $title"
        val result = device.executeShellCommand(
            "am start -W -f 0x$flags " +
                "-n $PACKAGE/com.miowser.app.benchmark.FixtureActivity --es device_catalog $action$titleArgument"
        )
        check("Status: ok" in result) { result }
        if (action != "open") device.requireNode(By.text("Device fixture: $action"))
    }

    @Test fun coldAndroidOpenPlaysWithDiscoveryTurnedOff() {
        seed(libraryBaseline = true)
        fixture("discovery_off")
        try {
            device.executeShellCommand("am force-stop $PACKAGE")
            device.executeShellCommand("am start -W -a android.intent.action.MAIN -c android.intent.category.LAUNCHER -n $PACKAGE/com.miowser.app.MainActivity")
            device.requireNode(By.text("Device discovery is off"))
            device.requireNode(By.text("Open video"))
            device.executeShellCommand("am force-stop $PACKAGE")
            fixture("create", "Direct-open-${UUID.randomUUID().toString().take(8)}.mp4")
            val started = android.os.SystemClock.elapsedRealtime()
            fixture("open")
            device.requireNode(By.res("player_first_frame"))
            android.util.Log.i("DeviceCatalogScale",
                "Android open to first frame ms=${android.os.SystemClock.elapsedRealtime() - started}")
            check(!device.hasObject(By.desc("Library loading")))
        } finally {
            device.executeShellCommand("am force-stop $PACKAGE")
            fixture("delete")
            fixture("discovery_on")
        }
    }

    @Test fun catalogQueriesKeepBoundedPagesAtScale() {
        fixture("scale")
    }

    @Test fun primaryCatalogReconcilesMediaStoreAndOpensThePlayer() {
        fun openDeviceVideos() {
            device.executeShellCommand("am start -W -n $PACKAGE/com.miowser.app.MainActivity")
            device.requireNode(By.res("hub_nav_device"))
        }
        seed(libraryBaseline = true)
        val permission = if (Build.VERSION.SDK_INT >= 33) "READ_MEDIA_VIDEO" else "READ_EXTERNAL_STORAGE"
        device.executeShellCommand("pm grant $PACKAGE android.permission.$permission")
        val title = "Device-catalog-${UUID.randomUUID().toString().take(8)}.mp4"
        val renamedTitle = "Renamed-$title"
        try {
            fixture("create", title)
            openDeviceVideos()
            val oldTitle = By.text(title)
            fun awaitFixtureArtwork() {
                device.findObject(By.desc("Search device videos"))?.click()
                device.requireNode(By.res("device_video_search")).text = title
                device.requireNode(By.res("device_video_grid").hasDescendant(By.res("video_card_artwork")))
            }
            awaitFixtureArtwork()
            fixture("artwork")
            device.executeShellCommand("am force-stop $PACKAGE")
            openDeviceVideos()
            awaitFixtureArtwork()
            val results = device.requireNode(By.res("device_video_grid").hasDescendant(oldTitle))
            checkNotNull(results.findObject(oldTitle)).click()
            device.requireNode(By.res("player_first_frame"))
            device.pressBack()
            device.requireNode(By.res("device_video_grid").hasDescendant(oldTitle))
            device.requireNode(By.res("device_video_search")).text = ""

            fixture("rename", renamedTitle)
            openDeviceVideos()
            device.requireNode(By.desc("Refresh device videos")).click()
            val newTitle = By.text(renamedTitle)
            device.requireNode(newTitle)
            check(device.wait(Until.gone(oldTitle), 10_000)) { "Previous name is still visible" }

            fixture("delete")
            openDeviceVideos()
            device.requireNode(By.desc("Refresh device videos")).click()
            check(device.wait(Until.gone(newTitle), 10_000)) { "Deleted video is still visible" }
            device.requireNode(By.textStartsWith("Fixture download "))
            check(device.wait(Until.gone(By.desc("Library loading")), 10_000))
            device.requireNode(By.res("hub_nav_network")).click()
            device.requireNode(By.text("Fixture video 000"))
            check(!device.hasObject(By.desc("Library loading")))
            device.requireNode(By.res("hub_nav_device")).click()
            device.requireNode(By.textStartsWith("Fixture download "))
            check(!device.hasObject(By.desc("Library loading")))
            device.requireNode(By.res("hub_nav_network")).click()
            device.requireNode(By.text("Fixture video 000"))
            check(!device.hasObject(By.desc("Library loading")))
            device.requireNode(By.res("hub_nav_downloads")).click()
            device.requireNode(By.res("downloads_list"))
        } finally {
            fixture("delete")
        }
    }
}
