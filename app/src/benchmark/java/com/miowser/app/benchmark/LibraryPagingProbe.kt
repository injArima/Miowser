package com.miowser.app.benchmark

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import com.miowser.app.browser.HubLibrarySort
import com.miowser.app.library.LIBRARY_MIGRATION_1_2
import com.miowser.app.library.LIBRARY_PAGE_SIZE
import com.miowser.app.library.LibraryBrowseQuery
import com.miowser.app.library.LibraryCardId
import com.miowser.app.library.LibraryDatabase
import com.miowser.app.library.LibraryFolderCount
import com.miowser.app.library.mediaWatchProgress
import java.util.UUID
import org.json.JSONObject

private data class PagingFixtureRow(
    val rowId: Int,
    val uri: String,
    val title: String,
    val source: String,
    val folderId: String?,
    val lastPlayed: Long,
    val position: Long,
    val duration: Long
) {
    val identityKey get() = "REMOTE_STREAM:$uri"
}

/** Starts with the actual exported P1 schema and opens it with the production migration. */
internal fun verifyPagingUpgrade(context: Context) {
    val name = "paging-fixture-${UUID.randomUUID()}.db"
    val rows = List(210) { index ->
        PagingFixtureRow(
            index, "https://example.test/${if (index == 3) 2 else index}",
            when (index) {
                0 -> "İstanbul 100%"
                1 -> "istanbul 100%"
                4 -> "\uD83D\uDE00"
                5 -> "\uE000"
                else -> "Video ${index % 70}"
            },
            if (index == 6) "ΤΕΛΟΣ" else "Example",
            if (index < 160) null else "other",
            (index / 3).toLong(), (index % 30).toLong(), if (index % 11 == 0) 0 else 20
        )
    }
    try {
        createPagingV1Fixture(context, name, rows)
        val db = Room.databaseBuilder(context, LibraryDatabase::class.java, name)
            .addMigrations(LIBRARY_MIGRATION_1_2).build()
        try {
                val dao = db.library()
                check(dao.media().map { it.identityKey } == rows.map { it.identityKey })
                check(dao.folderCounts().toSet() == setOf(LibraryFolderCount(null, 160), LibraryFolderCount("other", 50)))
                val occurrences = mutableMapOf<String, Int>()
                val ids = rows.map { row ->
                    val occurrence = occurrences[row.identityKey] ?: 0
                    occurrences[row.identityKey] = occurrence + 1
                    LibraryCardId(row.identityKey, occurrence)
                }
                for (sort in HubLibrarySort.entries) {
                    val order = when (sort) {
                        HubLibrarySort.RECENT -> compareByDescending<PagingFixtureRow> { it.lastPlayed }
                        HubLibrarySort.TITLE -> compareBy<PagingFixtureRow> { it.title.lowercase() }
                            .thenByDescending { it.lastPlayed }
                        HubLibrarySort.PROGRESS -> compareByDescending<PagingFixtureRow> { mediaWatchProgress(it.position, it.duration) }
                            .thenByDescending { it.lastPlayed }
                    }
                    for (search in listOf("", "ISTANBUL", "100%", "τελος", "/152", "missing")) {
                        val query = LibraryBrowseQuery(null, sort, search)
                        val expected = rows.filter { row ->
                            row.folderId == null && listOf(row.title, row.source, row.uri)
                                .any { it.contains(search, ignoreCase = true) }
                        }.sortedWith(order).map { ids[it.rowId] }
                        val actual = expected.indices.step(LIBRARY_PAGE_SIZE).flatMap { offset ->
                            dao.cardIds(query.page(offset, LIBRARY_PAGE_SIZE))
                        }
                        check(dao.browseCount(query.count()) == expected.size)
                        check(actual == expected) { "Unexpected page order for $sort / $search" }
                        check(dao.orderedKeys(query.keys()) == expected.map { it.identityKey })
                        expected.getOrNull(expected.size / 2)?.let { anchor ->
                            check(dao.browseCount(query.rank(anchor)) == expected.indexOf(anchor))
                        }
                    }
                }
                val folder = LibraryBrowseQuery("other", HubLibrarySort.RECENT, "")
                check(dao.browseCount(folder.count()) == 50)
                check(dao.cardIds(folder.page(48, 48)).size == 2)
        } finally {
            db.close()
        }
    } finally {
        context.deleteDatabase(name)
    }
}

private fun createPagingV1Fixture(context: Context, name: String, rows: List<PagingFixtureRow>) {
    val schema = context.assets.open("com.miowser.app.library.LibraryDatabase/1.json")
        .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
    val path = context.getDatabasePath(name)
    path.parentFile?.mkdirs()
    SQLiteDatabase.openOrCreateDatabase(path, null).use { db ->
        val entities = schema.getJSONArray("entities")
        for (index in 0 until entities.length()) {
            val entity = entities.getJSONObject(index)
            val table = entity.getString("tableName")
            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
            val indices = entity.optJSONArray("indices") ?: continue
            for (position in 0 until indices.length()) {
                db.execSQL(indices.getJSONObject(position).getString("createSql").replace("\${TABLE_NAME}", table))
            }
        }
        val setup = schema.getJSONArray("setupQueries")
        for (index in 0 until setup.length()) db.execSQL(setup.getString(index))
        db.beginTransaction()
        try {
            rows.forEach { row ->
                db.execSQL(
                    """
                    INSERT INTO library_media
                        (rowId, identityKey, mediaUri, title, sourceLabel, sourceKind, lastPlayedAt,
                         folderId, manualFolder, explicitlySaved, hlsRenditions)
                    VALUES (?, ?, ?, ?, ?, 'REMOTE_STREAM', ?, ?, 0, 1, '')
                    """.trimIndent(),
                    arrayOf<Any?>(row.rowId, row.identityKey, row.uri, row.title, row.source, row.lastPlayed, row.folderId)
                )
                db.execSQL("INSERT INTO library_watch_state VALUES (?, ?, ?)", arrayOf<Any>(row.rowId, row.position, row.duration))
            }
            db.version = 1
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
