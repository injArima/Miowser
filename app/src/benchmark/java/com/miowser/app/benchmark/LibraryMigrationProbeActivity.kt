package com.miowser.app.benchmark

import android.content.Context
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.miowser.app.backup.BACKUP_PREFERENCE_NAMES
import com.miowser.app.backup.BackupSource
import com.miowser.app.backup.DurableRestoreTransaction
import com.miowser.app.backup.encodePreferences
import com.miowser.app.grabber.HlsRendition
import com.miowser.app.grabber.MediaType
import com.miowser.app.library.*
import com.miowser.app.persistence.Persistence
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/** Exercises the production repository and restore journal in the isolated benchmark package. */
class LibraryMigrationProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        val label = TextView(this).apply { text = "Checking Library migration…" }
        setContentView(label)
        lifecycleScope.launch {
            label.text = when (intent.getStringExtra("stage")) {
                "edit" -> editMigratedRecords()
                "prepare" -> prepareInterruptedRestore()
                "verify" -> {
                    check(withContext(Dispatchers.IO) { LibraryRepository.readMedia(this@LibraryMigrationProbeActivity) }
                        == expectedAfterEdits)
                    check(withContext(Dispatchers.IO) { LibraryRepository.readFolders(this@LibraryMigrationProbeActivity) }
                        == LibraryFolderSnapshot(folders, root.id))
                    "Restored Library verified"
                }
                else -> error("Missing migration probe stage")
            }
        }
    }

    private suspend fun editMigratedRecords(): String {
        withContext(Dispatchers.IO) {
            val db = Room.databaseBuilder(applicationContext, LibraryDatabase::class.java, LIBRARY_DATABASE_NAME)
                .build()
            val before = db.library().metadata()
            val oldRows = db.library().media()
            // Abort a migration after it clears the old state and starts inserting the new rows.
            db.openHelper.writableDatabase.execSQL("""
                CREATE TRIGGER fail_fixture_migration BEFORE INSERT ON library_media
                WHEN NEW.mediaUri = '${local.mediaUri}'
                BEGIN SELECT RAISE(ABORT, 'migration fixture rollback'); END
            """.trimIndent())
            try {
                val failure = runCatching { LibraryRepository.readMedia(this@LibraryMigrationProbeActivity) }.exceptionOrNull()
                check(failure?.message.orEmpty().contains("migration fixture rollback"))
                check(db.library().metadata() == before && db.library().media() == oldRows)
            } finally {
                db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_fixture_migration")
                db.close()
            }
        }
        MediaLibrary.restoreAsync(this)
        LibraryFolders.restoreAsync(this)
        check(MediaLibrary.entries.toList() == initialEntries) { "Migrated media differs from the fixture" }
        check(MediaLibrary.opaqueRecords == listOf(opaque)) { "Opaque legacy record was not preserved" }
        check(LibraryFolders.entries.toList() == folders && LibraryFolders.defaultFolderId == root.id) {
            "Migrated folders or default differ from the fixture"
        }
        MediaLibrary.updateProgress(this, remote.identityKey, 9_000, remote.durationMs)
        MediaLibrary.updateProgress(this, local.identityKey, 7_000, local.durationMs)
        awaitWrites()
        MediaLibrary.relinkLocal(this, local.mediaUri, relinkedUri)
        awaitWrites()
        check(legacyMedia(this) == legacyRows)
        return "Library migration edits committed"
    }

    private suspend fun prepareInterruptedRestore(): String {
        val snapshot = withContext(Dispatchers.IO) { LibraryRepository.backupPreferences(this@LibraryMigrationProbeActivity) }
        val media = snapshot.getValue(LEGACY_MEDIA_PREFS).getValue("entries") as String
        check(LibraryMediaSnapshot(LibraryCodec.decode(media), LibraryCodec.unsupportedRows(media)) == expectedAfterEdits)
        val folderPrefs = snapshot.getValue(LEGACY_FOLDER_PREFS)
        check(LibraryFolderCodec.decode(folderPrefs["folders"] as String) == folders)
        check(folderPrefs["default_folder_id"] == root.id)
        MediaLibrary.restoreAsync(this)
        MediaLibrary.clear(this)
        awaitWrites()
        withContext(Dispatchers.IO) {
            check(LibraryRepository.readMedia(this@LibraryMigrationProbeActivity).entries.isEmpty())
            val sources = BACKUP_PREFERENCE_NAMES.map { name ->
                val bytes = encodePreferences(snapshot[name].orEmpty()).toByteArray(Charsets.UTF_8)
                BackupSource("prefs/$name.json") { bytes.inputStream() }
            }
            DurableRestoreTransaction(File(filesDir, "restore_transaction")).prepare(sources, System.currentTimeMillis())
        }
        return "Library backup journal staged"
    }

    private suspend fun awaitWrites() = suspendCancellableCoroutine { continuation ->
        Persistence.afterWrites { if (continuation.isActive) continuation.resume(Unit) }
    }

    companion object {
        private val root = LibraryFolder("root", "Local", domain = "example.test", additionalDomains = listOf("alt.test"))
        private val child = LibraryFolder("child", "Season", parentId = root.id,
            titleRule = LibraryTitleRule(LibraryTitleSource.MEDIA, LibraryRuleOperator.ALL, listOf("Episode 1", "日本語")))
        private val folders = listOf(child, root)
        private val remote = LibraryEntry(
            "https://example.test/main.m3u8", "https://example.test/watch", "Episode 1 日本語", "Remote",
            LibrarySourceKind.REMOTE_STREAM, MediaType.HLS, "Miowser fixture", null, 200, 1_000, 30_000,
            folderId = child.id, manualFolder = true,
            selectedSubtitle = LibrarySubtitle("https://example.test/sub.vtt", "https://example.test/watch", "English", null, null),
            hlsRenditions = listOf(HlsRendition("https://example.test/high.m3u8", "1280x720", 1_000_000))
        )
        private val local = LibraryEntry(
            "content://example.test/files/1", null, "Local video", "Local", LibrarySourceKind.LOCAL_FILE,
            null, null, null, 100, 2_000, 20_000
        )
        private val duplicate = remote.copy(lastPlayedAt = 50, positionMs = 5_000)
        private val opaque = LibraryCodec.encode(listOf(remote)).replace("\tREMOTE_STREAM\t", "\tFUTURE_SOURCE\t")
        private val initialEntries = listOf(remote, local, duplicate)
        private val legacyRows = LibraryCodec.encodeWithUnsupported(initialEntries, listOf(opaque))
        private const val relinkedUri = "content://example.test/files/renamed"
        private val expectedAfterEdits = LibraryMediaSnapshot(
            listOf(remote.copy(positionMs = 9_000), local.copy(mediaUri = relinkedUri, positionMs = 7_000), duplicate),
            listOf(opaque)
        )

        fun seed(context: Context) {
            check(context.getSharedPreferences(LEGACY_MEDIA_PREFS, Context.MODE_PRIVATE).edit().clear()
                .putString("entries", legacyRows).commit())
            check(context.getSharedPreferences(LEGACY_FOLDER_PREFS, Context.MODE_PRIVATE).edit().clear()
                .putString("folders", LibraryFolderCodec.encode(folders))
                .putString("default_folder_id", root.id).commit())
            LibraryRepository.requestLegacyImport(context)
        }

        private fun legacyMedia(context: Context): String? =
            context.getSharedPreferences(LEGACY_MEDIA_PREFS, Context.MODE_PRIVATE).getString("entries", null)
    }
}
