package com.miowser.app.library.device

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCatalogRefreshTest {
    private val media = DeviceSource("media:external_primary", DEVICE_MEDIA, "Device storage", generation = "published")
    private val folder = DeviceSource("content://folders/tree/shows", DEVICE_LINK, "Shows", generation = "published")

    @Test
    fun cachedIndexReadsNeverEnumerateSources() {
        assertFalse(DeviceCatalogRefresh.INDEX.selects(media, false, DeviceCatalogScope.All))
        assertFalse(DeviceCatalogRefresh.INDEX.selects(folder, true, DeviceCatalogScope.All))
    }

    @Test
    fun startupWithAutoRescanOffKeepsPublishedSources() {
        assertFalse(DeviceCatalogRefresh.MISSING.selects(media, true, DeviceCatalogScope.All))
        assertFalse(DeviceCatalogRefresh.MISSING.selects(folder, true, DeviceCatalogScope.All))
        assertTrue(DeviceCatalogRefresh.MISSING.selects(folder.copy(generation = null), false, DeviceCatalogScope.All))
    }

    @Test
    fun manualFolderRefreshDoesNotScanOtherSources() {
        val target = DeviceCatalogScope.Source(folder.id)
        assertTrue(DeviceCatalogRefresh.FORCE.selects(folder, true, target))
        assertFalse(DeviceCatalogRefresh.FORCE.selects(media, false, target))
        assertFalse(DeviceCatalogRefresh.FORCE.selects(folder.copy(id = "another"), false, target))
    }

    @Test
    fun permissionChangesOnlyRefreshMediaStore() {
        assertTrue(DeviceCatalogRefresh.FORCE.selects(media, true, DeviceCatalogScope.Media))
        assertFalse(DeviceCatalogRefresh.FORCE.selects(folder, true, DeviceCatalogScope.Media))
    }

    @Test
    fun automaticStartupCheckIncludesPublishedSources() {
        assertTrue(DeviceCatalogRefresh.CHECK.selects(media, true, DeviceCatalogScope.All))
        assertTrue(DeviceCatalogRefresh.CHECK.selects(folder, true, DeviceCatalogScope.All))
    }
}
