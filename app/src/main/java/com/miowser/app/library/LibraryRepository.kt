package com.miowser.app.library

import android.content.Context
import android.util.AtomicFile
import androidx.room.Room
import com.miowser.app.performance.traceSection
import com.miowser.app.persistence.Persistence
import java.io.File
import java.io.FileNotFoundException
import java.util.UUID
import java.util.concurrent.Callable

internal const val LEGACY_MEDIA_PREFS = "media_library"
internal const val LEGACY_FOLDER_PREFS = "library_folders"
internal const val LIBRARY_DATABASE_NAME = "library.db"
private const val LEGACY_ENTRIES = "entries"
private const val LEGACY_FOLDERS = "folders"
private const val LEGACY_DEFAULT = "default_folder_id"

internal data class LibraryMediaSnapshot(val entries: List<LibraryEntry>, val opaqueRecords: List<String>)
internal data class LibraryFolderSnapshot(val folders: List<LibraryFolder>, val defaultFolderId: String?)

internal object LibraryRepository {
    private val migrationLock = Any()
    @Volatile private var initializedDatabase: LibraryDatabase? = null
    private var openedDatabase: LibraryDatabase? = null

    private data class Structure(
        val media: LibraryMediaSnapshot? = null,
        val folders: LibraryFolderSnapshot? = null
    ) {
        fun with(change: Structure) = Structure(change.media ?: media, change.folders ?: folders)
    }

    // One staging slot on the UI owner, one set of failed writes on the ordered disk writer.
    private var batch: Structure? = null
    private var pendingStructure = Structure()
    private val pendingWatch = linkedMapOf<String, Pair<Long, Long>>()

    fun readMedia(context: Context): LibraryMediaSnapshot {
        val db = database(context)
        return db.runInTransaction(Callable { db.library().mediaSnapshot() })
    }

    fun readFolders(context: Context): LibraryFolderSnapshot {
        val db = database(context)
        return db.runInTransaction(Callable { db.library().folderSnapshot() })
    }

    /** Reads one committed generation for the existing backup archive format. */
    fun backupPreferences(context: Context): Map<String, Map<String, Any>> {
        val db = database(context)
        val (media, folders) = db.runInTransaction(Callable {
            val dao = db.library()
            dao.mediaSnapshot() to dao.folderSnapshot()
        })
        return mapOf(
            LEGACY_MEDIA_PREFS to mapOf(
                LEGACY_ENTRIES to LibraryCodec.encodeWithUnsupported(media.entries, media.opaqueRecords)
            ),
            LEGACY_FOLDER_PREFS to buildMap {
                put(LEGACY_FOLDERS, LibraryFolderCodec.encode(folders.folders))
                folders.defaultFolderId?.let { put(LEGACY_DEFAULT, it) }
            }
        )
    }

    /**
     * Call after the restore journal has committed both legacy preference files. The marker is
     * atomic and small enough to read without loading the legacy Library on every cold start.
     */
    fun requestLegacyImport(context: Context) = synchronized(migrationLock) {
        val marker = generationFile(context)
        val generation = UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        val output = marker.startWrite()
        try {
            output.write(generation)
        } catch (error: Throwable) {
            marker.failWrite(output)
            throw error
        }
        marker.finishWrite(output)
        check(marker.readFully().contentEquals(generation)) { "Could not save the Library restore generation." }
        initializedDatabase = null
    }

    private fun generationFile(context: Context) =
        AtomicFile(File(context.filesDir, "library-legacy-generation"))

    private fun database(context: Context): LibraryDatabase = initializedDatabase ?: synchronized(migrationLock) {
        initializedDatabase?.let { return@synchronized it }
        val app = context.applicationContext
        val db = openedDatabase ?: Room.databaseBuilder(app, LibraryDatabase::class.java, LIBRARY_DATABASE_NAME)
            .build().also { openedDatabase = it }
        val marker = generationFile(app)
        val generation = try {
            marker.readFully().toString(Charsets.UTF_8).also { check(UUID.fromString(it).toString() == it) }
        } catch (error: FileNotFoundException) {
            if (marker.baseFile.exists()) throw error
            null
        }
        db.runInTransaction {
            val dao = db.library()
            val previous = dao.metadata()
            if (previous == null || (generation != null && previous.generation != generation)) {
                traceSection("LibraryLegacyMigration") {
                    val media = app.getSharedPreferences(LEGACY_MEDIA_PREFS, Context.MODE_PRIVATE)
                    val folders = app.getSharedPreferences(LEGACY_FOLDER_PREFS, Context.MODE_PRIVATE)
                    val rawMedia = media.getString(LEGACY_ENTRIES, null).orEmpty()
                    dao.replaceMedia(LibraryMediaSnapshot(
                        LibraryCodec.decode(rawMedia).sortedByDescending(LibraryEntry::lastPlayedAt),
                        LibraryCodec.unsupportedRows(rawMedia)
                    ))
                    dao.replaceFolders(LibraryFolderCodec.decode(folders.getString(LEGACY_FOLDERS, null).orEmpty()))
                    dao.putMetadata(LibraryMetadata(
                        generation = generation ?: "initial",
                        defaultFolderId = folders.getString(LEGACY_DEFAULT, null)
                    ))
                }
            }
        }
        db.also { initializedDatabase = it }
    }

    /** Keep folder membership and folder edits in one transaction for a single UI action. */
    fun <T> batchStructure(context: Context, action: () -> T): T {
        Persistence.requireOwner()
        check(batch == null)
        batch = Structure()
        return try {
            action()
        } finally {
            val change = checkNotNull(batch)
            batch = null
            if (change.media != null || change.folders != null) queueStructure(context, change)
        }
    }

    fun saveMedia(context: Context, snapshot: LibraryMediaSnapshot) =
        saveStructure(context, Structure(media = snapshot))

    fun saveFolders(context: Context, snapshot: LibraryFolderSnapshot) =
        saveStructure(context, Structure(folders = snapshot))

    private fun saveStructure(context: Context, change: Structure) {
        Persistence.requireOwner()
        val staged = batch
        if (staged == null) queueStructure(context, change)
        else batch = staged.with(change)
    }

    private fun queueStructure(context: Context, change: Structure) {
        val app = context.applicationContext
        Persistence.submit("library") {
            pendingStructure = pendingStructure.with(change)
            // The newer complete media snapshot includes every earlier progress edit.
            if (change.media != null) pendingWatch.clear()
            traceSection("LibrarySnapshotCommit") { commitPending(app) }
        }
    }

    fun saveWatch(context: Context, entry: LibraryEntry) {
        Persistence.requireOwner()
        val app = context.applicationContext
        val key = entry.identityKey
        val watch = entry.positionMs to entry.durationMs
        Persistence.submit("library") {
            pendingWatch[key] = watch
            traceSection("LibraryProgressCommit") { commitPending(app) }
        }
    }

    // If a commit fails, the next Library command retries the outstanding work in order. This
    // also makes OrderedWriter's latest-command retry safe when several different videos change.
    private fun commitPending(context: Context): Boolean {
        val db = database(context)
        db.runInTransaction {
            val dao = db.library()
            pendingStructure.media?.let { dao.replaceMedia(it) }
            pendingStructure.folders?.let {
                dao.replaceFolders(it.folders)
                dao.setDefaultFolder(it.defaultFolderId)
            }
            pendingWatch.forEach { (key, watch) ->
                check(dao.updateWatch(key, watch.first, watch.second) == 1)
            }
        }
        pendingStructure = Structure()
        pendingWatch.clear()
        return true
    }

    private fun LibraryDao.mediaSnapshot() = LibraryMediaSnapshot(media().map { it.toEntry() }, opaqueRecords())

    private fun LibraryDao.folderSnapshot() = LibraryFolderSnapshot(
        folders().map { it.toFolder() }, checkNotNull(metadata()).defaultFolderId
    )

    private fun LibraryDao.replaceMedia(snapshot: LibraryMediaSnapshot) {
        clearWatch()
        clearMedia()
        clearOpaqueRecords()
        insertMedia(snapshot.entries.mapIndexed(::LibraryMediaRow))
        insertWatch(snapshot.entries.mapIndexed { rowId, entry ->
            LibraryWatchRow(rowId, entry.positionMs.coerceAtLeast(0), entry.durationMs.coerceAtLeast(0))
        })
        insertOpaqueRecords(snapshot.opaqueRecords.mapIndexed(::LibraryOpaqueRow))
    }

    private fun LibraryDao.replaceFolders(records: List<LibraryFolder>) {
        clearFolders()
        insertFolders(records.mapIndexed(::LibraryFolderRow))
    }
}
