package com.miowser.app.browser

/**
 * Returns the current navigation entry followed by entries in the requested direction.
 * Keeping this independent from WebView makes the ordering and bounds directly testable.
 */
internal fun directedNavigationIndices(
    entryCount: Int,
    currentIndex: Int,
    forward: Boolean,
    limit: Int = 100
): List<Int> {
    if (entryCount <= 0 || currentIndex !in 0 until entryCount || limit <= 0) return emptyList()
    return if (forward) {
        (currentIndex until entryCount).take(limit)
    } else {
        (currentIndex downTo 0).take(limit)
    }
}
