package com.miowser.app.browser

import android.content.Intent
import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Message
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miowser.app.adblock.AdBlock
import com.miowser.app.adblock.AdBlockFilters
import com.miowser.app.bookmarks.BookmarkList
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.DownloadConflictReason
import com.miowser.app.downloader.DownloadedFileOpener
import com.miowser.app.downloader.DownloadedFileInspector
import com.miowser.app.downloader.DownloadedFileType
import com.miowser.app.downloader.FileOpenResult
import com.miowser.app.downloader.DownloadKind
import com.miowser.app.downloader.DownloadQueue
import com.miowser.app.downloader.DownloadStatus
import com.miowser.app.downloader.copyForQueue
import com.miowser.app.downloader.findDownloadConflict
import com.miowser.app.downloader.renamedDownloadCopy
import com.miowser.app.downloader.rendererResourceKind
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.MediaUrlClassifier
import com.miowser.app.grabber.meetsMinimumDuration
import com.miowser.app.history.HistoryLog
import com.miowser.app.library.MediaLibrary
import com.miowser.app.library.FailedVideoLog
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.player.PlaybackRequest
import com.miowser.app.player.PlayerScreen
import com.miowser.app.ui.theme.MiowserPlayerTheme
import com.miowser.app.player.toPlaybackRequest
import com.miowser.app.player.toLibraryEntry
import com.miowser.app.settings.AppSettings
import com.miowser.app.settings.HomePageMode
import com.miowser.app.settings.NewTabConfirmation
import com.miowser.app.ui.BookmarksPage
import com.miowser.app.ui.AdBlockPage
import com.miowser.app.ui.BottomBar
import com.miowser.app.ui.BrowsingDataPage
import com.miowser.app.ui.HistoryPage
import com.miowser.app.ui.LinkContextMenu
import com.miowser.app.ui.MiowserSnackbarHost
import com.miowser.app.ui.MiowserSnackbarVisuals
import com.miowser.app.ui.OverflowMenuSheet
import com.miowser.app.ui.Screen
import com.miowser.app.ui.SettingsPage
import com.miowser.app.ui.SitePermissionsPage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

// Anything that isn't safe/sensible in a filename, replaced with "_"; matches the common
// filesystem-reserved set (Windows is the strictest, so satisfying it covers everything else).
private val UNSAFE_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")

/**
 * A page-triggered new-tab request (target="_blank"/window.open()) held pending a confirmation
 * dialog — see BrowserScreen's handleNewWindowRequested. resultMsg is what actually completes the
 * popup once a decision is made; never completing it (an implicit part of declining) is a
 * deliberate, safe way to block one — see completeNewWindow's own comment.
 */
private data class PendingNewTabRequest(val openerTabId: Long, val resultMsg: Message)
private data class PendingDownloadRefresh(val originalId: Long, val candidate: Download)
private data class PendingDownloadConflict(
    val existingId: Long,
    val candidate: Download,
    val reason: DownloadConflictReason
)
private data class PendingGeolocation(
    val origin: String,
    val callback: GeolocationPermissions.Callback
)

private fun sanitizeFileName(raw: String): String {
    val cleaned = raw.trim().replace(UNSAFE_FILENAME_CHARS, "_").take(80)
    return cleaned.ifBlank { "miowser_download" }
}

/** Strips a trailing ".ext" (2-4 alphanumeric chars) so callers can safely append their own without duplicating it. */
private fun stripKnownExtension(name: String): String {
    val ext = name.substringAfterLast('.', "")
    return if (ext.length in 2..4 && ext.all { it.isLetterOrDigit() }) name.substringBeforeLast('.') else name
}

/** Server-provided download filenames legitimately use longer suffixes such as .torrent. */
private fun suggestedDownloadExtension(name: String): String? {
    val extension = name.substringAfterLast('.', "").lowercase()
    return extension.takeIf { it.length in 1..10 && it.all(Char::isLetterOrDigit) }
}

private fun configuredBrowserHomeTarget(): String = when (AppSettings.current.homePageMode) {
    HomePageMode.MIOWSER -> BROWSER_HOME_URL
    HomePageMode.CUSTOM -> UrlOrSearchResolver.resolve(
        AppSettings.current.homePageUrl,
        AppSettings.current.searchEngine.queryUrlPrefix
    ).ifBlank { BROWSER_HOME_URL }
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
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // The overflow (hamburger) menu is the only thing left that's a transient sheet rather than
    // a full page — it's a picker, not a destination itself. Everything it (or AddressBar's
    // badge, or BottomBar's own buttons) launches is a Screen, rendered as an opaque overlay
    // below rather than replacing the Scaffold outright (see the comment at that render site).
    var showOverflowMenu by remember { mutableStateOf(false) }
    var activeScreen by remember { mutableStateOf<Screen?>(null) }
    var openingTabs by remember { mutableStateOf(false) }
    var showingHub by rememberSaveable { mutableStateOf(true) }
    var hubSection by rememberSaveable { mutableStateOf(HubSection.LIBRARY) }
    var hubDownloadSection by rememberSaveable { mutableStateOf(HubDownloadSection.DOWNLOADING) }
    var hubLibrarySort by rememberSaveable { mutableStateOf(HubLibrarySort.RECENT) }
    var playbackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    // A page-triggered new tab awaiting the user's confirmation (see handleNewWindowRequested) —
    // null the overwhelming majority of the time, since most of Settings.newTabConfirmation's
    // options resolve immediately without ever needing this at all.
    var pendingNewTabRequest by remember { mutableStateOf<PendingNewTabRequest?>(null) }
    var refreshTargetDownloadId by remember { mutableStateOf<Long?>(null) }
    var pendingDownloadRefresh by remember { mutableStateOf<PendingDownloadRefresh?>(null) }
    var pendingDownloadConflict by remember { mutableStateOf<PendingDownloadConflict?>(null) }
    // The URL behind a long-pressed link, driving LinkContextMenu below — null when it's not showing.
    var pendingLinkMenuUrl by remember { mutableStateOf<String?>(null) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    var pendingFileOwnerId by remember { mutableStateOf<Long?>(null) }
    var pendingWebPermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingWebPermissionIsPrivate by remember { mutableStateOf(false) }
    var pendingGeolocation by remember { mutableStateOf<PendingGeolocation?>(null) }
    var pendingGeolocationOwnerId by remember { mutableStateOf<Long?>(null) }
    var pendingGeolocationIsPrivate by remember { mutableStateOf(false) }
    var showPrivateStartConfirmation by remember { mutableStateOf(false) }
    var sitePermissionRevision by remember { mutableIntStateOf(0) }
    var pendingRuntimePermission by remember { mutableStateOf<PermissionRequest?>(null) }
    var rememberRuntimePermission by remember { mutableStateOf(false) }
    var pendingRuntimeGeolocation by remember { mutableStateOf<PendingGeolocation?>(null) }
    var rememberRuntimeGeolocation by remember { mutableStateOf(false) }
    var unsupportedSitePermission by remember { mutableStateOf<String?>(null) }
    // One shared bubble for lightweight confirmations (copy/download-started/bookmarked) —
    // rendered at the very end of the Box below so it's visible above whichever layer (browser,
    // a full page, the player) happens to be showing, rather than being tied to any one of their
    // own Scaffolds.
    val snackbarHostState = remember { SnackbarHostState() }
    var snackbarJob by remember { mutableStateOf<Job?>(null) }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var homeSearchFocusRequest by remember { mutableIntStateOf(0) }
    var addressBarFocusRequest by remember { mutableIntStateOf(0) }
    val snackbarBottomOffset = with(density) { bottomBarHeightPx.toDp() } + 8.dp

    val tabs = remember { mutableStateListOf<Tab>() }
    var activeTabId by remember { mutableStateOf<Long?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        pendingFileOwnerId = null
        val picked = runCatching {
            WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        }.getOrNull()
            ?.takeIf { uris ->
                uris.isNotEmpty() && uris.size <= 100 && uris.all { uri ->
                    uri.scheme == "content" && uri.authority?.startsWith(context.packageName) != true
                }
            }
        callback?.onReceiveValue(if (result.resultCode == Activity.RESULT_OK) picked else null)
    }
    val webPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val request = pendingRuntimePermission
        pendingRuntimePermission = null
        if (request != null) {
            if (results.isNotEmpty() && results.values.all { it }) {
                if (rememberRuntimePermission) request.resources.forEach {
                    SitePermissionStore.set(context, request.origin.toString(), it, SiteDecision.ALLOW)
                }
                request.grant(request.resources)
            } else request.deny()
        }
        rememberRuntimePermission = false
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingRuntimeGeolocation?.let {
            if (granted && rememberRuntimeGeolocation) {
                SitePermissionStore.set(context, it.origin, "geolocation", SiteDecision.ALLOW)
            }
            it.callback.invoke(it.origin, granted, false)
        }
        pendingRuntimeGeolocation = null
        pendingGeolocationOwnerId = null
        rememberRuntimeGeolocation = false
    }

    // Kotlin doesn't hoist local function declarations, so a genuine reference cycle —
    // createAndInsertTab's onNewWindowRequested lambda needs handleNewWindowRequested, which
    // needs completeNewWindow, which needs createAndInsertTab back again — can't all be plain
    // sequential `fun`s. This one lateinit var is the single break in that cycle: createAndInsertTab
    // calls through it rather than the function directly, and it's assigned its real value once
    // handleNewWindowRequested (defined further down, after createAndInsertTab already exists for
    // it to call) is itself ready.
    lateinit var onNewWindowRequestedHandler: (openerTabId: Long, isUserGesture: Boolean, resultMsg: Message) -> Unit
    lateinit var onWebDownloadRequestedHandler: (WebDownloadRequest) -> Unit

    fun handleFileChooser(ownerTabId: Long, callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams) {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = callback
        pendingFileOwnerId = ownerTabId
        try {
            fileChooserLauncher.launch(params.createIntent())
        } catch (_: Exception) {
            pendingFileCallback = null
            pendingFileOwnerId = null
            callback.onReceiveValue(null)
        }
    }

    fun runtimePermissionsFor(request: PermissionRequest): Array<String>? {
        val resources = request.resources.toSet()
        if (resources.isEmpty() || resources.any {
                it != PermissionRequest.RESOURCE_VIDEO_CAPTURE &&
                    it != PermissionRequest.RESOURCE_AUDIO_CAPTURE
            }) return null
        return buildList {
            if (PermissionRequest.RESOURCE_VIDEO_CAPTURE in resources) add(Manifest.permission.CAMERA)
            if (PermissionRequest.RESOURCE_AUDIO_CAPTURE in resources) add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
    }

    fun handleWebPermission(request: PermissionRequest, privateTab: Boolean) {
        val origin = request.origin.toString()
        val permissions = runtimePermissionsFor(request)
        if (Uri.parse(origin).scheme != "https" || permissions == null) {
            request.deny()
            unsupportedSitePermission = if (permissions == null) {
                "$origin requested a website resource Miowser cannot grant safely."
            } else {
                "Site permissions require a secure HTTPS page."
            }
            return
        }
        val decisions = request.resources.map {
            if (privateTab) SiteDecision.ASK else SitePermissionStore.get(context, origin, it)
        }
        if (decisions.all { it == SiteDecision.BLOCK }) {
            request.deny()
        } else if (decisions.all { it == SiteDecision.ALLOW }) {
            val missing = permissions.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missing.isEmpty()) request.grant(request.resources)
            else {
                pendingRuntimePermission = request
                rememberRuntimePermission = true
                webPermissionLauncher.launch(missing.toTypedArray())
            }
        } else {
            pendingWebPermission?.deny()
            pendingWebPermission = request
            pendingWebPermissionIsPrivate = privateTab
        }
    }

    fun handleGeolocation(origin: String, callback: GeolocationPermissions.Callback, privateTab: Boolean, ownerTabId: Long) {
        if (Uri.parse(origin).scheme != "https") {
            callback.invoke(origin, false, false)
            return
        }
        when (if (privateTab) SiteDecision.ASK else SitePermissionStore.get(context, origin, "geolocation")) {
            SiteDecision.BLOCK -> callback.invoke(origin, false, false)
            SiteDecision.ALLOW -> {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingRuntimeGeolocation = PendingGeolocation(origin, callback)
                    pendingGeolocationOwnerId = ownerTabId
                    rememberRuntimeGeolocation = true
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
            SiteDecision.ASK -> {
                pendingGeolocation?.let { it.callback.invoke(it.origin, false, false) }
                pendingGeolocation = PendingGeolocation(origin, callback)
                pendingGeolocationOwnerId = ownerTabId
                pendingGeolocationIsPrivate = privateTab
            }
        }
    }

    fun cancelWebPermission(request: PermissionRequest) {
        if (pendingWebPermission === request) pendingWebPermission = null
        if (pendingRuntimePermission === request) pendingRuntimePermission = null
    }

    fun cancelGeolocation(ownerTabId: Long) {
        if (pendingGeolocationOwnerId != ownerTabId) return
        pendingGeolocation?.let { it.callback.invoke(it.origin, false, false) }
        pendingGeolocation = null
        pendingRuntimeGeolocation = null
        pendingGeolocationOwnerId = null
    }

    // Creates a tab and inserts it into the list, then returns it. Page-triggered tabs
    // (openerTabId != null) are inserted immediately after their opener, matching how real
    // browsers place a target="_blank"/window.open() tab next to the page that opened it;
    // manually-opened tabs (the "+" button, openerTabId == null) are appended at the end.
    // A local function rather than a lambda so it can call itself for grandchild tabs
    // (a page opening a tab whose page opens another tab) without a lateinit dance.
    fun createAndInsertTab(
        initialUrl: String,
        openerTabId: Long?,
        privateTab: Boolean = false,
        initialTitle: String = "",
        initialLastActiveAt: Long = System.currentTimeMillis()
    ): Tab {
        val tabId = Tab.nextId()
        val tab = Tab(
            id = tabId,
            context = context,
            initialUrl = initialUrl,
            openerTabId = openerTabId,
            isPrivate = privateTab,
            initialTitle = initialTitle,
            initialLastActiveAt = initialLastActiveAt,
            canCapturePreview = {
                activeTabId == tabId && activeScreen == null && !showingHub && playbackRequest == null
            },
            onNewWindowRequested = { childOpenerId, isUserGesture, resultMsg ->
                onNewWindowRequestedHandler(childOpenerId, isUserGesture, resultMsg)
            },
            onLinkLongPressed = { url -> pendingLinkMenuUrl = url },
            onDownloadRequested = { request -> onWebDownloadRequestedHandler(request) },
            onFileChooserRequested = { callback, params -> handleFileChooser(tabId, callback, params) },
            onWebPermissionRequested = { request -> handleWebPermission(request, privateTab) },
            onWebPermissionCanceled = ::cancelWebPermission,
            onGeolocationRequested = { origin, callback -> handleGeolocation(origin, callback, privateTab, tabId) },
            onGeolocationCanceled = { cancelGeolocation(tabId) },
            onExternalNavigation = { url -> pendingExternalUrl = url }
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
            if (pendingFileOwnerId == id) {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = null
                pendingFileOwnerId = null
            }
            cancelGeolocation(id)
            closed.deleteThumbnail()
            closed.destroy()
            if (closed.isPrivate && tabs.none(Tab::isPrivate)) {
                BrowsingData.clearSharedPrivateSessionData(context, tabs.toList()) {
                    PrivateSessionStore.setActive(context, false)
                }
            }
            if (tabs.isEmpty()) {
                activeTabId = createAndInsertTab(configuredBrowserHomeTarget(), null).id
            } else if (activeTabId == id) {
                // Prefer returning to the tab that opened this one (a real page-triggered
                // child); manually-opened tabs have no opener, so fall back to whichever tab
                // is now immediately before this one's old position — "the previous tab in order".
                val opener = closed.openerTabId?.let { openerId -> tabs.firstOrNull { it.id == openerId } }
                activeTabId = opener?.id ?: tabs[(index - 1).coerceIn(0, tabs.size - 1)].id
            }
        }
    }

    fun archiveEligibleTabs(now: Long = System.currentTimeMillis()) {
        InactiveTabs.prune(context, AppSettings.current.autoDeleteInactiveTabs, now)
        val decisions = InactiveTabPolicy.decisions(
            tabs.map { tab ->
                LiveTabSummary(
                    id = tab.id,
                    url = tab.url.ifBlank { tab.addressBarText.text },
                    lastActiveAt = tab.lastActiveAt,
                    isPrivate = tab.isPrivate,
                    isHome = tab.isShowingHome,
                    isActive = tab.id == activeTabId
                )
            },
            now = now
        )
        if (decisions.isEmpty()) return

        val archived = decisions.mapNotNull { decision ->
            val tab = tabs.firstOrNull { it.id == decision.tabId } ?: return@mapNotNull null
            val url = tab.url.ifBlank { tab.addressBarText.text }
            if (url.isBlank()) return@mapNotNull null
            tabs.remove(tab)
            tab.deleteThumbnail()
            tab.destroy()
            InactiveTabRecord(
                title = tab.title,
                url = url,
                lastActiveAt = tab.lastActiveAt,
                archivedAt = now,
                reason = decision.reason
            )
        }
        InactiveTabs.archive(context, archived)
        if (archived.isNotEmpty()) TabSessionStore.save(context, tabs, activeTabId)
    }

    // Actually creates the tab and hands it to WebView via the transport — the other half of
    // Tab's onCreateWindow, deferred to here so it can run either immediately or only once a
    // confirmation dialog resolves. Also switches to it (requirement: a page opening a tab should
    // take the user there, not leave it running unseen in the background).
    fun completeNewWindow(openerTabId: Long, resultMsg: Message) {
        val child = createAndInsertTab("", openerTabId,
            privateTab = tabs.firstOrNull { it.id == openerTabId }?.isPrivate == true)
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = child.webView
        resultMsg.sendToTarget()
        activeTabId = child.id
    }

    // Decides a page-triggered new tab's fate per Settings.newTabConfirmation — see that enum's
    // own doc comment. Either resolves immediately (completeNewWindow) or holds the request
    // behind a confirmation dialog (pendingNewTabRequest, rendered below) until the user answers.
    fun handleNewWindowRequested(openerTabId: Long, isUserGesture: Boolean, resultMsg: Message) {
        val shouldConfirm = when (AppSettings.current.newTabConfirmation) {
            NewTabConfirmation.ALWAYS -> true
            NewTabConfirmation.NON_GESTURE_ONLY -> !isUserGesture
            NewTabConfirmation.NEVER -> false
        }
        if (shouldConfirm) {
            pendingNewTabRequest = PendingNewTabRequest(openerTabId, resultMsg)
        } else {
            completeNewWindow(openerTabId, resultMsg)
        }
    }
    onNewWindowRequestedHandler = { openerTabId, isUserGesture, resultMsg ->
        handleNewWindowRequested(openerTabId, isUserGesture, resultMsg)
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
        if (PrivateSessionStore.wasInterrupted(context)) {
            suspendCoroutine<Unit> { continuation ->
                BrowsingData.clearSharedPrivateSessionData(context, emptyList()) {
                    PrivateSessionStore.setActive(context, false)
                    continuation.resume(Unit)
                }
            }
        }
        DownloadQueue.restore(context)
        AppSettings.load(context)
        InactiveTabs.restore(context, AppSettings.current.autoDeleteInactiveTabs)
        HubSettingsStore.load(context).let { hubSettings ->
            hubLibrarySort = hubSettings.librarySort
            hubDownloadSection = hubSettings.defaultDownloadSection
        }
        AdBlockFilters.initialize(context)
        HistoryLog.restore(context)
        BookmarkList.restore(context)
        MediaLibrary.restore(context)
        SpeedDials.restore(context)

        if (tabs.isNotEmpty()) return@LaunchedEffect
        val saved = if (AppSettings.current.restoreTabsOnStartup) TabSessionStore.load(context) else null
        if (saved == null) {
            TabThumbnailCache.retainOnly(context, emptySet())
            activeTabId = createAndInsertTab(configuredBrowserHomeTarget(), null).id
            return@LaunchedEffect
        }
        val (savedTabs, activeIndex) = saved
        val restoredIds = mutableListOf<Long>()
        for (savedTab in savedTabs) {
            val openerId = savedTab.openerIndex.takeIf { it in restoredIds.indices }?.let { restoredIds[it] }
            val restoredTabId = Tab.nextId()
            val tab = Tab(
                id = restoredTabId,
                context = context,
                initialUrl = savedTab.url,
                openerTabId = openerId,
                isPrivate = false,
                thumbnailKey = savedTab.thumbnailKey.ifBlank {
                    "tab-$restoredTabId-${System.currentTimeMillis()}"
                },
                initialTitle = savedTab.title,
                initialLastActiveAt = savedTab.lastActiveAt.takeIf { it > 0L }
                    ?: System.currentTimeMillis(),
                canCapturePreview = {
                    activeTabId == restoredTabId && activeScreen == null && !showingHub && playbackRequest == null
                },
                onNewWindowRequested = { childOpenerId, isUserGesture, resultMsg ->
                    handleNewWindowRequested(childOpenerId, isUserGesture, resultMsg)
                },
                onLinkLongPressed = { url -> pendingLinkMenuUrl = url },
                onDownloadRequested = { request -> onWebDownloadRequestedHandler(request) },
                onFileChooserRequested = { callback, params -> handleFileChooser(restoredTabId, callback, params) },
                onWebPermissionRequested = { request -> handleWebPermission(request, false) },
                onWebPermissionCanceled = ::cancelWebPermission,
                onGeolocationRequested = { origin, callback -> handleGeolocation(origin, callback, false, restoredTabId) },
                onGeolocationCanceled = { cancelGeolocation(restoredTabId) },
                onExternalNavigation = { url -> pendingExternalUrl = url }
            )
            tabs.add(tab)
            restoredIds.add(tab.id)
        }
        TabThumbnailCache.retainOnly(context, tabs.map(Tab::thumbnailKey).toSet())
        activeTabId = tabs.getOrNull(activeIndex)?.id ?: tabs.firstOrNull()?.id
        archiveEligibleTabs()
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    var lastThumbnailTabId by remember { mutableStateOf<Long?>(null) }

    fun showActionSnackbar(
        message: String,
        actionLabel: String = "Dismiss",
        onDismiss: () -> Unit = {},
        onAction: () -> Unit = {}
    ) {
        // Keep onAction last: Kotlin assigns a trailing lambda to the final function parameter.
        // With onDismiss last, calls such as showActionSnackbar("…", "Switch") { switchTab() }
        // silently ran switchTab when the snackbar timed out, was swiped, or was closed instead.
        // Feedback is about the action that just happened. Replace an older snackbar instead of
        // queueing behind it for up to ten seconds; the old snackbar's dismissal callback still
        // runs from finally (important for permanently cleaning up a removed filter's cache).
        snackbarJob?.cancel()
        snackbarJob = coroutineScope.launch {
            var handled = false
            try {
                val result = snackbarHostState.showSnackbar(
                    MiowserSnackbarVisuals(
                        message = message,
                        actionLabel = actionLabel,
                        withDismissAction = actionLabel != "Dismiss",
                        duration = SnackbarDuration.Long,
                        onAction = onAction
                    )
                )
                handled = true
                if (result == SnackbarResult.Dismissed) onDismiss()
            } finally {
                if (!handled) onDismiss()
            }
        }
    }

    fun enqueueOrOfferRefresh(candidate: Download) {
        val targetId = refreshTargetDownloadId
        val target = targetId?.let { id -> DownloadQueue.downloads.firstOrNull { it.id == id } }
        if (target != null) {
            pendingDownloadRefresh = PendingDownloadRefresh(target.id, candidate)
            refreshTargetDownloadId = null
        } else {
            refreshTargetDownloadId = null
            val conflict = findDownloadConflict(DownloadQueue.downloads, candidate)
            if (conflict != null) {
                pendingDownloadConflict = PendingDownloadConflict(
                    existingId = conflict.existing.id,
                    candidate = candidate,
                    reason = conflict.reason
                )
            } else {
                DownloadQueue.enqueue(context, candidate)
                showActionSnackbar("Download started", "View") {
                    hubSection = HubSection.DOWNLOADS
                    hubDownloadSection = HubDownloadSection.DOWNLOADING
                    activeScreen = null
                    showingHub = true
                }
            }
        }
    }

    /**
     * WebView calls this for links/responses that are downloads rather than renderable pages.
     * Preserve its server-provided filename and the originating tab's request identity so signed,
     * cookie-protected downloads behave the same way they did inside the page.
     */
    fun enqueueWebDownload(request: WebDownloadRequest) {
        val scheme = runCatching { Uri.parse(request.url).scheme?.lowercase() }.getOrNull()
        val rendererKind = rendererResourceKind(request.url)
        if (scheme != "http" && scheme != "https" && rendererKind == null) {
            showActionSnackbar("This URL type can't be downloaded")
            return
        }

        val suggestedName = if (rendererKind != null) {
            val fallback = sanitizeFileName(
                URLUtil.guessFileName("download", request.contentDisposition, request.mimeType)
            )
            val extension = suggestedDownloadExtension(fallback)
            val pageName = request.pageTitle.trim().takeIf { it.isNotEmpty() && it != "New Tab" }
            sanitizeFileName(pageName?.let { title -> extension?.let { "$title.$it" } ?: title } ?: fallback)
        } else {
            sanitizeFileName(URLUtil.guessFileName(request.url, request.contentDisposition, request.mimeType))
        }
        val suggestedExtension = suggestedDownloadExtension(suggestedName)
        val mediaType = MediaUrlClassifier.classify(request.url)
            ?: MediaUrlClassifier.classifyByMimeType(request.mimeType)
        val download = Download(
            id = Download.nextId(),
            sourceUrl = request.url,
            pageUrl = request.pageUrl,
            userAgent = request.userAgent,
            referer = request.pageUrl,
            cookies = if (rendererKind == null) CookieManager.getInstance().getCookie(request.url) else null,
            kind = when {
                rendererKind != null -> DownloadKind.WEBVIEW_RESOURCE
                mediaType == MediaType.HLS -> DownloadKind.HLS
                mediaType == MediaType.DASH -> DownloadKind.DASH
                else -> DownloadKind.DIRECT_FILE
            },
            displayName = suggestedName,
            outputBaseName = sanitizeFileName(
                if (suggestedExtension != null) suggestedName.substringBeforeLast('.') else suggestedName
            ),
            suggestedExtension = suggestedExtension
        ).apply {
            totalBytes = request.contentLength.takeIf { it > 0L } ?: -1L
        }
        if (rendererKind != null) {
            val originTab = tabs.firstOrNull { it.id == request.originatingTabId }
            if (originTab == null) {
                showActionSnackbar("The originating page is no longer open")
                return
            }
            originTab.attachRendererTransfer(download)
        }
        enqueueOrOfferRefresh(download)
    }
    onWebDownloadRequestedHandler = { request -> enqueueWebDownload(request) }

    fun shareUrl(url: String, title: String? = null) {
        val pageTitle = title?.trim()?.takeIf { it.isNotEmpty() && it != "New Tab" }
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, pageTitle?.let { "$it\n$url" } ?: url)
            pageTitle?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }
        context.startActivity(Intent.createChooser(shareIntent, null))
    }

    // Builds a real Download from a detected item — cookies/UA/referer forwarded the same way
    // MediaProbe already does for its verification probes, so a download authenticates exactly
    // like the page load that found it did.
    fun downloadMedia(item: DetectedMedia) {
        val tab = activeTab ?: return
        val rendererKind = rendererResourceKind(item.url)
        val name = item.responseFileName?.trim()?.takeIf(String::isNotEmpty)
            ?: item.pageTitle?.trim()?.takeIf { it.isNotEmpty() && it != "New Tab" }
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
            cookies = if (rendererKind == null) CookieManager.getInstance().getCookie(item.url) else null,
            kind = when {
                rendererKind != null -> DownloadKind.WEBVIEW_RESOURCE
                item.type == MediaType.HLS -> DownloadKind.HLS
                item.type == MediaType.DASH -> DownloadKind.DASH
                else -> DownloadKind.DIRECT_FILE
            },
            displayName = name,
            outputBaseName = sanitizeFileName(stripKnownExtension(name))
        )
        if (rendererKind != null) tab.attachRendererTransfer(download)
        enqueueOrOfferRefresh(download)
    }

    /** Starts the exact authenticated remote source currently playing in the rich player. */
    fun downloadFromPlayer(request: PlaybackRequest.Remote) {
        val name = request.title.trim().takeIf { it.isNotEmpty() }
            ?: request.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
                .ifBlank { "video" }
        val download = Download(
            id = Download.nextId(),
            sourceUrl = request.url,
            pageUrl = request.pageUrl,
            userAgent = request.userAgent,
            referer = request.pageUrl,
            cookies = request.cookies,
            kind = when (request.mediaType) {
                MediaType.HLS -> DownloadKind.HLS
                MediaType.DASH -> DownloadKind.DASH
                else -> DownloadKind.DIRECT_FILE
            },
            displayName = name,
            outputBaseName = sanitizeFileName(stripKnownExtension(name))
        )
        enqueueOrOfferRefresh(download)
    }

    // Same cookies/UA/referer forwarding as downloadMedia above — playback authenticates the
    // same way a real page load (or a download) would have. Deliberately doesn't touch
    // activeScreen: PlayerScreen renders as its own opaque overlay on top of whatever's showing
    // (same pattern as everything else layered after the Scaffold below), so closing playback
    // naturally lands back on the Detected Media page the user was just on, not the raw browser.
    fun playMedia(item: DetectedMedia) {
        val tab = activeTab ?: return
        val title = item.pageTitle
            ?.trim()
            ?.takeIf { it.isNotEmpty() && it != "New Tab" }
            ?: item.url.substringBefore('?').substringBefore('#').substringAfterLast('/').ifBlank {
                "Untitled video"
            }
        val sourceLabel = runCatching { Uri.parse(item.pageUrl).host }
            .getOrNull()
            ?.removePrefix("www.")
            ?.takeIf(String::isNotBlank)
            ?: "Web stream"
        // Stop the page immediately, before Compose has even rendered the player overlay. The
        // playbackRequest-aware lifecycle effect below repeats this defensively for every path
        // that can open the player.
        tab.pausePageMedia()
        tab.webView.onPause()
        playbackRequest = PlaybackRequest.Remote(
            url = item.url,
            pageUrl = item.pageUrl,
            userAgent = tab.webView.settings.userAgentString,
            cookies = CookieManager.getInstance().getCookie(item.url),
            mediaType = item.type,
            title = title,
            sourceLabel = sourceLabel,
            pageTitle = item.pageTitle?.takeIf { it.isNotBlank() && it != "New Tab" } ?: tab.title,
            videoName = item.responseFileName?.takeIf(String::isNotBlank)
                ?: item.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
                    .takeIf(String::isNotBlank) ?: title
        )
    }

    // Used by History/Bookmarks pages to open a saved URL: reuses the active tab if there is
    // one, rather than always opening a new tab, matching how tapping the address bar and
    // navigating normally behaves.
    fun openUrlInActiveTab(url: String) {
        val tab = activeTab
        if (tab != null) {
            // Explicit, defense-in-depth clear — see Tab.clearDetections' doc comment for why
            // WebView's own onPageStarted-triggered clear alone isn't always enough.
            tab.navigateTo(url)
            activeTabId = tab.id
        } else {
            activeTabId = createAndInsertTab(url, null).id
        }
    }

    /**
     * Selects a tab from an asynchronous UI action such as a snackbar. Clearing every possible
     * browser overlay here is intentional: merely changing activeTabId can leave a full-page
     * destination covering the newly selected WebView, making a successful switch look like it
     * did nothing. The explicit pause/resume also applies the state in this frame rather than
     * waiting for the activeTabId LaunchedEffect below.
     */
    fun activateTab(id: Long) {
        if (tabs.none { it.id == id }) return
        activeScreen = null
        showOverflowMenu = false
        pendingLinkMenuUrl = null
        activeTabId = id
        tabs.forEach { tab ->
            if (tab.id == id) {
                tab.webView.onResume()
                tab.webView.requestFocus()
            } else {
                tab.webView.onPause()
            }
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
        activeTab?.let { tab ->
            val target = configuredBrowserHomeTarget()
            if (!(isBrowserHomeUrl(target) && tab.isShowingHome)) {
                tab.navigateTo(target)
            }
        }
    }

    // One resolver prevents simultaneously-enabled BackHandlers from racing by composition
    // order. That race previously let an internal page close behind the player, and opening a
    // downloaded video from the Hub had no enabled handler at all — so Android exited the app.
    val backAction = resolveBrowserBackAction(
        showingHub = showingHub,
        playerOpen = playbackRequest != null,
        internalPageOpen = activeScreen != null,
        webViewCanGoBack = activeTab?.canGoBack == true,
        activeTabHasOpener = activeTab?.openerTabId != null,
        hubOnLibrary = hubSection == HubSection.LIBRARY
    )
    BackHandler(enabled = backAction != BrowserBackAction.EXIT_APP) {
        when (backAction) {
            BrowserBackAction.CLOSE_PLAYER -> playbackRequest = null
            BrowserBackAction.CLOSE_INTERNAL_PAGE -> activeScreen =
                if (activeScreen == Screen.CLEAR_DATA || activeScreen == Screen.SITE_PERMISSIONS) Screen.SETTINGS else null
            BrowserBackAction.WEB_HISTORY -> goBack()
            BrowserBackAction.CLOSE_CHILD_TAB -> activeTab?.let { closeTab(it.id) }
            BrowserBackAction.SHOW_HUB -> showingHub = true
            BrowserBackAction.SHOW_HUB_LIBRARY -> hubSection = HubSection.LIBRARY
            BrowserBackAction.EXIT_APP -> Unit
        }
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
                // Home already owns the page's primary search/address field. Keeping the browser
                // omnibox above it duplicates the same action and pushes the reachable Home UI down.
                if (activeTab != null && !activeTab.isShowingHome) {
                    AddressBar(
                        value = activeTab.addressBarText,
                        onValueChange = { activeTab.addressBarText = it },
                        onFocusChanged = { focused -> activeTab.isAddressBarFocused = focused },
                        onSubmit = {
                            val resolved = UrlOrSearchResolver.resolve(
                                activeTab.addressBarText.text,
                                AppSettings.current.searchEngine.queryUrlPrefix
                            )
                            if (resolved.isNotEmpty()) {
                                activeTab.navigateTo(resolved)
                            }
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
                        detectedCount = activeTab.detectedMedia.count {
                            it.meetsMinimumDuration(AppSettings.current.grabberMinimumDurationSeconds)
                        },
                        onOpenDetected = { activeScreen = Screen.DETECTED },
                        isBookmarked = activeTab.url.isNotBlank() && !activeTab.isShowingHome &&
                            BookmarkList.isBookmarked(activeTab.url),
                        isPrivate = activeTab.isPrivate,
                        onToggleBookmark = {
                            if (activeTab.url.isNotBlank() && !activeTab.isShowingHome) {
                                val added = BookmarkList.toggle(context, activeTab.url, activeTab.title)
                                // Only on add, not remove — a confirmation for taking something away
                                // isn't useful the way one for adding it is, and the star's own
                                // color+fill change already signals "removed" clearly enough.
                                if (added) {
                                    val bookmarkedUrl = activeTab.url
                                    val bookmarkedTitle = activeTab.title
                                    showActionSnackbar("Bookmarked", "Undo") {
                                        if (BookmarkList.isBookmarked(bookmarkedUrl)) {
                                            BookmarkList.toggle(context, bookmarkedUrl, bookmarkedTitle)
                                        }
                                    }
                                }
                            }
                        },
                        focusRequestKey = addressBarFocusRequest
                    )
                }
            },
            bottomBar = {
                Box(modifier = Modifier.onSizeChanged { bottomBarHeightPx = it.height }) {
                    BottomBar(
                        canGoBack = activeTab?.canGoBack == true,
                        canGoForward = activeTab?.canGoForward == true,
                        onBack = { goBack() },
                        onForward = { goForward() },
                        onBookmarks = { activeScreen = Screen.BOOKMARKS },
                        onHome = { goHome() },
                        onHomeLongClick = {
                            activeTab?.let { tab ->
                                if (tab.isShowingHome) {
                                    homeSearchFocusRequest++
                                } else {
                                    tab.addressBarText = tab.addressBarText.copy(
                                        selection = TextRange(0, tab.addressBarText.text.length)
                                    )
                                    addressBarFocusRequest++
                                }
                            }
                        },
                        tabCount = tabs.size,
                        onTabs = {
                            if (!openingTabs) {
                                openingTabs = true
                                val sourceTab = activeTab
                                val openOverview = {
                                    openingTabs = false
                                    if (activeTabId == sourceTab?.id && activeScreen == null) {
                                        archiveEligibleTabs()
                                        activeScreen = Screen.TABS
                                    }
                                }
                                if (sourceTab == null) openOverview()
                                else sourceTab.captureThumbnail(onComplete = openOverview)
                            }
                        },
                        activeDownloadCount = DownloadQueue.downloads.count {
                            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
                                it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.MUXING
                        },
                        onMenu = { showOverflowMenu = true }
                    )
                }
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
                                swipeRefresh.visibility = if (
                                    tab.id == activeTabId && !tab.isShowingHome
                                ) View.VISIBLE else View.INVISIBLE
                                swipeRefresh.isRefreshing = isRefreshing
                            },
                            onRelease = { tab.destroy() }
                        )
                    }
                }
                activeTab?.pageError?.let { message ->
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Page unavailable", style = MaterialTheme.typography.headlineSmall)
                            Text(message, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))
                            Button(onClick = { activeTab.webView.reload() }) { Text("Try again") }
                        }
                    }
                }
                activeTab?.takeIf(Tab::isShowingHome)?.let { homeTab ->
                    key("browser-home-${homeTab.id}") {
                        BrowserHomePage(
                            speedDials = SpeedDials.entries,
                            folders = SpeedDials.folders,
                            sort = SpeedDials.sort,
                            focusRequestKey = homeSearchFocusRequest,
                            searchEngineName = AppSettings.current.searchEngine.label,
                            isPrivate = homeTab.isPrivate,
                            onSearch = { input ->
                                val resolved = UrlOrSearchResolver.resolve(
                                    input,
                                    AppSettings.current.searchEngine.queryUrlPrefix
                                )
                                if (resolved.isNotEmpty()) homeTab.navigateTo(resolved)
                            },
                            onOpenSpeedDial = { entry -> homeTab.navigateTo(entry.url) },
                            onSaveSpeedDial = { id, title, url, folderId ->
                                val saved = SpeedDials.save(context, id, title, url, folderId)
                                if (saved != null) showActionSnackbar("Speed dial saved")
                            },
                            onMoveSpeedDial = { id, folderId ->
                                SpeedDials.move(context, id, folderId)
                                showActionSnackbar("Speed dial moved")
                            },
                            onMoveSpeedDials = { ids, folderId ->
                                SpeedDials.moveMany(context, ids, folderId)
                                showActionSnackbar("${ids.size} speed dials moved")
                            },
                            onRemoveSpeedDial = { entry ->
                                SpeedDials.remove(context, entry.id)?.let { removed ->
                                    showActionSnackbar("Speed dial removed", "Undo") {
                                        SpeedDials.restoreRemoved(context, removed)
                                    }
                                }
                            },
                            onRemoveSpeedDials = { ids ->
                                val removed = SpeedDials.removeMany(context, ids)
                                if (removed.isNotEmpty()) {
                                    showActionSnackbar("${removed.size} speed dials removed", "Undo") {
                                        SpeedDials.restoreRemoved(context, removed)
                                    }
                                }
                            },
                            onSortSelected = { sort -> SpeedDials.setSort(context, sort) },
                            onAddFolder = { name ->
                                SpeedDials.addFolder(context, name)?.let {
                                    showActionSnackbar("Folder created")
                                }
                            },
                            onRenameFolder = { id, name ->
                                SpeedDials.renameFolder(context, id, name)
                                showActionSnackbar("Folder renamed")
                            },
                            onRemoveFolder = { folder ->
                                SpeedDials.removeFolder(context, folder.id)?.let {
                                    showActionSnackbar("Folder removed; its speed dials were moved home")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                AnimatedVisibility(
                    visible = activeScreen == null && activeTab?.isShowingHome == false &&
                        activeTab.showJumpToTop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp)
                        .zIndex(1f)
                ) {
                    SmallFloatingActionButton(
                        onClick = { activeTab?.jumpToTop() },
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Rounded.KeyboardArrowUp,
                            contentDescription = "Jump to top",
                            modifier = Modifier.size(28.dp)
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
            Screen.HUB -> Unit
            Screen.DETECTED -> if (activeTab != null) {
                DetectedMediaPage(
                    items = activeTab.detectedMedia,
                    onCopyUrl = { url ->
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", url)))
                            showActionSnackbar("URL copied", "Share") { shareUrl(url) }
                        }
                    },
                    onPlay = { item -> playMedia(item) },
                    onDownload = { item -> downloadMedia(item) },
                    scanProgress = activeTab.discoveryProgress,
                    onScan = { depth, pageLimit -> activeTab.startDiscovery(depth, pageLimit) },
                    onCancelScan = { activeTab.cancelDiscovery() },
                    minimumDurationSeconds = AppSettings.current.grabberMinimumDurationSeconds,
                    onBack = { activeScreen = null }
                )
            }
            Screen.TABS -> TabsPage(
                tabs = tabs,
                inactiveTabs = InactiveTabs.records,
                activeTabId = activeTabId,
                onSelectTab = { id ->
                    activeTabId = id
                    activeScreen = null
                },
                onCloseTab = { id -> closeTab(id) },
                onRestoreInactive = { record ->
                    val restored = createAndInsertTab(
                        initialUrl = record.url,
                        openerTabId = null,
                        privateTab = false,
                        initialTitle = record.title
                    )
                    InactiveTabs.take(context, record.id)
                    activeTabId = restored.id
                    activeScreen = null
                },
                onDeleteInactive = { record -> InactiveTabs.delete(context, record.id) },
                onNewTab = {
                    activeTabId = createAndInsertTab(configuredBrowserHomeTarget(), null,
                        privateTab = false).id
                    activeScreen = null
                },
                onNewPrivateTab = {
                    if (tabs.any(Tab::isPrivate)) {
                        activeTabId = createAndInsertTab(
                            configuredBrowserHomeTarget(), null, privateTab = true
                        ).id
                        activeScreen = null
                    } else {
                        showPrivateStartConfirmation = true
                    }
                },
                onBack = { activeScreen = null }
            )
            // DOWNLOADS is now an event into the Hub's Downloads tab, handled by the menu and
            // snackbar call sites. It remains in Screen while old saved UI state can still name
            // it, but it no longer renders a second, competing downloads page.
            Screen.DOWNLOADS -> Unit
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
            Screen.ADBLOCK -> AdBlockPage(
                enabled = AppSettings.current.adBlockEnabled,
                subscriptions = AdBlockFilters.subscriptions,
                manualRules = AdBlockFilters.manualRules,
                isRefreshing = AdBlockFilters.isRefreshing,
                onEnabledChange = { enabled ->
                    val previousEnabled = AppSettings.current.adBlockEnabled
                    AppSettings.update(context) { it.copy(adBlockEnabled = enabled) }
                    tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                    showActionSnackbar(
                        if (enabled) "Ad blocker enabled" else "Ad blocker disabled",
                        "Undo"
                    ) {
                        AppSettings.update(context) { it.copy(adBlockEnabled = previousEnabled) }
                        tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                    }
                },
                onAddSubscription = { name, url ->
                    AdBlockFilters.addSubscription(context, name, url) { message ->
                        tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                        showActionSnackbar(message)
                    }
                },
                onSubscriptionEnabledChange = { id, enabled ->
                    AdBlockFilters.setEnabled(context, id, enabled) {
                        tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                    }
                },
                onRefreshSubscription = { id ->
                    AdBlockFilters.refreshOne(context, id) { message ->
                        tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                        showActionSnackbar(message)
                    }
                },
                onRemoveSubscription = { id ->
                    val removed = AdBlockFilters.subscriptions.firstOrNull { it.id == id }
                    if (removed != null) {
                        AdBlockFilters.remove(context, id) {
                            tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                        }
                        // Removing the row and persisting its metadata are synchronous and fast;
                        // show Undo immediately. Rebuilding the full rule engine can take seconds
                        // with large lists and continues in the background via remove's callback.
                        showActionSnackbar(
                            message = "Filter list removed",
                            actionLabel = "Undo",
                            onAction = {
                                AdBlockFilters.restore(context, removed) {
                                    tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                                }
                            },
                            onDismiss = { AdBlockFilters.discardRemoved(context, removed.id) }
                        )
                    }
                },
                onRefreshAll = {
                    AdBlockFilters.refreshAll(context) { message ->
                        tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                        showActionSnackbar(message)
                    }
                },
                onSaveManualRules = { rules ->
                    val previousRules = AdBlockFilters.manualRules
                    AdBlockFilters.saveManualRules(context, rules) { message ->
                        tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                        showActionSnackbar(message, "Undo") {
                            AdBlockFilters.saveManualRules(context, previousRules) {
                                tabs.forEach { it.webView.applyCosmeticFiltersNow() }
                            }
                        }
                    }
                },
                onMessage = { message ->
                    showActionSnackbar(message)
                },
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
                onUpdate = { updated ->
                    val cleanupWasEnabled = AppSettings.current.autoDeleteInactiveTabs
                    AppSettings.update(context) { updated }
                    if (!cleanupWasEnabled && updated.autoDeleteInactiveTabs) {
                        InactiveTabs.prune(context, enabled = true)
                    }
                },
                onOpenBrowsingData = { activeScreen = Screen.CLEAR_DATA },
                onOpenSitePermissions = { activeScreen = Screen.SITE_PERMISSIONS },
                onBack = { activeScreen = null }
            )
            Screen.CLEAR_DATA -> BrowsingDataPage(
                onClear = { selection ->
                    BrowsingData.clear(context, tabs.toList(), selection) {
                        sitePermissionRevision++
                        showActionSnackbar("Selected browsing data cleared")
                    }
                },
                onBack = { activeScreen = Screen.SETTINGS }
            )
            Screen.SITE_PERMISSIONS -> SitePermissionsPage(
                decisions = remember(sitePermissionRevision) { SitePermissionStore.all(context) },
                onForget = { item ->
                    SitePermissionStore.set(context, item.origin, item.resource, SiteDecision.ASK)
                    sitePermissionRevision++
                },
                onBack = { activeScreen = Screen.SETTINGS }
            )
            null -> Unit
        }

        if (showingHub) {
            HubHomePage(
                selectedSection = hubSection,
                onSectionSelected = { hubSection = it },
                selectedDownloadSection = hubDownloadSection,
                onDownloadSectionSelected = { section ->
                    hubDownloadSection = section
                    HubSettingsStore.save(
                        context,
                        HubSettings(
                            librarySort = hubLibrarySort,
                            defaultDownloadSection = section
                        )
                    )
                },
                librarySort = hubLibrarySort,
                onLibrarySortSelected = { sort ->
                    hubLibrarySort = sort
                    HubSettingsStore.save(
                        context,
                        HubSettings(
                            librarySort = sort,
                            defaultDownloadSection = hubDownloadSection
                        )
                    )
                },
                libraryEntries = MediaLibrary.entries,
                onPlayLibraryEntry = { entry ->
                    playbackRequest = entry.toPlaybackRequest(
                        freshCookies = if (entry.sourceKind == LibrarySourceKind.REMOTE_STREAM) {
                            runCatching { CookieManager.getInstance().getCookie(entry.mediaUri) }.getOrNull()
                        } else {
                            null
                        },
                        fallbackUserAgent = activeTab?.webView?.settings?.userAgentString ?: "Miowser"
                    )
                },
                onRemoveLibraryEntry = { entry ->
                    MediaLibrary.remove(context, entry)
                    showActionSnackbar("Removed from Library", "Undo") {
                        MediaLibrary.restoreRemoved(context, listOf(entry))
                    }
                },
                onClearLibrary = {
                    val removed = MediaLibrary.entries.toList()
                    MediaLibrary.clear(context)
                    showActionSnackbar("Library cleared", "Undo") {
                        MediaLibrary.restoreRemoved(context, removed)
                    }
                },
                downloads = DownloadQueue.downloads,
                onPauseDownload = { download -> DownloadQueue.pause(context, download) },
                onResumeDownload = { download -> DownloadQueue.resume(context, download) },
                onCancelDownload = { download -> DownloadQueue.cancel(context, download) },
                onRetryDownload = { download -> DownloadQueue.retry(context, download) },
                onDeleteDownload = { download, deleteFile ->
                    DownloadQueue.delete(context, download, deleteFile)
                },
                onRefreshDownloadLink = { download ->
                    refreshTargetDownloadId = download.id
                    showingHub = false
                    openUrlInActiveTab(download.pageUrl)
                    showActionSnackbar("Choose the same video to refresh its link")
                },
                onOpenLocalDownload = { download ->
                    coroutineScope.launch {
                        val fileType = withContext(Dispatchers.IO) {
                            DownloadedFileInspector.inspect(context.applicationContext, download)
                        }
                        when (fileType) {
                            DownloadedFileType.MEDIA -> download.savedUri?.let { uri ->
                                playbackRequest = PlaybackRequest.Local(
                                    uri = uri,
                                    title = download.displayName,
                                    pageUrl = download.pageUrl.takeIf(String::isNotBlank),
                                    videoName = download.displayName
                                )
                            }
                            DownloadedFileType.APK -> when (DownloadedFileOpener.installApk(context, download)) {
                                FileOpenResult.INSTALL_PERMISSION_REQUIRED ->
                                    showActionSnackbar("Allow Miowser to install apps, then tap the APK again")
                                FileOpenResult.NO_HANDLER -> showActionSnackbar("No package installer is available")
                                FileOpenResult.FILE_UNAVAILABLE -> showActionSnackbar("Downloaded file is unavailable")
                                FileOpenResult.STARTED -> Unit
                            }
                            DownloadedFileType.OTHER -> when (DownloadedFileOpener.openWithSystem(context, download)) {
                                FileOpenResult.NO_HANDLER -> showActionSnackbar("No app can open this file type")
                                FileOpenResult.FILE_UNAVAILABLE -> showActionSnackbar("Downloaded file is unavailable")
                                else -> Unit
                            }
                        }
                    }
                },
                onOpenBrowser = { showingHub = false }
            )
        }

        if (showOverflowMenu) {
            OverflowMenuSheet(
                activeDownloadCount = DownloadQueue.downloads.count {
                    it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.MUXING
                },
                detectedCount = activeTab?.detectedMedia?.count {
                    it.meetsMinimumDuration(AppSettings.current.grabberMinimumDurationSeconds)
                } ?: 0,
                shareEnabled = activeTab?.isShowingHome == false && !activeTab.url.isBlank(),
                onShare = {
                    activeTab?.let { tab -> shareUrl(tab.url, tab.title) }
                },
                onSelect = { screen ->
                    showOverflowMenu = false
                    if (screen == Screen.HUB || screen == Screen.DOWNLOADS) {
                        hubSection = if (screen == Screen.DOWNLOADS) {
                            HubSection.DOWNLOADS
                        } else {
                            HubSection.LIBRARY
                        }
                        if (screen == Screen.DOWNLOADS) {
                            hubDownloadSection = HubDownloadSection.DOWNLOADING
                        }
                        activeScreen = null
                        showingHub = true
                    } else {
                        activeScreen = screen
                    }
                },
                onDismiss = { showOverflowMenu = false }
            )
        }

        playbackRequest?.let { request ->
            MiowserPlayerTheme {
                PlayerScreen(
                    request = request,
                    recordInLibrary = showingHub || activeTab?.isPrivate != true,
                    onOpenSourcePage = if (request is PlaybackRequest.Remote) ({
                        if (showingHub || activeTab?.isPrivate != true) {
                            val entry = MediaLibrary.entries.firstOrNull {
                                it.identityKey == request.toLibraryEntry().identityKey
                            }
                            FailedVideoLog.record(
                                context,
                                videoName = entry?.videoName ?: request.videoName ?: request.title,
                                pageTitle = entry?.pageTitle ?: request.pageTitle ?: request.title,
                                pageUrl = request.pageUrl
                            )
                            if (entry != null) MediaLibrary.remove(context, entry)
                        }
                        playbackRequest = null
                        activeScreen = null
                        showingHub = false
                        openUrlInActiveTab(request.pageUrl)
                    }) else null,
                    onDownload = when (request) {
                        is PlaybackRequest.Remote -> ({ downloadFromPlayer(request) })
                        is PlaybackRequest.Local -> null
                    },
                    onDismiss = { playbackRequest = null }
                )
            }
        }

        pendingExternalUrl?.let { url ->
            AlertDialog(
                onDismissRequest = { pendingExternalUrl = null },
                title = { Text("Open another app?") },
                text = { Text("This link will leave Miowser: ${Uri.parse(url).scheme ?: "unknown"}") },
                confirmButton = {
                    TextButton(onClick = {
                        pendingExternalUrl = null
                        val intent = ExternalNavigation.safeIntent(url)
                        if (intent == null || runCatching { context.startActivity(intent) }.isFailure) {
                            showActionSnackbar("No app can open this link")
                        }
                    }) { Text("Open") }
                },
                dismissButton = { TextButton(onClick = { pendingExternalUrl = null }) { Text("Cancel") } }
            )
        }

        if (showPrivateStartConfirmation) {
            AlertDialog(
                onDismissRequest = { showPrivateStartConfirmation = false },
                title = { Text("Start private browsing?") },
                text = { Text("Private tabs do not save browser history or player Library entries. This mode shares WebView storage with regular tabs, so starting and closing the private session clears cookies and website storage for all tabs. Downloads you start are saved normally.") },
                confirmButton = {
                    TextButton(onClick = {
                        showPrivateStartConfirmation = false
                        val openPrivateTab = {
                            activeTabId = createAndInsertTab(configuredBrowserHomeTarget(), null, privateTab = true).id
                            activeScreen = null
                            showingHub = false
                        }
                        if (tabs.any(Tab::isPrivate)) {
                            openPrivateTab()
                        } else {
                            PrivateSessionStore.setActive(context, true)
                            BrowsingData.clearSharedPrivateSessionData(context, tabs.toList()) {
                                openPrivateTab()
                            }
                        }
                    }) { Text("Continue") }
                },
                dismissButton = { TextButton(onClick = { showPrivateStartConfirmation = false }) { Text("Cancel") } }
            )
        }

        unsupportedSitePermission?.let { message ->
            AlertDialog(
                onDismissRequest = { unsupportedSitePermission = null },
                title = { Text("Permission blocked") },
                text = { Text(message) },
                confirmButton = {
                    TextButton(onClick = { unsupportedSitePermission = null }) { Text("OK") }
                }
            )
        }

        pendingWebPermission?.let { request ->
            val origin = request.origin.toString()
            val resources = request.resources.toList()
            AlertDialog(
                onDismissRequest = { request.deny(); pendingWebPermission = null },
                title = { Text("Site permission") },
                text = {
                    Column {
                        Text("$origin wants to use ${resources.joinToString { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "microphone" }}.")
                        if (!pendingWebPermissionIsPrivate) TextButton(onClick = {
                            pendingWebPermission = null
                            val missing = runtimePermissionsFor(request).orEmpty().filter {
                                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                            }
                            if (missing.isEmpty()) {
                                resources.forEach { SitePermissionStore.set(context, origin, it, SiteDecision.ALLOW) }
                                request.grant(request.resources)
                            } else {
                                pendingRuntimePermission = request
                                rememberRuntimePermission = true
                                webPermissionLauncher.launch(missing.toTypedArray())
                            }
                        }) { Text("Always allow for this site") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingWebPermission = null
                        val missing = runtimePermissionsFor(request).orEmpty().filter {
                            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                        }
                        if (missing.isEmpty()) request.grant(request.resources)
                        else {
                            pendingRuntimePermission = request
                            rememberRuntimePermission = false
                            webPermissionLauncher.launch(missing.toTypedArray())
                        }
                    }) { Text("Allow once") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (!pendingWebPermissionIsPrivate) {
                            resources.forEach { SitePermissionStore.set(context, origin, it, SiteDecision.BLOCK) }
                        }
                        request.deny()
                        pendingWebPermission = null
                    }) { Text("Block") }
                }
            )
        }

        pendingGeolocation?.let { pending ->
            AlertDialog(
                onDismissRequest = {
                    pending.callback.invoke(pending.origin, false, false)
                    pendingGeolocation = null
                },
                title = { Text("Location request") },
                text = {
                    Column {
                        Text("${pending.origin} wants to use your approximate location.")
                        if (!pendingGeolocationIsPrivate) TextButton(onClick = {
                            pendingGeolocation = null
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                SitePermissionStore.set(context, pending.origin, "geolocation", SiteDecision.ALLOW)
                                pending.callback.invoke(pending.origin, true, false)
                            } else {
                                pendingRuntimeGeolocation = pending
                                rememberRuntimeGeolocation = true
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                        }) { Text("Always allow for this site") }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        pendingGeolocation = null
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            pending.callback.invoke(pending.origin, true, false)
                        } else {
                            pendingRuntimeGeolocation = pending
                            rememberRuntimeGeolocation = false
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    }) { Text("Allow once") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        if (!pendingGeolocationIsPrivate) {
                            SitePermissionStore.set(context, pending.origin, "geolocation", SiteDecision.BLOCK)
                        }
                        pending.callback.invoke(pending.origin, false, false)
                        pendingGeolocation = null
                    }) { Text("Block") }
                }
            )
        }

        pendingNewTabRequest?.let { pending ->
            AlertDialog(
                onDismissRequest = {
                    // Tapping outside/system-back also counts as declining — resultMsg is simply
                    // never completed either way; see completeNewWindow's own comment for why
                    // that's a safe way to block a popup rather than something that leaves
                    // WebView hanging.
                    pendingNewTabRequest = null
                    showActionSnackbar("Popup blocked", "Allow") {
                        completeNewWindow(pending.openerTabId, pending.resultMsg)
                    }
                },
                title = { Text("Open new tab?") },
                text = { Text("This page wants to open a new tab.") },
                confirmButton = {
                    TextButton(onClick = {
                        completeNewWindow(pending.openerTabId, pending.resultMsg)
                        pendingNewTabRequest = null
                    }) { Text("Allow") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        pendingNewTabRequest = null
                        showActionSnackbar("Popup blocked", "Allow") {
                            completeNewWindow(pending.openerTabId, pending.resultMsg)
                        }
                    }) { Text("Block") }
                }
            )
        }

        pendingDownloadRefresh?.let { pending ->
            val original = DownloadQueue.downloads.firstOrNull { it.id == pending.originalId }
            AlertDialog(
                onDismissRequest = { pendingDownloadRefresh = null },
                title = { Text("Refresh download link?") },
                text = {
                    Text(
                        "Use this newly captured link for the existing download, or start it as a separate download?"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (original != null) {
                            DownloadQueue.replaceAndRestart(
                                context,
                                original,
                                pending.candidate.copyForQueue(id = original.id),
                                deleteExistingFile = original.status == DownloadStatus.COMPLETED
                            )
                            hubSection = HubSection.DOWNLOADS
                            hubDownloadSection = HubDownloadSection.DOWNLOADING
                            activeScreen = null
                            showingHub = true
                            showActionSnackbar("Link refreshed")
                        }
                        pendingDownloadRefresh = null
                    }) { Text("Refresh existing") }
                },
                dismissButton = {
                    TextButton(onClick = {
                        DownloadQueue.enqueue(
                            context,
                            renamedDownloadCopy(pending.candidate, DownloadQueue.downloads)
                        )
                        hubSection = HubSection.DOWNLOADS
                        hubDownloadSection = HubDownloadSection.DOWNLOADING
                        activeScreen = null
                        showingHub = true
                        pendingDownloadRefresh = null
                        showActionSnackbar("New download started")
                    }) { Text("Start new") }
                }
            )
        }

        pendingDownloadConflict?.let { pending ->
            val existing = DownloadQueue.downloads.firstOrNull { it.id == pending.existingId }
            if (existing == null) {
                pendingDownloadConflict = null
            } else {
                AlertDialog(
                    onDismissRequest = { pendingDownloadConflict = null },
                    title = {
                        Text(
                            if (pending.reason == DownloadConflictReason.SAME_SOURCE) {
                                if (existing.status == DownloadStatus.COMPLETED) {
                                    "This file has already been downloaded"
                                } else {
                                    "This download already exists"
                                }
                            } else {
                                "A file with this name already exists"
                            }
                        )
                    },
                    text = {
                        Text(
                            if (pending.reason == DownloadConflictReason.SAME_SOURCE) {
                                "Choose whether to refresh its link, replace the existing download, or save another renamed copy."
                            } else {
                                "Start another download with an automatic renamed filename?"
                            }
                        )
                    },
                    confirmButton = {
                        androidx.compose.foundation.layout.Column(
                            horizontalAlignment = Alignment.End
                        ) {
                            if (pending.reason == DownloadConflictReason.SAME_SOURCE) {
                                TextButton(onClick = {
                                    refreshTargetDownloadId = existing.id
                                    pendingDownloadConflict = null
                                    playbackRequest = null
                                    showingHub = false
                                    openUrlInActiveTab(existing.pageUrl.ifBlank { existing.sourceUrl })
                                    showActionSnackbar("Choose the same file to refresh its link")
                                }) { Text("Refresh link") }
                                TextButton(onClick = {
                                    DownloadQueue.replaceAndRestart(
                                        context,
                                        existing,
                                        pending.candidate.copyForQueue(id = existing.id),
                                        deleteExistingFile = existing.status == DownloadStatus.COMPLETED
                                    )
                                    pendingDownloadConflict = null
                                    hubSection = HubSection.DOWNLOADS
                                    hubDownloadSection = HubDownloadSection.DOWNLOADING
                                    playbackRequest = null
                                    showingHub = true
                                    showActionSnackbar("Existing download replaced")
                                }) {
                                    Text(
                                        if (existing.status == DownloadStatus.COMPLETED) {
                                            "Replace downloaded file"
                                        } else {
                                            "Replace current"
                                        }
                                    )
                                }
                            }
                            TextButton(onClick = {
                                val renamed = renamedDownloadCopy(
                                    pending.candidate,
                                    DownloadQueue.downloads
                                )
                                DownloadQueue.enqueue(context, renamed)
                                pendingDownloadConflict = null
                                showActionSnackbar("Renamed download started")
                            }) { Text("New renamed copy") }
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingDownloadConflict = null }) { Text("Cancel") }
                    }
                )
            }
        }

        pendingLinkMenuUrl?.let { url ->
            LinkContextMenu(
                url = url,
                onOpenInNewTab = {
                    activeTabId = createAndInsertTab(url, activeTabId,
                        privateTab = activeTab?.isPrivate == true).id
                },
                onOpenInBackgroundTab = {
                    val backgroundTab = createAndInsertTab(url, activeTabId,
                        privateTab = activeTab?.isPrivate == true)
                    showActionSnackbar("Opened in a background tab", "Switch") {
                        activateTab(backgroundTab.id)
                    }
                },
                onCopyLink = {
                    coroutineScope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", url)))
                        showActionSnackbar("URL copied", "Share") { shareUrl(url) }
                    }
                },
                onShareLink = { shareUrl(url) },
                onAddToSpeedDial = {
                    val saved = SpeedDials.save(context, null, defaultSpeedDialTitle(url), url)
                    showActionSnackbar(
                        if (saved != null) "Added to speed dial" else "Could not add speed dial"
                    )
                },
                onDismiss = { pendingLinkMenuUrl = null }
            )
        }

        MiowserSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottomOffset)
        )
    }

    // Switching tabs should close a Detected Media page showing the previous tab's results
    // rather than silently keep showing stale ones. (In practice hard to trigger — TabsPage is
    // itself the only way to switch tabs now, and picking a tab there already sends
    // activeScreen back to null — but harmless to keep as a belt-and-suspenders guard.)
    LaunchedEffect(activeTabId) {
        tabs.firstOrNull { it.id == activeTabId }?.markActive()
        lastThumbnailTabId?.takeIf { it != activeTabId }
            ?.let { previousId -> tabs.firstOrNull { it.id == previousId }?.markActive() }
        lastThumbnailTabId = activeTabId
        if (activeScreen == Screen.DETECTED) activeScreen = null
    }

    // Clearing History does not navigate or reload the WebViews kept alive behind our pages.
    // Returning to one of those already-finished tabs is a real visit, even though WebView has
    // no new onPageFinished callback to report it. The tab itself guards private/home/error and
    // in-flight pages, and only inserts a record if this URL is currently absent from History.
    LaunchedEffect(activeTabId, activeScreen, showingHub, playbackRequest, activeTab?.lastFinishedUrl, activeTab?.url,
        activeTab?.pageError) {
        if (!showingHub && activeScreen == null && playbackRequest == null) {
            activeTab?.recordVisibleVisitIfMissing()
        }
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

    // Rich playback owns audio/video while it is open. Pause the DOM media first (before
    // WebView.onPause can suspend JavaScript), then pause the WebView itself. Returning from the
    // player resumes the WebView engine but leaves the page's media element paused so two players
    // cannot immediately begin speaking at once.
    LaunchedEffect(activeTabId, showingHub, playbackRequest) {
        tabs.forEach { tab ->
            val shouldRunBrowser = !showingHub && playbackRequest == null && tab.id == activeTabId
            if (shouldRunBrowser) {
                tab.webView.onResume()
            } else {
                if (playbackRequest != null && tab.id == activeTabId) tab.pausePageMedia()
                tab.webView.onPause()
            }
        }
    }
    LaunchedEffect(AppSettings.current.javaScriptEnabled, AppSettings.current.cookiesEnabled,
        AppSettings.current.popupsEnabled) {
        CookieManager.getInstance().setAcceptCookie(AppSettings.current.cookiesEnabled)
        tabs.forEach { it.applyBrowserPreferences(AppSettings.current) }
    }
    DisposableEffect(lifecycleOwner, showingHub, playbackRequest) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (!showingHub && playbackRequest == null) {
                        tabs.firstOrNull { it.id == activeTabId }?.webView?.onResume()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    tabs.firstOrNull { it.id == activeTabId }?.markActive()
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
