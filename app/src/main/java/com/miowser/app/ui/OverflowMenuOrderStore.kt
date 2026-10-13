package com.miowser.app.ui

import android.content.Context
import com.miowser.app.persistence.Persistence

private const val PREFS_NAME = "browser_overflow_menu"
private const val KEY_ORDER = "tile_order"

/** Every reorderable action in the browser's overflow bottom sheet. */
enum class OverflowMenuAction {
    HUB,
    ADBLOCK,
    DOWNLOADS,
    HISTORY,
    DETECTED,
    SETTINGS,
    SHARE
}

val DEFAULT_OVERFLOW_MENU_ORDER: List<OverflowMenuAction> = listOf(
    OverflowMenuAction.HUB,
    OverflowMenuAction.ADBLOCK,
    OverflowMenuAction.DOWNLOADS,
    OverflowMenuAction.HISTORY,
    OverflowMenuAction.DETECTED,
    OverflowMenuAction.SETTINGS,
    OverflowMenuAction.SHARE
)

/**
 * Keeps valid saved actions in their chosen order and appends actions introduced by a newer app
 * version. Unknown and duplicate names are ignored, so a damaged/old preference cannot make a
 * menu destination disappear permanently.
 */
internal fun normalizeOverflowMenuOrder(savedNames: List<String>): List<OverflowMenuAction> {
    val saved = savedNames.mapNotNull { name ->
        runCatching { OverflowMenuAction.valueOf(name) }.getOrNull()
    }.distinct()
    return saved + DEFAULT_OVERFLOW_MENU_ORDER.filterNot(saved::contains)
}

object OverflowMenuOrderStore {
    fun load(context: Context): List<OverflowMenuAction> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null)
        return normalizeOverflowMenuOrder(
            raw?.split(',')?.filter(String::isNotBlank).orEmpty()
        )
    }

    fun save(context: Context, order: List<OverflowMenuAction>) {
        val normalized = normalizeOverflowMenuOrder(order.map(OverflowMenuAction::name))
        val appContext = context.applicationContext
        Persistence.submitSnapshot("menu-order") {
            appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_ORDER, normalized.joinToString(",", transform = OverflowMenuAction::name))
                .commit()
        }
    }
}
