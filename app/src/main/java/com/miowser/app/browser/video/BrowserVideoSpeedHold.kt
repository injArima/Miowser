package com.miowser.app.browser.video

/** Release/cancellation must never restore a speed onto a replacement fullscreen owner. */
internal class BrowserVideoSpeedHold {
    private var owner: BrowserVideoPipOwner? = null

    fun start(controller: BrowserPlaybackControls): Boolean {
        val state = controller.state.value
        val session = state.session ?: return false
        if (!session.state.playing || owner != null) return false
        owner = BrowserVideoPipOwner(session.id, state.attempt)
        controller.beginSpeedHold()
        return true
    }

    fun stop(controller: BrowserPlaybackControls) {
        val current = owner ?: return
        owner = null
        if (current.matches(controller.state.value)) controller.endSpeedHold()
    }
}
