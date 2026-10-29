package com.miowser.app

/** Intent contract shared by Android's process-text entry point and the existing main activity. */
internal object TextSearchActions {
    const val SEARCH_SELECTED_TEXT = "com.miowser.app.action.SEARCH_SELECTED_TEXT"
    private const val MAX_QUERY_CHARS = 8_192

    fun normalizedQuery(value: CharSequence?): String? = value
        ?.toString()
        ?.trim()
        ?.take(MAX_QUERY_CHARS)
        ?.takeIf(String::isNotEmpty)
}

