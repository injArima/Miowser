package com.miowser.app.settings

enum class GuideTopic { WELCOME, DEVICE, WEB_VIDEO, SUBTITLES, DOWNLOADS, WATCH_CATALOG, CUSTOMIZE, SETTINGS }

data class GuidePage(val topic: GuideTopic, val title: String, val description: String, val points: List<String>)

/** Show instructions for the chosen experience, without changing any preferences. */
fun onboardingPages(settings: Settings): List<GuidePage> = buildList {
    add(GuidePage(GuideTopic.WELCOME, "Welcome to Miowser",
        "A video player with a browser alongside it—or a browser with more possibilities. Make it yours.",
        when (settings.experience) {
            AppExperience.BROWSER_ONLY -> listOf(
                "Your app opens directly in the browser. Rich player, WebView player, and subtitle tools stay available.",
                "Open the browser menu for Downloads and master Settings. Device library is hidden.")
            AppExperience.PLAYER_ONLY -> listOf(
                "Device shows your videos, Downloads keeps downloaded files, and More holds settings and customization.",
                "The browser and online watch catalog are hidden. You can enable them again with Customize.")
            AppExperience.BOTH -> listOf(
                "Use Device for local videos, ${if (settings.watchCatalogAvailable) "Network for saved online videos, " else ""}Downloads for transfers and files, and More for settings.",
                "Tap the globe to browse. Your app opens in ${if (settings.startsInBrowser) "the browser" else "Device videos"}, following your preferred experience.")
        }))
    if (settings.deviceLibraryAvailable) add(GuidePage(GuideTopic.DEVICE, "Your device videos, in one place",
        "Browse videos in permitted device storage and linked folders without moving the original files.", listOf(
            "Choose access and folders in Media settings → Media library folders. Device video discovery can be turned off separately.",
            "With Auto rescan on, Miowser checks once at app startup. Use refresh when you want another scan.",
            "Local playback progress and resume are separate from online watch history.")))
    if (settings.browserAvailable) add(GuidePage(GuideTopic.WEB_VIDEO, "Watch web videos your way",
        "Play a video on its website, then use Miowser's browser enhancements when supported media is detected.", listOf(
            "Tap the floating video-assistant button for WebView player. Open Media in the browser menu to review detected files and play supported streams in Rich player.",
            "Enable Browse source while playing in Media settings → Advanced to choose another episode in the side pane. Tap that video's assistant button to return it to WebView player.",
            "Detection and available tracks depend on the site. Protected media may need the website's own player.")))
    add(GuidePage(GuideTopic.SUBTITLES, "Subtitles that work for you",
        "Select available subtitle tracks in the player, and customize their appearance in Media settings → Subtitles.", listOf(
            "Preview text size, colors, outlines, and positioning in subtitle settings. Supported embedded subtitles are available in Rich player.",
            "Inspect supported subtitle text alongside playback. Follow playback returns the current line to view after you scroll.",
            "Use the delay controls or Assisted Delay to line up subtitles. Choose a line, review the delay, and confirm to apply it.")))
    add(GuidePage(GuideTopic.DOWNLOADS, "Keep your downloads organized",
        if (settings.experience == AppExperience.BROWSER_ONLY)
            "Open Downloads from the browser menu. All downloads opens the full list without the hub bar."
        else "Use Downloads for active transfers and completed files. Downloaded videos can be opened in the player.", listOf(
            if (settings.browserAvailable) "On your first download, choose the default Downloads/Miowser folder or a folder of your own. Pause, resume, or retry supported transfers."
            else "Play completed downloads and manage existing transfers. Enable the browser with Customize when you want to find new web media.",
            if (settings.experience == AppExperience.BROWSER_ONLY) "The full Downloads page has a settings button for folder choices and reconnecting saved files."
            else "Touch and hold Downloads in the bottom bar for folder choices and reconnecting saved files.",
            "In Media settings, Disabled under Simultaneous download connections means one connection; 2, 4, and 8 enable parallel connections where supported.")))
    if (settings.watchCatalogAvailable) add(GuidePage(GuideTopic.WATCH_CATALOG, "Your online watch catalog",
        if (settings.libraryBehavior == LibraryBehavior.AUTOMATIC)
            "Automatic history records supported online playback and remembers its progress."
        else "Manual history keeps videos you choose to save and updates their playback progress.", listOf(
            if (settings.experience == AppExperience.BROWSER_ONLY) "Open Watch catalog using the Network icon in the browser menu. It opens directly, without the media hub."
            else "Open Network in the bottom bar to browse saved online videos and organize them into folders.",
            "Use the save action for detected media to add a video yourself. A catalog entry is a reference, not a downloaded video file.",
            "Device files and private browsing do not create online watch records. Disabling history hides the catalog and keeps existing entries for later.")))
    add(GuidePage(GuideTopic.CUSTOMIZE, "Choose your Miowser experience",
        "Explore first, then choose Customize on the welcome card in ${if (settings.experience == AppExperience.BROWSER_ONLY) "master Settings" else "More or master Settings"}.", listOf(
            "Choose Browser first or Player first, then Browser only, Player only, or Both. Priority controls startup when using both and the order of settings.",
            "Browser only keeps both browser players and subtitle tools. Player only shows device videos and downloads. Browser only and Both offer Automatic, Manual, or Disabled watch history.",
            "Review before Apply. Cancel leaves your setup unchanged, and Customize again restores access to hidden sections without deleting their data.")))
    add(GuidePage(GuideTopic.SETTINGS, "Find settings and keep your data",
        if (settings.experience == AppExperience.BROWSER_ONLY) "The browser menu's Settings button opens master Settings, including your customization card."
        else "More keeps master settings and your customization card together.${if (settings.browserAvailable) " The browser menu's Settings button opens master Settings too." else ""}", listOf(
            "Type in the settings search field without leaving the page. Selecting a result opens its setting, scrolls to it, and briefly highlights it.",
            "Use Media settings for playback, subtitles, themes, and downloads.${if (settings.browserAvailable) " Browser settings contains browsing, tab, privacy, and website preferences." else " Settings for hidden browser features stay out of the way."}",
            "Backup & restore saves app settings and saved references; it does not copy your video files. Reopen this guide from Getting started.")))
}
