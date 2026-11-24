package com.miowser.app.browser

import android.net.Uri
import android.webkit.PermissionRequest

/** Freeze the request's identity and release it from the tab as soon as it is answered. */
internal class WebPermissionLease(request: PermissionRequest, onComplete: () -> Unit) : PermissionRequest() {
    private val requestOrigin: Uri = request.origin
    private val requested = request.resources.toList().distinct()
    private val resolution = WebPermissionResolution(requested.toSet(),
        onGrant = { request.grant(it.toTypedArray()) }, onDeny = request::deny, onComplete = onComplete)
    val pending: Boolean get() = resolution.pending
    override fun getOrigin(): Uri = requestOrigin
    override fun getResources(): Array<String> = requested.toTypedArray()
    override fun grant(resources: Array<String>) = resolution.grant(resources.toList())
    override fun deny() = resolution.deny()
    fun cancel() = resolution.cancel()
}
