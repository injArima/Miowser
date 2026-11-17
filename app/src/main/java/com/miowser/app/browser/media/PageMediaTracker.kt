package com.miowser.app.browser.media

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.ScriptHandler
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import org.json.JSONObject
import java.net.URI
import java.util.IdentityHashMap
import java.util.UUID

/** One WebView's runtime-only, frame-bound media sessions. All calls belong to the UI thread. */
internal class PageMediaTracker(
    private val tabId: Long,
    private val webView: WebView,
    private val isPrivate: Boolean,
    private val onChanged: (PageMediaTracker) -> Unit
) : PageMediaControls {
    private class Channel(
        val document: String, val token: String, val navigation: Long, val prefix: String,
        val mainFrame: Boolean, val origin: String, val compatible: Boolean,
        var lastSeen: Long, var lastHello: Long = lastSeen,
        var sequence: Long = -1, var activation: Long = -1,
        var activity: Long = 0, var state: PageMediaSnapshot? = null,
        var element: String = "", var revision: Long = 0, var backgroundOwner: String? = null
    )
    private data class Pending(val id: Long, val owner: String, val proxy: JavaScriptReplyProxy)

    private val instance = UUID.randomUUID().toString()
    private val channels = IdentityHashMap<JavaScriptReplyProxy, Channel>()
    private val main = Handler(Looper.getMainLooper())
    private var grace: Runnable? = null
    private var pruneScheduled = false
    private val prune = Runnable {
        pruneScheduled = false
        if (!retired && accepting) {
            val now = SystemClock.elapsedRealtime()
            val expired = channels.filter { (proxy, channel) ->
                if (channel.state?.playing == true) now - channel.lastSeen > 30_000
                else proxy !== ownerProxy && now - channel.lastSeen > 60_000
            }
            expired.forEach { (proxy, channel) ->
                send(proxy, bound("reset", channel))
                channels.remove(proxy)
            }
            publish()
        }
    }
    private var script: ScriptHandler? = null
    private var installed = false
    private var retired = false
    private var accepting = false
    private var navigation = 0L
    private var nextFrame = 0L
    private var nextActivity = 0L
    private var nextCommand = 0L
    private var pending: Pending? = null
    private var backgroundAllowed = false
    private var ownerProxy: JavaScriptReplyProxy? = null
    private var helloWindow = 0L
    private var helloCount = 0
    private val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    override var snapshot: PageMediaSnapshot? = null
        private set

    fun install() {
        if (installed || retired) return
        onChanged(this) // Also register an initially empty tracker with the coordinator.
        if (!supported) return
        installed = true
        try {
            WebViewCompat.addWebMessageListener(webView, BRIDGE, setOf("*")) { _, message, origin, mainFrame, proxy ->
                if (retired || !accepting || origin.scheme !in setOf("http", "https") ||
                    origin.host.isNullOrBlank() || (origin.host?.length ?: 0) > 253) return@addWebMessageListener
                val text = runCatching { message.data }.getOrNull() ?: return@addWebMessageListener
                if (text.length > MAX_MESSAGE_CHARS) return@addWebMessageListener
                receive(text, mainFrame, origin.toString(), proxy)
            }
            script = WebViewCompat.addDocumentStartJavaScript(webView, PAGE_MEDIA_SCRIPT, setOf("*"))
        } catch (_: RuntimeException) {
            retire() // An optional WebView feature must never prevent navigation.
        }
    }

    fun onNavigationStarted() {
        accepting = false
        grace?.let(main::removeCallbacks)
        grace = null
        main.removeCallbacks(prune)
        pruneScheduled = false
        channels.forEach { (proxy, channel) -> send(proxy, bound("reset", channel)) }
        channels.clear()
        navigation++
        ownerProxy = null
        pending = null
        backgroundAllowed = false
        snapshot = null
        onChanged(this) // Null is meaningful even before the first playback sample.
    }

    fun onPageCommitted() {
        if (!supported || retired || !installed) return
        accepting = true
        val generation = navigation
        webView.evaluateJavascript("!!window.__miowserPageMedia") { present ->
            if (retired || !accepting || generation != navigation) return@evaluateJavascript
            if (present == "false") webView.evaluateJavascript(PAGE_MEDIA_SCRIPT, null)
            // Reconnect every descendant in its own context, including cross-origin frames.
            webView.evaluateJavascript("window.__miowserPageMedia && window.__miowserPageMedia.connect();", null)
        }
    }

    fun setBackgroundAllowed(allowed: Boolean) {
        backgroundAllowed = allowed && !retired && accepting && snapshot != null
        sendBackgroundPolicies()
    }

    override fun command(action: String, positionMs: Long?) {
        if (retired || !accepting || action !in PageMediaActions.supported) return
        val current = snapshot ?: return
        if (action !in current.actions) return
        if (action == "seekto" && (positionMs == null || positionMs !in 0..MAX_TIME_MS)) return
        val proxy = ownerProxy ?: return
        val channel = channels[proxy] ?: return
        if (channel.state?.ownerKey != current.ownerKey || channel.navigation != navigation) return
        val id = ++nextCommand
        pending = Pending(id, current.ownerKey, proxy)
        val json = bound("command", channel).put("commandId", id).put("ownerKey", current.ownerKey)
            .put("elementId", channel.element).put("sourceRevision", channel.revision).put("action", action)
        if (action == "seekto") json.put("positionMs", positionMs)
        if (!send(proxy, json)) pending = null
    }

    fun retire() {
        if (retired) return
        retired = true
        channels.forEach { (proxy, channel) -> send(proxy, bound("destroy", channel)) }
        onNavigationStarted()
        runCatching { script?.remove() }
        script = null
        if (installed) runCatching { WebViewCompat.removeWebMessageListener(webView, BRIDGE) }
        installed = false
    }

    private fun receive(text: String, mainFrame: Boolean, origin: String, proxy: JavaScriptReplyProxy) {
        runCatching {
            val json = JSONObject(text)
            val document = json.optString("documentId")
            if (document.length !in 1..80) return
            if (json.optString("type") == "hello") {
                hello(document, mainFrame, origin, proxy)
                return
            }
            val channel = channels[proxy] ?: return
            if (channel.document != document || channel.navigation != navigation ||
                channel.mainFrame != mainFrame || channel.origin != origin ||
                json.optLong("navigation", -1) != navigation || json.optString("token") != channel.token) return
            when (json.optString("type")) {
                "retire" -> {
                    channels.remove(proxy)
                    publish()
                }
                "ack" -> {
                    val expected = pending ?: return
                    if (expected.proxy !== proxy || expected.owner != snapshot?.ownerKey ||
                        expected.owner != json.optString("ownerKey") || expected.id != json.optLong("commandId", -1)) return
                    pending = null
                }
                "state" -> {
                    val sequence = json.optLong("sequence", -1)
                    if (sequence !in 0..MAX_SAFE_INTEGER || sequence <= channel.sequence) return
                    val activation = json.optLong("activation", -1)
                    if (activation !in 0..MAX_SAFE_INTEGER || activation < channel.activation) return
                    val stateJson = json.optJSONObject("media")
                    val next = stateJson?.let { decode(it, channel) }
                    if (stateJson != null && next == null) return
                    if (next != null && (channel.state?.ownerKey != next.ownerKey ||
                            activation != channel.activation || next.playing && channel.state?.playing != true))
                        channel.activity = ++nextActivity
                    channel.sequence = sequence
                    channel.activation = activation
                    channel.lastSeen = SystemClock.elapsedRealtime()
                    channel.state = next
                    publish()
                }
            }
        } // Page input is untrusted; malformed messages must not break normal browsing.
    }

    private fun hello(document: String, mainFrame: Boolean, origin: String, proxy: JavaScriptReplyProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - helloWindow >= 1000) { helloWindow = now; helloCount = 0 }
        val old = channels[proxy]
        if (old != null && old.document == document && old.navigation == navigation && old.origin == origin) {
            if (now - old.lastHello < 500) return
            old.lastHello = now
            send(proxy, bound("bind", old).put("ownerPrefix", old.prefix))
            return
        }
        if (++helloCount > 16) return
        channels.remove(proxy)
        // A stale non-owner cannot exhaust the bounded table after its frame disappeared.
        val expired = channels.filter { (key, value) -> key !== ownerProxy && now - value.lastSeen > 60_000 }
        expired.forEach { (key, value) -> send(key, bound("reset", value)); channels.remove(key) }
        if (channels.size >= 32) { publish(); return }
        val prefix = "$tabId:$instance:$navigation:${++nextFrame}:$document:"
        val channel = Channel(document, UUID.randomUUID().toString(), navigation, prefix, mainFrame,
            origin, SiteMediaCompatibility.supports(origin), now)
        channels[proxy] = channel
        send(proxy, bound("bind", channel).put("ownerPrefix", prefix))
        publish()
    }

    private fun decode(json: JSONObject, channel: Channel): PageMediaSnapshot? {
        val element = json.optString("elementId")
        val revision = json.optLong("sourceRevision", -1)
        if (!element.matches(Regex("m[0-9]{1,12}")) || revision !in 1..MAX_SAFE_INTEGER ||
            !json.optBoolean("everPlayed")) return null
        fun number(key: String, fallback: Double, max: Double) = json.optDouble(key, fallback)
            .takeIf { it.isFinite() }?.coerceIn(0.0, max) ?: fallback
        fun text(key: String) = json.optString(key).take(512)
        val actions = json.optJSONArray("actions") ?: return null
        if (actions.length() > PageMediaActions.supported.size) return null
        val supportedActions = (0 until actions.length()).map { actions.optString(it) }
            .filterTo(linkedSetOf()) { it in PageMediaActions.supported }
        val frameUrl = httpUrl(json.optString("pageUrl"))
        // An embedded player's metadata is its own; the published page remains the top-level tab.
        val pageUrl = httpUrl(webView.url.orEmpty()) ?: frameUrl ?: channel.origin
        // A provider embed's document title usually isn't the top-level episode title.
        // Explicit frame Media Session metadata wins; top-frame metadata-only sessions are not merged.
        val title = (if (!channel.mainFrame && !json.optBoolean("hasMetadataTitle"))
            webView.title.orEmpty().take(512).ifBlank { text("title") } else text("title"))
            .ifBlank { webView.title.orEmpty().take(512) }.ifBlank { "Browser media" }
        val artwork = httpUrl(json.optString("artworkUrl"))
        channel.element = element
        channel.revision = revision
        return PageMediaSnapshot(tabId, channel.prefix + element + ":" + revision,
            title, text("artist"), text("album"), artwork, pageUrl,
            json.optBoolean("playing"), json.optBoolean("buffering"),
            number("positionMs", 0.0, MAX_TIME_MS.toDouble()).toLong(),
            if (json.isNull("durationMs")) -1 else number("durationMs", -1.0, MAX_TIME_MS.toDouble()).toLong(),
            number("speed", 1.0, 16.0).toFloat().takeIf { it > 0f } ?: 1f, supportedActions, isPrivate)
    }

    private fun publish() {
        val candidates = channels.entries.filter { it.value.state != null }
        val selected = candidates.maxWithOrNull(compareBy<Map.Entry<JavaScriptReplyProxy, Channel>>(
            { it.value.state?.playing == true }, { it.value.activity }))
        var next = selected?.value?.state
        if (next != null) {
            grace?.let(main::removeCallbacks)
            grace = null
        } else if (snapshot != null && accepting && !retired) {
            // The DOM can briefly drop a player between tracks. Keep its metadata, never its
            // playing claim or command route; navigation bypasses this grace completely.
            next = snapshot?.copy(playing = false)
            if (grace == null) {
                val owner = next?.ownerKey
                val expiry = Runnable {
                    grace = null
                    if (snapshot?.ownerKey == owner && ownerProxy == null) {
                        snapshot = null
                        pending = null
                        backgroundAllowed = false
                        onChanged(this)
                    }
                }
                grace = expiry
                main.postDelayed(expiry, 5_000)
            }
        }
        val changed = snapshot != next
        if (snapshot?.ownerKey != next?.ownerKey) {
            pending = null
            // The coordinator must grant background permission anew to a replacement owner.
            backgroundAllowed = false
        }
        ownerProxy = selected?.key
        if (ownerProxy == null) { pending = null; backgroundAllowed = false }
        snapshot = next
        sendBackgroundPolicies()
        if (!pruneScheduled && accepting && !retired && channels.any { (proxy, channel) ->
                channel.state?.playing == true || proxy !== ownerProxy
            }) {
            pruneScheduled = true
            main.postDelayed(prune, 5_000)
        }
        if (changed) onChanged(this)
    }

    private fun sendBackgroundPolicies() {
        channels.forEach { (proxy, channel) ->
            val owner = snapshot?.ownerKey?.takeIf { backgroundAllowed && proxy === ownerProxy && channel.compatible }
            if (owner != channel.backgroundOwner) {
                channel.backgroundOwner = owner
                send(proxy, bound("background", channel).put("allowed", owner != null)
                    .put("ownerKey", owner ?: ""))
            }
        }
    }

    private fun bound(type: String, channel: Channel) = JSONObject().put("type", type)
        .put("documentId", channel.document).put("navigation", channel.navigation).put("token", channel.token)

    private fun send(proxy: JavaScriptReplyProxy, message: JSONObject): Boolean =
        runCatching { proxy.postMessage(message.toString()); true }.getOrDefault(false)

    private fun httpUrl(value: String): String? = value.takeIf { it.length in 1..4096 }?.let {
        runCatching { URI(it) }.getOrNull()?.takeIf { uri ->
            uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
        }?.toString()
    }

    private companion object {
        const val BRIDGE = "MiowserPageMedia"
        const val MAX_MESSAGE_CHARS = 16_384
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
        const val MAX_TIME_MS = 31_536_000_000L
    }
}
