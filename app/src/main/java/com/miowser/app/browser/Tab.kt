package com.miowser.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Message
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.TextFieldValue
import com.miowser.app.adblock.AdBlock
import com.miowser.app.adblock.CosmeticBridge
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.GrabberBridge
import com.miowser.app.grabber.MediaProbe
import com.miowser.app.grabber.installVideoSnifferScript
import com.miowser.app.grabber.MediaType
import com.miowser.app.grabber.MediaUrlClassifier
import java.util.Collections
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
 * @param onRequestChildTab called when this tab's page opens another one; must create the new
 *   tab (with this tab's id as its openerTabId, inserted beside this tab in the strip) and
 *   return it so its WebView can be attached to the popup navigation.
 */
class Tab(
    val id: Long,
    context: Context,
    initialUrl: String,
    val openerTabId: Long?,
    onRequestChildTab: (openerTabId: Long) -> Tab
) {
    var title by mutableStateOf("New Tab")
    var url by mutableStateOf("")
    var canGoBack by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    var loadProgress by mutableFloatStateOf(0f)
    var addressBarText by mutableStateOf(TextFieldValue(initialUrl))
    var isAddressBarFocused by mutableStateOf(false)

    /** Everything the grabber has found on the current page. Cleared on every new navigation. */
    val detectedMedia = mutableStateListOf<DetectedMedia>()

    // Guards against re-probing/re-reporting the same URL (a page can request or expose the same
    // manifest/element more than once) — synchronized because network-level candidates arrive
    // from shouldInterceptRequest, which Chromium calls off the main thread.
    private val seenGrabberUrls = Collections.synchronizedSet(mutableSetOf<String>())

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
        if (existingIndex >= 0) detectedMedia[existingIndex] = media else detectedMedia.add(media)
    }

    @SuppressLint("SetJavaScriptEnabled")
    val webView: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        // Cached here (main thread, at tab creation) rather than read from settings inside
        // onCandidateMediaUrl below: that callback fires from shouldInterceptRequest, which
        // Chromium calls off the main thread, and WebView.getSettings() — like any WebView
        // method — throws if called from there ("A WebView method was called on thread
        // 'ThreadPoolForeg'"), crashing the app. Caught via a real on-device crash while
        // verifying the grabber against a live HLS stream.
        val cachedUserAgent = settings.userAgentString
        addJavascriptInterface(CosmeticBridge(AdBlock.engine), "MiowserAdBlock")
        installCosmeticInjectionScript() // must run before loadUrl()

        addJavascriptInterface(
            GrabberBridge { src, duration, width, height ->
                if (seenGrabberUrls.add(src)) {
                    val isBlob = src.startsWith("blob:")
                    addOrReplaceDetection(
                        DetectedMedia(
                            url = src,
                            pageUrl = this@Tab.url,
                            type = if (isBlob) MediaType.UNKNOWN else (MediaUrlClassifier.classify(src) ?: MediaType.MP4),
                            // blob: URLs are confirmed-playing-in-the-DOM but not independently
                            // fetchable/verifiable over the network — the MSE case the grabber
                            // is supposed to distinguish, not silently claim as a real download.
                            isConfirmed = !isBlob,
                            sizeBytes = null,
                            isSizeApproximate = false,
                            durationSeconds = duration.takeIf { it >= 0 },
                            variantCount = null,
                            // The element's own rendered dimensions — real, DOM-sourced data,
                            // unlike the manifest-derived resolution on the network-probe path.
                            resolution = if (width > 0 && height > 0) "${width}x${height}" else null,
                            isEncrypted = false,
                            pageTitle = this@Tab.title
                        )
                    )
                }
            },
            "MiowserGrabber"
        )
        installVideoSnifferScript() // must also run before loadUrl()

        webViewClient = MiowserWebViewClient(
            adBlockEngine = AdBlock.engine,
            onCanGoBackChanged = { canGoBack = it },
            onUrlChanged = { newUrl ->
                // Qualified with this@Tab: unqualified `url` here would resolve to WebView's
                // own read-only `url` synthetic property (from getUrl()) via the enclosing
                // apply{} receiver, not Tab's — caught by the compiler as "val cannot be
                // reassigned" rather than silently doing the wrong thing.
                this@Tab.url = newUrl
                if (!isAddressBarFocused) addressBarText = TextFieldValue(newUrl)
            },
            onNewPageStarted = {
                detectedMedia.clear()
                seenGrabberUrls.clear()
            },
            onCandidateMediaUrl = { candidateUrl, guessedType ->
                if (seenGrabberUrls.add(candidateUrl)) {
                    MediaProbe.probe(
                        url = candidateUrl,
                        pageUrl = this@Tab.url,
                        userAgent = cachedUserAgent,
                        guessedType = guessedType
                    ) { result ->
                        // onResult is always main-thread-dispatched (see MediaProbe), so reading
                        // this@Tab.title here — unlike in the classify/probe call above, which
                        // still runs off-thread — is safe.
                        if (result != null) addOrReplaceDetection(result.copy(pageTitle = this@Tab.title))
                    }
                }
            }
        )
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                Log.d(CONSOLE_TAG, "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                return true
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                loadProgress = newProgress / 100f
                isLoading = newProgress < 100
            }

            override fun onReceivedTitle(view: WebView, title: String?) {
                this@Tab.title = title?.takeIf { it.isNotBlank() } ?: "New Tab"
            }

            // target="_blank" links and window.open() land here rather than navigating this
            // tab in place — requires setSupportMultipleWindows(true) above, otherwise WebView
            // never calls this at all. The new tab's WebView is handed back to the page via
            // WebView.WebViewTransport so the popup actually ends up showing in it.
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                val childTab = onRequestChildTab(this@Tab.id)
                val transport = resultMsg.obj as WebView.WebViewTransport
                transport.webView = childTab.webView
                resultMsg.sendToTarget()
                return true
            }
        }

        if (initialUrl.isNotBlank()) loadUrl(initialUrl)
    }

    companion object {
        private val idCounter = AtomicLong(0)
        fun nextId(): Long = idCounter.getAndIncrement()
    }
}
