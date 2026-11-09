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
    @Test fun primaryCatalogReconcilesMediaStoreAndOpensThePlayer() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        fun fixture(action: String, title: String? = null) {
            val flags = (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK).toString(16)
            val titleArgument = if (title == null) "" else " --es fixture_name $title"
            val result = device.executeShellCommand(
                "am start -W -f 0x$flags " +
                    "-n $PACKAGE/com.miowser.app.benchmark.FixtureActivity --es device_catalog $action$titleArgument"
            )
            check("Status: ok" in result) { result }
            device.requireNode(By.text("Device fixture: $action"))
        }
        fun openDeviceVideos() {
            device.executeShellCommand("am start -W -n $PACKAGE/com.miowser.app.MainActivity")
            device.requireNode(By.res("hub_destination_device"))
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
            val newTitle = By.text(renamedTitle)
            device.requireNode(newTitle)
            check(device.wait(Until.gone(oldTitle), 10_000)) { "Previous name is still visible" }

            fixture("delete")
            openDeviceVideos()
            check(device.wait(Until.gone(newTitle), 10_000)) { "Deleted video is still visible" }
            device.requireNode(By.textStartsWith("Fixture download "))
            device.requireNode(By.res("hub_destination_saved")).click()
            device.requireNode(By.text("Fixture video 000"))
            device.requireNode(By.res("hub_nav_downloads")).click()
            device.requireNode(By.res("downloads_list"))
        } finally {
            fixture("delete")
        }
    }
}
