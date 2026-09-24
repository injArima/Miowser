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
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.miowser.app.adblock.AdBlock
import com.miowser.app.adblock.CosmeticBridge
import com.miowser.app.downloader.Download
import com.miowser.app.downloader.RendererResourceDownloader
import com.miowser.app.downloader.RendererResourceKind
import com.miowser.app.downloader.rendererResourceKind
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.GrabberBridge
import com.miowser.app.grabber.MediaProbe
import com.miowser.app.grabber.installVideoSnifferScript
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

private const val CONSOLE_TAG = "Miowser/Console"

/**
 * One browser tab: its own persistent WebView plus the Compose-observable state derived from
 * it (title, URL, loading progress, back-stack availability, address-bar editing state, and now
 * the grabber's detected-media list for this page).
 *
 * WebViews are created once per tab and kept alive for the tab's lifetime. Switching tabs
 * shows/hides them (see BrowserScreen's tab Box, which keeps every tab's AndroidView composed
 * and toggles View.VISIBLE/INVISIBLE) rather than destroying and recreating one — "each with
 * its own persistent WebView, not destroyed on switch" was an explicit target.
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
 * @param onLinkLongPressed called with a link's URL when the user long-presses one — the entry
 *   point for the long-press context menu (open in new/background tab, copy, share).
 */
class Tab(
    val id: Long,
    context: Context,
    initialUrl: String,
    val openerTabId: Long?,
    val isPrivate: Boolean = false,
    val thumbnailKey: String = UUID.randomUUID().toString(),
    initialTitle: String = "",
    initialLastActiveAt: Long = System.currentTimeMillis(),
    canCapturePreview: () -> Boolean = { true },
    onNewWindowRequested: (openerTabId: Long, isUserGesture: Boolean, resultMsg: Message) -> Unit,
    onLinkLongPressed: (url: String) -> Unit,
    onDownloadRequested: (WebDownloadRequest) -> Unit,
    onFileChooserRequested: (ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams) -> Unit,
    onWebPermissionRequested: (PermissionRequest) -> Unit,
    onWebPermissionCanceled: (PermissionRequest) -> Unit,
    onGeolocationRequested: (String, GeolocationPermissions.Callback) -> Unit,
    onGeolocationCanceled: () -> Unit,
    onExternalNavigation: (String) -> Unit
) {
    var title by mutableStateOf(
        if (isBrowserHomeUrl(initialUrl)) "Home" else initialTitle.ifBlank { "New Tab" }
    )
    var url by mutableStateOf(if (isBrowserHomeUrl(initialUrl)) BROWSER_HOME_URL else "")
    var isShowingHome by mutableStateOf(isBrowserHomeUrl(initialUrl))
        private set
    var canGoBack by mutableStateOf(false)
    var canGoForward by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var loadProgress by mutableFloatStateOf(0f)
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
    /** Last successfully finished page, retained while its WebView stays alive across tab switches. */
    var lastFinishedUrl by mutableStateOf<String?>(null)
        private set

    /** Everything the grabber has found on the current page. Cleared on every new navigation. */
    val detectedMedia = mutableStateListOf<DetectedMedia>()
    var thumbnail by mutableStateOf(
        if (isPrivate) null else TabThumbnailCache.load(context, thumbnailKey)
    )
        private set
    var discoveryProgress by mutableStateOf<DiscoveryProgress?>(null)
        private set
    private var discoveryHandle: DiscoveryHandle? = null

    // Guards against re-probing/re-reporting the same URL (a page can request or expose the same
    // manifest/element more than once) — synchronized because network-level candidates arrive
    // from shouldInterceptRequest, which Chromium calls off the main thread.
    private val seenGrabberUrls = Collections.synchronizedSet(mutableSetOf<String>())
    private val detectionGeneration = AtomicLong(0L)
    private val discoveryGeneration = AtomicLong(0L)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val previewCaptureAllowed = canCapturePreview
    private var previewCaptureGeneration = 0L
    private var lastPreviewCaptureAt = 0L
    private val previewCaptureRunnable = Runnable { captureThumbnail() }
    private lateinit var rendererResourceDownloader: RendererResourceDownloader
    private var destroyed = false

    init {
        if (thumbnail != null) {
            TabThumbnailMemoryRegistry.touch(thumbnailKey) { thumbnail = null }
        }
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
        cancelDiscovery()
        detectionGeneration.incrementAndGet()
        detectedMedia.clear()
        seenGrabberUrls.clear()
        if (::rendererResourceDownloader.isInitialized) rendererResourceDownloader.invalidatePage()
    }

    /** Attaches this tab's live renderer to a blob:/data: queue item before it is enqueued. */
    fun attachRendererTransfer(download: Download) {
        val sourceUrl = download.sourceUrl
        download.rendererTransfer = { queued ->
            rendererResourceDownloader.run(webView, sourceUrl, queued)
        }
    }

    /** Captures the pixels currently shown inside the live WebView, at its real scroll position. */
    fun captureThumbnail(onComplete: () -> Unit = {}) {
        mainHandler.removeCallbacks(previewCaptureRunnable)
        val view = webView
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
    fun restoreThumbnailIfMissing() {
        if (destroyed || isPrivate || thumbnail != null) return
        val restored = TabThumbnailCache.load(tabContext, thumbnailKey) ?: return
        thumbnail = restored
        TabThumbnailMemoryRegistry.touch(thumbnailKey) { thumbnail = null }
    }

    /** Invalidates page-bound bridge tokens before releasing the underlying WebView. */
    fun destroy() {
        if (destroyed) return
        destroyed = true
        previewCaptureGeneration++
        mainHandler.removeCallbacks(previewCaptureRunnable)
        TabThumbnailMemoryRegistry.remove(thumbnailKey)
        cancelDiscovery()
        if (::rendererResourceDownloader.isInitialized) rendererResourceDownloader.invalidatePage()
        webView.destroy()
    }

    // Appends a new detection, or replaces an existing one sharing the same URL — needed because
    // seenGrabberUrls only prevents duplicate probes within one "generation" (between
    // onNewPageStarted clears); an async network probe already in flight when a clear happens
    // can still deliver its result afterward, alongside a fresh redetection of the same URL from
    // the new generation. Two DetectedMedia entries with the same url would otherwise both reach
    // DetectedMediaSheet's LazyColumn, which keys rows by url and crashes ("Key ... was already
    // used") the moment that happens — caught via a real on-device crash tapping the sheet open
    // after a live HLS page had re-detected its own manifest.
    private fun addOrReplaceDetection(media: DetectedMedia) {
        val existingIndex = detectedMedia.indexOfFirst { it.url == media.url }
        if (existingIndex >= 0) {
            detectedMedia[existingIndex] = mergeDetectedMedia(detectedMedia[existingIndex], media)
        } else {
            detectedMedia.add(media)
        }
    }

    // Shared by both paths that find a *candidate* URL rather than already having real headers
    // for it (unlike the DownloadListener's direct-file branch, which doesn't need this):
    // shouldInterceptRequest's network classification, and the DownloadListener's own manifest
    // branch (a direct navigation to an .m3u8/.mpd still benefits from real body-verification,
    // resolution, and variant-count extraction, not just the headers WebView already has).
    private fun probeCandidate(candidateUrl: String, guessedType: MediaType, userAgent: String,
                               sourcePageUrl: String = this@Tab.url) {
        val generation = detectionGeneration.get()
        MediaProbe.probe(
            url = candidateUrl,
            pageUrl = sourcePageUrl,
            userAgent = userAgent,
            guessedType = guessedType
        ) { result ->
            // onResult is always main-thread-dispatched (see MediaProbe), so reading
            // this@Tab.title here is safe regardless of which thread called probeCandidate.
            if (result != null && generation == detectionGeneration.get() && !destroyed) {
                addOrReplaceDetection(result.copy(pageTitle = this@Tab.title))
            }
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
        discoveryHandle?.cancel()
        discoveryHandle = null
        discoveryProgress = null
    }

    private fun launchDiscovery(startUrl: String, depth: Int, pageLimit: Int, showProgress: Boolean) {
        cancelDiscovery()
        val generation = detectionGeneration.get()
        val scanGeneration = discoveryGeneration.get()
        val userAgent = webView.settings.userAgentString
        if (showProgress) discoveryProgress = DiscoveryProgress(0, pageLimit, false)
        discoveryHandle = StaticMediaDiscovery.scan(startUrl, userAgent, depth, pageLimit,
            onCandidate = { candidate, type, sourcePageUrl ->
                mainHandler.post {
                    if (destroyed || generation != detectionGeneration.get() ||
                        scanGeneration != discoveryGeneration.get()) return@post
                    if (AppSettings.current.adBlockEnabled &&
                        AdBlock.engine.decide(sourcePageUrl, candidate, emptyMap(), false) != null) return@post
                    if (seenGrabberUrls.add(candidate)) {
                        probeCandidate(candidate, type, userAgent, sourcePageUrl)
                    }
                }
            },
            onProgress = { progress ->
                mainHandler.post {
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

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = AppSettings.current.javaScriptEnabled
        settings.domStorageEnabled = true
        settings.setGeolocationEnabled(true)
        settings.allowFileAccess = false
        settings.allowFileAccessFromFileURLs = false
        settings.allowUniversalAccessFromFileURLs = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.safeBrowsingEnabled = true
        settings.setSupportMultipleWindows(AppSettings.current.popupsEnabled)
        settings.javaScriptCanOpenWindowsAutomatically = AppSettings.current.popupsEnabled
        CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
        // Cached here (main thread, at tab creation) rather than read from settings inside
        // onCandidateMediaUrl below: that callback fires from shouldInterceptRequest, which
        // Chromium calls off the main thread, and WebView.getSettings() — like any WebView
        // method — throws if called from there ("A WebView method was called on thread
        // 'ThreadPoolForeg'"), crashing the app. Caught via a real on-device crash while
        // verifying the grabber against a live HLS stream.
        val cachedUserAgent = settings.userAgentString
        addJavascriptInterface(CosmeticBridge(AdBlock.engine), "MiowserAdBlock")
        installCosmeticInjectionScript() // must run before loadUrl()

        rendererResourceDownloader = RendererResourceDownloader(tabContext)
        addJavascriptInterface(rendererResourceDownloader, "MiowserDownloadBridge")

        addJavascriptInterface(
            GrabberBridge { src, duration, width, height ->
                val generation = detectionGeneration.get()
                mainHandler.post {
                    if (generation != detectionGeneration.get() || destroyed) return@post
                    // Marking it seen still prevents a redundant HTTP probe later, but every DOM
                    // metadata update is merged: loadedmetadata commonly arrives after the URL
                    // was first observed and carries the missing duration/dimensions.
                    val firstSeen = seenGrabberUrls.add(src)
                    val rendererKind = rendererResourceKind(src)
                    val dataMime = src.takeIf { rendererKind == RendererResourceKind.DATA }
                        ?.substringAfter("data:", "")
                        ?.substringBefore(',')
                        ?.substringBefore(';')
                    val detectedType = dataMime?.let(MediaUrlClassifier::classifyByMimeType)
                        ?: if (rendererKind != null) MediaType.UNKNOWN
                        else (MediaUrlClassifier.classify(src) ?: MediaType.UNKNOWN)
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
                            pageTitle = this@Tab.title
                        )
                    )
                    if (firstSeen && rendererKind == null) {
                        probeCandidate(src, detectedType, cachedUserAgent)
                    }
                }
            },
            "MiowserGrabber"
        )
        installVideoSnifferScript() // must also run before loadUrl()

        // A deliberate depth threshold: ordinary small scrolls should not grow another floating
        // control over the page. One visible WebView height is an intuitive "about one screen"
        // threshold and naturally adapts to portrait, landscape, and split-screen layouts.
        setOnScrollChangeListener { view, _, scrollY, _, _ ->
            showJumpToTop = view.height > 0 && scrollY >= view.height
            // Refresh after scrolling settles while this tab is still on screen. In particular,
            // a later switch must use this saved viewport instead of trying to copy a hidden tab.
            val now = SystemClock.uptimeMillis()
            if (now - lastPreviewCaptureAt >= 1_200L) {
                lastPreviewCaptureAt = now
                captureThumbnail()
            }
            schedulePreviewCapture()
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
            val guessedType = MediaUrlClassifier.classify(downloadUrl)
                ?: MediaUrlClassifier.classifyByMimeType(mimeType)
            val rendererKind = rendererResourceKind(downloadUrl)
            if (seenGrabberUrls.add(downloadUrl)) {
                if (guessedType != null && MediaUrlClassifier.isManifestType(guessedType)) {
                    probeCandidate(downloadUrl, guessedType, cachedUserAgent)
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
                        probeCandidate(downloadUrl, guessedType, cachedUserAgent)
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
                        userAgent = downloadUserAgent?.takeIf(String::isNotBlank) ?: cachedUserAgent,
                        contentDisposition = contentDisposition,
                        mimeType = mimeType,
                        contentLength = contentLength
                    )
                )
            }
        }

        webViewClient = MiowserWebViewClient(
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
            onPageStarted = {
                pageError = null
                lastFinishedUrl = null
            },
            onPageError = { message -> pageError = message },
            onExternalNavigation = onExternalNavigation,
            onCandidateMediaUrl = { candidateUrl, guessedType ->
                if (seenGrabberUrls.add(candidateUrl)) probeCandidate(candidateUrl, guessedType, cachedUserAgent)
            },
            onLikelyDownloadNavigation = { downloadUrl ->
                onDownloadRequested(
                    WebDownloadRequest(
                        originatingTabId = this@Tab.id,
                        url = downloadUrl,
                        pageUrl = this@Tab.url,
                        pageTitle = this@Tab.title,
                        userAgent = cachedUserAgent,
                        contentDisposition = null,
                        mimeType = null,
                        contentLength = -1L
                    )
                )
            },
            onPageLoadFinished = { loadedUrl, loadedTitle ->
                if (pageError == null) lastFinishedUrl = loadedUrl
                if (pageError == null) schedulePreviewCapture()
                if (pageError == null && !isBrowserHomeUrl(loadedUrl)) {
                    launchDiscovery(loadedUrl, depth = 0, pageLimit = 1, showProgress = false)
                }
                if (pageError == null && !isPrivate && !isBrowserHomeUrl(loadedUrl)) {
                    HistoryLog.recordVisit(tabContext, loadedUrl, loadedTitle)
                }
            }
        )
        webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                onFileChooserRequested(filePathCallback, fileChooserParams)
                return true
            }

            override fun onPermissionRequest(request: PermissionRequest) {
                onWebPermissionRequested(request)
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest) {
                onWebPermissionCanceled(request)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                onGeolocationRequested(origin, callback)
            }

            override fun onGeolocationPermissionsHidePrompt() {
                onGeolocationCanceled()
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(CONSOLE_TAG, "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                loadProgress = newProgress / 100f
                isLoading = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                if (!isShowingHome) {
                    this@Tab.title = title?.takeIf { it.isNotBlank() } ?: "New Tab"
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
                val href = message.data.getString("url")?.takeIf(String::isNotBlank)
                    ?: plainAnchorFallback
                if (href != null) onLinkLongPressed(href)
                true
            }
            requestFocusNodeHref(hrefHandler.obtainMessage())
            true
        }

        if (initialUrl.isNotBlank()) loadUrl(initialUrl)
    }

    fun navigateTo(targetUrl: String) {
        if (targetUrl.isBlank()) return
        lastFinishedUrl = null
        clearDetections()
        isShowingHome = isBrowserHomeUrl(targetUrl)
        if (isShowingHome) {
            title = "Home"
            url = BROWSER_HOME_URL
            addressBarText = TextFieldValue("")
        }
        webView.loadUrl(targetUrl)
    }

    fun showHome() = navigateTo(BROWSER_HOME_URL)

    fun markActive(now: Long = System.currentTimeMillis()) {
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
        webView.settings.javaScriptEnabled = preferences.javaScriptEnabled
        webView.settings.setSupportMultipleWindows(preferences.popupsEnabled)
        webView.settings.javaScriptCanOpenWindowsAutomatically = preferences.popupsEnabled
    }

    fun jumpToTop() {
        webView.evaluateJavascript("window.scrollTo({top: 0, left: 0, behavior: 'smooth'});", null)
    }

    /** Stops page-owned media before Miowser's rich player takes over audio/video playback. */
    fun pausePageMedia() {
        webView.evaluateJavascript(
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
