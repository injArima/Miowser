package com.miowser.app.settings

/** A search engine choice for the address bar's non-URL input path (see UrlOrSearchResolver). */
enum class SearchEngine(val label: String, val queryUrlPrefix: String) {
    DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
    GOOGLE("Google", "https://www.google.com/search?q="),
    BING("Bing", "https://www.bing.com/search?q="),
    BRAVE("Brave", "https://search.brave.com/search?q="),
    CUSTOM("Custom", "https://duckduckgo.com/?q=")
}

enum class InactiveTabRetention(val label: String, val days: Int?) {
    NEVER("Never", null),
    DAYS_7("7 days", 7),
    DAYS_30("30 days", 30),
    DAYS_90("90 days", 90)
}

enum class HomePageMode(val label: String) {
    MIOWSER("Miowser home"),
    CUSTOM("Custom URL")
}

/**
 * How much say the user gets before a page-triggered new tab (target="_blank", window.open())
 * actually opens — see BrowserScreen's handleNewWindowRequested. WebView tells us whether the
 * request came from a real tap (isUserGesture) or page script acting on its own; NON_GESTURE_ONLY
 * uses that to only prompt for the kind most likely to be an unwanted popup, auto-allowing a
 * genuine link tap.
 */
enum class NewTabConfirmation(val label: String) {
    NEVER("Never ask"),
    NON_GESTURE_ONLY("Only for popups"),
    ALWAYS("Always ask")
}

/**
 * How many concurrent connections a direct-file download splits itself across — see
 * DirectFileDownloader. 1 is a real "off": at that setting the downloader skips the range-support
 * probe entirely and goes straight through the original single-stream path, zero added overhead.
 * Above 1, a server that turns out not to support ranged requests is silently downgraded to
 * single-stream for that download rather than surfaced as an error — the multi-threaded path is
 * strictly a speed optimization, never something a download should fail over.
 */
enum class DownloadThreadCount(val count: Int, val label: String) {
    ONE(1, "1 (no parallel downloads)"),
    TWO(2, "2"),
    FOUR(4, "4"),
    EIGHT(8, "8")
}

/**
 * All user-configurable app settings, plain and Android-free so it's trivial to construct in
 * tests. Browser home is now the native Miowser speed-dial page by default; the earlier remote
 * URL remains available through CUSTOM mode. DuckDuckGo, ad blocking, and tab restore retain
 * their established defaults.
 */
data class Settings(
    val adBlockEnabled: Boolean = true,
    val homePageUrl: String = "https://en.wikipedia.org",
    val homePageMode: HomePageMode = HomePageMode.MIOWSER,
    val searchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val customSearchUrl: String = "https://duckduckgo.com/?q=%s",
    val restoreTabsOnStartup: Boolean = true,
    val newTabConfirmation: NewTabConfirmation = NewTabConfirmation.NON_GESTURE_ONLY,
    val downloadThreadCount: DownloadThreadCount = DownloadThreadCount.FOUR,
    val javaScriptEnabled: Boolean = true,
    val cookiesEnabled: Boolean = true,
    val popupsEnabled: Boolean = true,
    val grabberMinimumDurationSeconds: Int = 0,
    val inactiveTabRetention: InactiveTabRetention = InactiveTabRetention.DAYS_30,
    val reduceMotion: Boolean = false,
    val backgroundPlaybackEnabled: Boolean = true,
    val crashDiagnosticsEnabled: Boolean = false
) {
    val autoDeleteInactiveTabs: Boolean get() = inactiveTabRetention != InactiveTabRetention.NEVER
    val searchUrlTemplate: String get() = if (searchEngine == SearchEngine.CUSTOM) customSearchUrl
        else searchEngine.queryUrlPrefix + "%s"

    fun searchUrl(query: String): String = searchUrlTemplate.replace(
        "%s", java.net.URLEncoder.encode(query, "UTF-8")
    )
}

fun isValidCustomSearchUrl(template: String): Boolean = runCatching {
    if (template.split("%s").size != 2) return@runCatching false
    val uri = java.net.URI(template.replace("%s", "test"))
    uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
}.getOrDefault(false)
