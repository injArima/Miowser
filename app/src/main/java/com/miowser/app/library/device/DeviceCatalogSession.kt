package com.miowser.app.library.device

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.miowser.app.downloader.DownloadQueue
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.isImportableVideo
import com.miowser.app.settings.AppSettings
import com.miowser.app.startup.AppStartup
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/** Owns catalog jobs for the app process. Screens only read the published catalog. */
internal object DeviceCatalogSession {
    private data class Request(val refresh: DeviceCatalogRefresh, val target: DeviceCatalogScope)

    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val pending = mutableSetOf<Request>()
    private var started = false
    private var activeScan: Job? = null
    private var access = DeviceMediaAccess.NONE
    private var permissionEpoch = 0
    var status by mutableStateOf(DeviceCatalogStatus())
        private set
    var loadFailed by mutableStateOf(false)
        private set

    fun start(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        access = deviceMediaAccess(app)
        val imported = MediaLibrary.entries.asSequence().filter { it.sourceKind == LibrarySourceKind.LOCAL_FILE }
            .map { it.mediaUri }.filter { "/tree/" in it }.distinct().toList()

        AppStartup.scope.launch {
            for (request in requests) {
                pending.remove(request)
                if (!AppSettings.current.deviceDiscoveryEnabled) continue
                activeScan = launch { runRequest(app, request, imported) }
                activeScan?.join()
                activeScan = null
            }
        }
        var previousDownloads = downloads()
        AppStartup.scope.launch {
            snapshotFlow { downloads() }.collect { current ->
                if (current != previousDownloads) {
                    previousDownloads = current
                    enqueue(DeviceCatalogRefresh.CHECK, DeviceCatalogScope.Source(DOWNLOAD_SOURCE))
                }
            }
        }
        AppStartup.scope.launch {
            DeviceVideoCatalog.mountChanges(app).conflate().collect {
                enqueue(DeviceCatalogRefresh.CHECK)
            }
        }
        enqueue(
            if (AppSettings.current.deviceAutoRescanEnabled) DeviceCatalogRefresh.CHECK
            else DeviceCatalogRefresh.MISSING
        )
    }

    fun setDiscoveryEnabled(enabled: Boolean) {
        if (enabled) enqueue(DeviceCatalogRefresh.CHECK)
        else {
            activeScan?.cancel()
            status = status.copy(scanning = false)
        }
    }

    fun refresh(source: String? = null) =
        enqueue(DeviceCatalogRefresh.FORCE, source?.let { DeviceCatalogScope.Source(it) } ?: DeviceCatalogScope.All)

    fun folderRemoved() = enqueue(DeviceCatalogRefresh.INDEX)

    fun checkPermissions(context: Context, selectionMayHaveChanged: Boolean = false) {
        if (!started) return
        val current = deviceMediaAccess(context)
        if (current == access && !(selectionMayHaveChanged && current == DeviceMediaAccess.SELECTED)) return
        access = current
        permissionEpoch++
        status = status.copy(access = current, visible = status.visible.filterNot { it.startsWith("media:") }.toSet())
        enqueue(DeviceCatalogRefresh.FORCE, DeviceCatalogScope.Media)
    }

    private fun enqueue(refresh: DeviceCatalogRefresh, target: DeviceCatalogScope = DeviceCatalogScope.All) {
        if (!started || !AppSettings.current.deviceDiscoveryEnabled) return
        val request = Request(refresh, target)
        if (pending.add(request)) {
            if (refresh != DeviceCatalogRefresh.INDEX) status = status.copy(scanning = true)
            requests.trySend(request)
        }
    }

    private suspend fun runRequest(context: Context, request: Request, imported: List<String>) {
        loadFailed = false
        if (request.refresh != DeviceCatalogRefresh.INDEX) status = status.copy(scanning = true)
        val epoch = permissionEpoch
        try {
            DeviceVideoCatalog.scan(context, imported, downloads(), request.refresh, request.target, status).collect { next ->
                status = if (epoch == permissionEpoch) next else next.copy(
                    access = access, visible = next.visible.filterNot { it.startsWith("media:") }.toSet()
                )
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            Log.w("DeviceCatalogSession", "Could not read the device catalog", failure)
            loadFailed = true
        } finally {
            if (status.scanning) status = status.copy(scanning = false)
        }
    }

    private fun downloads(): List<DeviceDownload> = DownloadQueue.downloads.mapNotNull { download ->
        if (download.status != DownloadStatus.COMPLETED || !isImportableVideo(download.displayName, "")) null
        else download.savedUri?.let { DeviceDownload(it, download.displayName) }
    }.distinctBy { it.uri }
}
