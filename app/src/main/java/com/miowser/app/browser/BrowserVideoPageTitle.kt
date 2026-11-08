package com.miowser.app.browser

import java.net.URI
import java.net.URLDecoder

private val episodeNumber = Regex("[0-9]{1,6}(?:\\.[0-9]{1,3})?")
private val episodePath = Regex("(?i)/(?:episodes?|ep)[-_/]([0-9]{1,6}(?:\\.[0-9]{1,3})?)(?:\\.html?)?/?$")
private val episodeLabel = Regex("(?i)\\b(?:episode|ep\\.?)\\s*(?:[-:#]\\s*)?([0-9]{1,6}(?:\\.[0-9]{1,3})?)\\b")

/** Some episode sites change the URL and video but retain the previous document title. */
internal fun browserVideoPageTitle(pageUrl: String, pageTitle: String): String {
    if (pageTitle.isBlank() || pageTitle == "New Tab") return pageTitle
    val uri = runCatching { URI(pageUrl) }.getOrNull() ?: return pageTitle
    if (uri.scheme?.lowercase() !in setOf("http", "https")) return pageTitle
    val candidates = buildList {
        episodePath.find(uri.path.orEmpty())?.groupValues?.get(1)?.let(::add)
        uri.rawQuery.orEmpty().split('&').forEach { field ->
            val pair = field.split('=', limit = 2)
            val key = runCatching { URLDecoder.decode(pair[0], "UTF-8") }.getOrNull()
            val value = pair.getOrNull(1)?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            if (key?.lowercase() in setOf("ep", "episode") && value != null && episodeNumber.matches(value)) add(value)
        }
    }.map { it.toBigDecimal().stripTrailingZeros().toPlainString() }.distinct()
    val current = candidates.singleOrNull() ?: return pageTitle
    val label = episodeLabel.findAll(pageTitle).lastOrNull()
        ?: return "$pageTitle · Episode $current"
    return pageTitle.replaceRange(label.groups[1]!!.range, current)
}
