package com.miowser.app.settings

import java.text.Normalizer
import java.util.Locale

enum class SettingsSearchScope(val label: String) { BROWSER("Browser settings"), MEDIA("Media settings") }

enum class SettingsSearchPage(val scope: SettingsSearchScope) {
    MASTER(SettingsSearchScope.MEDIA), BROWSER(SettingsSearchScope.BROWSER), MEDIA(SettingsSearchScope.MEDIA),
    INTERFACE(SettingsSearchScope.MEDIA), VIDEO(SettingsSearchScope.MEDIA),
    SUBTITLES(SettingsSearchScope.MEDIA), ADVANCED(SettingsSearchScope.MEDIA)
}

data class SettingsSearchAvailability(
    val discovery: Boolean = true,
    val library: Boolean = true,
    val savedMedia: Boolean = false,
    val subtitleOutline: Boolean = true,
    val deviceLibrary: Boolean = true,
    val browser: Boolean = true
)

enum class SettingsSearchCondition { ALWAYS, DEVICE, BROWSER, DISCOVERY, RESUME, SAVED_LIBRARY, SUBTITLE_OUTLINE }

data class SettingsSearchEntry(
    val id: String,
    val scope: SettingsSearchScope,
    val page: SettingsSearchPage,
    val title: String,
    val path: String,
    val summary: String,
    val keywords: String = "",
    val anchor: String = title,
    val condition: SettingsSearchCondition = SettingsSearchCondition.ALWAYS
) {
    fun available(a: SettingsSearchAvailability): Boolean = (scope != SettingsSearchScope.BROWSER || a.browser) && when (condition) {
        SettingsSearchCondition.ALWAYS -> true
        SettingsSearchCondition.DEVICE -> a.deviceLibrary
        SettingsSearchCondition.BROWSER -> a.browser
        SettingsSearchCondition.DISCOVERY -> a.deviceLibrary && a.discovery
        SettingsSearchCondition.RESUME -> a.deviceLibrary || a.library
        SettingsSearchCondition.SAVED_LIBRARY -> a.library && a.savedMedia
        SettingsSearchCondition.SUBTITLE_OUTLINE -> a.subtitleOutline
    }
}

/** Actual app settings only; matching never executes the setting's action. */
object SettingsSearchIndex {
    private val marks = "\\p{M}+".toRegex()
    private val separators = "[^\\p{L}\\p{N}]+".toRegex()
    private fun browser(id: String, title: String, section: String, summary: String, keywords: String = "") =
        SettingsSearchEntry(id, SettingsSearchScope.BROWSER, SettingsSearchPage.BROWSER,
            title, "Browser settings › $section", summary, keywords)

    private fun media(id: String, title: String, section: String, summary: String, keywords: String = "",
        page: SettingsSearchPage = SettingsSearchPage.MEDIA,
        condition: SettingsSearchCondition = SettingsSearchCondition.ALWAYS) =
        SettingsSearchEntry(id, SettingsSearchScope.MEDIA, page,
            title, "Media settings › $section", summary,
            if (page == SettingsSearchPage.SUBTITLES) "$keywords subtitle subtitles caption captions cc" else keywords,
            condition = condition)

    val entries = listOf(
        media("master.guide", "Getting started", "Help", "Explore the current app and your chosen experience.", "onboarding guide tutorial help", page = SettingsSearchPage.MASTER),
        media("master.customize", "Customize Miowser", "Your experience", "Choose browser, player, or both and set up your watch catalog.", "setup mode features welcome", page = SettingsSearchPage.MASTER),
        media("master.browser", "Browser settings", "Settings", "Open browsing, tabs, privacy, and website preferences.", page = SettingsSearchPage.BROWSER, condition = SettingsSearchCondition.BROWSER),
        media("master.media", "Media settings", "Settings", "Open playback, subtitles, downloads, and appearance.", page = SettingsSearchPage.MASTER),
        media("master.backup", "Backup & restore", "Settings", "Save app data or restore a previous backup.", page = SettingsSearchPage.MASTER),
        browser("browser.search", "Search engine", "General", "Choose the address-bar search provider.", "google bing duckduckgo brave custom search url autocomplete"),
        browser("browser.home", "Browser home page", "General", "Choose the page opened in a new tab.", "homepage custom url speed dial new tab start"),
        browser("browser.restore", "Restore tabs on startup", "Tabs", "Reopen saved regular tabs.", "session restore restart"),
        browser("browser.archive", "Delete archived inactive tabs", "Tabs", "Choose how long archived tabs are kept.", "retention cleanup duplicate unused"),
        browser("browser.confirm", "Confirm before opening a new tab", "Tabs", "Choose when page-created tabs need approval.", "popup window confirmation"),
        browser("browser.clear", "Clear browsing data", "Privacy & security", "Choose history, cookies, cache, or app records to erase.", "delete history storage cache reset"),
        browser("browser.permissions", "Site permissions", "Privacy & security", "Review remembered website access.", "camera microphone location allow block"),
        browser("browser.elements", "Blocked page elements", "Privacy & security", "Review hidden and deleted website elements.", "purge cosmetic css revert hide remove"),
        browser("browser.cookies", "Cookies", "Privacy & security", "Allow sites to remember sign-ins and preferences.", "login storage website data"),
        browser("browser.popups", "Allow popups", "Privacy & security", "Allow websites to open windows.", "pop ups new window"),
        browser("browser.length", "Minimum media length", "Downloads", "Filter detected media by duration.", "video detector grabber duration seconds minutes"),
        browser("browser.javascript", "JavaScript", "Advanced", "Enable website scripts.", "scripts js website compatibility"),
        browser("browser.backup", "Backup & restore", "Data", "Save app data or restore a backup.", "export import recovery"),
        browser("browser.guide", "Getting started", "About", "Review Miowser's guide.", "onboarding help tutorial"),
        browser("browser.version", "Version", "About", "View the installed app version.", "build release number"),
        SettingsSearchEntry("browser.media", SettingsSearchScope.BROWSER, SettingsSearchPage.MEDIA,
            "Media settings", "Browser settings › Media", "Open library and player preferences.", "player video subtitles library"),
        media("media.folders", "Media library folders", "Media library", "Choose folders included in Device videos.", "directories storage scan roots linked folder", condition = SettingsSearchCondition.DEVICE),
        media("media.discovery", "Device video discovery", "Media library", "Find videos in storage and linked folders.", "scan local files library indexing", condition = SettingsSearchCondition.DEVICE),
        media("media.rescan", "Auto rescan", "Media library", "Scan once when the app starts.", "startup refresh new deleted files", condition = SettingsSearchCondition.DISCOVERY),
        media("media.background", "Background playback", "Video", "Keep permitted audio playing while the app is minimized.", "audio headphones screen off switching away"),
        media("media.orientation", "Screen orientation", "Video", "Choose how both players open.", "rotation rotate landscape portrait sensor lock"),
        media("media.jobs", "Simultaneous downloads", "Network", "Choose how many files download at once.", "queue concurrency parallel files"),
        media("media.connections", "Connections per download", "Network", "Choose the default connections per file.", "threads speed parallel network"),
        media("media.hostConnections", "Connections per site", "Network", "Limit connections to one server across downloads.", "host domain server limit"),
        media("media.retries", "Automatic download retries", "Network", "Retry temporary connection and server errors.", "retry attempts failures reconnect throttling"),
        media("media.permissions", "Media permissions", "Permissions", "Review Android device-video access.", "storage all files access permission", condition = SettingsSearchCondition.DEVICE),
        media("media.history", "Playback history", "History", "Choose automatic, manual, or disabled Library behavior.", "library mode hide disable network records", condition = SettingsSearchCondition.BROWSER),
        media("media.resume", "Resume video playback", "History", "Continue from saved playback progress.", "remember position continue timestamp", condition = SettingsSearchCondition.RESUME),
        media("media.clear", "Clear Library", "History", "Remove saved Library entries and progress.", "delete records clear history", condition = SettingsSearchCondition.SAVED_LIBRARY),
        media("interface.page", "Interface", "Interface", "Open theme and motion preferences.", "appearance colors", page = SettingsSearchPage.INTERFACE),
        media("interface.appearance", "Appearance", "Interface", "Choose System, Light, Dark, or AMOLED.", "theme night black mode", page = SettingsSearchPage.INTERFACE),
        media("interface.palette", "Color theme", "Interface", "Choose a named color palette.", "wallpaper night sea amber black gold summer ocean halloween retro sunset space man wood cotton candy grass ice cream umbrella", page = SettingsSearchPage.INTERFACE),
        media("interface.motion", "Reduce motion", "Interface", "Use simpler animations and transitions.", "animation accessibility motion effects", page = SettingsSearchPage.INTERFACE),
        media("video.page", "Video", "Video", "Open video display preferences.", "player display", page = SettingsSearchPage.VIDEO),
        media("video.aspect", "Aspect ratio", "Video", "Choose how video fills the screen.", "ar fit crop stretch zoom scale", page = SettingsSearchPage.VIDEO),
        media("video.brightness", "Brightness", "Video", "Use system brightness or a custom starting level.", "display light dim screen", page = SettingsSearchPage.VIDEO),
        media("subtitles.page", "Subtitles", "Subtitles", "Open subtitle appearance and preview.", "captions cc", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.size", "Font size", "Subtitles", "Adjust subtitle text size.", "captions cc large small", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.bold", "Bold", "Subtitles", "Use bold subtitle text.", "caption weight", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.color", "Text color", "Subtitles", "Choose the subtitle foreground color.", "caption colour white yellow cyan", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.opacity", "Text opacity", "Subtitles", "Adjust subtitle transparency.", "caption alpha visibility", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.outline", "Outline", "Subtitles", "Enable subtitle outlines.", "caption border stroke", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.outlineColor", "Outline color", "Subtitles", "Choose the subtitle outline color.", "caption border colour", page = SettingsSearchPage.SUBTITLES, condition = SettingsSearchCondition.SUBTITLE_OUTLINE),
        media("subtitles.offset", "Bottom offset", "Subtitles", "Adjust subtitle position above the screen edge.", "caption padding placement height", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.preset", "Caption preset", "Subtitles", "Choose a subtitle visual preset.", "style theme appearance", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.font", "Font", "Subtitles", "Choose the subtitle typeface.", "caption typography family", page = SettingsSearchPage.SUBTITLES),
        media("subtitles.reset", "Reset subtitle defaults", "Subtitles", "Restore the default caption appearance.", "reset captions appearance", page = SettingsSearchPage.SUBTITLES).copy(anchor = "Reset defaults"),
        media("advanced.page", "Advanced", "Advanced", "Open optional features and diagnostics.", "troubleshooting", page = SettingsSearchPage.ADVANCED),
        media("advanced.source", "Browse source while playing", "Advanced", "Browse the source page while keeping the player open.", "side pane companion browser next episode", page = SettingsSearchPage.ADVANCED, condition = SettingsSearchCondition.BROWSER),
        media("advanced.diagnostics", "Save crash diagnostics", "Advanced", "Keep redacted crash reports on this device.", "errors crash logs reporting", page = SettingsSearchPage.ADVANCED),
        media("advanced.reports", "Crash reports", "Advanced", "View, copy, share, or clear saved reports.", "errors logs stack trace", page = SettingsSearchPage.ADVANCED)
    )

    private val titles = entries.associate { it.id to normalize(it.title) }
    private val searchable = entries.associate {
        it.id to normalize("${it.title} ${it.path} ${it.summary} ${it.keywords}")
    }

    fun byId(id: String?): SettingsSearchEntry? = entries.firstOrNull { it.id == id }

    fun search(query: String, scope: SettingsSearchScope,
        availability: SettingsSearchAvailability = SettingsSearchAvailability()): List<SettingsSearchEntry> {
        val needle = normalize(query)
        val words = needle.split(' ').filter(String::isNotEmpty)
        return entries.asSequence().filter { it.scope == scope && it.available(availability) }
            .filter { entry ->
                val haystack = searchable.getValue(entry.id)
                words.all(haystack::contains)
            }.sortedBy { entry ->
                val title = titles.getValue(entry.id)
                when {
                    needle.isEmpty() || title == needle -> 0
                    title.startsWith(needle) -> 1
                    title.contains(needle) -> 2
                    else -> 3
                }
            }.toList()
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
        .replace(marks, "").lowercase(Locale.ROOT)
        .replace(separators, " ").trim()
}
