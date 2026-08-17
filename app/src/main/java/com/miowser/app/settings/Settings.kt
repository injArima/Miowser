package com.miowser.app.settings

/** A search engine choice for the address bar's non-URL input path (see UrlOrSearchResolver). */
enum class SearchEngine(val label: String, val queryUrlPrefix: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q=")
}

/**
 * All user-configurable app settings, plain and Android-free so it's trivial to construct in
 * tests. Defaults here are the app's actual out-of-the-box behavior from before Settings existed
 * (same starter URL, same DuckDuckGo default, ad-block on, tabs restored) — installing this
 * feature must not silently change existing behavior for anyone who never opens Settings.
 */
data class Settings(
    val adBlockEnabled: Boolean = true,
    val homePageUrl: String = "https://en.wikipedia.org",
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val restoreTabsOnStartup: Boolean = true
)
