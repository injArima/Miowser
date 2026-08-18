package com.miowser.app.browser

import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.viewinterop.AndroidView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miowser.app.adblock.AdBlock
import com.miowser.app.bookmarks.BookmarkList
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadKind
import com.miowser.app.downloader.DownloadQueue
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import com.miowser.app.history.HistoryLog
import com.miowser.app.player.PlaybackRequest
import com.miowser.app.player.PlayerScreen
import com.miowser.app.settings.AppSettings
import com.miowser.app.ui.BookmarksPage
import com.miowser.app.ui.BottomBar
import com.miowser.app.ui.HistoryPage
import com.miowser.app.ui.OverflowMenuSheet
import com.miowser.app.ui.Screen
import com.miowser.app.ui.SettingsPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Anything that isn't safe/sensible in a filename, replaced with "_"; matches the common
// filesystem-reserved set (Windows is the strictest, so satisfying it covers everything else).
private val UNSAFE_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")

private fun sanitizeFileName(raw: String): String {
    val cleaned = raw.trim().replace(UNSAFE_FILENAME_CHARS, "_").take(80)
    return cleaned.ifBlank { "miowser_download" }
}

/** Strips a trailing ".ext" (2-4 alphanumeric chars) so callers can safely append their own without duplicating it. */
private fun stripKnownExtension(name: String): String {
    val ext = name.substringAfterLast('.', "")
    return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) name.substringBeforeLast('.') else name
}

/**
 * Browser shell: an [AddressBar] driving whichever tab is active, over a Box that keeps every
 * open tab's WebView alive simultaneously (see the tab loop below) and just toggles visibility —
 * switching tabs never destroys one. Tab switching itself lives entirely in [TabsPage] now
 * (opened from BottomBar's Tabs button); there's no always-visible top tab strip anymore.
 */
@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    // The overflow (hamburger) menu is the only thing left that's a transient sheet rather than
    // a full page — it's a picker, not a destination itself. Everything it (or AddressBar's
    // badge, or BottomBar's own buttons) launches is a Screen, rendered as an opaque overlay
    // below rather than replacing the Scaffold outright (see the comment at that render site).
    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeScreen by remember { mutableStateOf<Screen?>(null) }
    var playbackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    // One shared bubble for lightweight confirmations (copy/download-started/bookmarked) —
    // rendered at the very end of the Box below so it's visible above whichever layer (browser,
    // a full page, the player) happens to be showing, rather than being tied to any one of their
    // own Scaffolds.
    val snackbarHostState = remember { SnackbarHostState() }

    val tabs = remember { mutableStateListOf<Tab>() }
    var activeTabId by remember { mutableStateOf<Long?>(null) }

    // Creates a tab and inserts it into the list, then returns it. Page-triggered tabs
    // (openerTabId != null) are inserted immediately after their opener, matching how real
    // browsers place a target="_blank"/window.open() tab next to the page that opened it;
    // manually-opened tabs (the "+" button, openerTabId == null) are appended at the end.
    // A local function rather than a lambda so it can call itself for grandchild tabs
    // (a page opening a tab whose page opens another tab) without a lateinit dance.
    fun createAndInsertTab(initialUrl: String, openerTabId: Long?): Tab {
        val tab = Tab(
            id = Tab.nextId(),
            context = context,
            initialUrl = initialUrl,
            openerTabId = openerTabId,
            onRequestChildTab = { childOpenerId -> createAndInsertTab("", childOpenerId) }
        )
        if (openerTabId != null) {
            val openerIndex = tabs.indexOfFirst { it.id == openerTabId }
            tabs.add(if (openerIndex == -1) tabs.size else openerIndex + 1, tab)
        } else {
            tabs.add(tab)
        }
        return tab
    }

    // Used by TabsPage's close button — the only place a tab can be closed now that there's no
    // top strip.
    fun closeTab(id: Long) {
        val index = tabs.indexOfFirst { it.id == id }
        if (index != -1) {
            val closed = tabs.removeAt(index)
            closed.webView.destroy()
            if (tabs.isEmpty()) {
                activeTabId = createAndInsertTab(AppSettings.current.homePageUrl, null).id
            } else if (activeTabId == id) {
                // Prefer returning to the tab that opened this one (a real page-triggered
                // child); manually-opened tabs have no opener, so fall back to whichever tab
                // is now immediately before this one's old position — "the previous tab in order".
                val opener = closed.openerTabId?.let { openerId -> tabs.firstOrNull { it.id == openerId } }
                activeTabId = opener?.id ?: tabs[(index - 1).coerceIn(0, tabs.size - 1)].id
            }
        }
    }

    // All one-time startup loads share a single LaunchedEffect(Unit) rather than several: the
    // tab-restore step below needs AppSettings already loaded (for restoreTabsOnStartup and
    // homePageUrl), and separate LaunchedEffect(Unit) blocks don't have a guaranteed run order
    // relative to each other — only within one coroutine does "load settings, then decide what
    // to do with tabs" actually happen in that order.
    //
    // Restores the last session if there is one and restoreTabsOnStartup allows it; otherwise
    // opens a single starter tab at the configured home page. Restored tabs are appended in their
    // saved order (not via the opener-adjacent insert above) so the saved strip order is
    // reproduced exactly rather than re-derived from opener relationships.
    LaunchedEffect(Unit) {
        AdBlock.engine.loadEasyListInBackground(context)
        DownloadQueue.restore(context)
        AppSettings.load(context)
        HistoryLog.restore(context)
        BookmarkList.restore(context)

        if (tabs.isNotEmpty()) return@LaunchedEffect
        val saved = if (AppSettings.current.restoreTabsOnStartup) TabSessionStore.load(context) else null
        if (saved == null) {
            activeTabId = createAndInsertTab(AppSettings.current.homePageUrl, null).id
            return@LaunchedEffect
        }
        val (savedTabs, activeIndex) = saved
        val restoredIds = mutableListOf<Long>()
        for (savedTab in savedTabs) {
            val openerId = savedTab.openerIndex.takeIf { it in restoredIds.indices }?.let { restoredIds[it] }
            val tab = Tab(
                id = Tab.nextId(),
                context = context,
                initialUrl = savedTab.url,
                openerTabId = openerId,
                onRequestChildTab = { childOpenerId -> createAndInsertTab("", childOpenerId) }
            )
            tabs.add(tab)
            restoredIds.add(tab.id)
        }
        activeTabId = tabs.getOrNull(activeIndex)?.id ?: tabs.firstOrNull()?.id
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }

    // Builds a real Download from a detected item — cookies/UA/referer forwarded the same way
    // MediaProbe already does for its verification probes, so a download authenticates exactly
    // like the page load that found it did.
    fun downloadMedia(item: DetectedMedia) {
        val tab = activeTab ?: return
        val name = item.pageTitle?.trim()?.takeIf { it.isNotEmpty() && it != "New Tab" }
            ?: item.url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank { "video" }
        val download = Download(
            id = Download.nextId(),
            // outputBaseName below has any existing extension stripped — DirectFileDownloader
            // always appends its own detected extension, and WebView falls back to using the raw
            // URL as a page's title when it has no real HTML <title> (true for a direct-file
            // navigation), so `name` here is very often already a filename ending in ".mp4" —
            // without stripping, the saved file ends up literally named "video.mp4.mp4". Caught
            // on-device: a real download landed with exactly that duplicated extension.
            sourceUrl = item.url,
            pageUrl = item.pageUrl,
            userAgent = tab.webView.settings.userAgentString,
            referer = item.pageUrl,
            cookies = CookieManager.getInstance().getCookie(item.url),
            kind = when (item.type) {
                MediaType.HLS -> DownloadKind.HLS
                MediaType.DASH -> DownloadKind.DASH
                else -> DownloadKind.DIRECT_FILE
            },
            displayName = name,
            outputBaseName = sanitizeFileName(stripKnownExtension(name))
        )
        DownloadQueue.enqueue(context, download)
        activeScreen = Screen.DOWNLOADS
        coroutineScope.launch { snackbarHostState.showSnackbar("Download started") }
    }

    // Same cookies/UA/referer forwarding as downloadMedia above — playback authenticates the
    // same way a real page load (or a download) would have. Deliberately doesn't touch
    // activeScreen: PlayerScreen renders as its own opaque overlay on top of whatever's showing
    // (same pattern as everything else layered after the Scaffold below), so closing playback
    // naturally lands back on the Detected Media page the user was just on, not the raw browser.
    fun playMedia(item: DetectedMedia) {
        val tab = activeTab ?: return
        playbackRequest = PlaybackRequest(
            url = item.url,
            pageUrl = item.pageUrl,
            userAgent = tab.webView.settings.userAgentString,
            cookies = CookieManager.getInstance().getCookie(item.url),
            mediaType = item.type
        )
    }

    // Used by History/Bookmarks pages to open a saved URL: reuses the active tab if there is
    // one, rather than always opening a new tab, matching how tapping the address bar and
    // navigating normally behaves.
    fun openUrlInActiveTab(url: String) {
        val tab = activeTab
        if (tab != null) {
            tab.webView.loadUrl(url)
            activeTabId = tab.id
        } else {
            activeTabId = createAndInsertTab(url, null).id
        }
    }

    // The one canonical "go back"/"go forward" — both the on-screen BottomBar buttons and the
    // hardware/gesture back button (BackHandler below) call these, so the two can never disagree
    // about what "back" does. The canGoBack()/canGoForward() checks are the real, current WebView
    // state, not the possibly-one-frame-stale Compose-tracked copy (Tab.canGoBack/canGoForward) —
    // belt-and-suspenders against ever trying to navigate a direction that isn't actually there.
    fun goBack() {
        activeTab?.webView?.let { if (it.canGoBack()) it.goBack() }
    }
    fun goForward() {
        activeTab?.webView?.let { if (it.canGoForward()) it.goForward() }
    }
    fun goHome() {
        activeTab?.webView?.loadUrl(AppSettings.current.homePageUrl)
    }

    // Only wired up while actually browsing (activeScreen == null) — a full page replaces the
    // WebView entirely, so back-navigating it while one is showing would be invisible and
    // confusing; the screen's own BackHandler below takes over instead.
    BackHandler(enabled = activeScreen == null && activeTab?.canGoBack == true) {
        goBack()
    }

    // Composed after the tab-navigation BackHandler above, so it's the most recently added and
    // wins the back press while the player is open — closing playback rather than navigating the
    // browser tab underneath it, which stays composed but hidden behind the player's own opaque
    // background.
    BackHandler(enabled = playbackRequest != null) {
        playbackRequest = null
    }

    // Highest priority of the three: a full page (Downloads/History/Bookmarks/Settings) has its
    // own back arrow already; this just makes the hardware/gesture back button do the same thing.
    BackHandler(enabled = activeScreen != null) {
        activeScreen = null
    }

    // Everything visual lives inside this one Box so SnackbarHost (added at the very end) can sit
    // structurally above the browser Scaffold, every full-page Screen overlay, the overflow menu,
    // and the player alike — a bubble triggered from any of them (Copy URL on Detected Media,
    // Download-started from the same page, Bookmarked from the browser's own AddressBar) needs to
    // actually be visible regardless of which of those happens to be on top at the time.
    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            topBar = {
                if (activeTab != null) {
                    AddressBar(
                        value = activeTab.addressBarText,
                        onValueChange = { activeTab.addressBarText = it },
                        onFocusChanged = { focused -> activeTab.isAddressBarFocused = focused },
                        onSubmit = {
                            val resolved = UrlOrSearchResolver.resolve(
                                activeTab.addressBarText.text,
                                AppSettings.current.searchEngine.queryUrlPrefix
                            )
                            if (resolved.isNotEmpty()) activeTab.webView.loadUrl(resolved)
                            keyboardController?.hide()
                            // Not focusManager.clearFocus(): loadUrl() makes the WebView request
                            // Android View focus for itself as a side effect of navigating, which
                            // was racing clearFocus() and winning. Direct focus to the WebView
                            // instead so the two cooperate rather than race.
                            activeTab.webView.requestFocus()
                        },
                        onReload = { activeTab.webView.reload() },
                        onStop = { activeTab.webView.stopLoading() },
                        isLoading = activeTab.isLoading,
                        loadProgress = activeTab.loadProgress,
                        detectedCount = activeTab.detectedMedia.size,
                        onOpenDetected = { activeScreen = Screen.DETECTED },
                        isBookmarked = activeTab.url.isNotBlank() && BookmarkList.isBookmarked(activeTab.url),
                        onToggleBookmark = {
                            if (activeTab.url.isNotBlank()) {
                                val added = BookmarkList.toggle(context, activeTab.url, activeTab.title)
                                // Only on add, not remove — a confirmation for taking something away
                                // isn't useful the way one for adding it is, and the star's own
                                // color+fill change already signals "removed" clearly enough.
                                if (added) {
                                    coroutineScope.launch { snackbarHostState.showSnackbar("Bookmarked") }
                                }
                            }
                        }
                    )
                }
            },
            bottomBar = {
                BottomBar(
                    canGoBack = activeTab?.canGoBack == true,
                    canGoForward = activeTab?.canGoForward == true,
                    onBack = { goBack() },
                    onForward = { goForward() },
                    onBookmarks = { activeScreen = Screen.BOOKMARKS },
                    onHome = { goHome() },
                    tabCount = tabs.size,
                    onTabs = { activeScreen = Screen.TABS },
                    activeDownloadCount = DownloadQueue.downloads.count {
                        it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MUXING
                    },
                    onMenu = { showOverflowMenu = true }
                )
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Every tab's AndroidView stays composed (and thus its WebView stays alive) for as
                // long as the tab exists; only the active one is made visible. Conditionally
                // composing just the active tab would call AndroidView's onRelease — which destroys
                // the WebView — on every switch, which is exactly the "don't destroy on switch"
                // requirement this is here to avoid.
                val pullToRefreshTint = MaterialTheme.colorScheme.primary.toArgb()
                tabs.forEach { tab ->
                    key(tab.id) {
                        // Tracks only a pull-triggered reload, not every ordinary page load —
                        // SwipeRefreshLayout.isRefreshing shows its spinner regardless of what set
                        // it, so tying this directly to tab.isLoading would flash the spinner on
                        // top of the page for a plain link tap too, not just an actual pull.
                        var isRefreshing by remember { mutableStateOf(false) }
                        LaunchedEffect(tab.isLoading) {
                            if (!tab.isLoading) isRefreshing = false
                        }
                        AndroidView(
                            modifier = Modifier.fillMaxSize(),
                            factory = { ctx ->
                                // Compose's own pull-to-refresh relies on the nested-scroll system
                                // to know when a drag is happening at the very top of scrollable
                                // content — the stock WebView never participates in that system, so
                                // it can't tell Compose whether it has scrolled to the top. The
                                // older View-based SwipeRefreshLayout sidesteps that: it asks the
                                // child directly via canChildScrollUp below, which WebView answers
                                // correctly through its ordinary View.canScrollVertically.
                                SwipeRefreshLayout(ctx).apply {
                                    addView(
                                        tab.webView,
                                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    )
                                    setColorSchemeColors(pullToRefreshTint)
                                    setOnChildScrollUpCallback { _, _ -> tab.webView.canScrollVertically(-1) }
                                    setOnRefreshListener {
                                        isRefreshing = true
                                        tab.webView.reload()
                                    }
                                }
                            },
                            update = { swipeRefresh ->
                                swipeRefresh.visibility = if (tab.id == activeTabId) View.VISIBLE else View.INVISIBLE
                                swipeRefresh.isRefreshing = isRefreshing
                            },
                            onRelease = { tab.webView.destroy() }
                        )
                    }
                }
            }
        }

        // Rendered as an opaque full-screen overlay after the Scaffold above — same pattern
        // PlayerScreen already uses below — rather than replacing the Scaffold outright: the tab
        // WebViews inside it must stay composed (AndroidView's onRelease destroys a WebView the
        // moment it leaves composition), so swapping to a full page can never mean un-composing that
        // Box. Each page's own SimplePageScaffold has an opaque background, so it fully covers the
        // browser UI beneath rather than actually needing it hidden any other way.
        when (activeScreen) {
            Screen.DETECTED -> if (activeTab != null) {
                DetectedMediaPage(
                    items = activeTab.detectedMedia,
                    onCopyUrl = { url ->
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", url)))
                            snackbarHostState.showSnackbar("URL copied")
                        }
                    },
                    onPlay = { item -> playMedia(item) },
                    onDownload = { item -> downloadMedia(item) },
                    onBack = { activeScreen = null }
                )
            }
            Screen.TABS -> TabsPage(
                tabs = tabs,
                activeTabId = activeTabId,
                onSelectTab = { id ->
                    activeTabId = id
                    activeScreen = null
                },
                onCloseTab = { id -> closeTab(id) },
                onNewTab = {
                    activeTabId = createAndInsertTab("", null).id
                    activeScreen = null
                },
                onBack = { activeScreen = null }
            )
            Screen.DOWNLOADS -> DownloadsPage(
                downloads = DownloadQueue.downloads,
                onCancel = { download -> DownloadQueue.cancel(download) },
                onRetry = { download -> DownloadQueue.retry(context, download) },
                onBack = { activeScreen = null }
            )
            Screen.HISTORY -> HistoryPage(
                entries = HistoryLog.entries,
                onOpenUrl = { url ->
                    openUrlInActiveTab(url)
                    activeScreen = null
                },
                onRemove = { entry -> HistoryLog.remove(context, entry) },
                onRemoveEntries = { entries -> HistoryLog.removeAll(context, entries) },
                onClear = { HistoryLog.clear(context) },
                onBack = { activeScreen = null }
            )
            Screen.BOOKMARKS -> BookmarksPage(
                bookmarks = BookmarkList.bookmarks,
                onOpenUrl = { url ->
                    openUrlInActiveTab(url)
                    activeScreen = null
                },
                onRemove = { bookmark -> BookmarkList.remove(context, bookmark) },
                onBack = { activeScreen = null }
            )
            Screen.SETTINGS -> SettingsPage(
                settings = AppSettings.current,
                onUpdate = { updated -> AppSettings.update(context) { updated } },
                onBack = { activeScreen = null }
            )
            null -> Unit
        }

        if (showOverflowMenu) {
            OverflowMenuSheet(
                activeDownloadCount = DownloadQueue.downloads.count {
                    it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.MUXING
                },
                detectedCount = activeTab?.detectedMedia?.size ?: 0,
                onSelect = { screen ->
                    showOverflowMenu = false
                    activeScreen = screen
                },
                onDismiss = { showOverflowMenu = false }
            )
        }

        playbackRequest?.let { request ->
            PlayerScreen(request = request, onDismiss = { playbackRequest = null })
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
        )
    }

    // Switching tabs should close a Detected Media page showing the previous tab's results
    // rather than silently keep showing stale ones. (In practice hard to trigger — TabsPage is
    // itself the only way to switch tabs now, and picking a tab there already sends
    // activeScreen back to null — but harmless to keep as a belt-and-suspenders guard.)
    LaunchedEffect(activeTabId) {
        if (activeScreen == Screen.DETECTED) activeScreen = null
    }

    // Select-all-on-focus, matching standard browser address-bar behavior. Deferred via
    // LaunchedEffect + a short delay rather than done synchronously in onFocusChanged: a tap
    // that both focuses the field and positions the cursor was racing a synchronous selection
    // update and winning — verified on-device via direct selection-state logging.
    LaunchedEffect(activeTab?.id, activeTab?.isAddressBarFocused) {
        if (activeTab?.isAddressBarFocused == true) {
            delay(50)
            activeTab.addressBarText = activeTab.addressBarText.copy(
                selection = TextRange(0, activeTab.addressBarText.text.length)
            )
        }
    }

    // Only the active tab's media should ever be playing. Resume it (and only it) whenever the
    // active tab changes or the app returns to the foreground; pause every tab when backgrounded.
    LaunchedEffect(activeTabId) {
        tabs.forEach { tab -> if (tab.id == activeTabId) tab.webView.onResume() else tab.webView.onPause() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tabs.firstOrNull { it.id == activeTabId }?.webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    tabs.forEach { it.webView.onPause() }
                    // The durability point for session restore: covers backgrounding + later
                    // process death, the overwhelmingly common real-world "app was gone" case.
                    TabSessionStore.save(context, tabs, activeTabId)
                    DownloadQueue.persist(context)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
