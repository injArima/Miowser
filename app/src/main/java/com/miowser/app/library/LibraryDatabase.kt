package com.miowser.app.library

import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RewriteQueriesToDropUnusedColumns
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteQuery
import com.miowser.app.grabber.HlsRendition
import com.miowser.app.grabber.MediaType
import org.json.JSONArray

@Entity(tableName = "library_media", indices = [
    Index("identityKey"),
    Index(value = ["folderId", "lastPlayedAt", "rowId"], orders = [Index.Order.ASC, Index.Order.DESC, Index.Order.ASC]),
    Index(value = ["folderId", "titleSortKey", "lastPlayedAt", "rowId"],
        orders = [Index.Order.ASC, Index.Order.ASC, Index.Order.DESC, Index.Order.ASC])
])
internal data class LibraryMediaRow(
    // Keep every legacy row, including duplicate identities that Library Health can repair.
    @PrimaryKey val rowId: Int,
    val identityKey: String,
    val mediaUri: String,
    val pageUrl: String?,
    val title: String,
    val sourceLabel: String,
    val sourceKind: LibrarySourceKind,
    val mediaType: MediaType?,
    val userAgent: String?,
    val thumbnailUrl: String?,
    val lastPlayedAt: Long,
    val pageTitle: String?,
    val videoName: String?,
    val folderId: String?,
    val qualityHeight: Int?,
    val requestReferer: String?,
    val requestOrigin: String?,
    @Embedded(prefix = "subtitle_") val selectedSubtitle: LibrarySubtitle?,
    val manualFolder: Boolean,
    val explicitlySaved: Boolean,
    val hlsRenditions: List<HlsRendition>,
    @ColumnInfo(defaultValue = "X''") val titleSortKey: ByteArray,
    @ColumnInfo(defaultValue = "''") val titleSearch: String,
    @ColumnInfo(defaultValue = "''") val sourceSearch: String,
    @ColumnInfo(defaultValue = "''") val uriSearch: String
) {
    constructor(rowId: Int, entry: LibraryEntry) : this(
        rowId, entry.identityKey, entry.mediaUri, entry.pageUrl, entry.title, entry.sourceLabel,
        entry.sourceKind, entry.mediaType, entry.userAgent, entry.thumbnailUrl, entry.lastPlayedAt,
        entry.pageTitle, entry.videoName, entry.folderId, entry.qualityHeight, entry.requestReferer,
        entry.requestOrigin, entry.selectedSubtitle, entry.manualFolder, entry.explicitlySaved,
        entry.hlsRenditions, libraryTitleSortKey(entry.title), librarySearchKey(entry.title),
        librarySearchKey(entry.sourceLabel), librarySearchKey(entry.mediaUri)
    )
}

@Entity(tableName = "library_watch_state")
internal data class LibraryWatchRow(
    @PrimaryKey val rowId: Int,
    val positionMs: Long,
    val durationMs: Long,
    @ColumnInfo(defaultValue = "0") val progress: Float
) {
    constructor(rowId: Int, entry: LibraryEntry) : this(
        rowId, entry.positionMs.coerceAtLeast(0), entry.durationMs.coerceAtLeast(0),
        mediaWatchProgress(entry.positionMs, entry.durationMs)
    )
}

@Entity(tableName = "library_folders")
internal data class LibraryFolderRow(
    @PrimaryKey val rowId: Int,
    val id: String,
    val name: String,
    val domain: String?,
    val parentId: String?,
    val additionalDomains: List<String>,
    val websiteOperator: LibraryRuleOperator,
    @Embedded(prefix = "title_") val titleRule: LibraryTitleRule?
) {
    constructor(rowId: Int, folder: LibraryFolder) : this(
        rowId, folder.id, folder.name, folder.domain, folder.parentId, folder.additionalDomains,
        folder.websiteOperator, folder.titleRule
    )

    fun toFolder() = LibraryFolder(id, name, domain, parentId, additionalDomains, websiteOperator, titleRule)
}

@Entity(tableName = "library_opaque_records")
internal data class LibraryOpaqueRow(@PrimaryKey val rowId: Int, val record: String)

@Entity(tableName = "library_metadata")
internal data class LibraryMetadata(
    @PrimaryKey val id: Int = 0,
    val generation: String,
    val defaultFolderId: String?
)

@Dao
internal interface LibraryDao {
    @RewriteQueriesToDropUnusedColumns
    @Query("""
        SELECT library_media.*, library_watch_state.positionMs, library_watch_state.durationMs
        FROM library_media INNER JOIN library_watch_state USING (rowId)
        ORDER BY library_media.rowId
    """)
    fun media(): List<LibraryEntry>

    @RawQuery fun cardIds(query: SupportSQLiteQuery): List<LibraryCardId>
    @RawQuery fun orderedKeys(query: SupportSQLiteQuery): List<String>
    @RawQuery fun browseCount(query: SupportSQLiteQuery): Int

    @Query("SELECT folderId, COUNT(*) AS count FROM library_media GROUP BY folderId")
    fun folderCounts(): List<LibraryFolderCount>

    @Query("SELECT * FROM library_folders ORDER BY rowId")
    fun folders(): List<LibraryFolderRow>

    @Query("SELECT record FROM library_opaque_records ORDER BY rowId")
    fun opaqueRecords(): List<String>

    @Query("SELECT * FROM library_metadata WHERE id = 0")
    fun metadata(): LibraryMetadata?

    @Query("""
        UPDATE library_watch_state SET positionMs = :positionMs, durationMs = :durationMs, progress = :progress
        WHERE rowId = (SELECT MIN(rowId) FROM library_media WHERE identityKey = :identityKey)
    """)
    fun updateWatch(identityKey: String, positionMs: Long, durationMs: Long, progress: Float): Int

    @Query("""
        UPDATE library_media SET thumbnailUrl = :thumbnail, qualityHeight = :height,
            subtitle_url = :subtitleUrl, subtitle_pageUrl = :subtitlePage, subtitle_label = :subtitleLabel,
            subtitle_requestReferer = :referer, subtitle_requestOrigin = :origin
        WHERE rowId = (SELECT MIN(rowId) FROM library_media WHERE identityKey = :identityKey)
    """)
    fun updateDetails(
        identityKey: String, thumbnail: String?, height: Int?, subtitleUrl: String?, subtitlePage: String?,
        subtitleLabel: String?, referer: String?, origin: String?
    ): Int

    @Query("UPDATE library_metadata SET defaultFolderId = :id WHERE id = 0")
    fun setDefaultFolder(id: String?)

    @Upsert fun putMetadata(metadata: LibraryMetadata)
    @Insert fun insertMedia(rows: List<LibraryMediaRow>)
    @Insert fun insertWatch(rows: List<LibraryWatchRow>)
    @Insert fun insertFolders(rows: List<LibraryFolderRow>)
    @Insert fun insertOpaqueRecords(rows: List<LibraryOpaqueRow>)
    @Query("DELETE FROM library_media") fun clearMedia()
    @Query("DELETE FROM library_watch_state") fun clearWatch()
    @Query("DELETE FROM library_folders") fun clearFolders()
    @Query("DELETE FROM library_opaque_records") fun clearOpaqueRecords()
}

internal class LibraryConverters {
    @TypeConverter fun encodeStrings(values: List<String>): String = JSONArray(values).toString()
    @TypeConverter fun decodeStrings(raw: String): List<String> = JSONArray(raw).let { array ->
        List(array.length(), array::getString)
    }
    @TypeConverter fun encodeRenditions(values: List<HlsRendition>): String = LibraryCodec.encodeHlsRenditions(values)
    @TypeConverter fun decodeRenditions(raw: String): List<HlsRendition> = LibraryCodec.decodeHlsRenditions(raw)
}

@Database(
    entities = [LibraryMediaRow::class, LibraryWatchRow::class, LibraryFolderRow::class,
        LibraryOpaqueRow::class, LibraryMetadata::class],
    version = 2
)
@TypeConverters(LibraryConverters::class)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}

internal val LIBRARY_MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE library_media ADD COLUMN titleSortKey BLOB NOT NULL DEFAULT X''")
        db.execSQL("ALTER TABLE library_media ADD COLUMN titleSearch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE library_media ADD COLUMN sourceSearch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE library_media ADD COLUMN uriSearch TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE library_watch_state ADD COLUMN progress REAL NOT NULL DEFAULT 0")

        db.compileStatement("""
            UPDATE library_media SET titleSortKey = ?, titleSearch = ?, sourceSearch = ?, uriSearch = ?
            WHERE rowId = ?
        """).use { update ->
            db.query("SELECT rowId, title, sourceLabel, mediaUri FROM library_media ORDER BY rowId").use { rows ->
                while (rows.moveToNext()) {
                    val title = rows.getString(1)
                    update.bindBlob(1, libraryTitleSortKey(title))
                    update.bindString(2, librarySearchKey(title))
                    update.bindString(3, librarySearchKey(rows.getString(2)))
                    update.bindString(4, librarySearchKey(rows.getString(3)))
                    update.bindLong(5, rows.getLong(0))
                    update.executeUpdateDelete()
                }
            }
        }
        db.compileStatement("UPDATE library_watch_state SET progress = ? WHERE rowId = ?").use { update ->
            db.query("SELECT rowId, positionMs, durationMs FROM library_watch_state ORDER BY rowId").use { rows ->
                while (rows.moveToNext()) {
                    update.bindDouble(1, mediaWatchProgress(rows.getLong(1), rows.getLong(2)).toDouble())
                    update.bindLong(2, rows.getLong(0))
                    update.executeUpdateDelete()
                }
            }
        }
        db.execSQL("CREATE INDEX index_library_media_folderId_lastPlayedAt_rowId ON library_media (folderId, lastPlayedAt DESC, rowId)")
        db.execSQL("CREATE INDEX index_library_media_folderId_titleSortKey_lastPlayedAt_rowId ON library_media (folderId, titleSortKey, lastPlayedAt DESC, rowId)")
    }
}
