package com.miowser.app.browser.video

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.util.IdentityHashMap
import java.util.UUID

/** Telemetry and button presentation; native code never starts or changes video playback here. */
internal class LiveVideoTracker(tabId: Long, private val webView: WebView,
    private val mutableSessions: MutableStateFlow<List<LiveVideoSession>>,
    initialButtonPolicy: VideoAssistantButtonPolicy = VideoAssistantButtonPolicy(),
    private val onFullscreenFailure: () -> Unit = {}) {
    private data class Channel(val frameId: Long, val document: String, val token: String,
        val navigation: Long, val connectedAt: Long, var lastUpdate: Long = -1,
        var lastHello: Long = connectedAt)
    private val registry = LiveVideoSessionRegistry(tabId)
    private val channels = IdentityHashMap<JavaScriptReplyProxy, Channel>()
    private val main = Handler(Looper.getMainLooper())
    val sessions: StateFlow<List<LiveVideoSession>> = mutableSessions.asStateFlow()
    private var script: ScriptHandler? = null
    private var accepting = false
    private var retired = false
    private var installed = false
    private var helloWindow = 0L
    private var helloCount = 0
    private var pruneScheduled = false
    private var buttonPolicy = initialButtonPolicy
    private var lastFailureAt = -10_000L
    val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val prune = object : Runnable {
        override fun run() {
            pruneScheduled = false
            if (retired) return
            val now = SystemClock.elapsedRealtime()
            // Frames without videos expire too, so unrelated/removed frames cannot fill the table.
            val expired = channels.filterValues {
                now - (it.lastUpdate.takeIf { time -> time >= 0 } ?: it.connectedAt) > LiveVideoSessionRegistry.LEASE_MS
            }
            expired.forEach { (proxy, channel) ->
                channels.remove(proxy)
                registry.closeFrame(channel.frameId)
                runCatching { proxy.postMessage(JSONObject().put("type", "reset")
                    .put("documentId", channel.document).toString()) }
            }
            registry.prune(now)
            publish()
        }
    }

    fun install() {
        if (!supported || retired || installed) return
        installed = true
        try {
            WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("*")) { _, message, origin, mainFrame, proxy ->
                // No privileged operations are exposed. Still bound and validate all page-controlled data.
                if (!accepting || retired || origin.scheme !in setOf("http", "https") ||
                    origin.host.isNullOrBlank() || (origin.host?.length ?: 0) > 253) return@addWebMessageListener
                val text = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
                if (text.length > MAX_MESSAGE_CHARS) return@addWebMessageListener
                receive(text, origin.toString().take(1024), mainFrame, proxy)
            }
            script = WebViewCompat.addDocumentStartJavaScript(webView,
                VIDEO_ASSISTANT_BUTTON_SCRIPT + LIVE_VIDEO_SESSION_SCRIPT, setOf("*"))
        } catch (_: RuntimeException) {
            // An optional feature/provider mismatch must not stop a tab from opening.
            retire()
        }
    }

    fun onNavigationStarted() {
        accepting = false
        channels.forEach { (proxy, channel) ->
            runCatching { proxy.postMessage(JSONObject().put("type", "reset")
                .put("documentId", channel.document).toString()) }
        }
        channels.clear()
        registry.reset()
        mutableSessions.value = emptyList()
        main.removeCallbacks(prune)
        pruneScheduled = false
    }

    /** Resume after main-frame commit; the frame-aware script reconnects existing child frames. */
    fun onPageCommitted() {
        if (!supported || retired || !installed) return
        accepting = true
        webView.evaluateJavascript("window.__miowserLiveVideos && window.__miowserLiveVideos.connect();", null)
    }

    fun activeVideo(): LiveVideoSession? {
        registry.prune(SystemClock.elapsedRealtime())
        return registry.active()
    }

    private fun receive(text: String, origin: String, mainFrame: Boolean, proxy: JavaScriptReplyProxy) {
        runCatching {
            val json = JSONObject(text)
            val document = json.optString("documentId")
            if (document.length !in 1..80) return
            when (json.optString("type")) {
                "hello" -> {
                    val now = SystemClock.elapsedRealtime()
                    if (now - helloWindow > 1000) { helloWindow = now; helloCount = 0 }
                    val old = channels[proxy]
                    if (old != null && old.document == document && old.navigation == registry.navigation) {
                        if (now - old.lastHello < 500) return
                        old.lastHello = now
                        acknowledge(proxy, old)
                        return
                    }
                    if (++helloCount > 16) return
                    old?.let { channels.remove(proxy); registry.closeFrame(it.frameId) }
                    val frame = registry.openFrame(document, origin, mainFrame) ?: return
                    val channel = Channel(frame, document, UUID.randomUUID().toString(), registry.navigation, now)
                    channels[proxy] = channel
                    acknowledge(proxy, channel)
                    publish()
                }
                "state", "retire", "fullscreenError" -> {
                    val channel = channels[proxy] ?: return
                    if (channel.document != document || channel.navigation != registry.navigation ||
                        json.optLong("navigation", -1) != channel.navigation ||
                        json.optString("token") != channel.token) return
                    if (json.optString("type") == "fullscreenError") {
                        val now = SystemClock.elapsedRealtime()
                        if (buttonPolicy.enabled && now - lastFailureAt >= 3_000) {
                            lastFailureAt = now
                            onFullscreenFailure()
                        }
                        return
                    }
                    if (json.optString("type") == "retire") {
                        channels.remove(proxy)
                        registry.closeFrame(channel.frameId)
                        publish()
                        return
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - channel.lastUpdate < 100) return
                    val videos = json.getJSONArray("videos")
                    val removed = json.getJSONArray("removed")
                    if (videos.length() > LiveVideoSessionRegistry.MAX_VIDEOS_PER_FRAME ||
                        removed.length() > LiveVideoSessionRegistry.MAX_VIDEOS_PER_FRAME) return
                    val states = (0 until videos.length()).map { decode(videos.getJSONObject(it)) }
                    val deleted = (0 until removed.length()).map { removed.getString(it).take(80) }
                    if (registry.update(channel.navigation, channel.frameId, json.getLong("sequence"), states, deleted, now)) {
                        channel.lastUpdate = now
                        publish()
                    }
                }
            }
        } // Malformed/untrusted telemetry is ignored; never fail normal browsing.
    }

    private fun acknowledge(proxy: JavaScriptReplyProxy, channel: Channel) {
        runCatching { proxy.postMessage(JSONObject().put("type", "bind").put("documentId", channel.document)
            .put("navigation", channel.navigation).put("token", channel.token).put("button", policyJson()).toString()) }
    }

    fun updateButtonPolicy(next: VideoAssistantButtonPolicy) {
        if (retired || next == buttonPolicy) return
        buttonPolicy = next
        channels.forEach { (proxy, channel) ->
            runCatching { proxy.postMessage(JSONObject().put("type", "buttonPolicy")
                .put("documentId", channel.document).put("navigation", channel.navigation)
                .put("token", channel.token).put("button", policyJson()).toString()) }
        }
    }

    private fun policyJson() = JSONObject().put("enabled", buttonPolicy.enabled)
        .put("background", buttonPolicy.background).put("foreground", buttonPolicy.foreground)
        .put("reducedMotion", buttonPolicy.reducedMotion)

    private fun publish() {
        mutableSessions.value = registry.snapshot()
        if (!pruneScheduled && channels.isNotEmpty() && !retired) {
            pruneScheduled = true
            main.postDelayed(prune, 15_000)
        }
    }

    fun retire() {
        if (retired) return
        retired = true
        onNavigationStarted()
        runCatching { script?.remove() }
        script = null
        if (installed) runCatching { WebViewCompat.removeWebMessageListener(webView, BRIDGE) }
    }

    private fun decode(json: JSONObject): LiveVideoState {
        fun number(key: String, max: Double = 31_536_000.0): Double = json.optDouble(key, 0.0)
            .takeIf { it.isFinite() }?.coerceIn(0.0, max) ?: 0.0
        fun ms(key: String): Long = (number(key) * 1000).toLong()
        fun ranges(array: JSONArray?): List<VideoTimeRange> {
            if (array == null || array.length() > 8) return emptyList()
            return (0 until array.length()).mapNotNull { index ->
                val range = array.optJSONArray(index) ?: return@mapNotNull null
                val start = range.optDouble(0, Double.NaN)
                val end = range.optDouble(1, Double.NaN)
                if (!start.isFinite() || !end.isFinite() || start < 0 || end <= start || end > 31_536_000) null
                else VideoTimeRange((start * 1000).toLong(), (end * 1000).toLong())
            }
        }
        val bounds = json.getJSONObject("bounds")
        fun bound(key: String): Double = bounds.optDouble(key, 0.0)
            .takeIf { it.isFinite() }?.coerceIn(-1_000_000.0, 1_000_000.0) ?: 0.0
        val source = if (json.isNull("source")) null else json.optString("source")
            .takeIf { it.length <= 8192 && it.isNotBlank() }
        val sourceKind = when (json.optString("sourceKind")) {
            "network" -> LiveVideoSourceKind.NETWORK
            "blob" -> LiveVideoSourceKind.BLOB
            "stream" -> LiveVideoSourceKind.MEDIA_STREAM
            "data" -> LiveVideoSourceKind.DATA
            else -> LiveVideoSourceKind.UNKNOWN
        }
        return LiveVideoState(json.getString("elementId"), source.takeUnless { sourceKind == LiveVideoSourceKind.DATA },
            sourceKind, ms("position"), if (json.isNull("duration")) null else ms("duration"),
            json.optBoolean("playing"), json.optBoolean("ended"), json.optBoolean("buffering"),
            json.optBoolean("muted"), number("speed", 16.0), number("width", 32768.0).toInt(),
            number("height", 32768.0).toInt(), json.optBoolean("visible"), number("intersection", 1.0),
            VideoFrameBounds(bound("left"), bound("top"), bound("width").coerceAtLeast(0.0),
                bound("height").coerceAtLeast(0.0), bound("viewportWidth").coerceAtLeast(0.0),
                bound("viewportHeight").coerceAtLeast(0.0)), ranges(json.optJSONArray("seekable")),
            ranges(json.optJSONArray("buffered")), json.optInt("readyState").coerceIn(0, 4),
            json.optLong("sourceRevision").coerceAtLeast(0), json.optBoolean("interacted"))
    }

    companion object {
        private const val BRIDGE = "MiowserLiveVideo"
        private const val MAX_MESSAGE_CHARS = 24_576
    }
}
