package com.miowser.app.adblock

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.webkit.ServiceWorkerClientCompat
import androidx.webkit.ServiceWorkerControllerCompat
import androidx.webkit.WebViewFeature
import com.miowser.app.diagnostics.SafeLog
import com.miowser.app.settings.AppSettings
import java.net.URI
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong

/** WebView service workers have a process-wide client, independent of any individual tab. */
internal object ServiceWorkerAdBlock {
    enum class Status { NOT_INSTALLED, ACTIVE, UNSUPPORTED, UNAVAILABLE }
    @Volatile var status = Status.NOT_INSTALLED
        private set
    val intercepted = AtomicLong()
    val blocked = AtomicLong()
    val missingContext = AtomicLong()

    /** Called on the main thread after WebView startup, before constructing/loading a tab. */
    fun install() {
        if (status != Status.NOT_INSTALLED) return
        try {
            if (!WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE) ||
                !WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
                status = Status.UNSUPPORTED
                return
            }
            ServiceWorkerControllerCompat.getInstance().setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse? {
                    intercepted.incrementAndGet()
                    if (!AppSettings.current.adBlockEnabled || !SiteProtection.initialized ||
                        !AdBlock.engine.isFullyLoaded) return null
                    val requestUrl = request.url.toString()
                    val context = ServiceWorkerRequestPolicy.context(requestUrl, request.requestHeaders)
                    if (context == null) {
                        missingContext.incrementAndGet()
                        return null
                    }
                    if (SiteProtection.mode(context.host) == SiteProtectionMode.OFF) return null
                    // No replacement fetch: a null result preserves Chromium's original cookies,
                    // method, body and streaming behavior. A blocked request gets its own stream.
                    return AdBlock.engine.decide(context.origin, requestUrl, request.requestHeaders, false)
                        ?.also { blocked.incrementAndGet() }
                }
            })
            status = Status.ACTIVE
        } catch (error: RuntimeException) {
            status = Status.UNAVAILABLE
            SafeLog.warning("Miowser/AdBlock", "Service-worker interception unavailable in this WebView provider", error)
        }
    }
}

internal data class ServiceWorkerRequestContext(val origin: String, val host: String)

/** Never guess that an ad server is the page, or attribute a worker to whichever tab is visible. */
internal object ServiceWorkerRequestPolicy {
    fun context(requestUrl: String, headers: Map<String, String>): ServiceWorkerRequestContext? {
        if (parse(requestUrl) == null) return null
        fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, true) }?.value
        // Origin is the sender's CORS origin; referrers can be shortened by referrer policy.
        val origin = header("Origin")
        if (origin != null) return parse(origin) // Explicit opaque/invalid origins remain unattributed.
        return header("Referer")?.let(::parse)
    }

    private fun parse(value: String): ServiceWorkerRequestContext? = runCatching {
        if (value.length !in 1..4096) return null
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme !in setOf("http", "https") || uri.rawUserInfo != null) return null
        val host = uri.host?.let(::canonicalHostname) ?: return null
        val port = uri.port
        if (port !in -1..65535 || port == 0) return null
        val origin = URI(scheme, null, host, port, null, null, null).toASCIIString()
        ServiceWorkerRequestContext(origin, host)
    }.getOrNull()
}
