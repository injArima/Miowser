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

data class BrowserMediaTrack(val id: Long, val label: String, val language: String, val selected: Boolean)

enum class BrowserVideoScale(val label: String, val css: String) {
    FIT("Fit", "contain"), ZOOM("Zoom", "cover"), FILL("Fill", "fill")
}

data class LiveVideoState(
    val elementId: String,
    val positionMs: Long,
    val durationMs: Long?,
    val playing: Boolean,
    val buffering: Boolean,
    val muted: Boolean,
    val speed: Double,
    val videoWidth: Int,
    val videoHeight: Int,
    val seekable: List<VideoTimeRange>,
    val buffered: List<VideoTimeRange>,
    val readyState: Int,
    val sourceRevision: Long,
    val captions: List<BrowserMediaTrack> = emptyList(),
    val audioTracks: List<BrowserMediaTrack> = emptyList(),
    val scale: BrowserVideoScale = BrowserVideoScale.FIT
) {
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
    val state: LiveVideoState,
    val updatedAtMs: Long
)

/** Pure, bounded owner-thread registry. Rendering cadence does not drive browser recomposition. */
internal class LiveVideoSessionRegistry(private val tabId: Long) {
    private data class Frame(val document: String, var sequence: Long = -1)
    private val frames = linkedMapOf<Long, Frame>()
    private val sessions = linkedMapOf<LiveVideoId, LiveVideoSession>()
    var navigation: Long = 0
        private set
    private var nextFrame = 0L

    fun reset() { navigation++; frames.clear(); sessions.clear() }

    fun openFrame(document: String): Long? {
        if (document.length !in 1..80 || frames.size >= MAX_FRAMES) return null
        val id = ++nextFrame
        frames[id] = Frame(document)
        return id
    }

    fun update(navigation: Long, frameId: Long, sequence: Long, states: List<LiveVideoState>,
        nowMs: Long): Boolean {
        val frame = frames[frameId] ?: return false
        if (navigation != this.navigation || sequence <= frame.sequence || sequence < 0 ||
            states.size > MAX_VIDEOS_PER_FRAME ||
            states.any { it.elementId.length !in 1..80 }) return false
        frame.sequence = sequence
        // Each message is a complete bounded frame snapshot, including removals.
        val present = states.mapTo(hashSetOf()) { it.elementId }
        sessions.keys.removeAll { it.frameId == frameId && it.elementId !in present }
        for (state in states) {
            val id = LiveVideoId(tabId, navigation, frameId, frame.document, state.elementId)
            if (id !in sessions && sessions.size >= MAX_SESSIONS) continue
            // Replace live dimensions/rate/source revision, including in-place quality changes.
            sessions[id] = LiveVideoSession(id, state, nowMs)
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

    companion object {
        const val MAX_FRAMES = 8
        const val MAX_VIDEOS_PER_FRAME = 8
        const val MAX_SESSIONS = 32
        const val LEASE_MS = 45_000L
    }
}
