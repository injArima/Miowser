package com.miowser.app.browser

import java.net.URI

enum class SiteSecurity(val label: String, val description: String) {
    SECURE("Secure connection", "This page finished loading over HTTPS without a reported certificate error."),
    CHECKING("Checking connection", "The page has not finished loading, so its connection is not verified yet."),
    INSECURE("Connection not secure", "This page uses unencrypted HTTP. Information you send could be read or changed in transit."),
    CERTIFICATE_ERROR("Certificate error", "Android could not verify this site's certificate. This connection is not trusted."),
    CERTIFICATE_BYPASSED("Certificate not verified", "This site's certificate failed verification. You chose to continue; the connection must not be treated as secure."),
    OTHER("No secure page", "There is no verified HTTPS page in this tab.")
}

internal fun siteSecurity(url: String, finishedUrl: String?, certificateErrorUrl: String?,
    certificateBypassed: Boolean): SiteSecurity {
    if (certificateErrorUrl != null &&
        runCatching { URI(certificateErrorUrl).host }.getOrNull() == runCatching { URI(url).host }.getOrNull())
        return if (certificateBypassed) SiteSecurity.CERTIFICATE_BYPASSED else SiteSecurity.CERTIFICATE_ERROR
    return when (runCatching { URI(url).scheme?.lowercase() }.getOrNull()) {
        "https" -> if (finishedUrl?.substringBefore('#') == url.substringBefore('#'))
            SiteSecurity.SECURE else SiteSecurity.CHECKING
        "http" -> SiteSecurity.INSECURE
        else -> SiteSecurity.OTHER
    }
}

/** HTTP approvals last only for this process and are separate for normal and private tabs. */
internal object HttpNavigationApproval {
    private val accepted = mutableSetOf<Pair<String, Boolean>>()

    @Synchronized fun requiresWarning(url: String, isPrivate: Boolean): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("http", ignoreCase = true)) return false
        val host = uri.host?.lowercase() ?: return false
        return host to isPrivate !in accepted
    }

    @Synchronized fun accept(url: String, isPrivate: Boolean) {
        val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return
        accepted.add(host to isPrivate)
    }

    @Synchronized fun clearPrivate() { accepted.removeAll { it.second } }
}
