package com.miowser.app.browser.video

/** Explicit PiP ownership belongs to one entry, never an arbitrary playing/background tab. */
internal data class BrowserVideoPipOwner(val id: LiveVideoId, val attempt: Long) {
    fun matches(state: BrowserVideoPlaybackState): Boolean = state.session?.id == id && state.attempt == attempt
}

internal fun keepBrowserRendererForPip(owner: BrowserVideoPipOwner?, fullscreenTabId: Long?,
    tabId: Long, state: BrowserVideoPlaybackState): Boolean = owner != null &&
    owner.id.tabId == tabId && fullscreenTabId == tabId && owner.matches(state)

/** Audio-only playback may keep only the WebView that owns the active fullscreen session alive. */
internal fun keepBrowserRendererForAudioOnly(audioOnly: Boolean, fullscreenTabId: Long?,
    tabId: Long, state: BrowserVideoPlaybackState): Boolean = audioOnly && state.audioOnly &&
    state.session?.id?.tabId == tabId && fullscreenTabId == tabId
