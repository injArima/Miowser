package com.miowser.app.library

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureNanoTime

/** On-device row-update prototype for the F7 migration gate; never touches user data. */
@RunWith(AndroidJUnit4::class)
class StructuredStoreScaleTest {
    @Test fun compareWholeSnapshotWithIndexedRows() {
        for (count in listOf(500, 5_000)) {
            val entries = List(count) { index ->
                LibraryEntry("https://media.example/$index", "https://media.example/watch/$index",
                    "Video $index", "media.example", LibrarySourceKind.REMOTE_STREAM,
                    null, null, null, index.toLong(), 0L, 60_000L,
                    folderId = "folder-${index % 20}")
            }
            val db = SQLiteDatabase.create(null)
            try {
                db.execSQL("CREATE TABLE media(id INTEGER PRIMARY KEY, folder TEXT, position_ms INTEGER)")
                db.execSQL("CREATE INDEX media_folder ON media(folder)")
                db.beginTransaction()
                try {
                    entries.forEachIndexed { index, entry ->
                        db.insertOrThrow("media", null, ContentValues().apply {
                            put("id", index)
                            put("folder", entry.folderId)
                            put("position_ms", entry.positionMs)
                        })
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }

                var selected = 0
                val indexedQueryMs = measureNanoTime {
                    db.query("media", arrayOf("id"), "folder=?", arrayOf("folder-1"),
                        null, null, null).use { selected = it.count }
                } / 1_000_000.0
                assertEquals(count / 20, selected)

                val rowsMs = measureNanoTime {
                    repeat(100) { turn ->
                        db.update("media", ContentValues().apply { put("position_ms", turn) },
                            "id=?", arrayOf((turn * count / 100).toString()))
                    }
                } / 1_000_000.0
                val snapshot = entries.toMutableList()
                val snapshotsMs = measureNanoTime {
                    repeat(100) { turn ->
                        val index = turn * count / 100
                        snapshot[index] = snapshot[index].copy(positionMs = turn.toLong())
                        LibraryCodec.encode(snapshot)
                    }
                } / 1_000_000.0
                println("Library on-device count=$count rowUpdatesMs=$rowsMs " +
                    "snapshotEncodeOnlyMs=$snapshotsMs indexedFolderQueryMs=$indexedQueryMs")
            } finally { db.close() }
        }
    }
}
