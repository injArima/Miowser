package com.miowser.app.browser

import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.miowser.app.library.LIBRARY_PAGE_SIZE
import com.miowser.app.library.LibraryBrowseQuery
import com.miowser.app.library.LibraryBrowseRevision
import com.miowser.app.library.LibraryCardId
import com.miowser.app.library.LibraryFolder
import com.miowser.app.library.LibraryFolderCount
import com.miowser.app.library.LibraryRepository
import com.miowser.app.library.LibraryWindow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

internal class HubLibraryPages {
    var window by mutableStateOf<LibraryWindow?>(null)
    var failed by mutableStateOf(false)
    var retry by mutableIntStateOf(0)
    var revision: LibraryBrowseRevision? = null
}

@Composable
internal fun rememberHubLibraryPages(
    query: LibraryBrowseQuery,
    grid: LazyGridState,
    folderCards: Int
): HubLibraryPages {
    val app = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val pages = remember(query, grid) { HubLibraryPages() }
    var savedIdentity by rememberSaveable { mutableStateOf<String?>(null) }
    var savedOccurrence by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(pages, grid, folderCards) {
        snapshotFlow {
            val index = grid.firstVisibleItemIndex - folderCards
            index to pages.window?.get(index)
        }.collect { (index, card) ->
            if (card != null || index < 0) {
                savedIdentity = card?.identityKey
                savedOccurrence = card?.occurrence ?: 0
            }
        }
    }
    LaunchedEffect(pages, lifecycle, folderCards, pages.retry) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            combine(
                LibraryRepository.browseRevisions.map { it.forSort(query.sort) }.distinctUntilChanged(),
                snapshotFlow { (grid.firstVisibleItemIndex - folderCards).coerceAtLeast(0) / LIBRARY_PAGE_SIZE }
            ) { revision, page -> revision to page }.collectLatest { (revision, page) ->
                val previous = pages.window
                val changed = pages.revision != revision
                val start = ((page - 1) * LIBRARY_PAGE_SIZE).coerceAtLeast(0)
                val end = ((page + 2) * LIBRARY_PAGE_SIZE).coerceAtMost(previous?.count ?: Int.MAX_VALUE)
                if (!changed && !pages.failed && previous != null &&
                    previous.start == start && previous.start + previous.ids.size == end) return@collectLatest

                val index = grid.firstVisibleItemIndex
                val offset = grid.firstVisibleItemScrollOffset
                val anchor = if (changed && index >= folderCards && !grid.isScrollInProgress) {
                    if (previous == null) savedIdentity?.let { LibraryCardId(it, savedOccurrence) }
                    else previous[index - folderCards]
                } else null
                try {
                    val next = withContext(Dispatchers.IO) {
                        LibraryRepository.readWindow(
                            app, query, page * LIBRARY_PAGE_SIZE,
                            previous?.count?.takeUnless { changed }, anchor, revision
                        )
                    } ?: return@collectLatest
                    if (LibraryRepository.browseRevisions.value.forSort(query.sort) != revision) return@collectLatest
                    pages.revision = revision
                    pages.failed = false
                    pages.window = next
                    val anchorIndex = next.anchorIndex?.plus(folderCards)
                    if (anchorIndex != null && anchorIndex != index && !grid.isScrollInProgress &&
                        grid.firstVisibleItemIndex == index && grid.firstVisibleItemScrollOffset == offset) {
                        grid.requestScrollToItem(anchorIndex, offset)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("HubLibrary", "Could not read Library page", failure)
                    pages.failed = true
                }
            }
        }
    }
    return pages
}

@Composable
internal fun rememberLibraryFolderCounts(active: Boolean): List<LibraryFolderCount> {
    val app = LocalContext.current.applicationContext
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val counts by produceState(emptyList<LibraryFolderCount>(), active, lifecycle, app) {
        if (!active) return@produceState
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            LibraryRepository.browseRevisions.map { it.layout }.distinctUntilChanged().collectLatest {
                try {
                    value = withContext(Dispatchers.IO) { LibraryRepository.readFolderCounts(app) }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (failure: Exception) {
                    android.util.Log.w("HubLibrary", "Could not read Library folder counts", failure)
                }
            }
        }
    }
    return counts
}

internal fun countLibraryBranches(
    directCounts: List<LibraryFolderCount>, folders: List<LibraryFolder>
): Map<String?, Int> {
    val parents = folders.associate { it.id to it.parentId }
    val counts = HashMap<String?, Int>()
    directCounts.forEach { (id, count) ->
        if (id == null) {
            counts[null] = count
        } else {
            val visited = HashSet<String>()
            var folder: String? = id
            while (folder != null && visited.add(folder)) {
                counts[folder] = (counts[folder] ?: 0) + count
                folder = parents[folder]
            }
        }
    }
    return counts
}
