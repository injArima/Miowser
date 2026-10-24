package com.miowser.app.browser.video

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared by clock leaves only. A drag must not seek a replacement source or fullscreen entry. */
internal class BrowserVideoTimelineInteraction {
    var scrubPositionMs by mutableStateOf<Long?>(null)
        private set
    private var owner: BrowserVideoPipOwner? = null
    private var sourceRevision: Long? = null

    fun update(state: BrowserVideoPlaybackState, targetMs: Long) {
        if (!state.canSeek) return
        if (scrubPositionMs == null) {
            owner = state.session?.let { BrowserVideoPipOwner(it.id, state.attempt) }
            sourceRevision = state.session?.state?.sourceRevision
        }
        scrubPositionMs = targetMs
    }

    fun targetFor(state: BrowserVideoPlaybackState): Long? = scrubPositionMs?.takeIf {
        state.canSeek && owner?.matches(state) == true && state.session?.state?.sourceRevision == sourceRevision
    }

    fun clear() { scrubPositionMs = null; owner = null; sourceRevision = null }
}
