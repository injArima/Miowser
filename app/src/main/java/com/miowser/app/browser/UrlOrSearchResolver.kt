package com.miowser.app.browser

import java.net.URLEncoder

/**
 * Decides whether address-bar input is a URL to navigate to directly or a search query to run
 * through the default search engine — the same URL/search-term detection every browser's
 * omnibox does. Pure logic, no Android dependency, so it's directly unit-testable.
 */
object UrlOrSearchResolver {
    private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://")
    private val DOMAIN_LIKE = Regex("^([a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,}(:[0-9]+)?(/.*)?$")
    private val LOCALHOST_LIKE = Regex("^localhost(:[0-9]+)?(/.*)?$")
    private val IP_LIKE = Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:[0-9]+)?(/.*)?$")

    private const val DEFAULT_SEARCH_URL_PREFIX = "https://duckduckgo.com/?q="

    /** [searchUrlPrefix] lets callers honor the user's chosen search engine (see Settings); defaults to DuckDuckGo. */
    fun resolve(input: String, searchUrlPrefix: String = DEFAULT_SEARCH_URL_PREFIX): String {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ""

        if (SCHEME_PREFIX.containsMatchIn(trimmed)) return trimmed

        val looksLikeUrl = !trimmed.contains(' ') &&
            (DOMAIN_LIKE.matches(trimmed) || LOCALHOST_LIKE.matches(trimmed) || IP_LIKE.matches(trimmed))
        if (looksLikeUrl) return "https://$trimmed"

        return searchUrlPrefix + URLEncoder.encode(trimmed, "UTF-8")
    }
}
