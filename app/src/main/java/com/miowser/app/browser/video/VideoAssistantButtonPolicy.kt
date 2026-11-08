package com.miowser.app.browser.video

/** App-owned presentation; never inferred from a page's telemetry or stored in Library entries. */
internal data class VideoAssistantButtonPolicy(
    val enabled: Boolean = false,
    val background: String = "#303038",
    val foreground: String = "#FFFFFF",
    val reducedMotion: Boolean = false
)
