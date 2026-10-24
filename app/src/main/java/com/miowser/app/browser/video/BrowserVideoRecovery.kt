package com.miowser.app.browser.video

/** A resume reply or deadline belongs to one interrupted fullscreen entry and one request. */
internal class BrowserVideoRecovery {
    var paused = false
        private set
    var requestId = 0L
        private set
    private var waitingFor: BrowserVideoPipOwner? = null

    fun pause() { paused = true; cancel() }

    fun resume(owner: BrowserVideoPipOwner?): Long? {
        val wasPaused = paused
        paused = false
        if (!wasPaused) return null
        cancel()
        waitingFor = owner
        return if (owner != null) requestId else null
    }

    fun accepts(owner: BrowserVideoPipOwner, replyId: Long): Boolean =
        !paused && waitingFor == owner && requestId == replyId

    fun cancel() { waitingFor = null; requestId++ }
}
