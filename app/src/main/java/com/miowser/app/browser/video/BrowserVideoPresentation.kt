package com.miowser.app.browser.video

/** Clock samples stay in the timeline leaves instead of invalidating all player controls. */
internal data class BrowserVideoDetails(
    val playing: Boolean, val buffering: Boolean, val muted: Boolean, val speed: Double,
    val width: Int, val height: Int, val sourceRevision: Long, val scale: BrowserVideoScale,
    val captions: List<BrowserMediaTrack>, val audioTracks: List<BrowserMediaTrack>
)

internal data class BrowserVideoPresentation(
    val owner: BrowserVideoPipOwner?, val video: BrowserVideoDetails?, val phase: BrowserVideoPhase,
    val canSeek: Boolean, val seeking: Boolean, val message: String?, val audioOnly: Boolean
) {
    val controlsEnabled: Boolean get() = owner != null && phase == BrowserVideoPhase.ACTIVE
}

internal fun browserVideoPresentation(state: BrowserVideoPlaybackState): BrowserVideoPresentation {
    val session = state.session
    val video = session?.state
    return BrowserVideoPresentation(session?.let { BrowserVideoPipOwner(it.id, state.attempt) },
        video?.let { BrowserVideoDetails(it.playing, it.buffering, it.muted, it.speed,
            it.videoWidth, it.videoHeight, it.sourceRevision, it.scale, it.captions, it.audioTracks) },
        state.phase, state.canSeek, state.pendingSeekMs != null, state.message, state.audioOnly)
}
