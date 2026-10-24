package com.miowser.app.browser.video

/** Chrome callbacks can reenter when released; each accepted custom View closes only once. */
internal class FullscreenCallbackLease(private val onClose: () -> Unit) {
    var closed: Boolean = false
        private set

    fun close() {
        if (closed) return
        closed = true
        onClose()
    }
}
