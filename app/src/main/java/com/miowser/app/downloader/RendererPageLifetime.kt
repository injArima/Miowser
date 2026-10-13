package com.miowser.app.downloader

/** Authorization captured when a download is queued, not when its worker eventually starts. */
internal class RendererPageLifetime {
    private var generation = 0L
    private var retiredReason: String? = null

    @Synchronized fun capture(): Long = generation

    @Synchronized fun failureFor(token: Long): String? = retiredReason ?: if (token != generation) {
        "The originating page changed. Open the page and start the download again."
    } else null

    @Synchronized fun invalidate() { generation++ }

    @Synchronized fun retire(reason: String) {
        if (retiredReason == null) {
            retiredReason = reason
            generation++
        }
    }
}
