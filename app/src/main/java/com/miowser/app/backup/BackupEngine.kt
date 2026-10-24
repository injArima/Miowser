package com.miowser.app.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.miowser.app.downloader.DownloadCodec
import com.miowser.app.downloader.DownloadStatus
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/** App-owned data only. Browser history, tabs, private state, cookies and transfer segments stay out. */
private val PREF_NAMES = BACKUP_PREFERENCE_NAMES
private val FILE_DIRECTORIES = listOf(
    "adblock", "speed-dial-icons", "library_thumbnails", "download_thumbnails", "library_subtitles"
)
private const val BACKUP_MIME = "application/zip"
private const val BACKUP_PREFIX = "miowser-backup-"
private const val AUTOMATIC_PREFIX = "miowser-auto-"
private const val MAX_AUTOMATIC_BACKUPS = 10

/** Only text/settings, small display assets and selected captions enter the archive. */
object BackupEngine {
    @Synchronized
    fun create(context: Context, treeUri: Uri, automatic: Boolean = false): BackupSummary {
        val app = context.applicationContext
        var snapshotDirectory: File? = null
        val temporary = File.createTempFile("miowser-backup-", ".zip", app.cacheDir)
        try {
            var staged: Triple<File, List<BackupSource>, Int>? = null
            repeat(3) {
                if (staged != null) return@repeat
                awaitWrites()
                PREF_NAMES.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).all }
                val logical = onOwner { PREF_NAMES.associateWith { snapshotPreferences(app, it) } }
                val directory = File(app.cacheDir, "backup-snapshot-${System.nanoTime()}").apply { mkdirs() }
                val sources = mutableListOf<BackupSource>()
                var assetRevision = 0L
                var skippedArtwork = 0
                try {
                    com.miowser.app.persistence.RestoreWriteFence.stage {
                        val assets = FILE_DIRECTORIES.flatMap { name -> File(app.filesDir, name).listFiles()
                            ?.filter { it.isFile && allowedBackupPath("files/$name/${it.name}") }.orEmpty() }
                        val preferences = logical.mapValues { (name, values) ->
                            val encoded = encodePreferences(backupPreferenceValues(name, values))
                            require(encoded.length.toLong() <= MAX_BACKUP_FILE_BYTES) { "A settings store is too large to back up safely." }
                            requireSpace(app, encoded.length.toLong() * 4 + 32L * 1024 * 1024)
                            File(directory, "prefs/$name.json").apply {
                                parentFile?.mkdirs()
                                bufferedWriter(Charsets.UTF_8).use { it.write(encoded) }
                                require(length() <= MAX_BACKUP_FILE_BYTES) { "A settings store is too large to back up safely." }
                            }
                        }
                        val candidates = assets.associateBy { "files/${it.parentFile!!.name}/${it.name}" }
                        val selection = selectBackupAssets(candidates.map { (name, file) ->
                            BackupAsset(name, file.length(), file.lastModified()) },
                            preferences.values.sumOf(File::length), preferences.size)
                        skippedArtwork = selection.skippedArtwork
                        requireSpace(app, (selection.assets.sumOf { it.bytes } + preferences.values.sumOf(File::length)) * 3 + 32L * 1024 * 1024)
                        preferences.forEach { (name, file) ->
                            sources += BackupSource("prefs/$name.json") { file.inputStream() }
                        }
                        selection.assets.map { candidates.getValue(it.path) }.forEach { file ->
                            val path = "files/${file.parentFile!!.name}/${file.name}"
                            val copy = File(directory, path).apply { parentFile?.mkdirs() }
                            file.copyTo(copy)
                            sources += BackupSource(path) { copy.inputStream() }
                        }
                        assetRevision = com.miowser.app.persistence.RestoreWriteFence.revision
                    }
                    awaitWrites()
                    val current = onOwner { PREF_NAMES.associateWith { snapshotPreferences(app, it) } }
                    if (logical == current && assetRevision == com.miowser.app.persistence.RestoreWriteFence.revision)
                        staged = Triple(directory, sources, skippedArtwork)
                    else directory.deleteRecursively()
                } catch (error: Throwable) { directory.deleteRecursively(); throw error }
            }
            val snapshot = staged ?: error("App data kept changing during backup. Please try again.")
            snapshotDirectory = snapshot.first
            val sources = snapshot.second
            val summary = FileOutputStream(temporary).use { BackupArchive.write(it, sources, System.currentTimeMillis(), snapshot.third) }
            try {
                publish(app, treeUri, temporary, summary.createdAt, automatic)
                return summary
            } finally { snapshot.first.deleteRecursively() }
        } finally {
            temporary.delete()
            snapshotDirectory?.deleteRecursively()
        }
    }

    fun inspect(context: Context, uri: Uri): PreparedBackup {
        val destination = File(context.cacheDir, "backup-restore-${System.nanoTime()}")
        val stream = context.contentResolver.openInputStream(uri) ?: error("Could not open the backup file.")
        val prepared = stream.use { BackupArchive.read(it, destination) }
        try {
            require(prepared.files.keys.filter { it.startsWith("prefs/") }.map { it.removePrefix("prefs/").removeSuffix(".json") }
                .toSet() == PREF_NAMES.toSet()) { "Backup settings are incomplete or unsupported." }
            prepared.files.keys.forEach { name ->
                if (name.startsWith("prefs/")) {
                    require(name.removePrefix("prefs/").removeSuffix(".json") in PREF_NAMES)
                    decodePreferences(requireNotNull(prepared.files[name]).readText())
                }
            }
            return prepared
        } catch (error: Exception) {
            prepared.discard()
            throw error
        }
    }

    private fun transaction(context: Context) = DurableRestoreTransaction(File(context.filesDir, "restore_transaction"))

    @Synchronized
    fun restore(context: Context, prepared: PreparedBackup) {
        val app = context.applicationContext
        val journal = transaction(app)
        val total = prepared.files.values.sumOf(File::length)
        requireSpace(app, total * 3 + 32L * 1024 * 1024)
        // A paused transfer may still be relinquishing ownership; wait before freezing records.
        val quiescent = onOwner { com.miowser.app.downloader.DownloadQueue.isQuiescent() }
        check(quiescent) { "Downloads are still pausing. Try restoring again once they stop." }
        awaitWrites()
        com.miowser.app.persistence.RestoreWriteFence.freeze()
        try {
            journal.prepare(prepared.files.map { (name, file) -> BackupSource(name) { file.inputStream() } },
                prepared.summary.createdAt)
            journal.recover { applyGeneration(app, it) }
        } catch (error: Throwable) {
            if (!journal.needsRecovery) com.miowser.app.persistence.RestoreWriteFence.thawAfterPreflightFailure()
            else throw RestoreNeedsRestartException(error)
            throw error
        } finally { prepared.discard() }
    }

    @Synchronized
    fun recoverInterruptedRestore(context: Context) {
        val directory = File(context.filesDir, "restore_transaction")
        if (!directory.exists()) return
        transaction(context).recover { applyGeneration(context, it) }
    }

    private fun applyGeneration(app: Context, prepared: PreparedBackup) {
        val restoredPrefs = PREF_NAMES.associateWith { name ->
            decodePreferences(requireNotNull(prepared.files["prefs/$name.json"]).readText())
        }
        require(prepared.files.keys.filter { it.startsWith("prefs/") }.size == PREF_NAMES.size)
        prepared.files.filterKeys { it.startsWith("files/") }.forEach { (name, source) ->
            val target = File(app.filesDir, name.removePrefix("files/"))
            require(target.canonicalPath.startsWith(app.filesDir.canonicalPath + File.separator))
            target.parentFile?.mkdirs()
            val atomic = android.util.AtomicFile(target)
            val output = atomic.startWrite()
            try { source.inputStream().use { it.copyTo(output) }; atomic.finishWrite(output) }
            catch (error: Throwable) { atomic.failWrite(output); throw error }
        }
        // Clear only backed-up private assets absent from the new generation, never public media.
        FILE_DIRECTORIES.forEach { directory ->
            File(app.filesDir, directory).listFiles()?.filter { it.isFile &&
                allowedBackupPath("files/$directory/${it.name}") &&
                "files/$directory/${it.name}" !in prepared.files }?.forEach { check(it.delete()) }
        }
        restoredPrefs.forEach { (name, values) ->
            check(commitPreferences(app, name, values)) { "Could not restore $name." }
        }
    }

    private fun requireSpace(context: Context, bytes: Long) {
        check(android.os.StatFs(context.filesDir.absolutePath).availableBytes >= bytes) {
            "Not enough private storage to stage and verify this backup safely."
        }
    }
    private fun awaitWrites() {
        val ready = java.util.concurrent.CountDownLatch(1)
        com.miowser.app.persistence.Persistence.afterWrites { ready.countDown() }
        check(ready.await(20, java.util.concurrent.TimeUnit.SECONDS)) { "App data was still being saved." }
    }
    private fun <T> onOwner(action: () -> T): T {
        val receipt = java.util.concurrent.CompletableFuture<T>()
        com.miowser.app.persistence.Persistence.onOwner {
            try { receipt.complete(action()) } catch (error: Throwable) { receipt.completeExceptionally(error) }
        }
        return receipt.get(20, java.util.concurrent.TimeUnit.SECONDS)
    }

    private fun publish(context: Context, treeUri: Uri, archive: File, createdAt: Long, automatic: Boolean) {
        val resolver = context.contentResolver
        val root = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date(createdAt))
        val prefix = if (automatic) AUTOMATIC_PREFIX else BACKUP_PREFIX
        val document = DocumentsContract.createDocument(resolver, root, BACKUP_MIME, "$prefix$stamp.zip")
            ?: error("Could not create a file in the selected backup folder.")
        try {
            val output = resolver.openOutputStream(document, "w") ?: error("Could not write the backup file.")
            output.use { archive.inputStream().use { input -> input.copyTo(it) } }
            // Some document providers acknowledge writes before persisting them; read the result
            // back and validate its hashes before reporting success or rotating older backups.
            val verificationDir = File(context.cacheDir, "backup-check-${System.nanoTime()}")
            val stored = resolver.openInputStream(document) ?: error("Could not verify the saved backup.")
            stored.use { BackupArchive.read(it, verificationDir).discard() }
        } catch (error: Exception) {
            runCatching { DocumentsContract.deleteDocument(resolver, document) }
            throw error
        }
        if (automatic) pruneOldBackups(context, treeUri)
    }

    private fun pruneOldBackups(context: Context, treeUri: Uri) {
        runCatching {
            val resolver = context.contentResolver
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri,
                DocumentsContract.getTreeDocumentId(treeUri))
            val records = mutableListOf<Pair<String, Uri>>()
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(1)
                    if (name.startsWith(AUTOMATIC_PREFIX) && name.endsWith(".zip")) {
                        records += name to DocumentsContract.buildDocumentUriUsingTree(treeUri, cursor.getString(0))
                    }
                }
            }
            records.sortedByDescending { it.first }.drop(MAX_AUTOMATIC_BACKUPS).forEach { (_, uri) ->
                DocumentsContract.deleteDocument(resolver, uri)
            }
        }
    }
}

private fun snapshotPreferences(context: Context, name: String): Map<String, Any> =
    context.getSharedPreferences(name, Context.MODE_PRIVATE).all.filterValues { it != null }
        .mapValues { (_, value) -> if (value is Set<*>) value.toSet() else requireNotNull(value) }

private fun backupPreferenceValues(name: String, values: Map<String, Any>): Map<String, Any> {
    if (name != "downloads") return values
    val raw = values["downloads"] as? String ?: return values
    val complete = DownloadCodec.decode(raw).filter { it.status == DownloadStatus.COMPLETED }
    return values + ("downloads" to DownloadCodec.encode(complete))
}

private fun encodePreferences(values: Map<String, Any>): String = JSONObject().apply {
    values.toSortedMap().forEach { (key, value) ->
        val item = JSONObject()
        when (value) {
            is String -> { item.put("type", "string"); item.put("value", value) }
            is Boolean -> { item.put("type", "boolean"); item.put("value", value) }
            is Int -> { item.put("type", "int"); item.put("value", value) }
            is Long -> { item.put("type", "long"); item.put("value", value) }
            is Float -> { item.put("type", "float"); item.put("value", value.toDouble()) }
            is Set<*> -> {
                require(value.all { it is String })
                item.put("type", "strings")
                item.put("value", JSONArray(value.sortedBy { it.toString() }))
            }
            else -> error("Unsupported setting type: $key")
        }
        put(key, item)
    }
}.toString()

private fun decodePreferences(raw: String): Map<String, Any> {
    val json = JSONObject(raw)
    require(json.length() <= 5_000) { "Too many saved settings." }
    return json.keys().asSequence().associateWith { key ->
        val entry = json.getJSONObject(key)
        when (entry.getString("type")) {
            "string" -> entry.getString("value")
            "boolean" -> entry.getBoolean("value")
            "int" -> entry.getInt("value")
            "long" -> entry.getLong("value")
            "float" -> entry.getDouble("value").toFloat()
            "strings" -> entry.getJSONArray("value").let { array ->
                require(array.length() <= 5_000)
                (0 until array.length()).map(array::getString).toSet()
            }
            else -> error("Backup contains an unsupported setting type.")
        }
    }
}

private fun commitPreferences(context: Context, name: String, values: Map<String, Any>): Boolean {
    val editor = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
    values.forEach { (key, value) ->
        when (value) {
            is String -> editor.putString(key, value)
            is Boolean -> editor.putBoolean(key, value)
            is Int -> editor.putInt(key, value)
            is Long -> editor.putLong(key, value)
            is Float -> editor.putFloat(key, value)
            is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    return editor.commit()
}

internal class RestoreNeedsRestartException(cause: Throwable) : Exception("Restore was interrupted. Close Miowser and reopen it to finish recovery safely.", cause)
