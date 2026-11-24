package com.miowser.app.browser

/** Resource IDs are exact WebView capabilities, not Android app-wide permission grants. */
internal object WebPermissionPolicy {
    const val CAMERA = "android.webkit.resource.VIDEO_CAPTURE"
    const val MICROPHONE = "android.webkit.resource.AUDIO_CAPTURE"
    const val PROTECTED_MEDIA = "android.webkit.resource.PROTECTED_MEDIA_ID"
    const val MIDI = "android.webkit.resource.MIDI_SYSEX"
    private val known = setOf(CAMERA, MICROPHONE, PROTECTED_MEDIA, MIDI)

    fun label(resource: String): String = when (resource) {
        CAMERA -> "Camera"
        MICROPHONE -> "Microphone"
        PROTECTED_MEDIA -> "Protected content (DRM)"
        MIDI -> "MIDI device control (SysEx)"
        "geolocation" -> "Location"
        else -> "Website capability: $resource"
    }

    fun review(resources: List<String>): WebPermissionReview = WebPermissionReview(resources.distinct().toList())
    fun isKnown(resource: String): Boolean = resource in known
    fun secureOrigin(origin: String): Boolean = runCatching {
        val uri = java.net.URI(origin)
        uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)
}

internal data class WebPermissionReview(val resources: List<String>) {
    val valid: Boolean get() = resources.isNotEmpty() && resources.all { it.isNotBlank() }
    val canRemember: Boolean get() = valid && resources.all(WebPermissionPolicy::isKnown)
    val sensitive: Boolean get() = resources.any { it != WebPermissionPolicy.CAMERA && it != WebPermissionPolicy.MICROPHONE }
    val names: String get() = resources.joinToString(transform = WebPermissionPolicy::label)
    val runtimePermissions: List<String> get() = buildList {
        if (WebPermissionPolicy.CAMERA in resources) add("android.permission.CAMERA")
        if (WebPermissionPolicy.MICROPHONE in resources) add("android.permission.RECORD_AUDIO")
    }
    val explanation: String get() = resources.joinToString("\n\n") { resource ->
        when (resource) {
            WebPermissionPolicy.CAMERA -> "Camera: capture video from your device. Android camera permission is also required."
            WebPermissionPolicy.MICROPHONE -> "Microphone: record audio from your device. Android microphone permission is also required."
            WebPermissionPolicy.PROTECTED_MEDIA -> "Protected content (DRM): use a protected-media identifier to request playback licenses. This does not provide camera or microphone access. Playback still depends on the site's license and your device's DRM support."
            WebPermissionPolicy.MIDI -> "MIDI device control (SysEx): exchange privileged messages with connected musical devices. These may change sounds, device settings, or firmware."
            else -> "Website capability: $resource\nThis WebView resource is not described by Miowser yet. You can explicitly allow this request once; it will not be remembered. WebView still decides whether the capability is supported."
        }
    }
    val confirmation: String? get() = if (sensitive)
        "Allow $names for this requesting website?\n\n$explanation" else null
}

/** One answer per request; cancellation and tab teardown cannot grant a stale request. */
internal class WebPermissionResolution(
    requested: Set<String>,
    private val onGrant: (List<String>) -> Unit,
    private val onDeny: () -> Unit,
    private val onComplete: () -> Unit
) {
    private val requested = requested.toSet()
    var pending: Boolean = true
        private set
    fun grant(reviewed: List<String>) = finish {
        val allowed = reviewed.distinct().filter { it in requested }
        if (allowed.isEmpty()) onDeny() else onGrant(allowed)
    }
    fun deny() = finish(onDeny)
    fun cancel() = finish {}
    private fun finish(action: () -> Unit) {
        if (!pending) return
        pending = false
        try { action() } finally { onComplete() }
    }
}
