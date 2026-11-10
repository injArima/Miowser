package com.miowser.app.benchmark

import android.content.Context
import android.util.Log
import androidx.room.Room
import com.miowser.app.library.device.DEVICE_LINK
import com.miowser.app.library.device.DEVICE_MEDIA
import com.miowser.app.library.device.DEVICE_PAGE_SIZE
import com.miowser.app.library.device.DeviceBrowseQuery
import com.miowser.app.library.device.DeviceCatalogDatabase
import com.miowser.app.library.device.DeviceSource
import com.miowser.app.library.device.DeviceVideo

internal fun verifyDeviceCatalogScale(context: Context) {
    val db = Room.inMemoryDatabaseBuilder(context, DeviceCatalogDatabase::class.java).build()
    try {
        val dao = db.catalog()
        val media = DeviceSource("media:fixture", DEVICE_MEDIA, "Fixture storage", generation = "fixture")
        val link = DeviceSource("link:fixture", DEVICE_LINK, "Fixture link", generation = "fixture")
        dao.saveSource(media)
        dao.saveSource(link)
        var inserted = 0
        for (size in listOf(10_000, 50_000)) {
            db.runInTransaction {
                for (start in inserted until size step 200) {
                    dao.stage((start until minOf(start + 200, size)).map { index ->
                        val uri = "content://media/fixture/video/media/$index"
                        DeviceVideo(media.id, "fixture", uri, uri, DEVICE_MEDIA, "Video $index",
                            "Folder ${index % 25}", 30_000, 720, index.toLong(), "fixture:$index")
                    })
                }
            }
            inserted = size
            val mediaOnly = DeviceBrowseQuery(setOf(media.id), null, "", false)
            val mixed = DeviceBrowseQuery(setOf(media.id, link.id), null, "", false)
            val search = DeviceBrowseQuery(setOf(media.id), null, "Folder 24", false)
            check(dao.count(search.count()) == size / 25)
            check(dao.count(mediaOnly.folderCount()) == 25)
            val folders = dao.folders(mediaOnly.folderPage(0, DEVICE_PAGE_SIZE))
            check(folders.size == 25 && folders.all { it.videoCount == size / 25 })
            val firstFolder = mediaOnly.copy(folder = folders.first().folder)
            check(dao.count(firstFolder.count()) == folders.first().videoCount)
            check(dao.rows(firstFolder.page(0, DEVICE_PAGE_SIZE)).all { it.folder == folders.first().folder })
            val pageStart = size / 2
            fun measure(query: DeviceBrowseQuery): Pair<List<String>, Double> {
                val samples = ArrayList<Long>(5)
                var rows = emptyList<String>()
                repeat(6) { run ->
                    val started = System.nanoTime()
                    check(dao.count(query.count()) == size)
                    rows = dao.rows(query.page(pageStart, DEVICE_PAGE_SIZE)).map(DeviceVideo::uri)
                    if (run > 0) samples += System.nanoTime() - started
                }
                check(rows.size == DEVICE_PAGE_SIZE)
                return rows to samples.sorted()[2] / 1_000_000.0
            }
            val fast = measure(mediaOnly)
            val aliasAware = measure(mixed)
            check(fast.first == aliasAware.first)
            Log.i("DeviceCatalogScale", "rows=$size count+middlePage medianMs mediaOnly=${fast.second} aliasAware=${aliasAware.second}")
        }
        val first = "content://media/fixture/video/media/0"
        dao.stage(listOf(DeviceVideo(link.id, "fixture", "content://fixture/document/0", first, DEVICE_LINK,
            "Alias of video zero", "Linked folder", 30_000, 720, 0, "fixture:0")))
        check(dao.count(DeviceBrowseQuery(setOf(media.id, link.id), null, "", false).count()) == 50_000)
        val aliasSearch = DeviceBrowseQuery(setOf(media.id, link.id), null, "Linked folder", false)
        check(dao.rows(aliasSearch.page(0, DEVICE_PAGE_SIZE)).single().canonicalUri == first)
    } finally {
        db.close()
    }
}
