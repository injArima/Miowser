package com.miowser.app.browser.video

/** Fixture-only state, separate from detected media and the native player's playback state. */
internal data class BrowserVideoLabSample(
    val documentId: String,
    val sequence: Long,
    val positionMs: Long,
    val durationMs: Long,
    val bufferedMs: Long,
    val playing: Boolean,
    val buffering: Boolean,
    val speed: Double,
    val width: Int,
    val height: Int,
    val seekStartMs: Long?,
    val seekEndMs: Long?,
    val sourceKind: String,
    val sourceChanges: Int,
    val metadataLoads: Int
) {
    val seekable: Boolean get() = seekStartMs != null && seekEndMs != null && seekEndMs > seekStartMs

    fun seekTarget(targetMs: Long): Long? = if (seekable) {
        targetMs.coerceIn(requireNotNull(seekStartMs), requireNotNull(seekEndMs))
    } else null
}

/** A delayed message from an old document must never control the new fixture. */
internal class BrowserVideoLabLease {
    var generation: Long = 0
        private set
    var documentId: String? = null
        private set
    private var sequence = -1L

    fun reset(): Long {
        generation++
        documentId = null
        sequence = -1L
        return generation
    }

    fun accept(generation: Long, documentId: String, sequence: Long): Boolean {
        if (generation != this.generation || documentId.length !in 1..80 || sequence < 0) return false
        if (this.documentId != null && this.documentId != documentId) return false
        if (sequence <= this.sequence) return false
        this.documentId = documentId
        this.sequence = sequence
        return true
    }
}
