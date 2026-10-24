package com.miowser.app

import android.content.Context
import android.content.pm.ShortcutManager

/** Launcher actions are delivered to the existing singleTask activity as well as cold starts. */
internal object LauncherShortcuts {
    const val OPEN_BROWSER = "com.miowser.app.action.OPEN_BROWSER"
    const val NEW_TAB = "com.miowser.app.action.NEW_TAB"

    fun isSupported(action: String?): Boolean = action == OPEN_BROWSER || action == NEW_TAB

    /** Retire a shortcut left by an older installation without touching other pinned shortcuts. */
    fun retireLeaks(context: Context) {
        val manager = context.getSystemService(ShortcutManager::class.java) ?: return
        val oldDynamic = manager.dynamicShortcuts.filter { it.shortLabel.toString() == "Leaks" }
        if (oldDynamic.isNotEmpty()) manager.removeDynamicShortcuts(oldDynamic.map { it.id })
        val oldPinned = manager.pinnedShortcuts.filter { it.shortLabel.toString() == "Leaks" }
        if (oldPinned.isNotEmpty()) manager.disableShortcuts(oldPinned.map { it.id })
    }
}

data class LauncherShortcutRequest(val sequence: Long, val action: String, val url: String? = null)
