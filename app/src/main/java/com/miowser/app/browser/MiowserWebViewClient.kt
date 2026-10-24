package com.miowser.app.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.SslErrorHandler
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.miowser.app.adblock.AdBlockEngine
import com.miowser.app.adblock.BlockedPageInfo
import com.miowser.app.adblock.StubResponses
import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.MediaUrlClassifier
import com.miowser.app.settings.AppSettings

private const val TAG = "Miowser/Intercept"

/**
 * Consults [AdBlockEngine] for every subresource request and returns its stub response when a
 * rule matches. All the actual rule-parsing, matching, and stub-selection logic lives in the
 * adblock package (see AdBlockEngine.decide / StubResponses) — this class is just the
 * WebView-facing glue.
 *
 * Requests that *aren't* blocked are also cheaply classified by [MediaUrlClassifier] — a
 * synchronous, allocation-light extension check — and handed to [onCandidateMediaUrl] for the
 * real verification probe, which is Tab's job (see MediaProbe), not this class's.
 *
 * Threading note: shouldInterceptRequest is called by Chromium off the main thread, so it must
 * never touch the WebView instance itself (e.g. view.getUrl() throws — "A WebView method was
 * called on thread 'ThreadPoolForeg'"). The current page URL is tracked separately here,
 * updated only from onPageStarted, which — unlike shouldInterceptRequest — is a normal
 * main-thread callback. onCandidateMediaUrl fires from this same background thread; callers
 * that touch Compose state must hop to the main thread themselves (MediaProbe's onResult
 * callback already does this for the async path Tab actually uses).
 */
class MiowserWebViewClient(
    private val isPrivate: Boolean,
    private val adBlockEngine: AdBlockEngine,
    private val onNavigationStateChanged: (canGoBack: Boolean, canGoForward: Boolean) -> Unit,
    private val onUrlChanged: (String) -> Unit,
    private val onNewPageStarted: () -> Unit,
    private val onPageStarted: () -> Unit,
    private val onPageError: (String) -> Unit,
    private val onRendererGone: (WebView, crashed: Boolean) -> Unit,
    private val onCertificateError: (String, SslErrorHandler, SslError) -> Unit,
    private val onInsecureNavigationRequested: (String) -> Unit,
    private val shouldBlockCompanionNavigation: (String) -> Boolean,
    private val onExternalNavigation: (String) -> Unit,
    private val onCandidateMediaUrl: (url: String, type: MediaType, referer: String?, origin: String?) -> Unit,
    private val onLikelyDownloadNavigation: (url: String) -> Unit,
    private val onPageLoadFinished: (url: String, title: String) -> Unit,
    private val onMainFrameBlocked: (BlockedPageInfo) -> Unit = {},
    private val onRequestBlocked: (String) -> Unit = {},
    private val onScaleChanged: (oldScale: Float, newScale: Float) -> Unit = { _, _ -> },
    private val onPageCommitted: () -> Unit = {}
) : WebViewClient() {

    @Volatile private var currentPageUrl: String = "about:blank"
    @Volatile private var retired = false
    @Volatile private var blockedMainFrame: BlockedPageInfo? = null

    fun retire() { retired = true }

    override fun onPageCommitVisible(view: WebView, url: String?) {
        if (!retired) onPageCommitted()
    }

    override fun onScaleChanged(view: WebView, oldScale: Float, newScale: Float) {
        if (!retired) onScaleChanged(oldScale, newScale)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        retire()
        // Android delivers this for each affected view, including views sharing a renderer.
        // Only retire the supplied view; unrelated tabs must keep their engines.
        onRendererGone(view, detail.didCrash())
        return true
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        if (retired) return
        if (url != null) {
            currentPageUrl = url
            onUrlChanged(url)
            onNewPageStarted()
            onPageStarted()
            refreshNavigationState(view)
        }
    }

    override fun onPageFinished(view: WebView, url: String?) {
        if (retired) return
        // Covers redirects: the URL at load-finish can differ from the one onPageStarted saw.
        if (url != null) {
            currentPageUrl = url
            onUrlChanged(url)
            refreshNavigationState(view)
            val block = blockedMainFrame
            if (block?.url == url) {
                // Publish on the UI callback, never from Chromium's interception thread. The
                // native error surface also stays visible if a cosmetic selector hides HTML.
                onPageError("The ad blocker blocked this page. Open the shield or site settings to review protection, then reload.")
                onMainFrameBlocked(block)
                return
            }
            // view.title is WebView's own synchronous getter — usually already populated by
            // load-finish for real pages, so this doesn't need to wait on the separate
            // WebChromeClient.onReceivedTitle callback (which can arrive slightly later, or not
            // at all for a page with no <title>) just to record a history entry.
            onPageLoadFinished(url, view.title?.takeIf { it.isNotBlank() } ?: url)
            // Document-start remains the flicker-free primary path. This second application
            // catches a filter rebuild that completed while the page itself was loading.
            view.applyCosmeticFiltersNow()
        }
    }

    /**
     * Confirmed on-device: view.canGoBack()/canGoForward() checked *synchronously* inside these
     * callbacks reports stale results — Forward showed enabled at the newest history entry
     * (nothing actually ahead) even right after onPageFinished, for a single fresh navigation
     * with no redirect involved. This is a known WebView/Chromium quirk: the WebBackForwardList
     * isn't necessarily settled yet at the exact moment these callbacks fire. view.post queues
     * the real check for the next pass through the message loop, by which point it reliably has —
     * the same "don't trust a synchronous read here" fix this codebase already uses for the
     * address bar's select-all-on-focus race (see BrowserScreen's matching LaunchedEffect).
     */
    private fun refreshNavigationState(view: WebView) {
        view.post {
            if (!retired) onNavigationStateChanged(view.canGoBack(), view.canGoForward())
        }
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        if (retired) return true
        val url = request.url.toString()
        if (request.isForMainFrame) {
            if (shouldBlockCompanionNavigation(url)) return true
            if (ExternalNavigation.isExternal(url)) {
                onExternalNavigation(url)
                return true
            }
            if (!ExternalNavigation.canStayInWebView(url)) {
                onPageError("This link uses an unsupported or unsafe address type.")
                return true
            }
            if (HttpNavigationApproval.requiresWarning(url, isPrivate)) {
                onInsecureNavigationRequested(url)
                return true
            }
        }
        if (request.isForMainFrame && request.method.equals("GET", ignoreCase = true) &&
            WebDownloadUrlClassifier.isLikelyDownload(url)
        ) {
            onLikelyDownloadNavigation(url)
            return true
        }
        return false
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        if (retired) { handler.cancel(); return }
        if (error.url == currentPageUrl || error.url == view.url) {
            onCertificateError(error.url, handler, error)
        } else handler.cancel()
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (retired) return
        if (request.isForMainFrame) {
            onPageError(error.description?.toString() ?: "This page could not be loaded.")
        }
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (retired) return null
        val requestUrl = request.url.toString()
        // For the main-frame navigation request itself, the request *is* the new page — the
        // tracked currentPageUrl still reflects the previous page until this commits.
        val pageUrl = if (request.isForMainFrame) requestUrl else currentPageUrl
        if (request.isForMainFrame) blockedMainFrame = null

        if (AppSettings.current.adBlockEnabled) {
            val response = if (request.isForMainFrame) {
                adBlockEngine.blockedNavigation(requestUrl, request.requestHeaders)?.let { info ->
                    blockedMainFrame = info
                    StubResponses.blockedDocument(info.rule, info.sources)
                }
            } else adBlockEngine.decide(pageUrl, requestUrl, request.requestHeaders, false)
            if (response != null) {
                onRequestBlocked(requestUrl)
                return response
            }
        }

        MediaUrlClassifier.classify(requestUrl)?.let { type ->
            val headers = request.requestHeaders
            onCandidateMediaUrl(
                requestUrl,
                type,
                headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value,
                headers.entries.firstOrNull { it.key.equals("Origin", ignoreCase = true) }?.value
            )
        }
        return null
    }

    // The documented hook for "the back/forward history changed", including history navigations
    // (our own Back/Forward buttons calling goBack()/goForward()) — a third refresh point
    // alongside onPageStarted/onPageFinished above, since none of the three is reliably invoked
    // for every navigation type on its own (see onPageStarted's/onPageFinished's own comments,
    // and the audit finding that motivated all this: navigating directly between two
    // media-manifest URLs skipped onPageStarted entirely).
    override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
        if (retired) return
        if (url != null && url.substringBefore('#') != currentPageUrl.substringBefore('#')) {
            currentPageUrl = url
            onUrlChanged(url)
            onNewPageStarted()
        }
        refreshNavigationState(view)
    }
}
