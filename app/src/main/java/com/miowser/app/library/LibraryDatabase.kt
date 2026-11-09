package com.miowser.app.library

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.miowser.app.grabber.HlsRendition
import com.miowser.app.grabber.MediaType
import org.json.JSONArray

@Entity(tableName = "library_media", indices = [Index("identityKey")])
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
    val hlsRenditions: List<HlsRendition>
) {
    constructor(rowId: Int, entry: LibraryEntry) : this(
        rowId, entry.identityKey, entry.mediaUri, entry.pageUrl, entry.title, entry.sourceLabel,
        entry.sourceKind, entry.mediaType, entry.userAgent, entry.thumbnailUrl, entry.lastPlayedAt,
        entry.pageTitle, entry.videoName, entry.folderId, entry.qualityHeight, entry.requestReferer,
        entry.requestOrigin, entry.selectedSubtitle, entry.manualFolder, entry.explicitlySaved,
        entry.hlsRenditions
    )
}

@Entity(tableName = "library_watch_state")
internal data class LibraryWatchRow(
    @PrimaryKey val rowId: Int,
    val positionMs: Long,
    val durationMs: Long
)

internal data class LibraryMediaWithWatch(
    @Embedded val media: LibraryMediaRow,
    val positionMs: Long,
    val durationMs: Long
) {
    fun toEntry(): LibraryEntry = with(media) {
        LibraryEntry(
            mediaUri, pageUrl, title, sourceLabel, sourceKind, mediaType, userAgent, thumbnailUrl,
            lastPlayedAt, positionMs, durationMs, pageTitle, videoName, folderId, qualityHeight,
            requestReferer, requestOrigin, selectedSubtitle, manualFolder, explicitlySaved, hlsRenditions
        )
    }
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
    @Query("""
        SELECT library_media.*, library_watch_state.positionMs, library_watch_state.durationMs
        FROM library_media INNER JOIN library_watch_state USING (rowId)
        ORDER BY library_media.rowId
    """)
    fun media(): List<LibraryMediaWithWatch>

    @Query("SELECT * FROM library_folders ORDER BY rowId")
    fun folders(): List<LibraryFolderRow>

    @Query("SELECT record FROM library_opaque_records ORDER BY rowId")
    fun opaqueRecords(): List<String>

    @Query("SELECT * FROM library_metadata WHERE id = 0")
    fun metadata(): LibraryMetadata?

    @Query("""
        UPDATE library_watch_state SET positionMs = :positionMs, durationMs = :durationMs
        WHERE rowId = (SELECT MIN(rowId) FROM library_media WHERE identityKey = :identityKey)
    """)
    fun updateWatch(identityKey: String, positionMs: Long, durationMs: Long): Int

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
    version = 1
)
@TypeConverters(LibraryConverters::class)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun library(): LibraryDao
}
