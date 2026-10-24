package com.miowser.app.browser.video

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.UUID

internal enum class BrowserVideoFixture(val label: String, val file: String) {
    DIRECT("Direct video", "video.html?kind=direct"),
    IFRAME("Cross-origin iframe", "frame.html"),
    MSE("MediaSource / blob", "video.html?kind=mse")
}

/** Only used by the isolated lab. No browser tab, Media3 player, or media repository is touched. */
@SuppressLint("SetJavaScriptEnabled")
internal class BrowserVideoLabController(context: Context) {
    private val lease = BrowserVideoLabLease()
    private val nonce = UUID.randomUUID().toString()
    private var reply: JavaScriptReplyProxy? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var destroyed = false
    private var started = true
    var customView by mutableStateOf<View?>(null)
        private set
    var sample by mutableStateOf<BrowserVideoLabSample?>(null)
        private set
    var entrySample by mutableStateOf<BrowserVideoLabSample?>(null)
        private set
    var exitSample by mutableStateOf<BrowserVideoLabSample?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var fixture by mutableStateOf(BrowserVideoFixture.DIRECT)
        private set

    private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)
    private val loaders = HOSTS.associateWith { host ->
        WebViewAssetLoader.Builder().setDomain(host)
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
    }

    val webView = WebView(context).apply {
        setBackgroundColor(android.graphics.Color.BLACK)
        settings.javaScriptEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.domStorageEnabled = false
        settings.mediaPlaybackRequiresUserGesture = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                !isFixtureUrl(request.url)

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
                if (isFixtureUrl(request.url)) {
                    loaders[request.url.host]?.shouldInterceptRequest(request.url) ?: blocked()
                } else blocked()

            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                if (customView != null) exitFullscreen()
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (destroyed || !started || customView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                this@BrowserVideoLabController.callback = callback
                customView = view
                notice = null
                // No source assignment, new player, WebView.onPause(), or page reload here.
            }

            override fun onHideCustomView() = exitFullscreen()
        }
    }

    init {
        if (supported) WebViewCompat.addWebMessageListener(webView, BRIDGE,
            HOSTS.map { "https://$it" }.toSet()) { _, message, sourceOrigin, _, proxy ->
            val data = message.data
            if (!destroyed && sourceOrigin.scheme == "https" && sourceOrigin.host in HOSTS &&
                data != null && data.length <= 4096) receive(data, proxy)
        }
        load(BrowserVideoFixture.DIRECT)
    }

    fun load(fixture: BrowserVideoFixture) {
        if (destroyed) return
        exitFullscreen()
        val generation = lease.reset()
        reply = null
        sample = null
        entrySample = null
        exitSample = null
        notice = if (supported) null else "Update Android System WebView to use the lab controls."
        this.fixture = fixture
        val separator = if ('?' in fixture.file) '&' else '?'
        webView.loadUrl("https://appassets.androidplatform.net/assets/video-lab/${fixture.file}" +
            "${separator}generation=$generation&nonce=$nonce")
    }

    private fun receive(data: String, proxy: JavaScriptReplyProxy) {
        runCatching {
            val json = JSONObject(data)
            if (json.optString("nonce") != nonce) return
            val type = json.optString("type")
            if (type !in setOf("state", "enter", "exit", "error")) return
            val document = json.getString("documentId")
            if (!lease.accept(json.getLong("generation"), document, json.getLong("sequence"))) return
            reply = proxy
            if (type == "error") {
                notice = json.optString("message").take(200)
                return
            }
            val next = decodeSample(json, document)
            sample = next
            if (type == "enter") { entrySample = next; exitSample = null }
            if (type == "exit") exitSample = next
            if (!started && next.playing) command("pause")
        }.onFailure { notice = "The fixture sent an invalid playback update." }
    }

    fun playPause() = command(if (sample?.playing == true) "pause" else "play")
    fun skip(deltaMs: Long) {
        val position = sample?.positionMs ?: return
        seek(position + deltaMs)
    }
    fun seek(positionMs: Long) { sample?.seekTarget(positionMs)?.let { command("seek", it / 1000.0) } }
    fun setSpeed(speed: Double) { if (speed in 0.25..3.0 && speed.isFinite()) command("rate", speed) }

    private fun command(action: String, value: Double? = null) {
        if (destroyed) return
        val document = lease.documentId ?: return
        val json = JSONObject().put("action", action).put("generation", lease.generation)
            .put("documentId", document).put("nonce", nonce)
        if (value != null) json.put("value", value)
        runCatching { reply?.postMessage(json.toString()) }
            .onFailure { notice = "The video document is no longer available. Reopen the fixture." }
    }

    fun exitFullscreen() {
        val current = callback
        callback = null
        val view = customView
        customView = null
        (view?.parent as? ViewGroup)?.removeView(view)
        // Clear ownership before notifying Chromium: onHideCustomView may be reentrant.
        current?.onCustomViewHidden()
    }

    fun onStart() { started = true; webView.onResume() }
    fun onStop() { started = false; command("pause"); webView.onPause() }

    fun close() {
        if (destroyed) return
        exitFullscreen()
        destroyed = true
        lease.reset()
        reply = null
        if (supported) WebViewCompat.removeWebMessageListener(webView, BRIDGE)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
    }

    private fun decodeSample(json: JSONObject, document: String): BrowserVideoLabSample {
        fun milliseconds(key: String): Long = (json.optDouble(key, 0.0)
            .takeIf { it.isFinite() && it >= 0 }?.coerceAtMost(31_536_000.0)?.times(1000))?.toLong() ?: 0
        val start = if (json.isNull("seekStart")) null else milliseconds("seekStart")
        val end = if (json.isNull("seekEnd")) null else milliseconds("seekEnd")
        return BrowserVideoLabSample(document, json.getLong("sequence"), milliseconds("position"),
            milliseconds("duration"), milliseconds("buffered"), json.optBoolean("playing"),
            json.optBoolean("buffering"),
            (json.optDouble("speed", 1.0).takeIf { it.isFinite() } ?: 1.0).coerceIn(0.25, 3.0),
            json.optInt("width").coerceIn(0, 16384), json.optInt("height").coerceIn(0, 16384),
            start, end, json.optString("sourceKind").take(24),
            json.optInt("sourceChanges"), json.optInt("metadataLoads"))
    }

    companion object {
        private const val BRIDGE = "MiowserVideoLab"
        private val HOSTS = setOf("appassets.androidplatform.net", "frame.miowser.invalid")
        private fun isFixtureUrl(uri: Uri): Boolean = uri.scheme == "https" && uri.host in HOSTS &&
            uri.port == -1 && uri.path?.startsWith("/assets/video-lab/") == true
        private fun blocked() = WebResourceResponse("text/plain", "UTF-8", 403, "Fixture only",
            emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}
