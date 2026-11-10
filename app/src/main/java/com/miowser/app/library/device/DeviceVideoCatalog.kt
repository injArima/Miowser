package com.miowser.app.library.device

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import androidx.core.content.ContextCompat
import androidx.room.Room
import com.miowser.app.library.walkImportedVideoFolder
import com.miowser.app.persistence.RestoreWriteFence
import java.util.UUID
import java.util.concurrent.Callable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal data class LinkedDeviceFolder(val source: DeviceSource, val hasReadAccess: Boolean)

internal enum class DeviceSourceIssue { OFFLINE, NEEDS_ACCESS, SCAN_FAILED }

internal data class DeviceCatalogStatus(
    val access: DeviceMediaAccess = DeviceMediaAccess.NONE,
    val sources: List<DeviceSource> = emptyList(),
    val visible: Set<String> = emptySet(),
    val issues: Map<String, DeviceSourceIssue> = emptyMap(),
    val revision: Long = 0,
    val scanning: Boolean = true,
    val checkingSource: String? = null
)

internal data class DeviceCatalogWindow<T>(val start: Int, val rows: List<T>, val count: Int) {
    operator fun get(index: Int): T? = rows.getOrNull(index - start)
}

internal typealias DeviceVideoWindow = DeviceCatalogWindow<DeviceVideo>

internal data class DevicePlaybackSelection(val query: DeviceBrowseQuery, val window: DeviceVideoWindow, val index: Int)

internal object DeviceVideoCatalog {
    private var opened: DeviceCatalogDatabase? = null
    private val scanLock = Mutex()
    private var revision = 0L

    @Synchronized
    private fun database(context: Context): DeviceCatalogDatabase =
        opened ?: Room.databaseBuilder(context.applicationContext, DeviceCatalogDatabase::class.java, "device-videos.db")
            .build().also { db ->
                db.catalog().discardInterrupted()
                opened = db
            }

    suspend fun linkedSources(context: Context): List<LinkedDeviceFolder> = withContext(Dispatchers.IO) {
        val grants = readTreeGrants(context)
        database(context).catalog().sources()
            .filter { it.kind == DEVICE_LINK && it.linked }
            .map { LinkedDeviceFolder(it, it.id in grants) }
    }

    suspend fun link(context: Context, tree: Uri, replaceId: String? = null): String = withContext(Dispatchers.IO) {
        val root = requireNotNull(linkedTree(tree))
        context.contentResolver.takePersistableUriPermission(tree, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val rootDocument = DocumentsContract.buildDocumentUriUsingTree(root, DocumentsContract.getTreeDocumentId(root))
        val label = context.contentResolver.query(rootDocument, arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE
        ), null, null, null)?.use {
            if (it.moveToFirst() && it.getString(1) == DocumentsContract.Document.MIME_TYPE_DIR) it.getString(0) else null
        }
        checkNotNull(label) { "Could not open the selected folder" }
        scanLock.withLock {
            val db = database(context)
            val dao = db.catalog()
            val sources = dao.sources()
            val id = root.toString()
            val existing = sources.firstOrNull { it.id == id }
            val replaced = sources.firstOrNull { it.id == replaceId && it.id != id && it.kind == DEVICE_LINK }
            val updated = (existing ?: newLink(root)).copy(linked = true, label = label)
            check(RestoreWriteFence.write {
                db.runInTransaction {
                    dao.saveSource(updated)
                    if (replaced != null) deactivate(dao, replaced)
                }
                true
            } == true)
            id
        }
    }

    suspend fun unlink(context: Context, id: String) = withContext(Dispatchers.IO) {
        scanLock.withLock {
            val db = database(context)
            val dao = db.catalog()
            val source = dao.sources().firstOrNull { it.id == id && it.kind == DEVICE_LINK }
                ?: return@withLock
            check(RestoreWriteFence.write { db.runInTransaction { deactivate(dao, source) }; true } == true)
        }
    }

    private fun deactivate(dao: DeviceCatalogDao, source: DeviceSource) {
        dao.saveSource(source.copy(linked = false, generation = null, stamp = null))
        source.generation?.let { dao.discard(source.id, it) }
    }

    fun readWindow(context: Context, query: DeviceBrowseQuery, index: Int, knownCount: Int?): DeviceVideoWindow {
        return readCatalogWindow(context, index, knownCount, { it.count(query.count()) }) { dao, start, size ->
            dao.rows(query.page(start, size))
        }
    }

    fun readFolders(context: Context, query: DeviceBrowseQuery, index: Int, knownCount: Int?): DeviceCatalogWindow<DeviceVideoFolder> =
        readCatalogWindow(context, index, knownCount, { it.count(query.folderCount()) }) { dao, start, size ->
            dao.folders(query.folderPage(start, size))
        }

    fun readFolderCover(context: Context, folder: DeviceVideoFolder): List<DeviceVideo> {
        val query = DeviceBrowseQuery(setOf(folder.sourceId), folder.sourceId, "", false, folder.folder)
        return database(context).catalog().rows(query.page(0, 4))
    }

    private fun <T> readCatalogWindow(
        context: Context, index: Int, knownCount: Int?, readCount: (DeviceCatalogDao) -> Int,
        readRows: (DeviceCatalogDao, Int, Int) -> List<T>
    ): DeviceCatalogWindow<T> {
        val db = database(context)
        return db.runInTransaction(Callable {
            val dao = db.catalog()
            val count = knownCount ?: readCount(dao)
            val page = index.coerceIn(0, (count - 1).coerceAtLeast(0)) / DEVICE_PAGE_SIZE
            val start = ((page - 1) * DEVICE_PAGE_SIZE).coerceAtLeast(0)
            val end = ((page + 2) * DEVICE_PAGE_SIZE).coerceAtMost(count)
            DeviceCatalogWindow(start, if (end > start) readRows(dao, start, end - start) else emptyList(), count)
        })
    }

    fun scan(
        context: Context, imported: List<String>, downloads: List<DeviceDownload>, force: Boolean,
        preferredSource: String? = null, previous: DeviceCatalogStatus? = null
    ): Flow<DeviceCatalogStatus> = flow {
        scanLock.withLock {
            val app = context.applicationContext
            val db = database(app)
            val dao = db.catalog()
            val access = deviceMediaAccess(app)
            val grants = readTreeGrants(app)
            val stored = dao.sources().associateBy { it.id }.toMutableMap()
            // A saved Library entry can prove that an existing tree grant came from an import.
            // Other grants, such as a backup destination, do not become sources automatically.
            imported.asSequence().mapNotNull { linkedTree(Uri.parse(it)) }.distinct()
                .filter { it.toString() in grants && it.toString() !in stored }.forEach { tree ->
                    val source = newLink(tree)
                    RestoreWriteFence.write { dao.saveSource(source) }
                    stored[source.id] = source
                }
            val mounted = if (access == DeviceMediaAccess.NONE) emptySet() else mountedVideoVolumes(app)
            val media = mounted.map { volume ->
                val id = "media:$volume"
                stored[id] ?: DeviceSource(id, DEVICE_MEDIA,
                    if (volume == "external_primary" || volume == "external") "Device storage" else "Storage $volume")
            }
            val links = stored.values.filter { it.kind == DEVICE_LINK && it.linked }.sortedBy { it.label }
            val downloadSource = stored[DOWNLOAD_SOURCE] ?: DeviceSource(DOWNLOAD_SOURCE, DEVICE_DOWNLOADS, "Downloads")
            val offline = if (access == DeviceMediaAccess.NONE) emptyList() else stored.values.filter {
                it.kind == DEVICE_MEDIA && it.id.removePrefix("media:") !in mounted
            }
            val sources = (listOfNotNull(downloadSource.takeIf { downloads.isNotEmpty() }) + media + offline + links)
                .associateBy { it.id }.toMutableMap()
            val visible = linkedSetOf<String>()
            val issues = offline.associate { it.id to DeviceSourceIssue.OFFLINE }.toMutableMap()
            links.filter { it.id !in grants }.forEach { issues[it.id] = DeviceSourceIssue.NEEDS_ACCESS }
            val downloadStamp = downloads.joinToString("\n") { "${it.uri}\t${it.title}" }.let {
                java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
            }
            if (downloads.isNotEmpty() && downloadSource.generation != null && downloadSource.stamp == downloadStamp) {
                visible += DOWNLOAD_SOURCE
            }
            val stamps = media.associate { source ->
                source.id to runCatching { videoVolumeStamp(app, source.id.removePrefix("media:")) }
            }
            media.filter { source ->
                val stamp = stamps.getValue(source.id)
                access == DeviceMediaAccess.FULL && source.generation != null && stamp.isSuccess &&
                    (stamp.getOrNull() == null || source.stamp?.substringBeforeLast(':') == stamp.getOrNull()?.version)
            }.forEach { visible += it.id }
            links.filter { it.id in grants && it.generation != null }.forEach { visible += it.id }
            val targeted = previous?.takeIf { force && preferredSource != null && it.access == access }
            targeted?.issues?.forEach { (id, issue) ->
                if (id != preferredSource && id in sources && issue == DeviceSourceIssue.SCAN_FAILED && id !in issues) {
                    issues[id] = issue
                    visible -= id
                }
            }
            suspend fun report(scanning: Boolean, checkingSource: String? = null) = emit(DeviceCatalogStatus(
                access, sources.values.toList(), visible.toSet(), issues.toMap(), revision, scanning, checkingSource
            ))
            report(true)
            for (source in sources.values.sortedBy { it.id != preferredSource }) {
                if (source.id in issues || RestoreWriteFence.frozen) continue
                try {
                    val volume = source.id.removePrefix("media:")
                    val stamp = stamps[source.id]?.getOrThrow()
                    val key = when (source.kind) {
                        DEVICE_MEDIA -> if (access == DeviceMediaAccess.FULL) stamp?.key else null
                        DEVICE_DOWNLOADS -> downloadStamp
                        else -> null
                    }
                    val forceSource = force && (preferredSource == null || preferredSource == source.id)
                    val alreadyVisible = source.id in visible && targeted?.visible?.contains(source.id) == true
                    if (source.id != preferredSource && alreadyVisible && source.kind != DEVICE_MEDIA) continue
                    if (!forceSource && key != null && source.generation != null && source.stamp == key &&
                        source.kind == DEVICE_MEDIA) continue
                    report(true, source.label)
                    val published = publish(db, source, key) { generation, signal, add ->
                        when (source.kind) {
                            DEVICE_MEDIA -> {
                                scanMediaVolume(app, volume, source.id, generation, stamp, signal, add)
                                check(deviceMediaAccess(app) == access && volume in mountedVideoVolumes(app) &&
                                    videoVolumeStamp(app, volume) == stamp) { "Video access changed during discovery" }
                                source.label
                            }
                            DEVICE_LINK -> {
                                val name = walkImportedVideoFolder(app, Uri.parse(source.id), signal = signal) { file ->
                                    val canonical = canonicalVideoUri(app, file.uri)
                                    add(DeviceVideo(source.id, generation, file.uri.toString(), canonical, DEVICE_LINK,
                                        file.name, file.relativeFolder.joinToString("/"), 0, null, file.modifiedAt,
                                        "${file.modifiedAt}:${file.size}"))
                                }
                                check(source.id in readTreeGrants(app)) { "Linked folder permission changed" }
                                name
                            }
                            else -> {
                                downloads.forEach { download ->
                                    signal.throwIfCanceled()
                                    runCatching { readDeviceDownload(app, download, generation) }.getOrNull()?.let(add)
                                }
                                source.label
                            }
                        }
                    }
                    sources[source.id] = published
                    visible += source.id
                    revision++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    visible -= source.id
                    issues[source.id] = if (source.kind == DEVICE_LINK && source.id !in readTreeGrants(app))
                        DeviceSourceIssue.NEEDS_ACCESS else DeviceSourceIssue.SCAN_FAILED
                    android.util.Log.w("DeviceVideoCatalog", "Could not refresh video source", failure)
                }
                report(true)
            }
            report(false)
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun publish(
        db: DeviceCatalogDatabase, source: DeviceSource, stamp: String?,
        read: (String, CancellationSignal, (DeviceVideo) -> Unit) -> String
    ): DeviceSource {
        val dao = db.catalog()
        val generation = UUID.randomUUID().toString()
        val batch = ArrayList<DeviceVideo>(192)
        fun flush() {
            if (batch.isEmpty()) return
            check(RestoreWriteFence.write { dao.stage(batch); true } == true)
            batch.clear()
        }
        try {
            val name = cancellableScan { signal ->
                read(generation, signal) {
                    batch.add(it)
                    if (batch.size == 192) flush()
                }
            }
            currentCoroutineContext().ensureActive()
            flush()
            val updated = source.copy(label = name, generation = generation, stamp = stamp)
            check(RestoreWriteFence.write {
                db.runInTransaction {
                    dao.saveSource(updated)
                    dao.prune(source.id, generation)
                }
                true
            } == true)
            return updated
        } catch (failure: Throwable) {
            runCatching { dao.discard(source.id, generation) }
            throw failure
        }
    }

    private suspend fun <T> cancellableScan(read: (CancellationSignal) -> T): T =
        suspendCancellableCoroutine { continuation ->
            val signal = CancellationSignal()
            continuation.invokeOnCancellation { signal.cancel() }
            try { continuation.resume(read(signal)) }
            catch (failure: Throwable) { continuation.resumeWithException(failure) }
        }

    private fun newLink(tree: Uri): DeviceSource {
        val title = DocumentsContract.getTreeDocumentId(tree).substringAfterLast('/').substringAfterLast(':')
        return DeviceSource(tree.toString(), DEVICE_LINK, title.ifBlank { "Linked folder" })
    }

    private fun readTreeGrants(context: Context): Set<String> = context.contentResolver.persistedUriPermissions
        .filter { it.isReadPermission }.mapNotNull { linkedTree(it.uri)?.toString() }.toSet()

    fun changes(context: Context): Flow<Unit> = callbackFlow {
        val resolver = context.contentResolver
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { trySend(Unit) }
        }
        val authorities = resolver.persistedUriPermissions.asSequence().filter { it.isReadPermission }
            .mapNotNull { linkedTree(it.uri)?.authority }.toSet() + "media"
        authorities.forEach { authority ->
            runCatching { resolver.registerContentObserver(Uri.Builder().scheme("content").authority(authority).build(), true, observer) }
        }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) { trySend(Unit) }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addDataScheme("file")
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
        awaitClose {
            resolver.unregisterContentObserver(observer)
            context.unregisterReceiver(receiver)
        }
    }
}
