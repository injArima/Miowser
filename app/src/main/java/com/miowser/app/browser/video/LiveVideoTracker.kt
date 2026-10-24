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

/** Frame-bound telemetry, commands and recovery for the optional browser-backed player. */
internal class LiveVideoTracker(tabId: Long, private val webView: WebView,
    private val mutableSessions: MutableStateFlow<List<LiveVideoSession>>,
    initialButtonPolicy: VideoAssistantButtonPolicy = VideoAssistantButtonPolicy(),
    private val playback: BrowserVideoController = BrowserVideoController(),
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
    private var toolbarTicket: String? = null
    private data class Owner(val id: LiveVideoId, val attempt: Long)
    private data class Pending(val id: Long, val command: BrowserVideoCommand, val timeout: Runnable)
    private var owner: Owner? = null
    private var fallbackOwner: Owner? = null // Release-only identity; cannot receive player commands.
    private var nativeFullscreen = false
    private val recovery = BrowserVideoRecovery()
    private var recoveryTimeout: Runnable? = null
    private var nextCommand = 0L
    private val pending = mutableMapOf<String, Pending>()
    val supported = WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER) &&
        WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)

    private val prune = object : Runnable {
        override fun run() {
            pruneScheduled = false
            if (retired) return
            val now = SystemClock.elapsedRealtime()
            // Frames without videos expire too, so unrelated/removed frames cannot fill the table.
            val expired = channels.filterValues {
                it.frameId != protectedOwner()?.frameId &&
                    now - (it.lastUpdate.takeIf { time -> time >= 0 } ?: it.connectedAt) > LiveVideoSessionRegistry.LEASE_MS
            }
            expired.forEach { (proxy, channel) ->
                channels.remove(proxy)
                registry.closeFrame(channel.frameId)
                runCatching { proxy.postMessage(JSONObject().put("type", "reset")
                    .put("documentId", channel.document).toString()) }
            }
            registry.prune(now, protectedOwner())
            publish()
        }
    }

    fun install() {
        if (!supported || retired || installed) return
        installed = true
        playback.connect(::sendControl)
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
                VIDEO_ASSISTANT_BUTTON_SCRIPT + BROWSER_VIDEO_CONTROL_SCRIPT + LIVE_VIDEO_SESSION_SCRIPT, setOf("*"))
        } catch (_: RuntimeException) {
            // An optional feature/provider mismatch must not stop a tab from opening.
            retire()
        }
    }

    fun onNavigationStarted() {
        toolbarTicket = null
        accepting = false
        onNativeFullscreenHidden("The page changed. Return to the website to open its current video.")
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
        registry.prune(SystemClock.elapsedRealtime(), protectedOwner())
        return registry.active()
    }

    /** Arms only this frame's current video; fullscreen still requires a real page tap. */
    fun armToolbarEntry(target: BrowserVideoToolbarTarget): String? {
        if (!buttonPolicy.enabled || !accepting || retired || nativeFullscreen || owner != null) return null
        registry.prune(SystemClock.elapsedRealtime())
        val session = registry.snapshot().firstOrNull { it.id == target.id } ?: return null
        if (session.state.sourceRevision != target.sourceRevision ||
            browserVideoToolbarTarget(registry.snapshot()) != target) return null
        cancelToolbarEntry()
        val entry = channels.entries.firstOrNull { it.value.frameId == target.id.frameId &&
            it.value.document == target.id.documentId && it.value.navigation == target.id.navigation } ?: return null
        val ticket = UUID.randomUUID().toString()
        return runCatching {
            entry.key.postMessage(JSONObject().put("type", "armToolbarEntry").put("documentId", entry.value.document)
                .put("navigation", entry.value.navigation).put("token", entry.value.token)
                .put("elementId", target.id.elementId).put("sourceRevision", target.sourceRevision).toString())
            toolbarTicket = ticket
            ticket
        }.getOrNull()
    }

    fun cancelToolbarEntry(ticket: String? = null) {
        if (ticket != null && ticket != toolbarTicket) return
        toolbarTicket = null
        channels.forEach { (proxy, channel) ->
            runCatching { proxy.postMessage(JSONObject().put("type", "cancelToolbarEntry")
                .put("documentId", channel.document).put("navigation", channel.navigation)
                .put("token", channel.token).toString()) }
        }
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
                "state", "retire", "fullscreenError", "fullscreenActive", "fullscreenEnded", "fullscreenResumed", "controlStarted", "controlAck" -> {
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
                        if (owner?.id?.frameId == channel.frameId) clearOwner("The video page is no longer available. Return to the website.")
                        channels.remove(proxy)
                        registry.closeFrame(channel.frameId)
                        publish()
                        return
                    }
                    val now = SystemClock.elapsedRealtime()
                    val type = json.optString("type")
                    val attempt = json.optLong("attempt", -1)
                    val element = json.optString("elementId")
                    if (type == "fullscreenResumed" && (recoveryTimeout == null ||
                            owner?.let { !recovery.accepts(BrowserVideoPipOwner(it.id, it.attempt), json.optLong("recoveryId", -1)) } != false ||
                            owner?.id?.frameId != channel.frameId || owner?.id?.elementId != element ||
                            owner?.attempt != attempt)) return
                    val command = if (type == "controlAck" || type == "controlStarted") {
                        val current = pending.values.firstOrNull { it.id == json.optLong("commandId", -1) } ?: return
                        if (!nativeFullscreen || current.command.attempt != attempt || current.command.owner.frameId != channel.frameId ||
                            current.command.owner.elementId != element || owner != Owner(current.command.owner, attempt)) return
                        current
                    } else null
                    if (type == "state" && now - channel.lastUpdate < 100) return
                    val videos = json.getJSONArray("videos")
                    val removed = json.getJSONArray("removed")
                    if (videos.length() > LiveVideoSessionRegistry.MAX_VIDEOS_PER_FRAME ||
                        removed.length() > LiveVideoSessionRegistry.MAX_VIDEOS_PER_FRAME) return
                    val states = (0 until videos.length()).map { decode(videos.getJSONObject(it)) }
                    val deleted = (0 until removed.length()).map { removed.getString(it).take(80) }
                    if (registry.update(channel.navigation, channel.frameId, json.getLong("sequence"), states, deleted, now)) {
                        channel.lastUpdate = now
                        if (type == "fullscreenActive" && attempt in 1..9_007_199_254_740_991L) {
                            val session = registry.snapshot().firstOrNull { it.id.frameId == channel.frameId && it.id.elementId == element }
                            if (session != null) {
                                if (owner != Owner(session.id, attempt)) clearOwner()
                                owner = Owner(session.id, attempt)
                            }
                        }
                        if (type == "fullscreenEnded" && owner?.id?.frameId == channel.frameId &&
                            owner?.id?.elementId == element && owner?.attempt == attempt) {
                            clearOwner(if (nativeFullscreen) "The website ended fullscreen. Return to the page." else null)
                        }
                        publish()
                        if (type == "fullscreenResumed") {
                            cancelRecovery()
                            if (json.optBoolean("ok") && playback.state.value.session?.id == owner?.id) playback.recovered()
                            else clearOwner("The website ended this video session. Return to the website to reopen it.")
                        }
                        if (type == "controlStarted" && command != null) playback.started(command.command.action)
                        if (type == "controlAck" && command != null) {
                            finish(command, json.optBoolean("ok"))
                        }
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
        val current = owner
        if (nativeFullscreen && current != null) {
            val session = mutableSessions.value.firstOrNull { it.id == current.id }
            if (session != null) {
                if (playback.state.value.session?.state?.sourceRevision?.let { it != session.state.sourceRevision } == true) cancelPending()
                playback.attach(session, current.attempt)
            } else clearOwner("The video connection was lost. Return to the website to reopen it.")
        }
        if (!pruneScheduled && channels.isNotEmpty() && !retired) {
            pruneScheduled = true
            main.postDelayed(prune, 15_000)
        }
    }

    fun retire() {
        if (retired) return
        retired = true
        onNavigationStarted()
        playback.disconnect()
        runCatching { script?.remove() }
        script = null
        if (installed) runCatching { WebViewCompat.removeWebMessageListener(webView, BRIDGE) }
    }

    fun onNativeFullscreenShown() { nativeFullscreen = true; publish() }
    fun onNativeFullscreenHidden(reason: String? = null) {
        // Send the exact owner's release before detaching; never exit a later/unrelated element.
        ownerMessage("releaseFullscreen")
        nativeFullscreen = false
        val wasOwned = owner != null
        clearOwner(if (wasOwned) reason else null)
    }

    fun onHostPaused(pip: Boolean) {
        cancelToolbarEntry()
        cancelRecovery()
        recovery.pause()
        ownerMessage("suspendFullscreen", pip = pip)
        cancelPending()
        playback.suspend()
    }

    fun onHostResumed() {
        if (!recovery.paused) return
        cancelRecovery()
        val expected = owner?.takeIf { nativeFullscreen }?.let { BrowserVideoPipOwner(it.id, it.attempt) }
        val ticket = recovery.resume(expected) ?: return
        playback.reconnecting()
        val timeout = Runnable {
            if (expected == null || !recovery.accepts(expected, ticket)) return@Runnable
            recoveryTimeout = null
            clearOwner("Player controls couldn't reconnect. Use the website controls or return to the page.")
        }
        recoveryTimeout = timeout
        main.postDelayed(timeout, 4_000)
        if (!ownerMessage("resumeFullscreen")) {
            clearOwner("The video connection is unavailable. Return to the website.")
        }
    }

    private fun protectedOwner(): LiveVideoId? = owner?.id?.takeIf {
        nativeFullscreen && (recovery.paused || recoveryTimeout != null)
    }

    private fun ownerMessage(type: String, pip: Boolean = false): Boolean {
        val current = owner ?: fallbackOwner?.takeIf { type == "releaseFullscreen" } ?: return false
        val entry = channels.entries.firstOrNull { it.value.frameId == current.id.frameId &&
            it.value.navigation == current.id.navigation && it.value.document == current.id.documentId } ?: return false
        return runCatching {
            entry.key.postMessage(JSONObject().put("type", type).put("documentId", entry.value.document)
                .put("navigation", entry.value.navigation).put("token", entry.value.token)
                .put("elementId", current.id.elementId).put("attempt", current.attempt)
                .put("recoveryId", recovery.requestId).put("pip", pip).toString())
            true
        }.getOrDefault(false)
    }

    private fun cancelRecovery() {
        recoveryTimeout?.let(main::removeCallbacks)
        recoveryTimeout = null
        recovery.cancel()
    }

    private fun cancelPending() {
        pending.values.forEach { main.removeCallbacks(it.timeout) }
        pending.clear()
    }

    private fun clearOwner(reason: String? = null) {
        if (reason != null) ownerMessage("fallbackFullscreen")
        if (reason != null && owner != null) fallbackOwner = owner
        else if (reason == null) fallbackOwner = null
        owner = null
        cancelRecovery()
        cancelPending()
        playback.detach(reason)
    }

    private fun sendControl(command: BrowserVideoCommand): Boolean {
        if (retired || !nativeFullscreen || owner != Owner(command.owner, command.attempt)) return false
        val entry = channels.entries.firstOrNull { it.value.frameId == command.owner.frameId &&
            it.value.navigation == command.owner.navigation && it.value.document == command.owner.documentId } ?: return false
        val channel = entry.value
        pending.remove(command.action.family)?.let { main.removeCallbacks(it.timeout) }
        val id = ++nextCommand
        val timeout = Runnable { pending[command.action.family]?.takeIf { it.id == id }?.let { finish(it, false) } }
        val item = Pending(id, command, timeout)
        pending[command.action.family] = item
        val json = controlJson(command, channel, id)
        return runCatching {
            entry.key.postMessage(json.toString())
            main.postDelayed(timeout, if (command.action.family == "seek") 8_000 else 5_000)
            true
        }.getOrElse {
            pending.remove(command.action.family)
            if (command.action == BrowserVideoAction.RICH_UI && command.value == 1.0) clearOwner("Player controls are unavailable. Use the website controls or return to the page.")
            false
        }
    }

    private fun controlJson(command: BrowserVideoCommand, channel: Channel, id: Long): JSONObject =
        JSONObject().put("type", "control").put("documentId", channel.document).put("navigation", channel.navigation)
            .put("token", channel.token).put("elementId", command.owner.elementId).put("attempt", command.attempt)
            .put("commandId", id).put("sourceRevision", command.sourceRevision).put("action", command.action.wireName).also { json ->
                command.value?.let { json.put("value", it) }
            }

    private fun finish(item: Pending, ok: Boolean) {
        if (pending[item.command.action.family]?.id != item.id) return
        pending.remove(item.command.action.family)
        main.removeCallbacks(item.timeout)
        if (!ok && item.command.action == BrowserVideoAction.RICH_UI && item.command.value == 1.0) {
            // If custom controls cannot attach, restore the already-working browser controls.
            channels.entries.firstOrNull { it.value.frameId == item.command.owner.frameId }?.let { entry ->
                runCatching { entry.key.postMessage(controlJson(item.command.copy(value = 0.0), entry.value, ++nextCommand).toString()) }
            }
            clearOwner("Player controls are unavailable. Use the website controls or return to the page.")
        } else playback.completed(item.command.action, ok)
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
        fun tracks(key: String, limit: Int): List<BrowserMediaTrack> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until minOf(array.length(), limit)).mapNotNull { index ->
                val track = array.optJSONObject(index) ?: return@mapNotNull null
                val id = track.optLong("id", -1).takeIf { it in 1..9_007_199_254_740_991L } ?: return@mapNotNull null
                BrowserMediaTrack(id, track.optString("label").take(80), track.optString("language").take(16), track.optBoolean("selected"))
            }
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
            json.optLong("sourceRevision").coerceAtLeast(0), json.optBoolean("interacted"),
            tracks("captions", 16), tracks("audioTracks", 8),
            BrowserVideoScale.entries.firstOrNull { it.css == json.optString("scale") } ?: BrowserVideoScale.FIT,
            when (json.optString("assistantAction")) {
                "row" -> VideoAssistantAction.ROW
                "toolbar" -> VideoAssistantAction.TOOLBAR
                else -> VideoAssistantAction.NONE
            })
    }

    companion object {
        private const val BRIDGE = "MiowserLiveVideo"
        private const val MAX_MESSAGE_CHARS = 24_576
    }
}
