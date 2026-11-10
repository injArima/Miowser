package com.miowser.app.browser

import android.content.Context
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import com.miowser.app.library.LibraryBrowseQuery
import com.miowser.app.library.LibraryBrowseRevision
import com.miowser.app.library.LibraryFolderCount
import com.miowser.app.library.LibraryWindow
import com.miowser.app.library.device.DEVICE_PAGE_SIZE
import com.miowser.app.library.device.DeviceBrowseQuery
import com.miowser.app.library.device.DeviceCatalogWindow
import com.miowser.app.library.device.DeviceVideo
import com.miowser.app.library.device.DeviceVideoFolder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext

/** Bounded browse data shared by the Hub tabs. Artwork stays in the thumbnail cache. */
internal class HubLibraryCache {
    val network = LruCache<LibraryBrowseQuery, NetworkPage>(4)
    val deviceVideos = LruCache<DeviceBrowseQuery, DevicePage<DeviceVideo>>(4)
    val deviceFolders = LruCache<DeviceBrowseQuery, DevicePage<DeviceVideoFolder>>(4)
    val folderCovers = LruCache<FolderCoverKey, List<DeviceVideo>>(24)
    var folderCounts: FolderCounts? = null

    data class NetworkPage(val revision: LibraryBrowseRevision, val window: LibraryWindow)
    data class DevicePage<T>(val revision: Long, val requestedPage: Int, val window: DeviceCatalogWindow<T>)
    data class FolderCoverKey(val source: String, val path: String, val query: DeviceBrowseQuery, val revision: Long)
    data class FolderCounts(val revision: Long, val counts: List<LibraryFolderCount>)
}

@Composable
internal fun <T> rememberDeviceCatalogWindow(
    query: DeviceBrowseQuery,
    revision: Long,
    grid: LazyGridState,
    cache: LruCache<DeviceBrowseQuery, HubLibraryCache.DevicePage<T>>,
    read: (Context, DeviceBrowseQuery, Int, Int?) -> DeviceCatalogWindow<T>
): DeviceCatalogWindow<T>? {
    val app = LocalContext.current.applicationContext
    var loaded by remember(query, grid) {
        mutableStateOf(cache.get(query)?.takeIf { it.revision == revision })
    }
    LaunchedEffect(query, revision, grid) {
        snapshotFlow { grid.firstVisibleItemIndex / DEVICE_PAGE_SIZE }.collectLatest { page ->
            val previous = loaded?.takeIf { it.revision == revision }
            if (previous?.requestedPage == page) return@collectLatest
            try {
                val window = withContext(Dispatchers.IO) {
                    read(app, query, page * DEVICE_PAGE_SIZE, previous?.window?.count)
                }
                val result = HubLibraryCache.DevicePage(revision, page, window)
                cache.put(query, result)
                loaded = result
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                Log.w("HubLibrary", "Could not read the device library page", failure)
                if (loaded == null) loaded = HubLibraryCache.DevicePage(revision, page, DeviceCatalogWindow(0, emptyList(), 0))
            }
        }
    }
    return loaded?.window
}
