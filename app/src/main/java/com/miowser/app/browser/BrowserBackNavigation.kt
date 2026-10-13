package com.miowser.app.browser

/** The one ordered back-navigation decision used by Android's button and predictive gesture. */
internal enum class BrowserBackAction {
    CLOSE_PLAYER,
    CLOSE_INTERNAL_PAGE,
    WEB_HISTORY,
    CLOSE_CHILD_TAB,
    SHOW_HUB,
    SHOW_HUB_LIBRARY,
    EXIT_APP
}

/**
 * Resolves the visible navigation layers from front to back. Transient Android dialogs, popup
 * menus, and modal sheets consume Back themselves before this app-level handler is reached.
 */
internal fun resolveBrowserBackAction(
    showingHub: Boolean,
    playerOpen: Boolean,
    internalPageOpen: Boolean,
    webViewCanGoBack: Boolean,
    activeTabHasOpener: Boolean,
    hubOnLibrary: Boolean = true
): BrowserBackAction = when {
    playerOpen -> BrowserBackAction.CLOSE_PLAYER
    showingHub -> if (hubOnLibrary) BrowserBackAction.EXIT_APP else BrowserBackAction.SHOW_HUB_LIBRARY
    internalPageOpen -> BrowserBackAction.CLOSE_INTERNAL_PAGE
    webViewCanGoBack -> BrowserBackAction.WEB_HISTORY
    activeTabHasOpener -> BrowserBackAction.CLOSE_CHILD_TAB
    else -> BrowserBackAction.SHOW_HUB
}
