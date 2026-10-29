package com.miowser.app.browser

import android.content.Context
import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import com.miowser.app.R
import org.json.JSONTokener

private const val SEARCH_WITH_MIOWSER_ITEM_ID = 0x4d_57_53_52
private const val MAX_SELECTED_SEARCH_CHARS = 8_192

/**
 * Adds Miowser's own search directly after Copy while retaining WebView's standard selection
 * actions. The matching ACTION_PROCESS_TEXT entry is removed only inside Miowser to avoid showing
 * the same action twice; other apps still discover ProcessTextActivity through the manifest.
 */
internal class MiowserWebView(
    context: Context,
    private val onSearchSelectedText: (String) -> Unit
) : WebView(context) {
    override fun startActionMode(callback: ActionMode.Callback): ActionMode? =
        super.startActionMode(wrap(callback))

    override fun startActionMode(callback: ActionMode.Callback, type: Int): ActionMode? =
        super.startActionMode(wrap(callback), type)

    private fun wrap(callback: ActionMode.Callback): ActionMode.Callback =
        SelectionSearchActionModeCallback(
            delegate = callback,
            webView = this,
            appPackage = context.packageName,
            onSearch = onSearchSelectedText
        )
}

private class SelectionSearchActionModeCallback(
    private val delegate: ActionMode.Callback,
    private val webView: WebView,
    private val appPackage: String,
    private val onSearch: (String) -> Unit
) : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        val created = delegate.onCreateActionMode(mode, menu)
        if (created) addSearchAction(menu)
        return created
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        val changedByWebView = delegate.onPrepareActionMode(mode, menu)
        return addSearchAction(menu) || changedByWebView
    }

    private fun addSearchAction(menu: Menu): Boolean {
        menu.removeItem(SEARCH_WITH_MIOWSER_ITEM_ID)
        for (index in menu.size() - 1 downTo 0) {
            val item = menu.getItem(index)
            val targetIntent = item.intent
            if (targetIntent?.action == Intent.ACTION_PROCESS_TEXT &&
                (targetIntent.component?.packageName == appPackage || targetIntent.`package` == appPackage)
            ) menu.removeItem(item.itemId)
        }

        val copyOrder = menu.findItem(android.R.id.copy)?.order ?: 0
        menu.add(
            Menu.NONE,
            SEARCH_WITH_MIOWSER_ITEM_ID,
            copyOrder + 1,
            webView.context.getString(R.string.search_with_miowser)
        ).setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
        if (item.itemId != SEARCH_WITH_MIOWSER_ITEM_ID)
            return delegate.onActionItemClicked(mode, item)
        webView.evaluateJavascript(
            "(function(){var s=window.getSelection&&window.getSelection();return s?s.toString():'';})()"
        ) { encoded ->
            val selected = runCatching { JSONTokener(encoded).nextValue() as? String }
                .getOrNull()
                ?.trim()
                ?.take(MAX_SELECTED_SEARCH_CHARS)
                .orEmpty()
            mode.finish()
            if (selected.isNotEmpty()) onSearch(selected)
        }
        return true
    }

    override fun onDestroyActionMode(mode: ActionMode) = delegate.onDestroyActionMode(mode)
}

