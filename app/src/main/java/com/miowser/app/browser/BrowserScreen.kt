package com.miowser.app.browser

import com.miowser.app.ui.theme.LocalMotionPolicy
import com.miowser.app.ui.theme.MotionTokens
import com.miowser.app.BuildConfig
import android.content.Intent
import android.Manifest
import android.app.Activity
import android.print.PrintAttributes
import android.print.PrintManager
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Message
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.widget.FrameLayout
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebView
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.SslErrorHandler
import android.net.http.SslError
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miowser.app.adblock.AdBlock
import com.miowser.app.adblock.AdBlockFilters
import com.miowser.app.adblock.SiteProtection
import com.miowser.app.bookmarks.BookmarkList
import com.miowser.app.browser.video.VideoAssistantButtonPolicy
import com.miowser.app.browser.video.NativeVideoAssistantDisplay
import com.miowser.app.browser.video.releaseVideoAssistantHost
import com.miowser.app.browser.video.BrowserVideoPlayerOverlay
import com.miowser.app.browser.video.BrowserVideoSurface
import com.miowser.app.browser.video.keepBrowserRendererForAudioOnly
import com.miowser.app.browser.video.keepBrowserRendererForPip
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
import com.miowser.app.library.isLibraryEntryAvailable
import com.miowser.app.library.LibraryEntry
import com.miowser.app.library.FailedVideoLog
import com.miowser.app.library.LibrarySourceKind
import com.miowser.app.player.PlaybackRequest
import com.miowser.app.player.LibraryPlaybackQueue
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
import com.miowser.app.ui.CrashDiagnosticsPage
import com.miowser.app.ui.HistoryPage
import com.miowser.app.ui.LinkContextMenu
import com.miowser.app.ui.MiowserSnackbarHost
import com.miowser.app.ui.MiowserSnackbarVisuals
import com.miowser.app.ui.OverflowMenuSheet
import com.miowser.app.ui.Screen
import com.miowser.app.ui.SettingsPage
import com.miowser.app.ui.SitePermissionsPage
import com.miowser.app.ui.ConfirmationAction
import com.miowser.app.ui.ConfirmationTone
import com.miowser.app.ui.MiowserConfirmationDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first

// Anything that isn't safe/sensible in a filename, replaced with "_"; matches the common
// filesystem-reserved set (Windows is the strictest, so satisfying it covers everything else).
private val UNSAFE_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")

private data class WebFullscreenVideo(
    val tabId: Long,
    val view: View,
    val callback: WebChromeClient.CustomViewCallback
)

/**
 * A page-triggered new-tab request (target="_blank"/window.open()) held pending a confirmation
 * dialog — see BrowserScreen's handleNewWindowRequested. resultMsg is what actually completes the
 * popup once a decision is made; never completing it (an implicit part of declining) is a
 * deliberate, safe way to block one — see completeNewWindow's own comment.
 */
private data class PendingNewTabRequest(val openerTabId: Long, val resultMsg: Message)
private data class PendingHttpNavigation(val tabId: Long, val url: String)
private data class PendingCertificateError(
    val tabId: Long, val url: String, val handler: SslErrorHandler, val error: SslError
)
private class ClosedTabUndo(val tab: Tab, val index: Int, val wasActive: Boolean) {
    var replacementTabId: Long? = null
    var resolved = false
}

private tailrec fun Context.findBrowserActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findBrowserActivity()
    else -> null
}
private data class PendingDownloadRefresh(
    val originalId: Long,
    val candidate: Download,
    val playAfterSaved: Boolean = false
)
private data class PendingDownloadConflict(
    val existingId: Long,
    val candidate: Download,
    val reason: DownloadConflictReason,
    val playAfterSaved: Boolean = false
)
private data class PendingBlobPlayback(val downloadId: Long, val tabId: Long)
private data class PressedLink(val url: String, val text: String?)
private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** The debug network permission is only relevant to explicitly entered local server addresses. */
internal fun isLocalServerAddress(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host?.lowercase() }.getOrNull() ?: return false
    if (host == "localhost" || host.endsWith(".local")) return true
    val ipv6 = host.removePrefix("[").removeSuffix("]")
    if (ipv6 == "::1" || ipv6.startsWith("fc") || ipv6.startsWith("fd") ||
        ipv6.startsWith("fe80:")) return true
    val parts = host.split('.').mapNotNull(String::toIntOrNull)
    if (parts.size != 4 || parts.any { it !in 0..255 }) return false
    return parts[0] == 10 || parts[0] == 127 ||
        (parts[0] == 192 && parts[1] == 168) ||
        (parts[0] == 172 && parts[1] in 16..31) ||
        (parts[0] == 169 && parts[1] == 254) ||
        (parts[0] == 100 && parts[1] in 64..127)
}
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
        AppSettings.current.searchUrlTemplate
    ).ifBlank { BROWSER_HOME_URL }
}

/**
 * Browser shell: an [AddressBar] driving whichever tab is active, over a Box that keeps every
 * open tab's WebView alive simultaneously (see the tab loop below) and just toggles visibility —
 * switching tabs never destroys one. Tab switching itself lives entirely in [TabsPage] now
 * (opened from BottomBar's Tabs button); there's no always-visible top tab strip anymore.
 */
@Composable
fun BrowserScreen(
    modifier: Modifier = Modifier,
    initialHubSettings: HubSettings = HubSettings(),
    tabController: TabController = remember { TabController() },
    shortcutRequest: com.miowser.app.LauncherShortcutRequest? = null,
    onShortcutHandled: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var videoButtonForeground by remember(lifecycleOwner) {
        mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED))
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val motion = LocalMotionPolicy.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    // The overflow (hamburger) menu is the only thing left that's a transient sheet rather than
    // a full page — it's a picker, not a destination itself. Everything it (or AddressBar's
    // badge, or BottomBar's own buttons) launches is a Screen, rendered as an opaque overlay
    // below rather than replacing the Scaffold outright (see the comment at that render site).
    var showOverflowMenu by remember { mutableStateOf(false) }
    var confirmClearCookiesAndSiteData by remember { mutableStateOf(false) }
    var findOwnerId by remember { mutableStateOf<Long?>(null) }
    var findQuery by remember { mutableStateOf("") }
    var findMatchIndex by remember { mutableIntStateOf(0) }
    var findMatchCount by remember { mutableIntStateOf(0) }
    var showBrowserDownloads by remember { mutableStateOf(false) }
    var showBackupRestore by remember { mutableStateOf(false) }
    var activeScreen by remember { mutableStateOf<Screen?>(null) }
    var openingTabs by remember { mutableStateOf(false) }
    var showingHub by rememberSaveable { mutableStateOf(true) }
    var hubSection by rememberSaveable { mutableStateOf(HubSection.LIBRARY) }
    var hubDownloadSection by rememberSaveable { mutableStateOf(initialHubSettings.defaultDownloadSection) }
    var hubLibrarySort by rememberSaveable { mutableStateOf(initialHubSettings.librarySort) }
    var playbackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var fullscreenVideo by remember { mutableStateOf<WebFullscreenVideo?>(null) }
    var browserVideoAudioOnly by remember { mutableStateOf(false) }
    val backgroundPlaybackEnabled = AppSettings.current.backgroundPlaybackEnabled
    LaunchedEffect(showingHub, hubSection, playbackRequest != null, fullscreenVideo != null, activeScreen) {
        val area = when {
            playbackRequest != null -> "rich_player"
            fullscreenVideo != null -> "web_player"
            activeScreen != null -> "page_${activeScreen!!.name.lowercase()}"
            showingHub -> "hub_${hubSection.name.lowercase()}"
            else -> "browser"
        }
        com.miowser.app.diagnostics.CrashDiagnostics.setActiveArea(area)
    }
    fun exitWebFullscreen() {
        val current = fullscreenVideo ?: return
        tabController.tabs.firstOrNull { it.id == current.tabId }
            ?.browserVideoPlayer?.setAudioOnly(false)
        browserVideoAudioOnly = false
        fullscreenVideo = null
        (context.findBrowserActivity() as? com.miowser.app.MainActivity)?.let { activity ->
            activity.browserVideoPipOwner?.takeIf { it.id.tabId == current.tabId }
                ?.let(activity::releaseBrowserVideoPictureInPicture)
        }
        tabController.tabs.firstOrNull { it.id == current.tabId }?.finishBrowserVideoFullscreen()
        runCatching { (current.view.parent as? ViewGroup)?.removeView(current.view) }
        runCatching { current.callback.onCustomViewHidden() }
    }
    // A page-triggered new tab awaiting the user's confirmation (see handleNewWindowRequested) —
    // null the overwhelming majority of the time, since most of Settings.newTabConfirmation's
    // options resolve immediately without ever needing this at all.
    var pendingNewTabRequest by remember { mutableStateOf<PendingNewTabRequest?>(null) }
    var pendingHttpNavigation by remember { mutableStateOf<PendingHttpNavigation?>(null) }
    var pendingCertificateError by remember { mutableStateOf<PendingCertificateError?>(null) }
    var showSiteInfoTabId by remember { mutableStateOf<Long?>(null) }
    var showZoomTabId by remember { mutableStateOf<Long?>(null) }
    var showTextSizeTabId by remember { mutableStateOf<Long?>(null) }
    var popupRoutingFailureTabId by remember { mutableStateOf<Long?>(null) }
    var pendingClosedTab by remember { mutableStateOf<ClosedTabUndo?>(null) }
    var refreshTargetDownloadId by remember { mutableStateOf<Long?>(null) }
    var pendingDownloadRefresh by remember { mutableStateOf<PendingDownloadRefresh?>(null) }
    var pendingDownloadConflict by remember { mutableStateOf<PendingDownloadConflict?>(null) }
    var pendingBlobPlayback by remember { mutableStateOf<PendingBlobPlayback?>(null) }
    var pendingBlobAction by remember { mutableStateOf<PendingBlobAction?>(null) }
    // The long-pressed anchor's URL and selectable text; null when its menu is closed.
    var pendingLinkMenu by remember { mutableStateOf<PressedLink?>(null) }
    var pendingExternalUrl by remember { mutableStateOf<String?>(null) }
    val fileSelection = remember(tabController) { BrowserFileSelection() }
    var pendingRelinkDownload by remember { mutableStateOf<Download?>(null) }
    var pendingRelinkLibraryEntry by remember { mutableStateOf<LibraryEntry?>(null) }
    var libraryPlaybackQueue by remember { mutableStateOf<LibraryPlaybackQueue?>(null) }
    var libraryPlayRequestId by remember { mutableIntStateOf(0) }
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
    val pendingDownloadDeletes = remember { mutableStateListOf<Long>() }
    val waitingForStorage = remember { ArrayDeque<() -> Unit>() }
    var storageRequestActive by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) coroutineScope.launch {
            if (snackbarHostState.showSnackbar("Downloads work, but notifications are off",
                    actionLabel = "Settings") == SnackbarResult.ActionPerformed) {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        storageRequestActive = false
        val queued = waitingForStorage.toList()
        waitingForStorage.clear()
        if (granted) queued.forEach { it() }
        else coroutineScope.launch {
            snackbarHostState.showSnackbar("Storage access is needed to save downloads. Tap Download to retry.")
        }
    }
    fun requestNotificationForDownload() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED) return
        val prompts = context.getSharedPreferences("permission_prompts", Context.MODE_PRIVATE)
        if (!prompts.getBoolean("notifications_asked", false)) {
            prompts.edit().putBoolean("notifications_asked", true).apply()
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    fun ensureDownloadPermission(retry: () -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
                PackageManager.PERMISSION_GRANTED) {
            waitingForStorage.addLast(retry)
            if (!storageRequestActive) {
                storageRequestActive = true
                storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            return false
        }
        requestNotificationForDownload()
        return true
    }
    var bottomBarHeightPx by remember { mutableIntStateOf(0) }
    var hubBottomBarHeightPx by remember { mutableIntStateOf(0) }
    var homeSearchFocusRequest by remember { mutableIntStateOf(0) }
    var addressBarFocusRequest by remember { mutableIntStateOf(0) }
    val snackbarBottomOffset = with(density) {
        (bottomBarHeightPx + if (showingHub && activeScreen == null && playbackRequest == null &&
            !showBackupRestore) hubBottomBarHeightPx else 0).toDp()
    } + 8.dp

    val tabs = tabController.tabs
    var pendingLocalNavigation by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val pending = pendingLocalNavigation
        pendingLocalNavigation = null
        if (granted && pending != null) {
            tabs.firstOrNull { it.id == pending.first }?.navigateTo(pending.second)
        } else if (!granted) coroutineScope.launch {
            snackbarHostState.showSnackbar("Local network access denied. Open the address again to retry.")
        }
    }
    fun requiresLocalNetworkGrant(url: String): Boolean =
        BuildConfig.DEBUG && Build.VERSION.SDK_INT >= 37 && isLocalServerAddress(url) &&
            ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) !=
                PackageManager.PERMISSION_GRANTED
    fun navigateTab(tab: Tab, url: String) {
        if (requiresLocalNetworkGrant(url)) {
            val alreadyRequesting = pendingLocalNavigation != null
            pendingLocalNavigation = tab.id to url
            if (!alreadyRequesting) localNetworkPermissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        } else tab.navigateTo(url)
    }
    val rendererTransferActivity by remember(tabs) {
        derivedStateOf { tabs.map { it.id to it.rendererTransferCount } }
    }
    DisposableEffect(tabController) { onDispose {
        exitWebFullscreen()
        fileSelection.cancel()
        pendingCertificateError?.handler?.cancel()
        pendingClosedTab?.let { pending ->
            if (!pending.resolved) {
                pending.resolved = true
                pending.tab.deleteThumbnail()
                pending.tab.destroy()
            }
        }
        tabController.dispose()
    } }
    var activeTabId by remember { mutableStateOf<Long?>(null) }

    fun showWebFullscreen(ownerId: Long, view: View, callback: WebChromeClient.CustomViewCallback) {
        if (ownerId == activeTabId && !showingHub && activeScreen == null && playbackRequest == null) {
            exitWebFullscreen()
            fullscreenVideo = WebFullscreenVideo(ownerId, view, callback)
            tabController.tabs.firstOrNull { it.id == ownerId }?.startBrowserVideoFullscreen()
        } else {
            runCatching { callback.onCustomViewHidden() }
        }
    }

    fun hideWebFullscreen(ownerId: Long) {
        if (fullscreenVideo?.tabId == ownerId) exitWebFullscreen()
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        fileSelection.complete(result.resultCode, result.data, context.packageName)
    }
    val relinkDownloadLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val download = pendingRelinkDownload
        val libraryEntry = pendingRelinkLibraryEntry
        pendingRelinkDownload = null
        pendingRelinkLibraryEntry = null
        if (uri != null && (download != null || libraryEntry != null)) coroutineScope.launch {
            val readable = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } == true }
                    .getOrDefault(false)
            }
            if (readable) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    if (download != null) DownloadQueue.relinkCompleted(context, download, uri)
                    else if (libraryEntry != null) MediaLibrary.relinkLocal(context, libraryEntry.mediaUri, uri.toString())
                }.onSuccess { snackbarHostState.showSnackbar("Downloaded file reconnected") }
                    .onFailure { snackbarHostState.showSnackbar("Could not reconnect this file") }
            } else snackbarHostState.showSnackbar("This file could not be opened")
        }
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
    lateinit var onTabClosedHandler: (ClosedTabUndo) -> Unit

    fun handleFileChooser(ownerTabId: Long, callback: ValueCallback<Array<Uri>>, params: WebChromeClient.FileChooserParams) {
        if (!fileSelection.start(ownerTabId, callback)) return
        try {
            fileChooserLauncher.launch(params.createIntent())
        } catch (_: Exception) {
            fileSelection.launchFailed(ownerTabId)
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

    fun handleRendererUnavailable(ownerId: Long) {
        if (fullscreenVideo?.tabId == ownerId) exitWebFullscreen()
        if (pendingNewTabRequest?.openerTabId == ownerId) pendingNewTabRequest = null
        if (pendingHttpNavigation?.tabId == ownerId) pendingHttpNavigation = null
        if (pendingCertificateError?.tabId == ownerId) {
            pendingCertificateError?.handler?.cancel()
            pendingCertificateError = null
        }
        fileSelection.cancelFor(ownerId)
        cancelGeolocation(ownerId)
        if (pendingBlobPlayback?.tabId == ownerId) pendingBlobPlayback = null
        if (pendingBlobAction?.tabId == ownerId) pendingBlobAction = null
        if (activeTabId == ownerId) pendingLinkMenu = null
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
        val deferLocalUrl = requiresLocalNetworkGrant(initialUrl)
        val tab = Tab(
            id = tabId,
            context = context,
            controller = tabController,
            initialUrl = if (deferLocalUrl) "" else initialUrl,
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
            onInsecureNavigationRequested = { ownerId, url ->
                pendingHttpNavigation = PendingHttpNavigation(ownerId, url)
            },
            onCertificateErrorRequested = { ownerId, url, handler, error ->
                pendingCertificateError?.handler?.cancel()
                pendingCertificateError = PendingCertificateError(ownerId, url, handler, error)
            },
            onPopupRoutingFailed = { ownerId -> popupRoutingFailureTabId = ownerId },
            onLinkLongPressed = { url, text -> pendingLinkMenu = PressedLink(url, text) },
            onDownloadRequested = { request -> onWebDownloadRequestedHandler(request) },
            onFileChooserRequested = { callback, params -> handleFileChooser(tabId, callback, params) },
            onFullscreenVideoRequested = ::showWebFullscreen,
            onFullscreenVideoHidden = ::hideWebFullscreen,
            onWebPermissionRequested = { request -> handleWebPermission(request, privateTab) },
            onWebPermissionCanceled = ::cancelWebPermission,
            onGeolocationRequested = { origin, callback -> handleGeolocation(origin, callback, privateTab, tabId) },
            onGeolocationCanceled = { cancelGeolocation(tabId) },
            onExternalNavigation = { url -> pendingExternalUrl = url },
            onRendererUnavailable = ::handleRendererUnavailable
        )
        if (openerTabId != null) {
            val openerIndex = tabs.indexOfFirst { it.id == openerTabId }
            tabs.add(if (openerIndex == -1) tabs.size else openerIndex + 1, tab)
        } else {
            tabs.add(tab)
        }
        if (deferLocalUrl) navigateTab(tab, initialUrl)
        return tab
    }

    fun finalizeClosedTab(pending: ClosedTabUndo) {
        if (pending.resolved) return
        pending.resolved = true
        if (pendingClosedTab === pending) pendingClosedTab = null
        pending.tab.deleteThumbnail()
        pending.tab.destroy()
        if (pending.tab.isPrivate && tabs.none(Tab::isPrivate)) {
            HttpNavigationApproval.clearPrivate()
            BrowsingData.clearSharedPrivateSessionData(context, tabs.toList()) {
                if (tabs.none(Tab::isPrivate)) {
                    val saved = PrivateSessionStore.saveActive(context, false)
                    coroutineScope.launch {
                        runCatching { withContext(Dispatchers.IO) {
                            check(saved.get()) { "Could not save private-session cleanup" }
                        } }.onFailure {
                            android.widget.Toast.makeText(context, it.message ?: "Could not save private-session cleanup",
                                android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    fun undoClosedTab(pending: ClosedTabUndo) {
        if (pending.resolved) return
        pending.resolved = true
        if (pendingClosedTab === pending) pendingClosedTab = null
        val replacementId = pending.replacementTabId
        if (replacementId != null && tabs.size == 1 && tabs[0].id == replacementId &&
            tabs[0].isShowingHome) {
            val replacement = tabs.removeAt(0)
            replacement.deleteThumbnail()
            replacement.destroy()
        }
        tabs.add(pending.index.coerceIn(0, tabs.size), pending.tab)
        if (pending.wasActive) activeTabId = pending.tab.id
    }

    // Used by TabsPage's close button and swipe gesture, and by child-tab back navigation.
    fun closeTab(id: Long, offerUndo: Boolean = true) {
        if (fullscreenVideo?.tabId == id) exitWebFullscreen()
        pendingClosedTab?.let(::finalizeClosedTab)
        val index = tabs.indexOfFirst { it.id == id }
        if (index != -1) {
            val wasActive = activeTabId == id
            val closed = tabs.removeAt(index)
            if (pendingCertificateError?.tabId == id) {
                pendingCertificateError?.handler?.cancel()
                pendingCertificateError = null
            }
            fileSelection.cancelFor(id)
            cancelGeolocation(id)
            closed.liveWebView?.onPause()
            val pending = ClosedTabUndo(closed, index, wasActive)
            if (tabs.isEmpty()) {
                val replacement = createAndInsertTab(configuredBrowserHomeTarget(), null)
                pending.replacementTabId = replacement.id
                activeTabId = replacement.id
            } else if (wasActive) {
                // Prefer returning to the tab that opened this one (a real page-triggered
                // child); manually-opened tabs have no opener, so fall back to whichever tab
                // is now immediately before this one's old position — "the previous tab in order".
                val opener = closed.openerTabId?.let { openerId -> tabs.firstOrNull { it.id == openerId } }
                activeTabId = opener?.id ?: tabs[(index - 1).coerceIn(0, tabs.size - 1)].id
            }
            if (offerUndo) {
                pendingClosedTab = pending
                onTabClosedHandler(pending)
            } else {
                finalizeClosedTab(pending)
            }
        }
    }

    fun archiveEligibleTabs(now: Long = System.currentTimeMillis()) {
        InactiveTabs.prune(context, AppSettings.current.inactiveTabRetention, now)
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
        val opener = tabs.firstOrNull { it.id == openerTabId } ?: return
        if (opener.rendererUnavailable || opener.liveWebView == null) return
        val child = createAndInsertTab("", openerTabId,
            privateTab = tabs.firstOrNull { it.id == openerTabId }?.isPrivate == true)
        val transport = resultMsg.obj as WebView.WebViewTransport
        transport.webView = child.ensureWebView()
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

    var recordsReady by remember { mutableStateOf(false) }
    var recordError by remember { mutableStateOf(false) }
    var recordRetry by remember { mutableIntStateOf(0) }
    var startupReady by remember { mutableStateOf(false) }
    var browserStartupError by remember { mutableStateOf(false) }
    val filterRevision by AdBlock.engine.revision.collectAsState()
    var remindRefreshWhenFiltersReady by remember { mutableStateOf(false) }
    val defaultFilters = AdBlockFilters.subscriptions.filter { it.isBuiltIn }
    val defaultFiltersReady = defaultFilters.size == 3 && defaultFilters.all {
        it.networkRuleCount + it.cosmeticRuleCount > 0
    } && AdBlock.engine.isFullyLoaded
    var browserRetry by remember { mutableIntStateOf(0) }
    var browserRequested by remember { mutableStateOf(!showingHub) }
    var libraryIdle by remember { mutableStateOf(false) }
    var pendingStartupUrl by remember { mutableStateOf<String?>(null) }
    // Keep the load running if Back returns to Hub; never publish a partial restored session.
    LaunchedEffect(showingHub) { if (!showingHub) browserRequested = true }
    LaunchedEffect(showingHub) {
        if (!showingHub && AppSettings.current.adBlockEnabled && !defaultFiltersReady)
            remindRefreshWhenFiltersReady = true
    }
    // Only after a rendered, settled Library; stop issuing stages when it is hidden/busy.
    LaunchedEffect(libraryIdle, recordsReady, showingHub, hubSection, playbackRequest,
        activeScreen, showBackupRestore, browserRequested, lifecycleOwner) {
        if (!libraryIdle || !recordsReady || !showingHub || hubSection != HubSection.LIBRARY ||
            playbackRequest != null || activeScreen != null || showBackupRestore || browserRequested) return@LaunchedEffect
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(1_500)
            com.miowser.app.startup.BrowserPreparation.warmAfterLibrary(context.applicationContext)
            kotlinx.coroutines.awaitCancellation()
        }
    }
    LaunchedEffect(recordRetry) {
        if (recordsReady) return@LaunchedEffect
        recordError = false
        try {
            if (tabs.isNotEmpty()) {
                recordsReady = true
                return@LaunchedEffect
            }
            val saved = withContext(Dispatchers.IO) { if (AppSettings.current.restoreTabsOnStartup) TabSessionStore.load(context) else null }
            if (saved == null) {
                TabThumbnailCache.retainOnly(context, emptySet())
                activeTabId = createAndInsertTab(configuredBrowserHomeTarget(), null).id
                recordsReady = true
                return@LaunchedEffect
            }
            val (savedTabs, activeIndex) = saved
            val restoredIds = savedTabs.map { Tab.nextId() }
            val openerIds = restoredOpenerIds(savedTabs, restoredIds)
            for ((index, savedTab) in savedTabs.withIndex()) {
                val openerId = openerIds[index]
                val restoredTabId = restoredIds[index]
                val tab = Tab(
                    id = restoredTabId,
                    context = context,
                    controller = tabController,
                    initialUrl = savedTab.url,
                    openerTabId = openerId,
                    isPrivate = false,
                    thumbnailKey = savedTab.thumbnailKey.ifBlank {
                        "tab-$restoredTabId-${System.currentTimeMillis()}"
                    },
                    initialTitle = savedTab.title,
                    initialLastActiveAt = savedTab.lastActiveAt.takeIf { it > 0L }
                        ?: System.currentTimeMillis(),
                    initialDesktopMode = savedTab.isDesktopMode,
                    canCapturePreview = {
                        activeTabId == restoredTabId && activeScreen == null && !showingHub && playbackRequest == null
                    },
                    onNewWindowRequested = { childOpenerId, isUserGesture, resultMsg ->
                        handleNewWindowRequested(childOpenerId, isUserGesture, resultMsg)
                    },
                    onInsecureNavigationRequested = { ownerId, url ->
                        pendingHttpNavigation = PendingHttpNavigation(ownerId, url)
                    },
                    onCertificateErrorRequested = { ownerId, url, handler, error ->
                        pendingCertificateError?.handler?.cancel()
                        pendingCertificateError = PendingCertificateError(ownerId, url, handler, error)
                    },
                    onPopupRoutingFailed = { ownerId -> popupRoutingFailureTabId = ownerId },
                    onLinkLongPressed = { url, text -> pendingLinkMenu = PressedLink(url, text) },
                    onDownloadRequested = { request -> onWebDownloadRequestedHandler(request) },
                    onFileChooserRequested = { callback, params -> handleFileChooser(restoredTabId, callback, params) },
                    onFullscreenVideoRequested = ::showWebFullscreen,
                    onFullscreenVideoHidden = ::hideWebFullscreen,
                    onWebPermissionRequested = { request -> handleWebPermission(request, false) },
                    onWebPermissionCanceled = ::cancelWebPermission,
                    onGeolocationRequested = { origin, callback -> handleGeolocation(origin, callback, false, restoredTabId) },
                    onGeolocationCanceled = { cancelGeolocation(restoredTabId) },
                    onExternalNavigation = { url -> pendingExternalUrl = url },
                    onRendererUnavailable = ::handleRendererUnavailable
                )
                tabs.add(tab)
            }
            val retainedKeys = tabs.map(Tab::thumbnailKey).toSet()
            TabThumbnailCache.retainOnly(context, retainedKeys)
            activeTabId = tabs.getOrNull(activeIndex)?.id ?: tabs.firstOrNull()?.id
            recordsReady = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) {
            tabs.forEach { it.destroy() }
            tabs.clear()
            activeTabId = null
            recordError = true
        }
    }
    LaunchedEffect(browserRequested, browserRetry, recordsReady) {
        if (!browserRequested || !recordsReady || startupReady) return@LaunchedEffect
        browserStartupError = false
        try {
            com.miowser.app.startup.BrowserPreparation.ensureSavedData(context)
            com.miowser.app.startup.AppStartup.ensurePrivacy(context)
            archiveEligibleTabs()
            startupReady = true
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (_: Exception) { browserStartupError = true }
    }

    LaunchedEffect(shortcutRequest?.sequence, recordsReady) {
        val request = shortcutRequest ?: return@LaunchedEffect
        if (!recordsReady) return@LaunchedEffect
        when (request.action) {
            com.miowser.app.LauncherShortcuts.NEW_TAB ->
                activeTabId = createAndInsertTab(configuredBrowserHomeTarget(), null).id
            Intent.ACTION_VIEW -> request.url?.let { url ->
                activeTabId = createAndInsertTab(url, null).id
            }
        }
        activeScreen = null
        playbackRequest = null
        showOverflowMenu = false
        showingHub = false
        onShortcutHandled()
    }

    // New generations affect request interception immediately; also refresh CSS on already-open pages.
    LaunchedEffect(Unit) {
        com.miowser.app.adblock.AdBlock.engine.revision.collect { generation ->
            if (generation > 0) tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
        }
    }

    // Save meaningful session edits while foregrounded, not only when Android pauses us.
    LaunchedEffect(recordsReady) {
        if (recordsReady) androidx.compose.runtime.snapshotFlow {
            TabSessionStore.snapshot(tabs, activeTabId)
        }.collect { snapshot -> TabSessionStore.saveSnapshot(context, snapshot) }
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }
    LaunchedEffect(filterRevision, defaultFiltersReady, showingHub, startupReady, activeTab?.isShowingHome) {
        if (remindRefreshWhenFiltersReady && filterRevision > 0 && defaultFiltersReady &&
            !showingHub && startupReady && activeTab?.isShowingHome == false) {
            remindRefreshWhenFiltersReady = false
            Toast.makeText(context, "Filter lists are ready. Refresh this tab for full protection.", Toast.LENGTH_LONG).show()
        }
    }
    val findView = activeTab?.liveWebView.takeIf { activeTabId == findOwnerId }
    DisposableEffect(findOwnerId, findView) {
        val view = findView
        if (view != null) {
            view.setFindListener { activeIndex, total, _ ->
                findMatchIndex = if (total > 0) activeIndex + 1 else 0
                findMatchCount = total
            }
        }
        onDispose {
            view?.let { runCatching { it.clearMatches(); it.setFindListener(null) } }
        }
    }
    LaunchedEffect(findOwnerId, findQuery, findView) {
        val view = findView ?: return@LaunchedEffect
        if (findQuery.isBlank()) {
            findMatchIndex = 0
            findMatchCount = 0
            view.clearMatches()
        } else {
            delay(120)
            view.findAllAsync(findQuery)
        }
    }
    LaunchedEffect(activeTabId, showingHub, activeScreen, playbackRequest) {
        if (findOwnerId != null &&
            (activeTabId != findOwnerId || showingHub || activeScreen != null || playbackRequest != null)) {
            findOwnerId = null
        }
    }
    SiteProtection.revision // Recalculate media counts when this site's override changes.
    val minimumMediaLengthSeconds = SiteProtection.mediaMinimumFor(
        activeTab?.url, AppSettings.current.grabberMinimumDurationSeconds
    )
    var lastThumbnailTabId by remember { mutableStateOf<Long?>(null) }

    fun showActionSnackbar(
        message: String,
        actionLabel: String = "Dismiss",
        onDismiss: () -> Unit = {},
        secondaryActionLabel: String? = null,
        onSecondaryAction: (() -> Unit)? = null,
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
                        withDismissAction = actionLabel != "Dismiss" && secondaryActionLabel == null,
                        duration = SnackbarDuration.Long,
                        onAction = onAction,
                        secondaryActionLabel = secondaryActionLabel,
                        onSecondaryAction = onSecondaryAction
                    )
                )
                handled = true
                if (result == SnackbarResult.Dismissed) onDismiss()
            } finally {
                if (!handled) onDismiss()
            }
        }
    }

    fun showSavedSnackbar(
        message: String,
        actionLabel: String = "Dismiss",
        secondaryActionLabel: String? = null,
        onSecondaryAction: (() -> Unit)? = null,
        onAction: () -> Unit = {}
    ) {
        com.miowser.app.persistence.Persistence.afterWrites {
            showActionSnackbar(message, actionLabel, secondaryActionLabel = secondaryActionLabel,
                onSecondaryAction = onSecondaryAction, onAction = onAction)
        }
    }

    fun deleteDownloadWithUndo(download: Download, deleteFile: Boolean) {
        if (download.id in pendingDownloadDeletes) return
        val wasActive = download.status == DownloadStatus.QUEUED ||
            download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.MUXING
        if (wasActive) DownloadQueue.pause(context, download)
        pendingDownloadDeletes.add(download.id)
        var undone = false
        showActionSnackbar(
            if (deleteFile) "Download file removed" else "Download record removed", "Undo",
            onDismiss = {
                pendingDownloadDeletes.remove(download.id)
                if (!undone) DownloadQueue.delete(context, download, deleteFile)
            },
            onAction = {
                undone = true
                pendingDownloadDeletes.remove(download.id)
                if (wasActive && download.status == DownloadStatus.PAUSED) DownloadQueue.resume(context, download)
            }
        )
    }

    onTabClosedHandler = { pending ->
        showActionSnackbar("Tab closed", "Undo",
            onDismiss = { finalizeClosedTab(pending) },
            onAction = { undoClosedTab(pending) })
    }

    LaunchedEffect(popupRoutingFailureTabId) {
        popupRoutingFailureTabId?.let { tabId ->
            popupRoutingFailureTabId = null
            showActionSnackbar("This site opened a window without a usable web address", "Site settings") {
                showSiteInfoTabId = tabId
            }
        }
    }

    fun enqueueOrOfferRefresh(
        candidate: Download,
        playAfterSaved: Boolean = false,
        streamRequest: PlaybackRequest.Remote? = null,
        originatingTabId: Long? = null
    ) {
        if (!ensureDownloadPermission {
                enqueueOrOfferRefresh(candidate, playAfterSaved, streamRequest, originatingTabId)
            }) return
        val targetId = refreshTargetDownloadId
        val target = targetId?.let { id -> DownloadQueue.downloads.firstOrNull { it.id == id } }
        if (target != null) {
            pendingDownloadRefresh = PendingDownloadRefresh(target.id, candidate, playAfterSaved)
            refreshTargetDownloadId = null
        } else {
            refreshTargetDownloadId = null
            val conflict = findDownloadConflict(DownloadQueue.downloads, candidate)
            if (conflict != null) {
                pendingDownloadConflict = PendingDownloadConflict(
                    existingId = conflict.existing.id,
                    candidate = candidate,
                    reason = conflict.reason,
                    playAfterSaved = playAfterSaved
                )
            } else {
                DownloadQueue.enqueue(context, candidate)
                if (playAfterSaved) pendingBlobPlayback = PendingBlobPlayback(candidate.id, activeTabId ?: -1L)
                showSavedSnackbar(
                    if (playAfterSaved) "Saving blob to play" else "Download started",
                    "View",
                    secondaryActionLabel = streamRequest?.let { "Stream" },
                    onSecondaryAction = streamRequest?.let { remote -> {
                        // The alternative action replaces this download, including any saved
                        // output or partial recovery data, before opening the same remote link.
                        DownloadQueue.delete(context, candidate, deleteFile = true)
                        tabs.firstOrNull { it.id == originatingTabId }?.let { tab ->
                            tab.pausePageMedia()
                            tab.liveWebView?.onPause()
                            activeTabId = tab.id
                        }
                        libraryPlayRequestId++
                        libraryPlaybackQueue = null
                        playbackRequest = remote
                        Toast.makeText(context,
                            "Not downloading. Tap Download in the player to save it.",
                            Toast.LENGTH_LONG).show()
                    } }
                ) {
                    showBrowserDownloads = true
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
        val streamRequest = if (rendererKind == null) {
            streamableDownloadType(request, suggestedName)?.let { type ->
                PlaybackRequest.Remote(
                    url = request.url,
                    pageUrl = request.pageUrl,
                    userAgent = request.userAgent,
                    cookies = CookieManager.getInstance().getCookie(request.url),
                    mediaType = type,
                    title = suggestedName,
                    sourceLabel = Uri.parse(request.pageUrl).host?.removePrefix("www.") ?: "Web stream",
                    pageTitle = request.pageTitle,
                    videoName = suggestedName,
                    requestReferer = request.pageUrl
                )
            }
        } else null
        enqueueOrOfferRefresh(download, streamRequest = streamRequest,
            originatingTabId = request.originatingTabId)
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
    fun downloadMedia(item: DetectedMedia, playAfterSaved: Boolean = false) {
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
            userAgent = tab.liveWebView?.settings?.userAgentString ?: "Miowser",
            referer = item.requestReferer ?: item.pageUrl,
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
        enqueueOrOfferRefresh(download, playAfterSaved)
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
            referer = request.requestReferer ?: request.pageUrl,
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
    fun detectedPlaybackRequest(item: DetectedMedia, tab: Tab): PlaybackRequest.Remote {
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
        return PlaybackRequest.Remote(
            url = item.url,
            pageUrl = item.pageUrl,
            userAgent = tab.liveWebView?.settings?.userAgentString ?: "Miowser",
            cookies = CookieManager.getInstance().getCookie(item.url),
            mediaType = item.type,
            // Media3 cannot sideload separate text tracks onto a multi-period DASH source.
            externalSubtitles = if (item.type == MediaType.DASH) emptyList()
                else com.miowser.app.player.detectedSubtitleTracks(item, tab.detectedMedia),
            requestReferer = item.requestReferer,
            requestOrigin = item.requestOrigin,
            title = title,
            sourceLabel = sourceLabel,
            pageTitle = item.pageTitle?.takeIf { it.isNotBlank() && it != "New Tab" } ?: tab.title,
            videoName = item.responseFileName?.takeIf(String::isNotBlank)
                ?: item.url.substringBefore('?').substringBefore('#').substringAfterLast('/')
                    .takeIf(String::isNotBlank) ?: title,
            posterUrl = item.posterUrl,
            posterCookies = item.posterUrl?.let { CookieManager.getInstance().getCookie(it) },
            hlsRenditions = item.hlsRenditions
        )
    }

    fun playMedia(item: DetectedMedia) {
        val tab = activeTab ?: return
        // Stop the page immediately, before Compose has rendered the player overlay.
        tab.pausePageMedia()
        tab.liveWebView?.onPause()
        libraryPlayRequestId++
        libraryPlaybackQueue = null
        playbackRequest = detectedPlaybackRequest(item, tab)
    }

    suspend fun prepareLibraryPlayback(entry: LibraryEntry): PlaybackRequest {
        if (entry.sourceKind == LibrarySourceKind.LOCAL_FILE && !withContext(Dispatchers.IO) {
                isLibraryEntryAvailable(context.applicationContext, entry)
            }) throw IllegalStateException("File unavailable")
        if (entry.sourceKind == LibrarySourceKind.REMOTE_STREAM) {
            com.miowser.app.startup.AppStartup.ensurePrivacy(context)
        }
        val prepared = entry.toPlaybackRequest(
            freshCookies = if (entry.sourceKind == LibrarySourceKind.REMOTE_STREAM) {
                runCatching { CookieManager.getInstance().getCookie(entry.mediaUri) }.getOrNull()
            } else null,
            fallbackUserAgent = activeTab?.liveWebView?.settings?.userAgentString ?: "Miowser"
        )
        val pageTab = activeTab?.takeIf { tab ->
            tab.url == entry.pageUrl && entry.sourceKind == LibrarySourceKind.REMOTE_STREAM
        }
        return if (prepared is PlaybackRequest.Remote && pageTab != null) {
            val video = pageTab.detectedMedia.firstOrNull { it.url == prepared.url }
            if (video == null) prepared else prepared.copy(
                externalSubtitles = (prepared.externalSubtitles +
                    com.miowser.app.player.detectedSubtitleTracks(video, pageTab.detectedMedia))
                    .distinctBy { it.url }
            )
        } else prepared
    }

    fun playLibraryEntry(entry: LibraryEntry, queue: LibraryPlaybackQueue? = null) {
        val requestId = ++libraryPlayRequestId
        coroutineScope.launch {
            try {
                val prepared = prepareLibraryPlayback(entry)
                if (requestId != libraryPlayRequestId) return@launch
                if (queue != null) libraryPlaybackQueue = queue
                playbackRequest = prepared
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (failure: Exception) {
                if (requestId != libraryPlayRequestId) return@launch
                showActionSnackbar(if (failure.message == "File unavailable")
                    "This Library file is unavailable" else "Couldn't prepare playback. Please try again.")
            }
        }
    }

    fun playLibraryNeighbor(currentKey: String, direction: Int) {
        val queue = libraryPlaybackQueue ?: return
        val requestId = ++libraryPlayRequestId
        coroutineScope.launch {
            var candidate = queue.neighbor(currentKey, MediaLibrary.entries, direction)
            while (candidate != null) {
                val entry = candidate
                if (entry.sourceKind != LibrarySourceKind.LOCAL_FILE || withContext(Dispatchers.IO) {
                        isLibraryEntryAvailable(context.applicationContext, entry)
                    }) {
                    try {
                        val prepared = prepareLibraryPlayback(entry)
                        if (requestId == libraryPlayRequestId) playbackRequest = prepared
                    } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                    catch (_: Exception) {
                        if (requestId == libraryPlayRequestId) {
                            showActionSnackbar("Couldn't prepare playback. Please try again.")
                        }
                    }
                    return@launch
                }
                if (requestId != libraryPlayRequestId) return@launch
                candidate = queue.neighbor(entry.identityKey, MediaLibrary.entries, direction)
            }
            if (requestId == libraryPlayRequestId) {
                showActionSnackbar("No more available videos in this playlist")
            }
        }
    }

    fun addMediaToLibrary(item: DetectedMedia) {
        val tab = activeTab ?: return
        if (tab.isPrivate) return
        val durationMs = item.durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
            ?.let { (it * 1000).toLong() } ?: 0L
        val entry = detectedPlaybackRequest(item, tab).toLibraryEntry(durationMs = durationMs)
        val result = runCatching { MediaLibrary.add(context, entry) }
        showActionSnackbar(result.fold(
            onSuccess = { if (it) "Added to library" else "Already in library" },
            onFailure = { it.message ?: "Could not add video" }
        ))
    }

    // Used by History/Bookmarks pages to open a saved URL: reuses the active tab if there is
    // one, rather than always opening a new tab, matching how tapping the address bar and
    // navigating normally behaves.
    fun openUrlInActiveTab(url: String) {
        val tab = activeTab
        if (tab != null) {
            // Explicit, defense-in-depth clear — see Tab.clearDetections' doc comment for why
            // WebView's own onPageStarted-triggered clear alone isn't always enough.
            navigateTab(tab, url)
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
        pendingLinkMenu = null
        activeTabId = id
        tabs.forEach { tab ->
            if (tab.id == id) {
                tab.liveWebView?.onResume()
                tab.liveWebView?.requestFocus()
            } else {
                tab.liveWebView?.onPause()
            }
        }
    }

    // The one canonical "go back"/"go forward" — both the on-screen BottomBar buttons and the
    // hardware/gesture back button (BackHandler below) call these, so the two can never disagree
    // about what "back" does. The canGoBack()/canGoForward() checks are the real, current WebView
    // state, not the possibly-one-frame-stale Compose-tracked copy (Tab.canGoBack/canGoForward) —
    // belt-and-suspenders against ever trying to navigate a direction that isn't actually there.
    fun goBack() {
        activeTab?.liveWebView?.let { if (it.canGoBack()) it.goBack() }
    }
    fun goForward() {
        activeTab?.liveWebView?.let { if (it.canGoForward()) it.goForward() }
    }
    fun goHome() {
        activeTab?.let { tab ->
            val target = configuredBrowserHomeTarget()
            if (!(isBrowserHomeUrl(target) && tab.isShowingHome)) {
                navigateTab(tab, target)
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
            BrowserBackAction.CLOSE_PLAYER -> {
                libraryPlayRequestId++
                playbackRequest = null
                libraryPlaybackQueue = null
            }
            BrowserBackAction.CLOSE_INTERNAL_PAGE -> activeScreen =
                if (activeScreen == Screen.CLEAR_DATA || activeScreen == Screen.SITE_PERMISSIONS ||
                    activeScreen == Screen.CRASH_DIAGNOSTICS) Screen.SETTINGS else null
            BrowserBackAction.WEB_HISTORY -> goBack()
            BrowserBackAction.CLOSE_CHILD_TAB -> activeTab?.let { closeTab(it.id) }
            BrowserBackAction.SHOW_HUB -> showingHub = true
            BrowserBackAction.SHOW_HUB_LIBRARY -> hubSection = HubSection.LIBRARY
            BrowserBackAction.EXIT_APP -> Unit
        }
    }

    LaunchedEffect(startupReady, pendingStartupUrl) {
        if (startupReady) pendingStartupUrl?.let { url ->
            openUrlInActiveTab(url)
            pendingStartupUrl = null
        }
    }

    BackHandler(enabled = !showingHub && !startupReady) { showingHub = true }
    BackHandler(enabled = showBackupRestore) { showBackupRestore = false }

    BackHandler(enabled = fullscreenVideo != null) { exitWebFullscreen() }

    LaunchedEffect(fullscreenVideo, activeTabId, activeScreen, showingHub, playbackRequest) {
        val fullscreen = fullscreenVideo ?: return@LaunchedEffect
        if (fullscreen.tabId != activeTabId || activeScreen != null || showingHub || playbackRequest != null) {
            exitWebFullscreen()
        }
    }

    val fullscreenActivity = remember(context) { context.findBrowserActivity() }
    DisposableEffect(fullscreenVideo, fullscreenActivity) {
        val controller = if (fullscreenVideo != null) {
            fullscreenActivity?.window?.let { window ->
                WindowCompat.getInsetsController(window, window.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        } else null
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Everything visual lives inside this one Box so SnackbarHost (added at the very end) can sit
    // structurally above the browser Scaffold, every full-page Screen overlay, the overflow menu,
    // and the player alike — a bubble triggered from any of them (Copy URL on Detected Media,
    // Download-started from the same page, Bookmarked from the browser's own AddressBar) needs to
    // actually be visible regardless of which of those happens to be on top at the time.
    Box(modifier = Modifier.fillMaxSize()) {
        if (startupReady || !showingHub) Scaffold(
            modifier = modifier,
            topBar = {
                // Home already owns the page's primary search/address field. Keeping the browser
                // omnibox above it duplicates the same action and pushes the reachable Home UI down.
                if (activeTab != null && !activeTab.isShowingHome) {
                    AddressBar(
                        tabId = activeTab.id,
                        value = activeTab.addressBarText,
                        onValueChange = { activeTab.addressBarText = it },
                        onFocusChanged = { focused -> activeTab.isAddressBarFocused = focused },
                        onSearchSelection = { selected ->
                            val query = AppSettings.current.searchUrl(selected)
                            keyboardController?.hide()
                            val newTab = createAndInsertTab(query, activeTab.id, privateTab = activeTab.isPrivate)
                            activateTab(newTab.id)
                        },
                        onSubmit = {
                            val resolved = UrlOrSearchResolver.resolve(
                                activeTab.addressBarText.text,
                                AppSettings.current.searchUrlTemplate
                            )
                            if (resolved.isNotEmpty()) {
                                navigateTab(activeTab, resolved)
                            }
                            keyboardController?.hide()
                            // Not focusManager.clearFocus(): loadUrl() makes the WebView request
                            // Android View focus for itself as a side effect of navigating, which
                            // was racing clearFocus() and winning. Direct focus to the WebView
                            // instead so the two cooperate rather than race.
                            activeTab.liveWebView?.requestFocus()
                        },
                        onReload = { activeTab.reload() },
                        onStop = {
                            activeTab.liveWebView?.stopLoading()
                            activeTab.isLoading = false
                        },
                        isLoading = activeTab.isLoading,
                        searchEngine = AppSettings.current.searchEngine,
                        suggestionsEnabled = activeScreen == null && playbackRequest == null &&
                            !showOverflowMenu && !showBrowserDownloads && !showBackupRestore &&
                            showSiteInfoTabId == null,
                        onSuggestionSelected = { suggestion ->
                            val resolved = if (suggestion.kind == BrowserSuggestionKind.SEARCH)
                                AppSettings.current.searchUrl(suggestion.value)
                            else suggestion.value
                            if (resolved.isNotEmpty()) navigateTab(activeTab, resolved)
                            keyboardController?.hide()
                            activeTab.liveWebView?.requestFocus()
                        },
                        detectedCount = activeTab.detectedMedia.count {
                            it.meetsMinimumDuration(minimumMediaLengthSeconds)
                        },
                        onOpenDetected = { activeScreen = Screen.DETECTED },
                        isBookmarked = activeTab.url.isNotBlank() && !activeTab.isShowingHome &&
                            BookmarkList.isBookmarked(activeTab.url),
                        isPrivate = activeTab.isPrivate,
                        siteSecurity = siteSecurity(
                            activeTab.url,
                            activeTab.lastFinishedUrl.takeIf { activeTab.pageError == null },
                            activeTab.certificateErrorUrl,
                            activeTab.certificateBypassed
                        ),
                        onOpenSiteInfo = { showSiteInfoTabId = activeTab.id },
                        onToggleBookmark = {
                            if (activeTab.url.isNotBlank() && !activeTab.isShowingHome) {
                                val added = BookmarkList.toggle(context, activeTab.url, activeTab.title)
                                // Only on add, not remove — a confirmation for taking something away
                                // isn't useful the way one for adding it is, and the star's own
                                // color+fill change already signals "removed" clearly enough.
                                if (added) {
                                    val bookmarkedUrl = activeTab.url
                                    val bookmarkedTitle = activeTab.title
                                    showSavedSnackbar("Bookmarked", "Undo") {
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
                        onTabsLongClick = {
                            val newTab = createAndInsertTab(
                                configuredBrowserHomeTarget(),
                                openerTabId = null,
                                privateTab = activeTab?.isPrivate == true
                            )
                            activeTabId = newTab.id
                        },
                        activeDownloadCount = DownloadQueue.downloads.count {
                            it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
                                it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.MUXING
                        },
                        onMenu = { showOverflowMenu = true },
                        ready = startupReady
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Only acquired engines have AndroidView hosts. Ordinary switching keeps those
                // hosts composed and changes visibility. The controller owns destruction;
                // onRelease only detaches so a temporary host removal cannot destroy a warm page.
                val pullToRefreshTint = MaterialTheme.colorScheme.primary.toArgb()
                tabs.forEach { tab ->
                    val engine = tab.liveWebView ?: return@forEach
                    key(tab.id, engine) {
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
                                    val content = FrameLayout(ctx)
                                    val display = NativeVideoAssistantDisplay(ctx)
                                    content.addView(engine, FrameLayout.LayoutParams(-1, -1))
                                    content.addView(display, FrameLayout.LayoutParams(-1, -1))
                                    addView(
                                        content,
                                        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                                    )
                                    tab.attachVideoAssistantDisplay(engine, display)
                                    setColorSchemeColors(pullToRefreshTint)
                                    setOnChildScrollUpCallback { _, _ ->
                                        display.ownsGesture || (tab.liveWebView === engine && engine.canScrollVertically(-1))
                                    }
                                    setOnRefreshListener {
                                        isRefreshing = true
                                        tab.reload()
                                    }
                                }
                            },
                            update = { swipeRefresh ->
                                swipeRefresh.visibility = if (
                                    tab.id == activeTabId && !tab.isShowingHome
                                ) View.VISIBLE else View.INVISIBLE
                                swipeRefresh.isRefreshing = isRefreshing
                            },
                            onRelease = { host ->
                                releaseVideoAssistantHost(host, tab::detachVideoAssistantDisplay)
                            }
                        )
                    }
                }
                activeTab?.pageError?.let { message ->
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.fillMaxSize()
                                .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(if (activeTab.blockedPageInfo == null) "Page unavailable" else "Page blocked",
                                style = MaterialTheme.typography.headlineSmall)
                            Text(message, modifier = Modifier.padding(top = 12.dp, bottom = 20.dp))
                            activeTab.blockedPageInfo?.let { BlockedPageDetails(it) }
                            Button(onClick = { activeTab.reload() }) {
                                Text(if (activeTab.rendererUnavailable) "Reload" else "Try again")
                            }
                        }
                    }
                }
                if (!startupReady && (recordError || browserStartupError)) {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Browser could not open", style = MaterialTheme.typography.headlineSmall)
                            Button(onClick = { if (!recordsReady) recordRetry++ else browserRetry++ },
                                modifier = Modifier.padding(top = 20.dp)) {
                                Text("Retry")
                            }
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
                            searchEngine = AppSettings.current.searchEngine,
                            suggestionsEnabled = !showingHub && activeScreen == null && playbackRequest == null &&
                                !showOverflowMenu && !showBrowserDownloads && !showBackupRestore,
                            isPrivate = homeTab.isPrivate,
                            onSearch = { input ->
                                val resolved = UrlOrSearchResolver.resolve(
                                    input,
                                    AppSettings.current.searchUrlTemplate
                                )
                                if (resolved.isNotEmpty()) navigateTab(homeTab, resolved)
                            },
                            onSearchSuggestion = { suggestion ->
                                navigateTab(homeTab, AppSettings.current.searchUrl(suggestion))
                            },
                            onOpenSpeedDial = { entry -> navigateTab(homeTab, entry.url) },
                            onSaveSpeedDial = { id, title, url, folderId ->
                                if (SpeedDials.duplicateFor(url, id) != null) {
                                    showActionSnackbar("A speed dial for this domain already exists", "Add anyway") {
                                        SpeedDials.save(context, id, title, url, folderId, allowDuplicate = true)
                                            ?.let { showSavedSnackbar("Speed dial saved") }
                                    }
                                } else {
                                    SpeedDials.save(context, id, title, url, folderId)
                                        ?.let { showSavedSnackbar("Speed dial saved") }
                                }
                            },
                            onMoveSpeedDial = { id, folderId ->
                                SpeedDials.move(context, id, folderId)
                                showSavedSnackbar("Speed dial moved")
                            },
                            onMoveSpeedDials = { ids, folderId ->
                                SpeedDials.moveMany(context, ids, folderId)
                                showSavedSnackbar("${ids.size} speed dials moved")
                            },
                            onRemoveSpeedDial = { entry ->
                                SpeedDials.remove(context, entry.id)?.let { removed ->
                                    showSavedSnackbar("Speed dial removed", "Undo") {
                                        SpeedDials.restoreRemoved(context, removed)
                                    }
                                }
                            },
                            onRemoveSpeedDials = { ids ->
                                val removed = SpeedDials.removeMany(context, ids)
                                if (removed.isNotEmpty()) {
                                    showSavedSnackbar("${removed.size} speed dials removed", "Undo") {
                                        SpeedDials.restoreRemoved(context, removed)
                                    }
                                }
                            },
                            onSortSelected = { sort -> SpeedDials.setSort(context, sort) },
                            onAddFolder = { name ->
                                SpeedDials.addFolder(context, name)?.let {
                                    showSavedSnackbar("Folder created")
                                }
                            },
                            onRenameFolder = { id, name ->
                                SpeedDials.renameFolder(context, id, name)
                                showSavedSnackbar("Folder renamed")
                            },
                            onRemoveFolder = { folder ->
                                SpeedDials.removeFolder(context, folder.id)?.let {
                                    showSavedSnackbar("Folder removed; its speed dials were moved home")
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                AnimatedVisibility(
                    visible = activeScreen == null && activeTab?.isShowingHome == false &&
                        activeTab.showJumpToTop,
                    enter = if (motion.reduced) fadeIn(motion.spec(MotionTokens.feedbackEnter)) else
                        fadeIn(motion.spec(MotionTokens.feedbackEnter)) +
                        slideInVertically(motion.spec(MotionTokens.feedbackEnter)) {
                            with(density) { MotionTokens.compactTravel.roundToPx() }
                        } + scaleIn(motion.spec(MotionTokens.feedbackEnter), initialScale = MotionTokens.pressedScale),
                    exit = if (motion.reduced) fadeOut(motion.spec(MotionTokens.feedbackExit)) else
                        fadeOut(motion.spec(MotionTokens.feedbackExit)) +
                        slideOutVertically(motion.spec(MotionTokens.feedbackExit)) {
                            with(density) { MotionTokens.compactTravel.roundToPx() }
                        } + scaleOut(motion.spec(MotionTokens.feedbackExit), targetScale = MotionTokens.pressedScale),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(BrowserFloatingActionLayout.EDGE_DP.dp)
                        .zIndex(1f)
                ) {
                    SmallFloatingActionButton(
                        onClick = { activeTab?.jumpToTop() },
                        modifier = Modifier.size(BrowserFloatingActionLayout.JUMP_SIZE_DP.dp).testTag("browser_jump_to_top")
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
        // Acquired WebViews stay hosted behind full pages; the controller retains their engines
        // even if a host is temporarily detached. Each SimplePageScaffold has an opaque background,
        // so it fully covers the
        // browser UI beneath rather than actually needing it hidden any other way.
        when (activeScreen) {
            Screen.HUB -> Unit
            Screen.DETECTED -> if (activeTab != null) {
                DetectedMediaPage(
                    items = activeTab.detectedMedia,
                    userAgent = activeTab.liveWebView?.settings?.userAgentString ?: "Miowser",
                    onCopyUrl = { url ->
                        coroutineScope.launch {
                            clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", url)))
                            showActionSnackbar("URL copied", "Share") { shareUrl(url) }
                        }
                    },
                    onPlay = { item ->
                        if (rendererResourceKind(item.url) == com.miowser.app.downloader.RendererResourceKind.BLOB) {
                            pendingBlobAction = PendingBlobAction(activeTab.id, item, BlobActionKind.PLAY)
                        } else playMedia(item)
                    },
                    onAddToLibrary = { item ->
                        if (rendererResourceKind(item.url) == com.miowser.app.downloader.RendererResourceKind.BLOB) {
                            pendingBlobAction = PendingBlobAction(activeTab.id, item, BlobActionKind.ADD_TO_LIBRARY)
                        } else addMediaToLibrary(item)
                    },
                    savedMediaUrls = MediaLibrary.entries.mapTo(mutableSetOf()) { it.mediaUri },
                    canSaveToLibrary = !activeTab.isPrivate,
                    onDownload = { item ->
                        if (rendererResourceKind(item.url) == com.miowser.app.downloader.RendererResourceKind.BLOB) {
                            pendingBlobAction = PendingBlobAction(activeTab.id, item, BlobActionKind.DOWNLOAD)
                        } else downloadMedia(item)
                    },
                    scanProgress = activeTab.discoveryProgress,
                    onScan = { depth, pageLimit -> activeTab.startDiscovery(depth, pageLimit) },
                    onCancelScan = { activeTab.cancelDiscovery() },
                    minimumDurationSeconds = minimumMediaLengthSeconds,
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
                onCloseTabs = { ids, exitBrowser ->
                    tabs.map { it.id }.filter(ids::contains).forEach { closeTab(it, offerUndo = false) }
                    if (exitBrowser) {
                        activeScreen = null
                        showingHub = true
                    }
                },
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
            // DOWNLOADS opens the browser sheet via the overflow menu. Keep this old Screen
            // value harmless for any restored UI state that still names it.
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
                isRefreshingAll = AdBlockFilters.isRefreshingAll,
                isAddingLists = AdBlockFilters.isAddingLists,
                bulkStatus = AdBlockFilters.bulkStatus,
                listProgress = AdBlockFilters.listProgress,
                currentPageUrl = activeTab?.takeUnless { it.isPrivate }?.url.orEmpty(),
                protectionSummary = { activeTab?.protectionSummary() ?: "No page activity yet" },
                onSiteModeChanged = { host, mode ->
                    com.miowser.app.adblock.SiteProtection.set(context, host, mode)
                    tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                    activeTab?.liveWebView?.reload()
                },
                onAddSubscriptions = { urls ->
                    AdBlockFilters.addSubscriptions(context, urls) { message ->
                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                        showActionSnackbar(message)
                    }
                },
                onEnabledChange = { enabled ->
                    val previousEnabled = AppSettings.current.adBlockEnabled
                    AppSettings.update(context) { it.copy(adBlockEnabled = enabled) }
                    if (enabled) com.miowser.app.adblock.AdBlock.engine.start(context)
                    tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                    showActionSnackbar(
                        if (enabled) "Ad blocker enabled" else "Ad blocker disabled",
                        "Undo"
                    ) {
                        AppSettings.update(context) { it.copy(adBlockEnabled = previousEnabled) }
                        if (previousEnabled) com.miowser.app.adblock.AdBlock.engine.start(context)
                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                    }
                },
                onAddSubscription = { name, url ->
                    AdBlockFilters.addSubscription(context, name, url) { message ->
                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                        showActionSnackbar(message)
                    }
                },
                onSubscriptionEnabledChange = { id, enabled ->
                    AdBlockFilters.setEnabled(context, id, enabled) { error ->
                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                        if (error != null) showActionSnackbar(error)
                    }
                },
                onRefreshSubscription = { id ->
                    AdBlockFilters.refreshOne(context, id) { message ->
                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                        showActionSnackbar(message)
                    }
                },
                onRenameSubscription = { id, name ->
                    AdBlockFilters.rename(context, id, name) { error ->
                        showActionSnackbar(error ?: "Filter list renamed")
                    }
                },
                onRemoveSubscription = { id ->
                    val removed = AdBlockFilters.subscriptions.firstOrNull { it.id == id }
                    if (removed != null) {
                        AdBlockFilters.remove(context, id, onRemoved = {
                            showActionSnackbar(
                                message = "Filter list removed",
                                actionLabel = "Undo",
                                onAction = {
                                    AdBlockFilters.restore(context, removed) { error ->
                                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                                        if (error != null) showActionSnackbar(error)
                                    }
                                },
                                onDismiss = { AdBlockFilters.discardRemoved(context, removed.id) }
                            )
                        }) { error ->
                            tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                            if (error != null) showActionSnackbar(error)
                        }
                    }
                },
                onRefreshAll = {
                    AdBlockFilters.refreshAll(context) { message ->
                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                        showActionSnackbar(message)
                    }
                },
                onSaveManualRules = { rules ->
                    val previousRules = AdBlockFilters.manualRules
                    AdBlockFilters.saveManualRules(context, rules) { message ->
                        tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
                        showActionSnackbar(message, "Undo") {
                            AdBlockFilters.saveManualRules(context, previousRules) {
                                tabs.forEach { it.liveWebView?.applyCosmeticFiltersNow() }
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
                    val previousRetention = AppSettings.current.inactiveTabRetention
                    AppSettings.update(context) { updated }
                    if (previousRetention != updated.inactiveTabRetention) {
                        InactiveTabs.prune(context, updated.inactiveTabRetention)
                    }
                },
                onOpenBrowsingData = { activeScreen = Screen.CLEAR_DATA },
                onOpenSitePermissions = { activeScreen = Screen.SITE_PERMISSIONS },
                onOpenBackupRestore = { showBackupRestore = true },
                onOpenCrashReports = { activeScreen = Screen.CRASH_DIAGNOSTICS },
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
            Screen.CRASH_DIAGNOSTICS -> CrashDiagnosticsPage(
                onBack = { activeScreen = Screen.SETTINGS }
            )
            null -> Unit
        }

        pendingBlobAction?.takeIf { action ->
            action.tabId == activeTabId && activeScreen == Screen.DETECTED && !showingHub
        }?.let { action ->
            val sources = verifiedSourcesForBlob(
                action.item,
                tabs.firstOrNull { it.id == action.tabId }?.detectedMedia.orEmpty()
            )
            BlobMediaActionDialog(
                action = action,
                sources = sources,
                onSource = { source ->
                    pendingBlobAction = null
                    when (action.kind) {
                        BlobActionKind.PLAY -> playMedia(source)
                        BlobActionKind.DOWNLOAD -> downloadMedia(source)
                        BlobActionKind.ADD_TO_LIBRARY -> addMediaToLibrary(source)
                    }
                },
                onSaveBlob = {
                    pendingBlobAction = null
                    downloadMedia(action.item, playAfterSaved = action.kind == BlobActionKind.PLAY)
                },
                onReturnToPage = {
                    pendingBlobAction = null
                    activeScreen = null
                    showActionSnackbar("Use the video player on this page")
                },
                onDismiss = { pendingBlobAction = null }
            )
        }

        if (showingHub) {
            HubHomePage(
                onBottomBarHeightChanged = { hubBottomBarHeightPx = it },
                onLibraryIdleChanged = { libraryIdle = it },
                uiVisible = playbackRequest == null && activeScreen == null && !showBackupRestore,
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
                libraryEntries = MediaLibrary.entries,
                onPlayLibraryEntry = { entry, visibleEntries ->
                    playLibraryEntry(entry, LibraryPlaybackQueue.fromVisible(visibleEntries))
                },
                onRemoveLibraryEntry = { entry ->
                    MediaLibrary.remove(context, entry)
                    showSavedSnackbar("Removed from Library", "Undo") {
                        MediaLibrary.restoreRemoved(context, listOf(entry))
                    }
                },
                onRelinkLibraryEntry = { entry ->
                    pendingRelinkLibraryEntry = entry
                    relinkDownloadLauncher.launch(arrayOf("*/*"))
                },
                onSaveLibraryEntry = { entry ->
                    coroutineScope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            FailedVideoLog.saveFromLibrary(context.applicationContext, entry)
                        }
                        showActionSnackbar(if (saved) "Video details saved" else "Video details already saved")
                    }
                },
                onClearLibrary = {
                    val removed = MediaLibrary.entries.toList()
                    MediaLibrary.clear(context)
                    showSavedSnackbar("Library cleared", "Undo") {
                        MediaLibrary.restoreRemoved(context, removed)
                    }
                },
                downloads = DownloadQueue.downloads.filterNot { it.id in pendingDownloadDeletes },
                onPauseDownload = { download -> DownloadQueue.pause(context, download) },
                onResumeDownload = { download ->
                    if (ensureDownloadPermission { DownloadQueue.resume(context, download) })
                        DownloadQueue.resume(context, download)
                },
                onCancelDownload = { download -> DownloadQueue.cancel(context, download) },
                onRetryDownload = { download ->
                    if (ensureDownloadPermission { DownloadQueue.retry(context, download) })
                        DownloadQueue.retry(context, download)
                },
                onDeleteDownload = { download, deleteFile ->
                    deleteDownloadWithUndo(download, deleteFile)
                },
                onRefreshDownloadLink = { download ->
                    refreshTargetDownloadId = download.id
                    showingHub = false
                    if (startupReady) openUrlInActiveTab(download.pageUrl) else pendingStartupUrl = download.pageUrl
                    showActionSnackbar("Choose the same video to refresh its link")
                },
                onOpenLocalDownload = { download ->
                    coroutineScope.launch {
                        val fileType = withContext(Dispatchers.IO) {
                            DownloadedFileInspector.inspect(context.applicationContext, download)
                        }
                        when (fileType) {
                            DownloadedFileType.MEDIA -> download.savedUri?.let { uri ->
                                libraryPlayRequestId++
                                libraryPlaybackQueue = null
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
                onRelinkDownload = { download ->
                    pendingRelinkDownload = download
                    relinkDownloadLauncher.launch(arrayOf("*/*"))
                },
                onOpenBrowser = { showingHub = false },
                onOpenBackupRestore = { showBackupRestore = true },
                onOpenRecordInBrowser = { value, search ->
                    val target = if (search) {
                        AppSettings.current.searchUrl(value)
                    } else {
                        UrlOrSearchResolver.resolve(value, AppSettings.current.searchUrlTemplate)
                    }
                    if (target.isNotBlank()) {
                        showingHub = false
                        activeScreen = null
                        if (startupReady) openUrlInActiveTab(target) else pendingStartupUrl = target
                    }
                }
            )
        }

        if (showOverflowMenu) {
            OverflowMenuSheet(
                activeDownloadCount = DownloadQueue.downloads.count {
                    it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.DOWNLOADING ||
                        it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.MUXING ||
                        it.status == DownloadStatus.FAILED
                },
                detectedCount = activeTab?.detectedMedia?.count {
                    it.meetsMinimumDuration(minimumMediaLengthSeconds)
                } ?: 0,
                shareEnabled = activeTab?.isShowingHome == false && !activeTab.url.isBlank(),
                pageActionsEnabled = activeTab?.let {
                    !it.isShowingHome && it.liveWebView != null && it.pageError == null
                } == true,
                onShare = {
                    activeTab?.let { tab -> shareUrl(tab.url, tab.title) }
                },
                onFind = {
                    activeTab?.let { tab ->
                        findQuery = ""
                        findMatchIndex = 0
                        findMatchCount = 0
                        findOwnerId = tab.id
                    }
                },
                onZoom = { showZoomTabId = activeTab?.id },
                onTextSize = { showTextSizeTabId = activeTab?.id },
                onClearCache = {
                    BrowsingData.clearCache(context, tabs.toList())
                    showActionSnackbar("Browser cache cleared")
                },
                onClearCookiesAndSiteData = { confirmClearCookiesAndSiteData = true },
                desktopMode = activeTab?.isDesktopMode == true,
                onToggleDesktopMode = { activeTab?.let { it.applyDesktopMode(!it.isDesktopMode) } },
                onPrint = {
                    val tab = activeTab
                    val view = tab?.liveWebView
                    val activity = context.findBrowserActivity()
                    if (tab != null && view != null && activity != null) {
                        runCatching {
                            val name = sanitizeFileName(tab.title.ifBlank { "Web page" })
                            val manager = activity.getSystemService(Context.PRINT_SERVICE) as PrintManager
                            manager.print(name, view.createPrintDocumentAdapter(name),
                                PrintAttributes.Builder().build())
                        }.onFailure { showActionSnackbar("Could not open print options") }
                    }
                },
                onSelect = { screen ->
                    showOverflowMenu = false
                    if (screen == Screen.DOWNLOADS) {
                        activeScreen = null
                        showBrowserDownloads = true
                    } else if (screen == Screen.HUB) {
                        hubSection = HubSection.LIBRARY
                        activeScreen = null
                        showingHub = true
                    } else {
                        activeScreen = screen
                    }
                },
                onDismiss = { showOverflowMenu = false }
            )
        }

        if (confirmClearCookiesAndSiteData) {
            MiowserConfirmationDialog(
                title = "Clear cookies and site data?",
                message = "This removes cookies and website storage for all sites. You may be signed out of websites.",
                onDismiss = { confirmClearCookiesAndSiteData = false },
                actions = listOf(
                    ConfirmationAction("Cancel", { confirmClearCookiesAndSiteData = false }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Clear data", {
                        confirmClearCookiesAndSiteData = false
                        BrowsingData.clear(context, tabs.toList(), BrowsingDataSelection(
                            history = false,
                            cookiesAndSiteStorage = true,
                            cache = false,
                            formData = false,
                            sitePermissions = false
                        )) { showActionSnackbar("Cookies and site data cleared") }
                    }, ConfirmationTone.CAUTION)
                )
            )
        }

        if (findOwnerId == activeTabId && findView != null && !showingHub &&
            activeScreen == null && playbackRequest == null) {
            BrowserFindBar(findQuery, findMatchIndex, findMatchCount,
                onQueryChange = { findQuery = it },
                onPrevious = { findView.findNext(false) },
                onNext = { findView.findNext(true) },
                onClose = { findOwnerId = null; findQuery = "" },
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding()
                    .padding(top = 64.dp, start = 12.dp, end = 12.dp).zIndex(1f))
        }

        if (showBrowserDownloads) {
            BrowserDownloadsSheet(
                uiVisible = playbackRequest == null,
                downloads = DownloadQueue.downloads.filterNot { it.id in pendingDownloadDeletes },
                onPause = { DownloadQueue.pause(context, it) },
                onResume = { download ->
                    if (ensureDownloadPermission { DownloadQueue.resume(context, download) })
                        DownloadQueue.resume(context, download)
                },
                onCancel = { DownloadQueue.cancel(context, it) },
                onRetry = { download ->
                    if (ensureDownloadPermission { DownloadQueue.retry(context, download) })
                        DownloadQueue.retry(context, download)
                },
                onDelete = ::deleteDownloadWithUndo,
                onRefreshLink = { download ->
                    showBrowserDownloads = false
                    refreshTargetDownloadId = download.id
                    showingHub = false
                    activeScreen = null
                    val page = download.pageUrl.ifBlank { download.sourceUrl }
                    if (startupReady) openUrlInActiveTab(page) else pendingStartupUrl = page
                    showActionSnackbar("Choose the same video to refresh its link")
                },
                onOpenHubDownloads = {
                    showBrowserDownloads = false
                    hubSection = HubSection.DOWNLOADS
                    hubDownloadSection = HubDownloadSection.DOWNLOADING
                    activeScreen = null
                    showingHub = true
                },
                onDismiss = { showBrowserDownloads = false }
            )
        }

        playbackRequest?.let { request ->
            val currentKey = request.toLibraryEntry().identityKey
            val queue = libraryPlaybackQueue?.takeIf {
                showingHub && currentKey in it.identityKeys
            }
            MiowserPlayerTheme {
                PlayerScreen(
                    request = request,
                    playlist = queue?.entries(MediaLibrary.entries).orEmpty(),
                    onSelectPlaylistEntry = if (queue != null) ({ entry -> playLibraryEntry(entry) }) else null,
                    onPlayNext = if (queue?.neighbor(currentKey, MediaLibrary.entries, 1) != null)
                        ({ playLibraryNeighbor(currentKey, 1) }) else null,
                    onPlayPrevious = if (queue?.neighbor(currentKey, MediaLibrary.entries, -1) != null)
                        ({ playLibraryNeighbor(currentKey, -1) }) else null,
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
                                pageUrl = request.pageUrl,
                                folderId = entry?.folderId,
                                manualFolder = entry?.manualFolder == true,
                                explicitlySaved = entry?.explicitlySaved == true
                            )
                            if (entry != null) MediaLibrary.remove(context, entry)
                        }
                        playbackRequest = null
                        libraryPlayRequestId++
                        libraryPlaybackQueue = null
                        activeScreen = null
                        showingHub = false
                        activeTabId = createAndInsertTab(
                            request.pageUrl,
                            openerTabId = activeTabId,
                            privateTab = activeTab?.isPrivate == true
                        ).id
                    }) else null,
                    onDownload = when (request) {
                        is PlaybackRequest.Remote -> ({ downloadFromPlayer(request) })
                        is PlaybackRequest.Local -> null
                    },
                    onDismiss = {
                        libraryPlayRequestId++
                        playbackRequest = null
                        libraryPlaybackQueue = null
                    }
                )
            }
        }

        pendingExternalUrl?.let { url ->
            MiowserConfirmationDialog(
                title = "Open another app?",
                message = "This link will leave Miowser: ${Uri.parse(url).scheme ?: "unknown"}",
                onDismiss = { pendingExternalUrl = null },
                actions = listOf(
                    ConfirmationAction("Cancel", { pendingExternalUrl = null }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Open", {
                        pendingExternalUrl = null
                        val intent = ExternalNavigation.safeIntent(url)
                        if (intent == null || runCatching { context.startActivity(intent) }.isFailure) {
                            showActionSnackbar("No app can open this link")
                        }
                    })
                )
            )
        }

        if (showPrivateStartConfirmation) {
            MiowserConfirmationDialog(
                title = "Start private browsing?",
                message = "Private tabs do not save browser history or player Library entries. This mode shares WebView storage with regular tabs, so starting and closing the private session clears cookies and website storage for all tabs. Downloads you start are saved normally.",
                onDismiss = { showPrivateStartConfirmation = false },
                actions = listOf(
                    ConfirmationAction("Cancel", { showPrivateStartConfirmation = false }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Continue", {
                        showPrivateStartConfirmation = false
                        val openPrivateTab = {
                            activeTabId = createAndInsertTab(configuredBrowserHomeTarget(), null, privateTab = true).id
                            activeScreen = null
                            showingHub = false
                        }
                        if (tabs.any(Tab::isPrivate)) {
                            openPrivateTab()
                        } else {
                            val ready = PrivateSessionStore.saveActive(context, true)
                            coroutineScope.launch {
                                runCatching { withContext(Dispatchers.IO) {
                                    check(ready.get()) { "Could not prepare private browsing" }
                                } }.onSuccess {
                                    BrowsingData.clearSharedPrivateSessionData(context, tabs.toList()) { openPrivateTab() }
                                }.onFailure { showActionSnackbar(it.message ?: "Could not prepare private browsing") }
                            }
                        }
                    })
                )
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
            MiowserConfirmationDialog(
                title = "Site permission",
                message = "$origin wants to use ${resources.joinToString { if (it == PermissionRequest.RESOURCE_VIDEO_CAPTURE) "camera" else "microphone" }}.",
                onDismiss = { request.deny(); pendingWebPermission = null },
                actions = listOf(
                    ConfirmationAction("Block", {
                        if (!pendingWebPermissionIsPrivate) {
                            resources.forEach { SitePermissionStore.set(context, origin, it, SiteDecision.BLOCK) }
                        }
                        request.deny()
                        pendingWebPermission = null
                    }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Allow once", {
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
                    })
                ) + if (!pendingWebPermissionIsPrivate) listOf(
                    ConfirmationAction("Always allow for this site", {
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
                    })
                ) else emptyList()
            )
        }

        pendingGeolocation?.let { pending ->
            MiowserConfirmationDialog(
                title = "Location request",
                message = "${pending.origin} wants to use your approximate location.",
                onDismiss = {
                    pending.callback.invoke(pending.origin, false, false)
                    pendingGeolocation = null
                },
                actions = listOf(
                    ConfirmationAction("Block", {
                        if (!pendingGeolocationIsPrivate) {
                            SitePermissionStore.set(context, pending.origin, "geolocation", SiteDecision.BLOCK)
                        }
                        pending.callback.invoke(pending.origin, false, false)
                        pendingGeolocation = null
                    }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Allow once", {
                        pendingGeolocation = null
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                            pending.callback.invoke(pending.origin, true, false)
                        } else {
                            pendingRuntimeGeolocation = pending
                            rememberRuntimeGeolocation = false
                            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                        }
                    })
                ) + if (!pendingGeolocationIsPrivate) listOf(
                    ConfirmationAction("Always allow for this site", {
                            pendingGeolocation = null
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                SitePermissionStore.set(context, pending.origin, "geolocation", SiteDecision.ALLOW)
                                pending.callback.invoke(pending.origin, true, false)
                            } else {
                                pendingRuntimeGeolocation = pending
                                rememberRuntimeGeolocation = true
                                locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                            }
                    })
                ) else emptyList()
            )
        }

        showSiteInfoTabId?.let { tabId ->
            val siteTab = tabs.firstOrNull { it.id == tabId }
            if (siteTab != null) {
                val host = SiteProtection.host(siteTab.url)
                SiteProtection.revision // observe changes to per-site settings
                val security = siteSecurity(siteTab.url,
                    siteTab.lastFinishedUrl.takeIf { siteTab.pageError == null },
                    siteTab.certificateErrorUrl, siteTab.certificateBypassed)
                var protectionStats by remember(tabId) { mutableStateOf(siteTab.protectionStats()) }
                LaunchedEffect(siteTab) {
                    while (true) {
                        delay(1_000)
                        protectionStats = siteTab.protectionStats()
                    }
                }
                SiteInfoSheet(
                    host = host,
                    security = security,
                    certificate = if (security == SiteSecurity.SECURE)
                        siteTab.liveWebView?.certificate else null,
                    protectionStats = protectionStats,
                    adBlockEnabled = AppSettings.current.adBlockEnabled,
                    siteMode = host?.let(SiteProtection::mode)
                        ?: com.miowser.app.adblock.SiteProtectionMode.STANDARD,
                    openPageLinksHere = host?.let(SiteProtection::openPageLinksHere) == true,
                    mediaMinimumOverride = host?.let(SiteProtection::mediaMinimumOverride),
                    globalMediaMinimum = AppSettings.current.grabberMinimumDurationSeconds,
                    canInterceptPopups = AppSettings.current.popupsEnabled,
                    onSiteModeChanged = { mode ->
                        if (host != null) {
                            SiteProtection.set(context, host, mode)
                            siteTab.liveWebView?.reload()
                        }
                    },
                    onOpenPageLinksHereChanged = { enabled ->
                        if (host != null) SiteProtection.setOpenPageLinksHere(context, host, enabled)
                    },
                    onMediaMinimumChanged = { seconds ->
                        if (host != null) SiteProtection.setMediaMinimum(context, host, seconds)
                    },
                    onClearCache = {
                        BrowsingData.clearCache(context, tabs.toList())
                        showActionSnackbar("Browser cache cleared")
                    },
                    onHardReload = {
                        BrowsingData.clearCache(context, tabs.toList())
                        showSiteInfoTabId = null
                        siteTab.reload()
                        showActionSnackbar("Reloading without cached files")
                    },
                    onDismiss = { showSiteInfoTabId = null }
                )
            } else showSiteInfoTabId = null
        }

        showZoomTabId?.let { tabId ->
            val zoomTab = tabs.firstOrNull { it.id == tabId && it.id == activeTabId }
            if (zoomTab?.liveWebView != null) {
                BrowserZoomSheet(
                    zoomFactor = zoomTab.pageZoomFactor,
                    onZoomIn = zoomTab::zoomPageIn,
                    onZoomOut = zoomTab::zoomPageOut,
                    onReset = zoomTab::resetPageZoom,
                    onDismiss = { showZoomTabId = null }
                )
            } else showZoomTabId = null
        }

        showTextSizeTabId?.let { tabId ->
            val textTab = tabs.firstOrNull { it.id == tabId && it.id == activeTabId }
            if (textTab?.liveWebView != null) {
                BrowserTextSizeSheet(
                    textZoomPercent = textTab.textZoomPercent,
                    onChange = textTab::setTextZoom,
                    onDismiss = { showTextSizeTabId = null }
                )
            } else showTextSizeTabId = null
        }

        pendingCertificateError?.let { pending ->
            val reason = when (pending.error.primaryError) {
                SslError.SSL_EXPIRED -> "The certificate has expired."
                SslError.SSL_NOTYETVALID -> "The certificate is not valid yet."
                SslError.SSL_IDMISMATCH -> "The certificate does not match this site."
                SslError.SSL_UNTRUSTED -> "The certificate is not trusted."
                SslError.SSL_DATE_INVALID -> "The certificate dates are invalid."
                else -> "The certificate could not be verified."
            }
            MiowserConfirmationDialog(
                onDismiss = {
                    pending.handler.cancel()
                    tabs.firstOrNull { it.id == pending.tabId }?.blockCertificateError()
                    pendingCertificateError = null
                },
                title = "Connection not verified",
                message = "${SiteProtection.host(pending.url) ?: pending.url}: $reason " +
                    "Someone could intercept this connection. Continue anyway?",
                actions = listOf(
                    ConfirmationAction("Don't proceed", {
                        pending.handler.cancel()
                        tabs.firstOrNull { it.id == pending.tabId }?.blockCertificateError()
                        pendingCertificateError = null
                    }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Continue anyway", {
                        val tab = tabs.firstOrNull { it.id == pending.tabId }
                        pendingCertificateError = null
                        if (tab != null) {
                            tab.acceptCertificateError()
                            pending.handler.proceed()
                        } else pending.handler.cancel()
                    }, ConfirmationTone.CAUTION)
                )
            )
        }

        pendingHttpNavigation?.let { pending ->
            MiowserConfirmationDialog(
                onDismiss = {
                    tabs.firstOrNull { it.id == pending.tabId }?.cancelRoutedPopup()
                    pendingHttpNavigation = null
                },
                title = "Unencrypted connection",
                message = "${SiteProtection.host(pending.url) ?: pending.url} uses HTTP. " +
                    "Information you send could be read or changed in transit. Continue?",
                actions = listOf(
                    ConfirmationAction("Cancel", {
                        tabs.firstOrNull { it.id == pending.tabId }?.cancelRoutedPopup()
                        pendingHttpNavigation = null
                    }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Continue", {
                        val tab = tabs.firstOrNull { it.id == pending.tabId }
                        pendingHttpNavigation = null
                        if (tab != null) {
                            HttpNavigationApproval.accept(pending.url, tab.isPrivate)
                            tab.navigateTo(pending.url)
                        }
                    }, ConfirmationTone.CAUTION)
                )
            )
        }

        pendingNewTabRequest?.let { pending ->
            MiowserConfirmationDialog(
                onDismiss = {
                    // Tapping outside/system-back also counts as declining — resultMsg is simply
                    // never completed either way; see completeNewWindow's own comment for why
                    // that's a safe way to block a popup rather than something that leaves
                    // WebView hanging.
                    pendingNewTabRequest = null
                    showActionSnackbar("Popup blocked", "Allow") {
                        completeNewWindow(pending.openerTabId, pending.resultMsg)
                    }
                },
                title = "Open new tab?",
                message = "This page wants to open a new tab.",
                actions = listOf(
                    ConfirmationAction("Block", {
                        pendingNewTabRequest = null
                        showActionSnackbar("Popup blocked", "Allow") {
                            completeNewWindow(pending.openerTabId, pending.resultMsg)
                        }
                    }, ConfirmationTone.CAUTION),
                    ConfirmationAction("Allow", {
                        completeNewWindow(pending.openerTabId, pending.resultMsg)
                        pendingNewTabRequest = null
                    })
                )
            )
        }

        pendingDownloadRefresh?.let { pending ->
            val original = DownloadQueue.downloads.firstOrNull { it.id == pending.originalId }
            MiowserConfirmationDialog(
                title = "Refresh download link?",
                message = "Use this newly captured link for the existing download, or start it as a separate download?",
                onDismiss = { pendingDownloadRefresh = null },
                actions = listOf(
                    ConfirmationAction("Refresh existing", {
                        if (original != null) {
                            val checkingSavedProgress = DownloadQueue.refreshLink(context, original, pending.candidate)
                            if (pending.playAfterSaved) {
                                pendingBlobPlayback = PendingBlobPlayback(original.id, activeTabId ?: -1L)
                            }
                            showSavedSnackbar(
                                if (checkingSavedProgress) "Link refreshed; checking saved progress"
                                else "Link refreshed; starting download",
                                "View"
                            ) {
                                showBrowserDownloads = true
                            }
                        }
                        pendingDownloadRefresh = null
                    }),
                    ConfirmationAction("Start new", {
                        val renamed = renamedDownloadCopy(pending.candidate, DownloadQueue.downloads)
                        DownloadQueue.enqueue(context, renamed)
                        if (pending.playAfterSaved) {
                            pendingBlobPlayback = PendingBlobPlayback(renamed.id, activeTabId ?: -1L)
                        }
                        pendingDownloadRefresh = null
                        showSavedSnackbar("New download started", "View") {
                            showBrowserDownloads = true
                        }
                    })
                )
            )
        }

        pendingDownloadConflict?.let { pending ->
            val existing = DownloadQueue.downloads.firstOrNull { it.id == pending.existingId }
            if (existing == null) {
                pendingDownloadConflict = null
            } else {
                MiowserConfirmationDialog(
                    onDismiss = { pendingDownloadConflict = null },
                    title = if (pending.reason == DownloadConflictReason.SAME_SOURCE) {
                        if (existing.status == DownloadStatus.COMPLETED) "This file has already been downloaded"
                        else "This download already exists"
                    } else "A file with this name already exists",
                    message = if (pending.reason == DownloadConflictReason.SAME_SOURCE) {
                        "Choose whether to refresh its link, replace the existing download, or save another renamed copy."
                    } else "Start another download with an automatic renamed filename?",
                    actions = listOf(
                        ConfirmationAction("Cancel", { pendingDownloadConflict = null }, ConfirmationTone.CAUTION)
                    ) + (if (pending.reason == DownloadConflictReason.SAME_SOURCE) listOf(
                                ConfirmationAction("Refresh link", {
                                    refreshTargetDownloadId = existing.id
                                    pendingDownloadConflict = null
                                    playbackRequest = null
                                    showingHub = false
                                    openUrlInActiveTab(existing.pageUrl.ifBlank { existing.sourceUrl })
                                    showActionSnackbar("Choose the same file to refresh its link")
                                }),
                                ConfirmationAction(
                                    if (existing.status == DownloadStatus.COMPLETED) "Replace downloaded file" else "Replace current",
                                    {
                                    DownloadQueue.replaceAndRestart(
                                        context,
                                        existing,
                                        pending.candidate.copyForQueue(id = existing.id),
                                        deleteExistingFile = existing.status == DownloadStatus.COMPLETED
                                    )
                                    if (pending.playAfterSaved) {
                                        pendingBlobPlayback = PendingBlobPlayback(existing.id, activeTabId ?: -1L)
                                    }
                                    pendingDownloadConflict = null
                                    playbackRequest = null
                                    showSavedSnackbar("Existing download replaced", "View") {
                                        showBrowserDownloads = true
                                    }
                                }, ConfirmationTone.CAUTION)
                            ) else emptyList()) + ConfirmationAction("New renamed copy", {
                                val renamed = renamedDownloadCopy(
                                    pending.candidate,
                                    DownloadQueue.downloads
                                )
                                DownloadQueue.enqueue(context, renamed)
                                if (pending.playAfterSaved) {
                                    pendingBlobPlayback = PendingBlobPlayback(renamed.id, activeTabId ?: -1L)
                                }
                                pendingDownloadConflict = null
                                showSavedSnackbar("Renamed download started")
                            })
                )
            }
        }

        pendingLinkMenu?.let { pressed ->
            val url = pressed.url
            LinkContextMenu(
                url = url,
                linkText = pressed.text,
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
                    val saveLink = { allowDuplicate: Boolean ->
                        val saved = SpeedDials.save(context, null, "", url, allowDuplicate = allowDuplicate)
                        if (saved?.autoTitle == true) {
                            coroutineScope.launch {
                                val metadata = SpeedDialSiteMetadataStore.fetch(context, saved.url, needTitle = true)
                                metadata.title?.let { SpeedDials.updateAutoTitle(context, saved.url, it) }
                            }
                        }
                        showActionSnackbar(
                            if (saved != null) "Added to speed dial" else "Could not add speed dial"
                        )
                    }
                    if (SpeedDials.duplicateFor(url) != null) {
                        showActionSnackbar("A speed dial for this domain already exists", "Add anyway") {
                            saveLink(true)
                        }
                    } else {
                        saveLink(false)
                    }
                },
                onDismiss = { pendingLinkMenu = null }
            )
        }

        if (showBackupRestore) {
            com.miowser.app.backup.BackupRestorePage(
                activeDownloads = DownloadQueue.downloads.any { item ->
                    item.status == DownloadStatus.QUEUED || item.status == DownloadStatus.DOWNLOADING ||
                        item.status == DownloadStatus.MUXING || DownloadQueue.isTransferActive(item.id)
                },
                onBack = { showBackupRestore = false }
            )
        }

        MiowserSnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = snackbarBottomOffset)
        )

        fullscreenVideo?.takeIf { fullscreen ->
            fullscreen.tabId == activeTabId && !showingHub && activeScreen == null && playbackRequest == null
        }?.let { fullscreen ->
            key(fullscreen.view) {
                Box(Modifier.fillMaxSize().zIndex(2f)) {
                    BrowserVideoSurface(fullscreen.view, tabs.firstOrNull { it.id == fullscreen.tabId }?.browserVideoPlayer)
                    tabs.firstOrNull { it.id == fullscreen.tabId }?.let { owner ->
                        BrowserVideoPlayerOverlay(owner.browserVideoPlayer, owner.title, ::exitWebFullscreen,
                            sourceLabel = SiteProtection.host(owner.url).orEmpty(),
                            backgroundPlaybackEnabled = backgroundPlaybackEnabled,
                            onAudioOnlyChanged = { browserVideoAudioOnly = it })
                    }
                }
            }
        }
    }

    LaunchedEffect(pendingBlobAction, activeTabId, activeScreen, showingHub) {
        val action = pendingBlobAction ?: return@LaunchedEffect
        if (action.tabId != activeTabId || activeScreen != Screen.DETECTED || showingHub) {
            pendingBlobAction = null
        }
    }

    LaunchedEffect(pendingBlobPlayback) {
        val pending = pendingBlobPlayback ?: return@LaunchedEffect
        var terminalStatus: DownloadStatus?
        while (true) {
            terminalStatus = snapshotFlow {
                DownloadQueue.downloads.firstOrNull { it.id == pending.downloadId }?.status
            }.first { status ->
                status == null || status == DownloadStatus.COMPLETED ||
                    status == DownloadStatus.FAILED || status == DownloadStatus.CANCELED
            }
            if (terminalStatus != DownloadStatus.FAILED ||
                !DownloadQueue.isTransferActive(pending.downloadId)) break
            delay(250)
        }
        val download = DownloadQueue.downloads.firstOrNull { it.id == pending.downloadId }
        if (pendingBlobPlayback != pending) return@LaunchedEffect
        if (terminalStatus == DownloadStatus.COMPLETED && download?.savedUri != null) {
            val fileType = withContext(Dispatchers.IO) {
                DownloadedFileInspector.inspect(context.applicationContext, download)
            }
            if (fileType == DownloadedFileType.MEDIA && activeTabId == pending.tabId &&
                activeScreen == Screen.DETECTED && !showingHub) {
                libraryPlayRequestId++
                libraryPlaybackQueue = null
                playbackRequest = PlaybackRequest.Local(
                    uri = download.savedUri!!,
                    title = download.displayName,
                    pageUrl = download.pageUrl.takeIf(String::isNotBlank),
                    videoName = download.displayName
                )
            } else if (fileType == DownloadedFileType.MEDIA) {
                showSavedSnackbar("Blob saved; open it from Downloads")
            } else {
                showActionSnackbar("Blob saved, but it is not a playable video")
            }
        } else if (terminalStatus == DownloadStatus.FAILED) {
            showActionSnackbar("Blob could not be saved as a file. Choose a detected source or the website player.")
        }
        pendingBlobPlayback = null
    }

    // Switching tabs should close a Detected Media page showing the previous tab's results
    // rather than silently keep showing stale ones. (In practice hard to trigger — TabsPage is
    // itself the only way to switch tabs now, and picking a tab there already sends
    // activeScreen back to null — but harmless to keep as a belt-and-suspenders guard.)
    LaunchedEffect(activeTabId, showingHub, startupReady) {
        if (!startupReady || showingHub) return@LaunchedEffect
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

    // Select-all-on-focus, matching standard browser address-bar behavior. The trailing end
    // anchors the visible part of long addresses at the path/query users are about to edit.
    LaunchedEffect(activeTab?.id, activeTab?.isAddressBarFocused) {
        val tab = activeTab
        if (tab?.isAddressBarFocused == true) {
            val originalText = tab.addressBarText.text
            delay(50)
            // A long press or quick edit during this focus delay owns the selection. Do not
            // snap the cursor back after the user has already started moving it.
            if (tab.isAddressBarFocused && tab.addressBarText.text == originalText &&
                tab.addressBarText.selection.collapsed
            ) {
                tab.addressBarText = tab.addressBarText.copy(
                    selection = TextRange(0, tab.addressBarText.text.length)
                )
            }
        }
    }

    // Rich playback owns audio/video while it is open. Pause the DOM media first (before
    // WebView.onPause can suspend JavaScript), then pause the WebView itself. Returning from the
    // player resumes the WebView engine but leaves the page's media element paused so two players
    // cannot immediately begin speaking at once.
    LaunchedEffect(activeTabId, showingHub, playbackRequest, startupReady, activeScreen,
        activeTab?.isShowingHome, activeTab?.rendererUnavailable, rendererTransferActivity) {
        if (startupReady && !showingHub && activeScreen == null && playbackRequest == null &&
            activeTab?.isShowingHome == false && !activeTab.rendererUnavailable) {
            try { activeTab.ensureWebView() }
            catch (_: Exception) { startupReady = false; browserStartupError = true; return@LaunchedEffect }
        }
        tabs.forEach { tab ->
            val visibleBrowser = !showingHub && activeScreen == null &&
                playbackRequest == null && tab.id == activeTabId
            val shouldRunBrowser = visibleBrowser || tab.rendererTransferCount > 0
            if (shouldRunBrowser) {
                if (!visibleBrowser) tab.pausePageMedia()
                tab.liveWebView?.onResume()
            } else {
                if (tab.id == activeTabId &&
                    (activeScreen != null || playbackRequest != null || showingHub)) {
                    tab.pausePageMedia()
                }
                tab.liveWebView?.onPause()
            }
        }
    }
    val videoButtonBackground = MaterialTheme.colorScheme.primaryContainer.toArgb()
    val videoButtonForegroundColor = MaterialTheme.colorScheme.onPrimaryContainer.toArgb()
    val videoButtonAppearance = remember(videoButtonBackground, videoButtonForegroundColor, motion.reduced) {
        VideoAssistantButtonPolicy(
            background = String.format(java.util.Locale.ROOT, "#%06X", videoButtonBackground and 0xFFFFFF),
            foreground = String.format(java.util.Locale.ROOT, "#%06X", videoButtonForegroundColor and 0xFFFFFF),
            reducedMotion = motion.reduced
        )
    }
    val allowVideoButton = videoButtonForeground && !showingHub && activeScreen == null &&
        playbackRequest == null && fullscreenVideo == null && !showOverflowMenu &&
        !showBrowserDownloads && !showBackupRestore && !openingTabs &&
        showSiteInfoTabId == null && showZoomTabId == null && showTextSizeTabId == null &&
        pendingLinkMenu == null && pendingNewTabRequest == null && pendingHttpNavigation == null &&
        pendingCertificateError == null && pendingDownloadConflict == null && pendingBlobAction == null &&
        pendingWebPermission == null && pendingRuntimePermission == null && pendingGeolocation == null &&
        pendingRuntimeGeolocation == null && fileSelection.ownerId == null && !showPrivateStartConfirmation &&
        !confirmClearCookiesAndSiteData && !storageRequestActive && pendingExternalUrl == null
    val enabledVideoButtonAppearance = remember(videoButtonAppearance) { videoButtonAppearance.copy(enabled = true) }
    SideEffect {
        tabs.forEach { tab ->
            val enabled = allowVideoButton && tab.id == activeTabId && !tab.isShowingHome && tab.pageError == null
            tab.updateVideoAssistantButton(if (enabled) enabledVideoButtonAppearance else videoButtonAppearance)
        }
    }
    LaunchedEffect(startupReady, AppSettings.current.javaScriptEnabled, AppSettings.current.cookiesEnabled,
        AppSettings.current.popupsEnabled) {
        if (!startupReady) return@LaunchedEffect
        if (tabController.liveEngineCount > 0) {
            CookieManager.getInstance().setAcceptCookie(AppSettings.current.cookiesEnabled)
        }
        tabs.forEach { it.applyBrowserPreferences(AppSettings.current) }
    }
    LaunchedEffect(activeTabId, fileSelection.ownerId, pendingGeolocationOwnerId, pendingNewTabRequest,
        pendingHttpNavigation, pendingCertificateError, fullscreenVideo, tabController.liveEngineCount,
        tabs.map { it.isLoading to it.hasPlayingMedia }, pendingWebPermission, pendingRuntimePermission,
        DownloadQueue.downloads.map { it.id to it.status }) {
        tabController.configureMemory(context)
        tabController.setVisible(activeTabId, listOfNotNull(fileSelection.ownerId, pendingGeolocationOwnerId,
            pendingNewTabRequest?.openerTabId, pendingHttpNavigation?.tabId,
            pendingCertificateError?.tabId, fullscreenVideo?.tabId).toSet() +
            if (pendingWebPermission != null || pendingRuntimePermission != null) tabs.map { it.id }.toSet() else emptySet())
    }
    DisposableEffect(tabController, context.applicationContext) {
        val app = context.applicationContext
        val callbacks = object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(configuration: android.content.res.Configuration) = Unit
            override fun onLowMemory() { com.miowser.app.persistence.Persistence.onOwner { tabController.trimMemory(80) } }
            override fun onTrimMemory(level: Int) {
                if (level >= 10) com.miowser.app.persistence.Persistence.onOwner { tabController.trimMemory(level) }
            }
        }
        app.registerComponentCallbacks(callbacks)
        onDispose { app.unregisterComponentCallbacks(callbacks) }
    }
    DisposableEffect(lifecycleOwner, showingHub, playbackRequest, activeScreen, activeTabId, recordsReady,
        browserVideoAudioOnly, backgroundPlaybackEnabled) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    videoButtonForeground = true
                    tabController.onForeground()
                    if (!showingHub && activeScreen == null && playbackRequest == null) {
                        tabs.firstOrNull { it.id == activeTabId }?.liveWebView?.onResume()
                    }
                    tabs.forEach { it.resumeBrowserVideoHost() }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    videoButtonForeground = false
                    if (!showingHub) tabs.firstOrNull { it.id == activeTabId }?.markActive()
                    val pipOwner = (context.findBrowserActivity() as? com.miowser.app.MainActivity)?.browserVideoPipOwner
                    tabs.forEach { tab ->
                        val keepForAudio = backgroundPlaybackEnabled && keepBrowserRendererForAudioOnly(browserVideoAudioOnly,
                            fullscreenVideo?.tabId, tab.id, tab.browserVideoPlayer.state.value)
                        val keepForPip = keepBrowserRendererForPip(pipOwner, fullscreenVideo?.tabId, tab.id, tab.browserVideoPlayer.state.value)
                        if (!keepForAudio) tab.pauseBrowserVideoHost(keepForPip)
                        if (keepForPip || keepForAudio) {
                            tab.liveWebView?.onResume()
                        } else tab.liveWebView?.onPause()
                    }
                    // Final checkpoint in addition to the foreground session-change observer.
                    if (recordsReady) TabSessionStore.save(context, tabs, activeTabId)
                    DownloadQueue.persist(context)
                }
                Lifecycle.Event.ON_STOP -> {
                    val host = context.findBrowserActivity() as? com.miowser.app.MainActivity
                    host?.browserVideoPipOwner?.let { owner ->
                        tabs.firstOrNull { it.id == owner.id.tabId }?.let { tab ->
                            if (owner.matches(tab.browserVideoPlayer.state.value)) {
                                tab.browserVideoPlayer.pause()
                                tab.liveWebView?.onPause()
                            }
                        }
                        host.releaseBrowserVideoPictureInPicture(owner)
                    }
                    pendingClosedTab?.let { pending ->
                        finalizeClosedTab(pending)
                        snackbarJob?.cancel()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
