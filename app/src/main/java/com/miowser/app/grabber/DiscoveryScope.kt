package com.miowser.app.grabber

import java.net.URI

/** Page traversal stays on the exact origin; media files may still live on a separate CDN. */
object DiscoveryScope {
    fun allows(origin: URI, candidate: URI): Boolean =
        origin.scheme.equals(candidate.scheme, true) &&
            origin.host != null && origin.host.equals(candidate.host, true) &&
            effectivePort(origin) == effectivePort(candidate)

    private fun effectivePort(uri: URI): Int = when {
        uri.port >= 0 -> uri.port
        uri.scheme.equals("https", true) -> 443
        else -> 80
    }
}
