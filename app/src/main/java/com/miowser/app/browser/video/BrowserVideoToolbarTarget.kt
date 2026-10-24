package com.miowser.app.browser.video

/** Small, stable projection: the browser toolbar never observes the playback clock. */
internal data class BrowserVideoToolbarTarget(val id: LiveVideoId, val sourceRevision: Long)

internal fun browserVideoToolbarTarget(sessions: List<LiveVideoSession>): BrowserVideoToolbarTarget? {
    val best = sessions.asSequence().filter {
        val video = it.state
        video.assistantAction != VideoAssistantAction.NONE && video.visible && video.playing &&
            !video.ended && video.readyState >= 2 && video.videoWidth > 0 && video.videoHeight > 0
    }.maxWithOrNull(compareBy<LiveVideoSession> { it.lastInteractionMs }
        .thenBy { it.state.bounds.width * it.state.bounds.height * it.state.intersectionRatio }) ?: return null
    return best.takeIf { it.state.assistantAction == VideoAssistantAction.TOOLBAR }
        ?.let { BrowserVideoToolbarTarget(it.id, it.state.sourceRevision) }
}
