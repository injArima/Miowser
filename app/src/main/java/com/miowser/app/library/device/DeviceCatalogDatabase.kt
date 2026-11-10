package com.miowser.app.library.device

import androidx.room.AutoMigration
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.RoomDatabase
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.miowser.app.library.librarySearchKey

internal const val DEVICE_PAGE_SIZE = 48
internal const val DEVICE_MEDIA = 0
internal const val DEVICE_LINK = 1
internal const val DEVICE_DOWNLOADS = 2
internal const val DOWNLOAD_SOURCE = "downloads"

internal fun deviceFolderKey(source: String, folder: String): String = "$source/$folder"

internal fun isDeviceFolderExcluded(exclusions: Set<String>, source: String, folder: String): Boolean {
    val key = deviceFolderKey(source, folder)
    return exclusions.any { excluded ->
        key == excluded || key.startsWith(if (excluded.endsWith('/')) excluded else "$excluded/")
    }
}

@Entity(tableName = "device_sources")
internal data class DeviceSource(
    @PrimaryKey val id: String,
    val kind: Int,
    val label: String,
    val generation: String? = null,
    val stamp: String? = null,
    val linked: Boolean = true
)

@Entity(
    tableName = "device_videos",
    primaryKeys = ["sourceId", "generation", "uri"],
    indices = [
        Index("canonicalUri"),
        Index(value = ["sourceId", "generation", "modifiedAt"]),
        Index(value = ["sourceId", "generation", "nameKey"]),
        Index(value = ["sourceId", "generation", "folder"])
    ]
)
internal data class DeviceVideo(
    val sourceId: String,
    val generation: String,
    val uri: String,
    val canonicalUri: String,
    val priority: Int,
    val title: String,
    val folder: String,
    val durationMs: Long,
    val height: Int?,
    val modifiedAt: Long,
    val revision: String,
    val nameKey: String = librarySearchKey(title),
    val folderKey: String = librarySearchKey(folder)
)

internal data class DeviceVideoFolder(val sourceId: String, val folder: String, val videoCount: Int)

@Dao
internal interface DeviceCatalogDao {
    @Query("SELECT * FROM device_sources")
    fun sources(): List<DeviceSource>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveSource(source: DeviceSource)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun stage(videos: List<DeviceVideo>)

    @Query("DELETE FROM device_videos WHERE sourceId = :source AND generation = :generation")
    fun discard(source: String, generation: String)

    @Query("DELETE FROM device_videos WHERE sourceId = :source AND generation != :generation")
    fun prune(source: String, generation: String)

    @Query("""
        DELETE FROM device_videos WHERE NOT EXISTS (
            SELECT 1 FROM device_sources s WHERE s.id = sourceId AND s.generation = device_videos.generation
        )
    """)
    fun discardInterrupted()

    @RawQuery fun rows(query: SupportSQLiteQuery): List<DeviceVideo>
    @RawQuery fun folders(query: SupportSQLiteQuery): List<DeviceVideoFolder>
    @RawQuery fun count(query: SupportSQLiteQuery): Int

    @Query("""
        SELECT DISTINCT v.folder FROM device_videos v
        JOIN device_sources s ON s.id = v.sourceId AND s.generation = v.generation
        WHERE v.sourceId = :source AND (:prefix = '' OR instr(v.folder, :prefix) = 1)
    """)
    fun directoryPaths(source: String, prefix: String): List<String>
}

@Database(
    entities = [DeviceSource::class, DeviceVideo::class],
    version = 2,
    autoMigrations = [AutoMigration(from = 1, to = 2)]
)
internal abstract class DeviceCatalogDatabase : RoomDatabase() {
    abstract fun catalog(): DeviceCatalogDao
}

internal data class DeviceBrowseQuery(
    val allowedSources: Set<String>,
    val source: String?,
    val search: String,
    val titleOrder: Boolean,
    val folder: String? = null,
    val unified: Boolean = false,
    val exclusions: Set<String> = emptySet()
) {
    private val sources = if (source == null) allowedSources else allowedSources.intersect(setOf(source))
    private val placeholders = sources.joinToString { "?" }.ifBlank { "NULL" }
    private val aliasSources = if (unified) allowedSources else sources
    private val aliasPlaceholders = aliasSources.joinToString { "?" }.ifBlank { "NULL" }
    private val term = librarySearchKey(search.trim())
    private val matching = if (term.isEmpty()) "" else " AND (instr(v.nameKey, ?) > 0 OR instr(v.folderKey, ?) > 0)"
    private val duplicateMatch = if (unified || term.isEmpty()) "" else " AND (instr(d.nameKey, ?) > 0 OR instr(d.folderKey, ?) > 0)"
    private val folderMatch = if (folder == null) "" else " AND v.folder = ?"
    private val duplicateFolder = if (unified || folder == null) "" else " AND d.folder = ?"
    // MediaStore locators already include the volume and row ID. Only mixed or linked
    // sources can publish multiple locators for the same canonical URI.
    private val deduplicate = sources.any { !it.startsWith("media:") }
    // Find URI matches before checking source generations. On a fresh database SQLite
    // otherwise tends to put the source loop first, repeating the alias lookup per source.
    private val aliases = if (!deduplicate) "" else """
        AND NOT EXISTS (
            SELECT 1 FROM device_videos d INDEXED BY index_device_videos_canonicalUri
            CROSS JOIN device_sources ds ON ds.id = d.sourceId AND ds.generation = d.generation
            WHERE d.sourceId IN ($aliasPlaceholders)$duplicateMatch$duplicateFolder AND d.canonicalUri = v.canonicalUri AND (
                d.priority < v.priority OR (d.priority = v.priority AND (
                    d.sourceId < v.sourceId OR (d.sourceId = v.sourceId AND d.uri < v.uri)
                ))
            )
        )
    """.trimIndent()
    private val excluded: List<Pair<String, String>> = exclusions.map { it.substringBefore('/') to it.substringAfter('/', "") }
        .filter { (id, _) -> id in sources }
    private val exclusionsFilter: String = if (excluded.isEmpty()) "" else """
        AND NOT EXISTS (
            SELECT 1 FROM (VALUES ${excluded.joinToString { "(?, ?)" }}) e
            WHERE e.column1 = v.sourceId AND (
                e.column2 = '' OR e.column2 = v.folder OR instr(v.folder, e.column2 || '/') = 1
            )
        )
    """.trimIndent()
    private val scopedFilter = """
        FROM device_videos v JOIN device_sources s ON s.id = v.sourceId AND s.generation = v.generation
        WHERE v.sourceId IN ($placeholders)$matching$folderMatch
    """.trimIndent()
    private val filter = "$scopedFilter $exclusionsFilter $aliases"
    private val scopeArgs: List<Any> = (sources.toList() + if (term.isEmpty()) emptyList() else listOf(term, term))
        .let { if (folder == null) it else it + folder }
    private val args: List<Any> = scopeArgs + excluded.flatMap { (id, path) -> listOf(id, path) } +
        if (!deduplicate) emptyList() else if (unified) aliasSources.toList() else scopeArgs
    private val folderFilter = if (unified) filter else scopedFilter
    private val folderArgs = if (unified) args else scopeArgs

    fun count() = SimpleSQLiteQuery("SELECT COUNT(*) $filter", args.toTypedArray())

    fun page(start: Int, limit: Int): SimpleSQLiteQuery {
        val order = if (titleOrder) "v.nameKey, v.modifiedAt DESC" else "v.modifiedAt DESC, v.nameKey"
        return SimpleSQLiteQuery("SELECT v.* $filter ORDER BY $order, v.canonicalUri LIMIT ? OFFSET ?",
            (args + listOf(limit, start)).toTypedArray())
    }

    fun folderCount() = SimpleSQLiteQuery(
        "SELECT COUNT(*) FROM (SELECT 1 $folderFilter GROUP BY v.sourceId, v.folder)", folderArgs.toTypedArray()
    )

    fun folderPage(start: Int, limit: Int) = SimpleSQLiteQuery(
        "SELECT v.sourceId, v.folder, COUNT(DISTINCT v.canonicalUri) AS videoCount $folderFilter " +
            "GROUP BY v.sourceId, v.folder ORDER BY v.folder COLLATE NOCASE, v.folder, v.sourceId LIMIT ? OFFSET ?",
        (folderArgs + listOf(limit, start)).toTypedArray()
    )
}
