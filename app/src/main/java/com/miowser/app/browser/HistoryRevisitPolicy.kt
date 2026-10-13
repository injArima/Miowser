package com.miowser.app.browser

/** Only a completed, visible normal page can restore its missing History entry. */
internal fun shouldRecordRevisitedPage(
    currentUrl: String,
    finishedUrl: String?,
    isPrivate: Boolean,
    isHome: Boolean,
    hasPageError: Boolean,
    alreadyInHistory: Boolean
): Boolean = finishedUrl != null && currentUrl == finishedUrl && !isPrivate && !isHome &&
    !hasPageError && !alreadyInHistory
