package com.miowser.app.browser.video

/** Runtime-only identities. Never serialized into tab backups, history or the Library. */
data class LiveVideoId(
    val tabId: Long,
    val navigation: Long,
    val frameId: Long,
    val documentId: String,
    val elementId: String
)

data class VideoTimeRange(val startMs: Long, val endMs: Long) {
    init { require(startMs >= 0 && endMs >= startMs) }
}

/** Frame-local CSS pixels. These are not top-level screen coordinates for nested iframes. */
data class VideoFrameBounds(
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
    val viewportWidth: Double,
    val viewportHeight: Double
)

enum class LiveVideoSourceKind { NETWORK, BLOB, MEDIA_STREAM, DATA, UNKNOWN }
enum class LiveVideoSeekEvidence { SEEKABLE_RANGES, FINITE_DURATION_ONLY, UNKNOWN }

enum class VideoAssistantAction { NONE, ROW, TOOLBAR }

data class BrowserMediaTrack(val id: Long, val label: String, val language: String, val selected: Boolean)

enum class BrowserVideoScale(val label: String, val css: String) {
    FIT("Fit", "contain"), ZOOM("Zoom", "cover"), FILL("Fill", "fill")
}

data class LiveVideoState(
    val elementId: String,
    val source: String?,
    val sourceKind: LiveVideoSourceKind,
    val positionMs: Long,
    val durationMs: Long?,
    val playing: Boolean,
    val ended: Boolean,
    val buffering: Boolean,
    val muted: Boolean,
    val speed: Double,
    val videoWidth: Int,
    val videoHeight: Int,
    val visible: Boolean,
    val intersectionRatio: Double,
    val bounds: VideoFrameBounds,
    val seekable: List<VideoTimeRange>,
    val buffered: List<VideoTimeRange>,
    val readyState: Int,
    val sourceRevision: Long,
    val interacted: Boolean,
    val captions: List<BrowserMediaTrack> = emptyList(),
    val audioTracks: List<BrowserMediaTrack> = emptyList(),
    val scale: BrowserVideoScale = BrowserVideoScale.FIT,
    val assistantAction: VideoAssistantAction = VideoAssistantAction.NONE
) {
    val seekEvidence: LiveVideoSeekEvidence get() = when {
        seekable.isNotEmpty() -> LiveVideoSeekEvidence.SEEKABLE_RANGES
        durationMs != null && durationMs > 0 && readyState >= 1 -> LiveVideoSeekEvidence.FINITE_DURATION_ONLY
        else -> LiveVideoSeekEvidence.UNKNOWN
    }

    /** An empty range is retained as unknown/unavailable, not guessed from duration. */
    fun seekTarget(targetMs: Long): Long? {
        if (seekable.isEmpty()) return null
        val containing = seekable.firstOrNull { targetMs in it.startMs..it.endMs }
        if (containing != null) return targetMs
        return seekable.flatMap { listOf(it.startMs, it.endMs) }
            .minByOrNull { kotlin.math.abs(it - targetMs.coerceIn(0, MAX_TIME_MS)) }
    }

    companion object { const val MAX_TIME_MS = 31_536_000_000L }
}

data class LiveVideoSession(
    val id: LiveVideoId,
    /** Supplied by Android's frame callback, not trusted from a page JSON field. */
    val frameOrigin: String,
    val mainFrame: Boolean,
    val state: LiveVideoState,
    val updatedAtMs: Long,
    val lastInteractionMs: Long
)

/** Pure, bounded owner-thread registry. Rendering cadence does not drive browser recomposition. */
internal class LiveVideoSessionRegistry(private val tabId: Long) {
    private data class Frame(val id: Long, val document: String, val origin: String, val main: Boolean,
        var sequence: Long = -1)
    private val frames = linkedMapOf<Long, Frame>()
    private val sessions = linkedMapOf<LiveVideoId, LiveVideoSession>()
    var navigation: Long = 0
        private set
    private var nextFrame = 0L

    fun reset() { navigation++; frames.clear(); sessions.clear() }

    fun openFrame(document: String, origin: String, main: Boolean): Long? {
        if (document.length !in 1..80 || origin.length !in 1..1024 || frames.size >= MAX_FRAMES) return null
        val id = ++nextFrame
        frames[id] = Frame(id, document, origin, main)
        return id
    }

    fun update(navigation: Long, frameId: Long, sequence: Long, states: List<LiveVideoState>,
        removed: List<String>, nowMs: Long): Boolean {
        val frame = frames[frameId] ?: return false
        if (navigation != this.navigation || sequence <= frame.sequence || sequence < 0 ||
            states.size > MAX_VIDEOS_PER_FRAME || removed.size > MAX_VIDEOS_PER_FRAME ||
            states.any { it.elementId.length !in 1..80 }) return false
        frame.sequence = sequence
        // Each message is a complete bounded frame snapshot. This also handles rapid replacement
        // when more removals occur between batches than fit in the explicit removed-ID list.
        val present = states.mapTo(hashSetOf()) { it.elementId }
        sessions.keys.removeAll { it.frameId == frameId && it.elementId !in present }
        removed.forEach { sessions.remove(LiveVideoId(tabId, navigation, frameId, frame.document, it)) }
        for (state in states) {
            val id = LiveVideoId(tabId, navigation, frameId, frame.document, state.elementId)
            val old = sessions[id]
            if (old == null && (sessions.size >= MAX_SESSIONS ||
                    sessions.keys.count { it.frameId == frameId } >= MAX_VIDEOS_PER_FRAME)) continue
            // Replace live dimensions/rate/source, even when quality changes under the same blob URL.
            sessions[id] = LiveVideoSession(id, frame.origin, frame.main, state, nowMs,
                if (state.interacted) nowMs else old?.lastInteractionMs ?: 0)
        }
        return true
    }

    fun closeFrame(id: Long) {
        frames.remove(id)
        sessions.keys.removeAll { it.frameId == id }
    }

    fun prune(nowMs: Long, protectedOwner: LiveVideoId? = null) {
        sessions.values.removeAll { it.id != protectedOwner && nowMs - it.updatedAtMs > LEASE_MS }
    }
    fun snapshot(): List<LiveVideoSession> = sessions.values.toList()

    fun active(): LiveVideoSession? = sessions.values.asSequence()
        .filter { it.state.visible && it.state.playing && !it.state.ended }
        .maxWithOrNull(compareBy<LiveVideoSession> { it.lastInteractionMs }
            .thenBy { it.state.bounds.width * it.state.bounds.height * it.state.intersectionRatio }
            .thenBy { it.updatedAtMs })

    companion object {
        const val MAX_FRAMES = 8
        const val MAX_VIDEOS_PER_FRAME = 8
        const val MAX_SESSIONS = 32
        const val LEASE_MS = 45_000L
    }
}
