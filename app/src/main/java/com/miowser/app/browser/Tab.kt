package com.miowser.app.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.os.SystemClock
import android.net.Uri
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.net.http.SslError
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.miowser.app.adblock.AdBlock
import com.miowser.app.adblock.CosmeticBridge
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.RendererResourceDownloader
import com.miowser.app.downloader.installRendererBlobFrameScript
import com.miowser.app.downloader.RendererResourceKind
import com.miowser.app.downloader.rendererResourceKind
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.GrabberBridge
import com.miowser.app.grabber.MediaProbe
import com.miowser.app.grabber.installVideoSnifferScript
import com.miowser.app.browser.video.LiveVideoTracker
import com.miowser.app.browser.video.FullscreenCallbackLease
import com.miowser.app.browser.video.VideoAssistantButtonPolicy
import com.miowser.app.browser.video.BrowserVideoController
import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.MediaUrlClassifier
import com.miowser.app.grabber.StaticMediaDiscovery
import com.miowser.app.grabber.DiscoveryHandle
import com.miowser.app.grabber.DiscoveryProgress
import com.miowser.app.grabber.mergeDetectedMedia
import com.miowser.app.history.HistoryLog
import com.miowser.app.settings.AppSettings
import com.miowser.app.settings.Settings
import java.util.Collections
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicBoolean

private const val CONSOLE_TAG = "Miowser/Console"

data class SiteProtectionStats(
    val blockedRequests: Int,
    val blockedPopups: Int,
    val topBlockedHosts: List<Pair<String, Int>>
)

/** Keep the installed WebView's browser version while requesting a desktop viewport and UA. */
internal fun desktopUserAgent(mobile: String): String = mobile
    .replace(Regex("\\(Linux; Android[^)]*\\)"), "(X11; Linux x86_64)")
    .replace(Regex("Version/\\d+(?:\\.\\d+)* "), "")
    .replace(" Mobile Safari/", " Safari/")

/**
 * A lightweight tab record with observable navigation metadata and optional live engine.
 * Construction and session snapshots do not create WebViews. The activity's TabController
 * acquires an engine on activation/navigation and retains it across ordinary switches.
 * Preview decoding is independent of the engine and occurs only for visible overview cards.
 *
 * @param openerTabId the tab whose page opened this one via a real navigation event
 *   (target="_blank"/window.open(), handled by onCreateWindow below) — null for tabs the user
 *   opened manually via the "+" button. Closing a tab returns to its opener when it has one;
 *   see BrowserScreen's close-tab logic.
 * @param onNewWindowRequested called when this tab's page tries to open another one — never
 *   creates the tab itself (unlike this parameter's predecessor): BrowserScreen's
 *   handleNewWindowRequested decides, based on Settings.newTabConfirmation and whether this was
 *   a real tap (isUserGesture) or page script acting alone, whether to create+switch to it right
 *   away or hold the request behind a confirmation dialog first. Either way onCreateWindow itself
 *   always returns true immediately — completing (or simply never completing) resultMsg is what
 *   actually decides the popup's fate, asynchronously, exactly as WebView's own API expects.
 * @param onLinkLongPressed called with a link's URL and anchor text on a long press — the entry
 *   point for the long-press context menu (open in new/background tab, copy, share).
 */
class Tab(
    val id: Long,
    context: Context,
    private val controller: TabController,
    initialUrl: String,
    val openerTabId: Long?,
    val isPrivate: Boolean = false,
    val thumbnailKey: String = UUID.randomUUID().toString(),
    initialTitle: String = "",
    initialLastActiveAt: Long = System.currentTimeMillis(),
    initialDesktopMode: Boolean = false,
    canCapturePreview: () -> Boolean = { true },
    private val onNewWindowRequested: (openerTabId: Long, isUserGesture: Boolean, resultMsg: Message) -> Unit,
    private val onInsecureNavigationRequested: (tabId: Long, url: String) -> Unit,
    private val onCertificateErrorRequested: (tabId: Long, url: String, handler: SslErrorHandler, error: SslError) -> Unit,
    private val onPopupRoutingFailed: (tabId: Long) -> Unit,
    private val onLinkLongPressed: (url: String, text: String?) -> Unit,
    private val onDownloadRequested: (WebDownloadRequest) -> Unit,
    private val onFileChooserRequested: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit,
    private val onFullscreenVideoRequested: (Long, View, WebChromeClient.CustomViewCallback) -> Unit,
    private val onFullscreenVideoHidden: (Long) -> Unit,
    private val onWebPermissionRequested: (PermissionRequest) -> Unit,
    private val onWebPermissionCanceled: (PermissionRequest) -> Unit,
    private val onGeolocationRequested: (String, GeolocationPermissions.Callback) -> Unit,
    private val onGeolocationCanceled: () -> Unit,
    private val onExternalNavigation: (String) -> Unit,
    private val onRendererUnavailable: (Long) -> Unit
) {
    var title by mutableStateOf(
        if (isBrowserHomeUrl(initialUrl)) "Home" else initialTitle.ifBlank { "New Tab" }
    )
    var url by mutableStateOf(initialUrl)
    var isShowingHome by mutableStateOf(isBrowserHomeUrl(initialUrl))
        private set
    var isDesktopMode by mutableStateOf(initialDesktopMode)
        private set
    var pageZoomFactor by mutableFloatStateOf(1f)
        private set
    var textZoomPercent by mutableIntStateOf(100)
        private set
    private var mobileUserAgent: String? = null
    private var requestUserAgent: java.util.concurrent.atomic.AtomicReference<String>? = null
    private var desktopViewportScript: androidx.webkit.ScriptHandler? = null

    fun applyDesktopMode(enabled: Boolean) {
        if (isDesktopMode == enabled) return
        isDesktopMode = enabled
        liveWebView?.let { view ->
            val settings = view.settings
            val mobile = mobileUserAgent ?: settings.userAgentString.also { mobileUserAgent = it }
            if (enabled) {
                desktopViewportScript = view.installDesktopViewportScript()
            } else {
                desktopViewportScript?.remove()
                desktopViewportScript = null
            }
            settings.userAgentString = if (enabled) desktopUserAgent(mobile) else mobile
            requestUserAgent?.set(settings.userAgentString)
            settings.useWideViewPort = enabled
            settings.loadWithOverviewMode = enabled
            view.reload()
        }
    }

    fun zoomPageIn() { liveWebView?.zoomBy(1.2f) }
    fun zoomPageOut() { liveWebView?.zoomBy(1f / 1.2f) }
    fun resetPageZoom() {
        if (pageZoomFactor.isFinite() && pageZoomFactor > 0f)
            liveWebView?.zoomBy((1f / pageZoomFactor).coerceIn(0.01f, 100f))
    }
    fun setTextZoom(percent: Int) {
        textZoomPercent = percent.coerceIn(50, 200)
        liveWebView?.settings?.textZoom = textZoomPercent
    }
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var addressBarText by mutableStateOf(
        TextFieldValue(if (isBrowserHomeUrl(initialUrl)) "" else initialUrl)
    )
    var isAddressBarFocused by mutableStateOf(false)
    var lastActiveAt by mutableLongStateOf(initialLastActiveAt)
        private set
    var showJumpToTop by mutableStateOf(false)
        private set
    var pageError by mutableStateOf<String?>(null)
        private set
    var blockedPageInfo by mutableStateOf<com.miowser.app.adblock.BlockedPageInfo?>(null)
        private set
    var rendererUnavailable by mutableStateOf(false)
        private set
    var certificateErrorUrl by mutableStateOf<String?>(null)
        private set
    var certificateBypassed by mutableStateOf(false)
        private set

    fun acceptCertificateError() {
        certificateBypassed = true
        pageError = null
        blockedPageInfo = null
    }

    fun blockCertificateError() {
        blockedPageInfo = null
        certificateBypassed = false
        pageError = "This site's security certificate could not be verified. The connection was blocked."
    }
    /** Last successfully finished page, retained while its WebView stays alive across tab switches. */
    var lastFinishedUrl by mutableStateOf<String?>(null)
        private set

    /** Everything the grabber has found on the current page. Cleared on every new navigation. */
    val detectedMedia = mutableStateListOf<DetectedMedia>()
    private val detectionIndexes = mutableMapOf<String, Int>()
    private var grabberBridge: GrabberBridge? = null
    private var liveVideoTracker: LiveVideoTracker? = null
    internal val browserVideoPlayer = BrowserVideoController()
    internal fun startBrowserVideoFullscreen() { liveVideoTracker?.onNativeFullscreenShown() }
    internal fun finishBrowserVideoFullscreen() { liveVideoTracker?.onNativeFullscreenHidden() }
    internal fun pauseBrowserVideoHost(pip: Boolean) { liveVideoTracker?.onHostPaused(pip) }
    internal fun resumeBrowserVideoHost() { liveVideoTracker?.onHostResumed() }
    internal fun attachVideoAssistantDisplay(engine: WebView, display: com.miowser.app.browser.video.NativeVideoAssistantDisplay) {
        if (liveWebView === engine) liveVideoTracker?.attachNativeDisplay(display)
    }
    internal fun detachVideoAssistantDisplay(display: com.miowser.app.browser.video.NativeVideoAssistantDisplay) {
        liveVideoTracker?.detachNativeDisplay(display)
    }
    private var videoButtonPolicy = VideoAssistantButtonPolicy()
    internal fun updateVideoAssistantButton(policy: VideoAssistantButtonPolicy) {
        if (videoButtonPolicy == policy) return
        videoButtonPolicy = policy
        liveVideoTracker?.updateButtonPolicy(policy)
    }
    internal var hasPlayingMedia by mutableStateOf(false)
        private set
    private val probeHandles = java.util.concurrent.ConcurrentHashMap.newKeySet<com.miowser.app.grabber.MediaProbeHandle>()
    var thumbnail by mutableStateOf<Bitmap?>(null)
        private set
    var discoveryProgress by mutableStateOf<DiscoveryProgress?>(null)
        private set
    private var discoveryHandle: DiscoveryHandle? = null
    private var pendingAutomaticDiscovery: Runnable? = null
    private val blockedHosts = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()
    private val blockedRequests = java.util.concurrent.atomic.AtomicInteger(0)
    private val blockedPopups = java.util.concurrent.atomic.AtomicInteger(0)
    fun protectionStats(): SiteProtectionStats = SiteProtectionStats(
        blockedRequests.get(), blockedPopups.get(),
        blockedHosts.entries.sortedByDescending { it.value.get() }.take(6)
            .map { it.key to it.value.get() }
    )
    fun protectionSummary(): String {
        val hosts = blockedHosts.entries.sortedByDescending { it.value.get() }.take(6)
            .joinToString("\n") { "${it.key}: ${it.value.get()} requests blocked by network filters" }
        return listOf(hosts, "${blockedPopups.get()} unsolicited pop-ups blocked").filter(String::isNotBlank).joinToString("\n")
    }

    // Guards against re-probing/re-reporting the same URL (a page can request or expose the same
    // manifest/element more than once) — synchronized because network-level candidates arrive
    // from shouldInterceptRequest, which Chromium calls off the main thread.
    private val seenGrabberUrls = Collections.synchronizedSet(mutableSetOf<String>())
    private val detectionGeneration = AtomicLong(0L)
    private val discoveryGeneration = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val previewCaptureAllowed = canCapturePreview
    private var previewCaptureGeneration = 0L
    private val previewCaptureRunnable = Runnable { captureThumbnail() }
    private lateinit var rendererResourceDownloader: RendererResourceDownloader
    var rendererTransferCount by mutableIntStateOf(0)
        private set
    private var destroyed = false
    private var retireEngineCallbacks: (() -> Unit)? = null
    private val popupProxies = mutableMapOf<WebView, () -> Unit>()
    private var interceptingPopupUntil = 0L
    private var routedPopupTarget: String? = null

    fun cancelRoutedPopup() {
        interceptingPopupUntil = 0L
        routedPopupTarget = null
    }

    private fun shouldBlockCompanionNavigation(target: String): Boolean {
        if (SystemClock.uptimeMillis() >= interceptingPopupUntil) {
            interceptingPopupUntil = 0L
            return false
        }
        return target != routedPopupTarget
    }

    /** Capture a page-created window's first HTTP(S) destination before it can render. */
    private fun routePopupIntoCurrentTab(resultMsg: Message) {
        val proxy = WebView(tabContext)
        val routed = AtomicBoolean(false)
        val disposed = AtomicBoolean(false)
        val sourceEngine = liveWebView
        var rendererGone = false
        var timeout: Runnable? = null
        fun disposeProxy() {
            if (!disposed.compareAndSet(false, true)) return
            timeout?.let(mainHandler::removeCallbacks)
            popupProxies.remove(proxy)
            (proxy.parent as? ViewGroup)?.removeView(proxy)
            proxy.destroy()
        }
        popupProxies[proxy] = {
            rendererGone = true
            routed.set(true)
            disposeProxy()
        }
        interceptingPopupUntil = SystemClock.uptimeMillis() + 5_000L
        routedPopupTarget = null
        fun route(target: String) {
            if (disposed.get()) return
            val scheme = Uri.parse(target).scheme
            if (scheme != "http" && scheme != "https") return
            if (!routed.compareAndSet(false, true)) return
            routedPopupTarget = target
            interceptingPopupUntil = SystemClock.uptimeMillis() + 30_000L
            mainHandler.post {
                disposeProxy()
                if (!destroyed && !rendererGone && liveWebView === sourceEngine && !rendererUnavailable) navigateTo(target)
            }
        }
        proxy.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (request.isForMainFrame) route(request.url.toString())
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (url != null) route(url)
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                rendererGone = true
                val pending = !disposed.get()
                routed.set(true)
                disposeProxy()
                if (pending && !destroyed) {
                    cancelRoutedPopup()
                    onPopupRoutingFailed(id)
                }
                return true
            }
        }
        (resultMsg.obj as WebView.WebViewTransport).webView = proxy
        resultMsg.sendToTarget()
        timeout = Runnable {
            if (!routed.get()) {
                routed.set(true)
                disposeProxy()
                interceptingPopupUntil = 0L
                if (!destroyed) onPopupRoutingFailed(id)
            }
        }
        mainHandler.postDelayed(timeout, 5_000L)
    }

    /**
     * Resets grabber detection state for a brand-new page. Called from two places: WebView's own
     * onPageStarted (via onNewPageStarted below) for ordinary navigations, and proactively by
     * BrowserScreen right before every explicit loadUrl() it issues itself (address bar submit,
     * opening a History/Bookmarks entry, Home) — confirmed on-device that WebView doesn't always
     * route a direct navigation from one raw media-manifest URL straight to another (e.g. an
     * .m3u8 page's address bar overwritten with a .mpd URL) through onPageStarted at all, only
     * onPageFinished, which never fires this clear on its own. Calling it twice for one
     * navigation (WebView's callback firing after this one already ran) is harmless — clearing an
     * already-empty list is a no-op.
     */
    fun clearDetections() {
        blockedHosts.clear()
        blockedRequests.set(0)
        blockedPopups.set(0)
        cancelDiscovery()
        detectionGeneration.incrementAndGet()
        grabberBridge?.reset()
        liveVideoTracker?.onNavigationStarted()
        hasPlayingMedia = false
        probeHandles.toList().forEach { it.cancel() }
        probeHandles.clear()
        detectionIndexes.clear()
        detectedMedia.clear()
        seenGrabberUrls.clear()
        if (::rendererResourceDownloader.isInitialized) rendererResourceDownloader.invalidatePage()
    }

    /** Attaches this tab's live renderer to a blob:/data: queue item before it is enqueued. */
    fun attachRendererTransfer(download: Download) {
        val view = liveWebView ?: return
        val bridge = rendererResourceDownloader
        val pageToken = bridge.capturePageToken(download)
        val sourceUrl = download.sourceUrl
        download.rendererTransfer = { queued ->
            val resumed = AtomicBoolean(false)
            mainHandler.post {
                if (!destroyed && liveWebView === view && !rendererUnavailable && bridge.isCurrentPageToken(pageToken)) {
                    resumed.set(true)
                    rendererTransferCount++
                    // The Media page pauses its WebView. Its renderer still has to run the
                    // user-requested blob transfer, without resuming the page's video.
                    view.onResume()
                    if (!previewCaptureAllowed()) pausePageMedia()
                }
            }
            try {
                bridge.run(view, sourceUrl, queued, pageToken)
            } finally {
                mainHandler.post {
                    if (resumed.getAndSet(false)) rendererTransferCount = (rendererTransferCount - 1).coerceAtLeast(0)
                }
            }
        }
    }

    /** Captures the pixels currently shown inside the live WebView, at its real scroll position. */
    fun captureThumbnail(onComplete: () -> Unit = {}) {
        mainHandler.removeCallbacks(previewCaptureRunnable)
        val view = liveWebView ?: run { onComplete(); return }
        val activity = tabContext.findActivity()
        if (destroyed || isShowingHome || !previewCaptureAllowed() || activity == null ||
            view.width <= 0 || view.height <= 0 || !view.isShown || view.visibility != View.VISIBLE
        ) {
            onComplete()
            return
        }

        // PixelCopy reads the compositor's displayed viewport. It does not need to reconstruct
        // the current scroll offset from a document-sized picture, which is especially unreliable
        // on pages with nested scrollers, sticky elements, or a video surface.
        val location = IntArray(2)
        view.getLocationInWindow(location)
        val source = Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        val windowBounds = Rect(0, 0, activity.window.decorView.width, activity.window.decorView.height)
        if (!source.intersect(windowBounds) || source.isEmpty) {
            onComplete()
            return
        }
        val targetWidth = 240
        val targetHeight = (source.height().toFloat() / source.width() * targetWidth)
            .toInt().coerceIn(180, 480)
        val preview = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val generation = ++previewCaptureGeneration
        var completionSent = false
        val finish = {
            if (!completionSent) {
                completionSent = true
                onComplete()
            }
        }
        // A lost surface must not leave the Tabs button waiting indefinitely for PixelCopy.
        val timeout = Runnable { finish() }
        mainHandler.postDelayed(timeout, 750L)
        try {
            PixelCopy.request(activity.window, source, preview, { result ->
                mainHandler.removeCallbacks(timeout)
                if (result == PixelCopy.SUCCESS && !destroyed && generation == previewCaptureGeneration &&
                    previewCaptureAllowed() && TabThumbnailCache.isUsablePreview(preview)
                ) {
                    thumbnail = preview
                    TabThumbnailMemoryRegistry.touch(thumbnailKey) { thumbnail = null }
                    if (!isPrivate) TabThumbnailCache.save(tabContext, thumbnailKey, preview)
                } else {
                    preview.recycle()
                }
                finish()
            }, mainHandler)
        } catch (_: IllegalArgumentException) {
            mainHandler.removeCallbacks(timeout)
            preview.recycle()
            finish()
        }
    }

    private fun schedulePreviewCapture() {
        mainHandler.removeCallbacks(previewCaptureRunnable)
        if (!destroyed && !isShowingHome) mainHandler.postDelayed(previewCaptureRunnable, 450L)
    }

    fun deleteThumbnail() {
        previewCaptureGeneration++
        mainHandler.removeCallbacks(previewCaptureRunnable)
        thumbnail = null
        TabThumbnailMemoryRegistry.remove(thumbnailKey)
        TabThumbnailCache.remove(tabContext, thumbnailKey)
    }

    /** Lazy-grid items may be disposed while offscreen; restore their bounded cached preview. */
    suspend fun restoreThumbnailIfMissing() {
        if (destroyed || isPrivate || thumbnail != null) return
        val generation = previewCaptureGeneration
        val restored = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { TabThumbnailCache.load(tabContext.applicationContext, thumbnailKey) }.getOrNull()
        } ?: return
        if (destroyed || generation != previewCaptureGeneration || thumbnail != null) {
            restored.recycle()
            return
        }
        thumbnail = restored
        TabThumbnailMemoryRegistry.touch(thumbnailKey) { thumbnail = null }
    }

    /** Invalidates page-bound bridge tokens before releasing the underlying WebView. */
    internal val canRetireEngine: Boolean get() = !destroyed && !rendererUnavailable &&
        !isLoading && !hasPlayingMedia && rendererTransferCount == 0 && popupProxies.isEmpty() &&
        com.miowser.app.downloader.DownloadQueue.downloads.none {
            it.kind == com.miowser.app.downloader.DownloadKind.WEBVIEW_RESOURCE &&
                it.rendererTransfer != null && it.pageUrl == url &&
                it.status in setOf(com.miowser.app.downloader.DownloadStatus.QUEUED,
                    com.miowser.app.downloader.DownloadStatus.DOWNLOADING,
                    com.miowser.app.downloader.DownloadStatus.PAUSED)
        }

    internal fun retireBackgroundEngine() {
        val view = liveWebView ?: return
        if (!canRetireEngine) return
        // Process-local, bounded navigation state, including private tabs; never a backup/session file.
        if (view.copyBackForwardList().size <= 32) runCatching {
            val state = android.os.Bundle()
            if (view.saveState(state) != null) controller.rememberNavigation(id, state)
        }
        retireEngineCallbacks?.invoke()
        retireEngineCallbacks = null
        previewCaptureGeneration++
        mainHandler.removeCallbacks(previewCaptureRunnable)
        clearDetections()
        cancelRoutedPopup()
        lastFinishedUrl = null
        controller.release(id, view)
    }

    fun destroy() {
        if (destroyed) return
        destroyed = true
        controller.takeNavigation(id)
        previewCaptureGeneration++
        mainHandler.removeCallbacks(previewCaptureRunnable)
        TabThumbnailMemoryRegistry.remove(thumbnailKey)
        retireEngineCallbacks?.invoke()
        retireEngineCallbacks = null
        popupProxies.values.toList().forEach { it() }
        clearDetections()
        controller.release(id)
    }

    private fun handleRendererGone(view: WebView, crashed: Boolean) {
        if (liveWebView !== view) return
        blockedPageInfo = null
        rendererUnavailable = true
        retireEngineCallbacks?.invoke()
        retireEngineCallbacks = null
        previewCaptureGeneration++
        mainHandler.removeCallbacks(previewCaptureRunnable)
        clearDetections()
        cancelRoutedPopup()
        // Related proxies receive their own crash callback. Retire our pending routes as
        // well so their timeout/navigation cannot fire after the owning page has failed.
        popupProxies.values.toList().forEach { it() }
        isLoading = false
        canGoBack = false
        canGoForward = false
        showJumpToTop = false
        lastFinishedUrl = null
        pageError = if (crashed) "This page stopped unexpectedly. Reload to open it again."
            else "This page was closed to free memory. Reload to open it again."
        onRendererUnavailable(id)
        controller.release(id, view)
    }

    /** Recovery is explicit; switching to an affected tab must not silently reload it. */
    fun reload() {
        if (destroyed || isShowingHome) return
        blockedPageInfo = null
        rendererUnavailable = false
        pageError = null
        lastFinishedUrl = null
        clearDetections()
        val view = liveWebView
        if (view != null) view.reload() else ensureWebView()
    }

    // Appends a new detection, or replaces an existing one sharing the same URL — needed because
    // seenGrabberUrls only prevents duplicate probes within one "generation" (between
    // onNewPageStarted clears); an async network probe already in flight when a clear happens
    // can still deliver its result afterward, alongside a fresh redetection of the same URL from
    // the new generation. Two DetectedMedia entries with the same url would otherwise both reach
    // DetectedMediaSheet's LazyColumn, which keys rows by url and crashes ("Key ... was already
    // used") the moment that happens — caught via a real on-device crash tapping the sheet open
    // after a live HLS page had re-detected its own manifest.
    private fun markGrabberSeen(address: String): Boolean = synchronized(seenGrabberUrls) {
        address.length <= 8192 && seenGrabberUrls.size < 256 && seenGrabberUrls.add(address)
    }

    private fun addOrReplaceDetection(media: DetectedMedia) {
        if (media.url.length > 8192) return
        val existingIndex = detectionIndexes[media.url] ?: -1
        if (existingIndex >= 0) {
            detectedMedia[existingIndex] = mergeDetectedMedia(detectedMedia[existingIndex], media)
        } else if (detectedMedia.size < 128) {
            detectionIndexes[media.url] = detectedMedia.size
            detectedMedia.add(media)
        }
    }

    // Shared by both paths that find a *candidate* URL rather than already having real headers
    // for it (unlike the DownloadListener's direct-file branch, which doesn't need this):
    // shouldInterceptRequest's network classification, and the DownloadListener's own manifest
    // branch (a direct navigation to an .m3u8/.mpd still benefits from real body-verification,
    // resolution, and variant-count extraction, not just the headers WebView already has).
    private fun probeCandidate(candidateUrl: String, guessedType: MediaType, userAgent: String,
                               sourcePageUrl: String = this@Tab.url,
                               requestReferer: String? = null,
                               requestOrigin: String? = null,
                               sourceIsAlive: () -> Boolean = { true }) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            val generation = detectionGeneration.get()
            mainHandler.post {
                if (!destroyed && generation == detectionGeneration.get() && sourceIsAlive())
                    probeCandidate(candidateUrl, guessedType, userAgent, sourcePageUrl,
                        requestReferer, requestOrigin, sourceIsAlive)
            }
            return
        }
        probeHandles.removeAll { it.stopped.get() || it.future?.isDone == true }
        if (candidateUrl.length > 8192 || sourcePageUrl.length > 8192 || probeHandles.size >= 48) return
        val generation = detectionGeneration.get()
        var handle: com.miowser.app.grabber.MediaProbeHandle? = null
        handle = MediaProbe.probe(
            url = candidateUrl,
            pageUrl = sourcePageUrl,
            userAgent = userAgent,
            guessedType = guessedType
        ) { result ->
            handle?.let { probeHandles.remove(it) }
            // onResult is always main-thread-dispatched (see MediaProbe), so reading
            // this@Tab.title here is safe regardless of which thread called probeCandidate.
            if (result != null && generation == detectionGeneration.get() && !destroyed && sourceIsAlive()) {
                val observed = detectedMedia.firstOrNull { it.url == candidateUrl }
                addOrReplaceDetection(result.copy(
                    pageUrl = this@Tab.url,
                    pageTitle = this@Tab.title,
                    requestReferer = observed?.requestReferer ?: requestReferer,
                    requestOrigin = observed?.requestOrigin ?: requestOrigin
                ))
            }
        }
        probeHandles.add(handle)
        if (generation != detectionGeneration.get() || destroyed || !sourceIsAlive()) {
            probeHandles.remove(handle)
            handle.cancel()
        }
    }

    /** Manual crawl; static HTML is also scanned once automatically after each successful page load. */
    fun startDiscovery(depth: Int, pageLimit: Int) {
        val startUrl = lastFinishedUrl ?: return
        if (isBrowserHomeUrl(startUrl) || pageError != null || destroyed) return
        launchDiscovery(startUrl, depth, pageLimit, showProgress = true)
    }

    fun cancelDiscovery() {
        discoveryGeneration.incrementAndGet()
        pendingAutomaticDiscovery?.let(mainHandler::removeCallbacks)
        pendingAutomaticDiscovery = null
        discoveryHandle?.cancel()
        discoveryHandle = null
        discoveryProgress = null
    }

    private fun launchDiscovery(startUrl: String, depth: Int, pageLimit: Int, showProgress: Boolean) {
        cancelDiscovery()
        val generation = detectionGeneration.get()
        val scanGeneration = discoveryGeneration.get()
        val userAgent = liveWebView?.settings?.userAgentString ?: return
        if (showProgress) discoveryProgress = DiscoveryProgress(0, pageLimit, false)
        discoveryHandle = StaticMediaDiscovery.scan(startUrl, userAgent, depth, pageLimit,
            onCandidate = { candidate, type, sourcePageUrl ->
                // The scan callback is on its worker; filtering can be expensive and does
                // not need WebView or Compose access.
                val blocked = AppSettings.current.adBlockEnabled &&
                    AdBlock.engine.decide(sourcePageUrl, candidate, emptyMap(), false) != null
                mainHandler.post {
                    if (destroyed || generation != detectionGeneration.get() ||
                        scanGeneration != discoveryGeneration.get()) return@post
                    if (blocked) return@post
                    if (markGrabberSeen(candidate)) {
                        probeCandidate(candidate, type, userAgent, sourcePageUrl)
                    }
                }
            },
            onProgress = { progress ->
                if (showProgress) mainHandler.post {
                    if (destroyed || generation != detectionGeneration.get() ||
                        scanGeneration != discoveryGeneration.get()) return@post
                    if (showProgress) discoveryProgress = progress
                }
            }
        )
    }

    // Captured under its own name before entering the apply block below: View (WebView's
    // superclass) exposes its own `context` property, which would otherwise silently shadow
    // this constructor parameter for any unqualified `context` reference inside that block —
    // the same class of shadowing bug already documented above for `url` (WebView's own `url`
    // property vs. this@Tab.url), just for a name that can't be disambiguated with a `this@Tab.`
    // qualifier since it's a constructor parameter rather than a declared property.
    private val tabContext = context

    /** A lookup never creates a renderer. Only navigation/activation/popup transport may acquire one. */
    val liveWebView: WebView? get() = controller.peek(id)

    fun ensureWebView(): WebView {
        check(!destroyed) { "Tab is closed" }
        check(!rendererUnavailable) { "Reload this tab before opening its renderer" }
        return controller.acquire(id) { createWebView(url) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(initialTarget: String): WebView {
        // A retained WebView keeps its actual scale across navigation; a newly created one does not.
        pageZoomFactor = 1f
        val view = WebView(tabContext)
        val alive = AtomicBoolean(true)
        val permissionRequests = mutableSetOf<PermissionRequest>()
        return try { view.apply {
            CookieManager.getInstance().setAcceptCookie(AppSettings.current.cookiesEnabled)
            settings.javaScriptEnabled = AppSettings.current.javaScriptEnabled
            mobileUserAgent = settings.userAgentString
            if (isDesktopMode) {
                settings.userAgentString = desktopUserAgent(mobileUserAgent!!)
                settings.useWideViewPort = true
                settings.loadWithOverviewMode = true
            }
            settings.domStorageEnabled = true
            settings.textZoom = textZoomPercent
            settings.setSupportZoom(true)
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setGeolocationEnabled(true)
            settings.allowFileAccess = false
            settings.allowFileAccessFromFileURLs = false
            settings.allowUniversalAccessFromFileURLs = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.safeBrowsingEnabled = true
            settings.setSupportMultipleWindows(AppSettings.current.popupsEnabled)
            settings.javaScriptCanOpenWindowsAutomatically = AppSettings.current.popupsEnabled
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            // Request interception runs off-main-thread and cannot read WebView settings there.
            // Keep this renderer's agent current when the desktop switch changes it.
            val cachedUserAgent = java.util.concurrent.atomic.AtomicReference(settings.userAgentString)
            requestUserAgent = cachedUserAgent
            addJavascriptInterface(CosmeticBridge(AdBlock.engine), "MiowserAdBlock")
            installCosmeticInjectionScript() // must run before loadUrl()
            desktopViewportScript = if (isDesktopMode) installDesktopViewportScript() else null

            val bridge = RendererResourceDownloader(tabContext)
            rendererResourceDownloader = bridge
            addJavascriptInterface(bridge, "MiowserDownloadBridge")
            installRendererBlobFrameScript()

            addJavascriptInterface(
                GrabberBridge(onActivity = { playing -> if (alive.get()) hasPlayingMedia = playing }) { src, duration, width, height, frameUrl, isAudio ->
                    if (!alive.get()) return@GrabberBridge
                    val generation = detectionGeneration.get()
                    mainHandler.post {
                        if (!alive.get() || generation != detectionGeneration.get() || destroyed) return@post
                        // Marking it seen still prevents a redundant HTTP probe later, but every DOM
                        // metadata update is merged: loadedmetadata commonly arrives after the URL
                        // was first observed and carries the missing duration/dimensions.
                        val firstSeen = markGrabberSeen(src)
                        val rendererKind = rendererResourceKind(src)
                        val dataMime = src.takeIf { rendererKind == RendererResourceKind.DATA }
                            ?.substringAfter("data:", "")
                            ?.substringBefore(',')
                            ?.substringBefore(';')
                        val detectedType = dataMime?.let(MediaUrlClassifier::classifyByMimeType)
                            ?: MediaUrlClassifier.classify(src)
                            ?: if (isAudio) MediaType.AUDIO else MediaType.UNKNOWN
                        val frameReferer = frameUrl.takeIf {
                            it.startsWith("https://", ignoreCase = true) ||
                                it.startsWith("http://", ignoreCase = true)
                        }
                        addOrReplaceDetection(
                            DetectedMedia(
                                url = src,
                                pageUrl = this@Tab.url,
                                type = detectedType,
                                // Renderer URLs are downloadable through Miowser's tokenized bridge,
                                // but aren't network-fetchable by the rich player. Keep them
                                // unconfirmed here so row-tap never hands an unusable blob: URL to
                                // Media3; DetectedMediaPage independently enables its download icon.
                                isConfirmed = rendererKind == null,
                                sizeBytes = null,
                                isSizeApproximate = false,
                                durationSeconds = duration.takeIf { it.isFinite() && it > 0.0 },
                                variantCount = null,
                                // The element's own rendered dimensions — real, DOM-sourced data,
                                // unlike the manifest-derived resolution on the network-probe path.
                                resolution = if (width > 0 && height > 0) "${width}x${height}" else null,
                                isEncrypted = false,
                                pageTitle = this@Tab.title,
                                requestReferer = frameReferer.takeIf { rendererKind != null }
                            )
                        )
                        if (firstSeen && rendererKind == null) {
                            probeCandidate(src, detectedType, cachedUserAgent.get(),
                                sourcePageUrl = frameReferer ?: this@Tab.url,
                                requestReferer = frameReferer)
                        }
                    }
                }.also { grabberBridge = it },
                "MiowserGrabber"
            )
            installVideoSnifferScript() // must also run before loadUrl()
            // Add-on telemetry only; the existing grabber and playback entry points stay intact.
            liveVideoTracker = LiveVideoTracker(this@Tab.id, this, videoButtonPolicy, browserVideoPlayer,
                onFullscreenFailure = {
                    if (alive.get() && videoButtonPolicy.enabled) android.widget.Toast.makeText(tabContext,
                        "This page couldn't open video fullscreen. Its original player is still available.",
                        android.widget.Toast.LENGTH_LONG).show()
                }).also { it.install() }

            // A deliberate depth threshold: ordinary small scrolls should not grow another floating
            // control over the page. One visible WebView height is an intuitive "about one screen"
            // threshold and naturally adapts to portrait, landscape, and split-screen layouts.
            setOnScrollChangeListener { view, _, scrollY, _, _ ->
                if (!alive.get()) return@setOnScrollChangeListener
                showJumpToTop = view.height > 0 && scrollY >= view.height
                // Refresh after scrolling settles while this tab is still on screen. In particular,
                // a later switch must use this saved viewport instead of trying to copy a hidden tab.
                // Do not allocate/copy/compress previews during a fling. The debounced
                // capture runs after scrolling stops; opening Tabs can still request one.
                schedulePreviewCapture()
                pendingAutomaticDiscovery?.let { pending ->
                    mainHandler.removeCallbacks(pending)
                    mainHandler.postDelayed(pending, 1_000L)
                }
            }

            // The other half of the "no downloader embedded" gap: without this, WebView falls back
            // to its own default handling — historically observed as the OS just downloading the raw
            // file — for any URL whose response it can't render itself, which includes a direct
            // navigation (typed in the address bar, or a plain <a href> link) straight to an .mpd,
            // .m3u8, or any direct media file. shouldInterceptRequest's network-classify path only
            // catches *sub-resources* a page requests while rendering; a direct navigation *to* the
            // media file never gets that far; this is the real browser↔downloader handoff point 1DM
            // itself uses (project memory: 1dm-browser-downloader-handoff.md,
            // DownloadHandler.onDownloadStart). WebView only fires this once it already knows the
            // response can't be rendered, so contentLength/mimeType here are real, already-fetched
            // header data — no separate verification probe needed for a direct file the way
            // onCandidateMediaUrl below needs one for a URL merely seen in passing.
            setDownloadListener { downloadUrl, downloadUserAgent, contentDisposition, mimeType, contentLength ->
                if (!alive.get()) return@setDownloadListener
                val guessedType = MediaUrlClassifier.classify(downloadUrl)
                    ?: MediaUrlClassifier.classifyByMimeType(mimeType)
                val rendererKind = rendererResourceKind(downloadUrl)
                if (markGrabberSeen(downloadUrl)) {
                    if (guessedType != null && MediaUrlClassifier.isManifestType(guessedType)) {
                        probeCandidate(downloadUrl, guessedType, cachedUserAgent.get())
                    } else {
                        addOrReplaceDetection(
                            DetectedMedia(
                                url = downloadUrl,
                                pageUrl = this@Tab.url,
                                type = guessedType ?: MediaType.UNKNOWN,
                                isConfirmed = rendererKind == null,
                                sizeBytes = contentLength.takeIf { it > 0 },
                                isSizeApproximate = false,
                                durationSeconds = null,
                                variantCount = null,
                                resolution = null,
                                isEncrypted = false,
                                pageTitle = this@Tab.title
                            )
                        )
                        if (guessedType != null && rendererKind == null) {
                            // DownloadListener provides a quick row immediately; the bounded probe
                            // then enriches it with redirect/name/MIME/size and MP4 metadata.
                            probeCandidate(downloadUrl, guessedType, cachedUserAgent.get())
                        }
                    }
                }

                // A bare manifest navigation remains a grabber detection so pasting an .m3u8/.mpd
                // into the address bar doesn't unexpectedly begin a download. If the server marks
                // that same manifest as an attachment, or this is any ordinary/direct file, this is
                // a genuine webpage download and should enter Miowser's queue immediately.
                val isBareManifestNavigation = guessedType?.let(MediaUrlClassifier::isManifestType) == true &&
                    contentDisposition.isNullOrBlank()
                if (!isBareManifestNavigation) {
                    onDownloadRequested(
                        WebDownloadRequest(
                            originatingTabId = this@Tab.id,
                            url = downloadUrl,
                            pageUrl = this@Tab.url,
                            pageTitle = this@Tab.title,
                            userAgent = downloadUserAgent?.takeIf(String::isNotBlank) ?: cachedUserAgent.get(),
                            contentDisposition = contentDisposition,
                            mimeType = mimeType,
                            contentLength = contentLength
                        )
                    )
                }
            }

            val client = MiowserWebViewClient(
                isPrivate = isPrivate,
                onCertificateError = { target, handler, error ->
                    isLoading = false
                    blockedPageInfo = null
                    certificateErrorUrl = target
                    certificateBypassed = false
                    pageError = "This site's security certificate could not be verified."
                    onCertificateErrorRequested(this@Tab.id, target, handler, error)
                },
                onInsecureNavigationRequested = { target -> onInsecureNavigationRequested(this@Tab.id, target) },
                shouldBlockCompanionNavigation = ::shouldBlockCompanionNavigation,
                onRequestBlocked = { requestUrl ->
                    blockedRequests.incrementAndGet()
                    com.miowser.app.adblock.SiteProtection.host(requestUrl)?.let { host ->
                        if (blockedHosts.size < 32 || blockedHosts.containsKey(host)) {
                            blockedHosts.computeIfAbsent(host) { java.util.concurrent.atomic.AtomicInteger(0) }.incrementAndGet()
                        }
                    }
                },
                onScaleChanged = { oldScale, newScale ->
                    if (oldScale > 0f && newScale > 0f) {
                        pageZoomFactor = (pageZoomFactor * newScale / oldScale).coerceIn(0.25f, 5f)
                    }
                },
                adBlockEngine = AdBlock.engine,
                onNavigationStateChanged = { back, forward ->
                    canGoBack = back
                    canGoForward = forward
                },
                onUrlChanged = { newUrl ->
                    // Qualified with this@Tab: unqualified `url` here would resolve to WebView's
                    // own read-only `url` synthetic property (from getUrl()) via the enclosing
                    // apply{} receiver, not Tab's — caught by the compiler as "val cannot be
                    // reassigned" rather than silently doing the wrong thing.
                    val isHome = isBrowserHomeUrl(newUrl)
                    isShowingHome = isHome
                    this@Tab.url = if (isHome) BROWSER_HOME_URL else newUrl
                    if (isHome) this@Tab.title = "Home"
                    if (!isAddressBarFocused) {
                        addressBarText = TextFieldValue(if (isHome) "" else newUrl)
                    }
                },
                onNewPageStarted = { clearDetections() },
                onPageCommitted = { liveVideoTracker?.onPageCommitted() },
                onPageStarted = {
                    isLoading = true
                    pageError = null
                    blockedPageInfo = null
                    if (certificateErrorUrl?.let(com.miowser.app.adblock.SiteProtection::host) !=
                        com.miowser.app.adblock.SiteProtection.host(this@Tab.url)) {
                        certificateErrorUrl = null
                        certificateBypassed = false
                    }
                    lastFinishedUrl = null
                    if (routedPopupTarget == this@Tab.url) interceptingPopupUntil = 0L
                },
                onPageError = { message ->
                    isLoading = false
                    pageError = message
                    blockedPageInfo = null
                },
                onMainFrameBlocked = { info ->
                    isLoading = false
                    blockedPageInfo = info
                },
                onRendererGone = ::handleRendererGone,
                onExternalNavigation = onExternalNavigation,
                onCandidateMediaUrl = { candidateUrl, guessedType, referer, origin ->
                    if (!alive.get()) return@MiowserWebViewClient
                    val browserReferer = referer?.takeIf {
                        it.startsWith("https://", ignoreCase = true) ||
                            it.startsWith("http://", ignoreCase = true)
                    }
                    val browserOrigin = origin?.takeIf {
                        it.startsWith("https://", ignoreCase = true) ||
                            it.startsWith("http://", ignoreCase = true)
                    }
                    if (markGrabberSeen(candidateUrl)) {
                        probeCandidate(candidateUrl, guessedType, cachedUserAgent.get(),
                            sourcePageUrl = browserReferer ?: this@Tab.url,
                            requestReferer = browserReferer,
                            requestOrigin = browserOrigin,
                            sourceIsAlive = alive::get)
                    } else if (browserReferer != null || browserOrigin != null) {
                        val generation = detectionGeneration.get()
                        mainHandler.post {
                            if (!alive.get() || generation != detectionGeneration.get() || destroyed) return@post
                            val existing = detectedMedia.firstOrNull { it.url == candidateUrl }
                            if (existing != null) addOrReplaceDetection(existing.copy(
                                requestReferer = browserReferer ?: existing.requestReferer,
                                requestOrigin = browserOrigin ?: existing.requestOrigin
                            ))
                        }
                    }
                },
                onLikelyDownloadNavigation = { downloadUrl ->
                    onDownloadRequested(
                        WebDownloadRequest(
                            originatingTabId = this@Tab.id,
                            url = downloadUrl,
                            pageUrl = this@Tab.url,
                            pageTitle = this@Tab.title,
                            userAgent = cachedUserAgent.get(),
                            contentDisposition = null,
                            mimeType = null,
                            contentLength = -1L
                        )
                    )
                },
                onPageLoadFinished = { loadedUrl, loadedTitle ->
                    isLoading = false
                    liveVideoTracker?.onPageCommitted()
                    if (isDesktopMode && desktopViewportScript == null) {
                        view.applyDesktopViewportNow()
                    }
                    if (pageError == null) lastFinishedUrl = loadedUrl
                    if (pageError == null) schedulePreviewCapture()
                    if (pageError == null && !isBrowserHomeUrl(loadedUrl)) {
                        pendingAutomaticDiscovery?.let(mainHandler::removeCallbacks)
                        val generation = detectionGeneration.get()
                        val pending = Runnable {
                            pendingAutomaticDiscovery = null
                            if (!destroyed && generation == detectionGeneration.get()) {
                                launchDiscovery(loadedUrl, depth = 0, pageLimit = 1, showProgress = false)
                            }
                        }
                        pendingAutomaticDiscovery = pending
                        mainHandler.postDelayed(pending, 1_000L)
                    }
                    if (pageError == null && !isPrivate && !isBrowserHomeUrl(loadedUrl)) {
                        HistoryLog.recordVisit(tabContext, loadedUrl, loadedTitle)
                    }
                }
            )
            webViewClient = client
            retireEngineCallbacks = {
                alive.set(false)
                grabberBridge?.retire()
                liveVideoTracker?.retire()
                liveVideoTracker = null
                client.retire()
                bridge.retire("The originating page is no longer available. Reload it and start the download again.")
                permissionRequests.toList().forEach { request ->
                    onWebPermissionCanceled(request)
                    runCatching { request.deny() }
                }
                permissionRequests.clear()
            }
            webChromeClient = object : WebChromeClient() {
                private var fullscreenLease: FullscreenCallbackLease? = null
                override fun onShowCustomView(customView: View, callback: CustomViewCallback) {
                    if (!alive.get() || fullscreenLease?.closed == false) {
                        runCatching { callback.onCustomViewHidden() }; return
                    }
                    val lease = FullscreenCallbackLease { callback.onCustomViewHidden() }
                    fullscreenLease = lease
                    onFullscreenVideoRequested(this@Tab.id, customView, CustomViewCallback { lease.close() })
                }

                override fun onHideCustomView() {
                    if (!alive.get() || fullscreenLease?.closed != false) return
                    liveVideoTracker?.onNativeFullscreenHidden()
                    onFullscreenVideoHidden(this@Tab.id)
                }

                override fun onShowFileChooser(
                    webView: WebView,
                    filePathCallback: ValueCallback<Array<Uri>>,
                    fileChooserParams: FileChooserParams
                ): Boolean {
                    if (!alive.get()) { filePathCallback.onReceiveValue(null); return true }
                    onFileChooserRequested(filePathCallback, fileChooserParams)
                    return true
                }

                override fun onPermissionRequest(request: PermissionRequest) {
                    if (!alive.get()) { request.deny(); return }
                    permissionRequests.add(request)
                    onWebPermissionRequested(request)
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest) {
                    permissionRequests.remove(request)
                    onWebPermissionCanceled(request)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String,
                    callback: GeolocationPermissions.Callback
                ) {
                    if (!alive.get()) { callback.invoke(origin, false, false); return }
                    onGeolocationRequested(origin, callback)
                }

                override fun onGeolocationPermissionsHidePrompt() {
                    if (!alive.get()) return
                    onGeolocationCanceled()
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    // Page-supplied text can contain tokens or personal data. Log location only.
                    com.miowser.app.diagnostics.SafeLog.debug(CONSOLE_TAG,
                        "Page console ${consoleMessage.messageLevel()} at ${com.miowser.app.diagnostics.safeDiagnosticUrl(consoleMessage.sourceId())}:${consoleMessage.lineNumber()}")
                    return true
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    if (!alive.get()) return
                    if (!isShowingHome) {
                        this@Tab.title = title?.takeIf { it.isNotBlank() } ?: "New Tab"
                        if (!isPrivate && !title.isNullOrBlank()) {
                            SpeedDials.updateAutoTitle(context, view.url ?: this@Tab.url, title)
                        }
                    }
                }

                override fun onReceivedIcon(view: WebView, icon: Bitmap) {
                    if (!alive.get()) return
                    if (!isPrivate && !isShowingHome) {
                        SpeedDialSiteMetadataStore.cacheVisitedIcon(context, view.url ?: this@Tab.url, icon)
                    }
                }

                // target="_blank" links and window.open() land here rather than navigating this
                // tab in place — requires setSupportMultipleWindows(true) above, otherwise WebView
                // never calls this at all. Must return true immediately (WebView needs a synchronous
                // answer to "will you provide a window"), but the actual tab creation and
                // resultMsg completion happen later, once BrowserScreen has decided — see
                // onNewWindowRequested's own doc comment above.
                override fun onCreateWindow(
                    view: WebView,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message
                ): Boolean {
                    if (!alive.get()) return false
                    val host = com.miowser.app.adblock.SiteProtection.host(this@Tab.url)
                    val mode = host?.let(com.miowser.app.adblock.SiteProtection::mode)
                    if (!isUserGesture && AppSettings.current.adBlockEnabled &&
                        mode != com.miowser.app.adblock.SiteProtectionMode.OFF) {
                        blockedPopups.incrementAndGet()
                        return false
                    }
                    if (isUserGesture && host != null &&
                        com.miowser.app.adblock.SiteProtection.openPageLinksHere(host)) {
                        routePopupIntoCurrentTab(resultMsg)
                        return true
                    }
                    onNewWindowRequested(this@Tab.id, isUserGesture, resultMsg)
                    return true
                }
            }

            // hitTestResult reliably tells us whether the long-pressed node is a link, but its `extra`
            // value is ambiguous for SRC_IMAGE_ANCHOR_TYPE on real WebView builds: some return the
            // enclosing anchor and others return the image source. requestFocusNodeHref resolves the
            // focused DOM node and returns separate "url" (the <a href>) and "src" values, so opening
            // a video-card thumbnail follows the card link rather than loading its JPEG.
            setOnLongClickListener {
                if (!alive.get()) return@setOnLongClickListener false
                val result = hitTestResult
                if (
                    result.type != WebView.HitTestResult.SRC_ANCHOR_TYPE &&
                    result.type != WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
                ) {
                    return@setOnLongClickListener false
                }

                val plainAnchorFallback = result.extra.takeIf {
                    result.type == WebView.HitTestResult.SRC_ANCHOR_TYPE
                }
                val hrefHandler = Handler(Looper.getMainLooper()) { message ->
                    if (!alive.get()) return@Handler true
                    val href = message.data.getString("url")?.takeIf(String::isNotBlank)
                        ?: plainAnchorFallback
                    if (href != null) onLinkLongPressed(
                        href, message.data.getString("title")?.trim()?.takeIf(String::isNotEmpty)
                    )
                    true
                }
                requestFocusNodeHref(hrefHandler.obtainMessage())
                true
            }

            if (initialTarget.isNotBlank() && !isBrowserHomeUrl(initialTarget)) {
                if (HttpNavigationApproval.requiresWarning(initialTarget, isPrivate)) {
                    onInsecureNavigationRequested(this@Tab.id, initialTarget)
                } else {
                    val state = controller.takeNavigation(this@Tab.id)
                    val restored = state?.let { runCatching { restoreState(it) }.getOrNull() }
                    if (restored == null) loadUrl(initialTarget)
                    else {
                        this@Tab.canGoBack = canGoBack()
                        this@Tab.canGoForward = canGoForward()
                    }
                }
            }
        } } catch (failure: Exception) {
            alive.set(false)
            retireEngineCallbacks?.invoke()
            retireEngineCallbacks = null
            view.destroy()
            throw failure
        }
    }

    fun navigateTo(targetUrl: String) {
        controller.takeNavigation(id)
        if (targetUrl.isBlank()) return
        if (HttpNavigationApproval.requiresWarning(targetUrl, isPrivate)) {
            onInsecureNavigationRequested(id, targetUrl)
            return
        }
        lastFinishedUrl = null
        rendererUnavailable = false
        pageError = null
        blockedPageInfo = null
        clearDetections()
        // The native homepage has no AddressBar. If it was removed while focused, its last
        // focus callback may never clear this flag; then WebView navigation callbacks cannot
        // publish the URL of a speed dial (or another programmatic navigation) to the bar.
        isAddressBarFocused = false
        isShowingHome = isBrowserHomeUrl(targetUrl)
        if (isShowingHome) {
            title = "Home"
            url = BROWSER_HOME_URL
            addressBarText = TextFieldValue("")
        }
        url = targetUrl
        addressBarText = TextFieldValue(if (isShowingHome) "" else targetUrl)
        val engine = liveWebView
        if (engine != null) engine.loadUrl(targetUrl)
        else if (!isShowingHome) ensureWebView()
    }

    fun showHome() = navigateTo(BROWSER_HOME_URL)

    fun markActive(now: Long = System.currentTimeMillis()) {
        controller.touch(id)
        lastActiveAt = now
    }

    /** A tab switch does not reload WebView, so restore a cleared history entry on reactivation. */
    fun recordVisibleVisitIfMissing() {
        val finishedUrl = lastFinishedUrl ?: return
        if (shouldRecordRevisitedPage(
                currentUrl = url,
                finishedUrl = finishedUrl,
                isPrivate = isPrivate,
                isHome = isShowingHome,
                hasPageError = pageError != null,
                alreadyInHistory = HistoryLog.entries.any { it.url == finishedUrl }
            )) {
            HistoryLog.recordVisit(tabContext, finishedUrl, title)
        }
    }

    fun applyBrowserPreferences(preferences: Settings) {
        val webView = liveWebView ?: return
        webView.settings.javaScriptEnabled = preferences.javaScriptEnabled
        webView.settings.setSupportMultipleWindows(preferences.popupsEnabled)
        webView.settings.javaScriptCanOpenWindowsAutomatically = preferences.popupsEnabled
    }

    fun jumpToTop() {
        liveWebView?.evaluateJavascript("window.scrollTo({top: 0, left: 0, behavior: 'smooth'});", null)
    }

    /** Stops page-owned media before Miowser's rich player takes over audio/video playback. */
    fun pausePageMedia() {
        liveWebView?.evaluateJavascript(
            """
            (() => {
                document.querySelectorAll('video, audio').forEach(media => {
                    try { media.pause(); } catch (_) {}
                });
            })();
            """.trimIndent(),
            null
        )
    }

    companion object {
        private val idCounter = AtomicLong(0)
        fun nextId(): Long = idCounter.getAndIncrement()
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
