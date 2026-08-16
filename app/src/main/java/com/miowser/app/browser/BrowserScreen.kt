package com.miowser.app.browser

import android.view.View
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miowser.app.adblock.AdBlock
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STARTER_URL = "https://en.wikipedia.org"

/**
 * Browser shell: a [TabStrip] + [AddressBar] driving whichever tab is active, over a Box that
 * keeps every open tab's WebView alive simultaneously (see the tab loop below) and just
 * toggles visibility — switching tabs never destroys one.
 */
@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val clipboard = LocalClipboard.current
    val coroutineScope = rememberCoroutineScope()
    var showDetectedSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        AdBlock.engine.loadEasyListInBackground(context)
    }

    val tabs = remember { mutableStateListOf<Tab>() }
    var activeTabId by remember { mutableStateOf<Long?>(null) }

    // Creates a tab and inserts it into the list, then returns it. Page-triggered tabs
    // (openerTabId != null) are inserted immediately after their opener, matching how real
    // browsers place a target="_blank"/window.open() tab next to the page that opened it;
    // manually-opened tabs (the "+" button, openerTabId == null) are appended at the end.
    // A local function rather than a lambda so it can call itself for grandchild tabs
    // (a page opening a tab whose page opens another tab) without a lateinit dance.
    fun createAndInsertTab(initialUrl: String, openerTabId: Long?): Tab {
        val tab = Tab(
            id = Tab.nextId(),
            context = context,
            initialUrl = initialUrl,
            openerTabId = openerTabId,
            onRequestChildTab = { childOpenerId -> createAndInsertTab("", childOpenerId) }
        )
        if (openerTabId != null) {
            val openerIndex = tabs.indexOfFirst { it.id == openerTabId }
            tabs.add(if (openerIndex == -1) tabs.size else openerIndex + 1, tab)
        } else {
            tabs.add(tab)
        }
        return tab
    }

    // Restore the last session if there is one; otherwise open a single starter tab. Restored
    // tabs are appended in their saved order (not via the opener-adjacent insert above) so the
    // saved strip order is reproduced exactly rather than re-derived from opener relationships.
    LaunchedEffect(Unit) {
        if (tabs.isNotEmpty()) return@LaunchedEffect
        val saved = TabSessionStore.load(context)
        if (saved == null) {
            activeTabId = createAndInsertTab(STARTER_URL, null).id
            return@LaunchedEffect
        }
        val (savedTabs, activeIndex) = saved
        val restoredIds = mutableListOf<Long>()
        for (savedTab in savedTabs) {
            val openerId = savedTab.openerIndex.takeIf { it in restoredIds.indices }?.let { restoredIds[it] }
            val tab = Tab(
                id = Tab.nextId(),
                context = context,
                initialUrl = savedTab.url,
                openerTabId = openerId,
                onRequestChildTab = { childOpenerId -> createAndInsertTab("", childOpenerId) }
            )
            tabs.add(tab)
            restoredIds.add(tab.id)
        }
        activeTabId = tabs.getOrNull(activeIndex)?.id ?: tabs.firstOrNull()?.id
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }

    BackHandler(enabled = activeTab?.canGoBack == true) {
        activeTab?.webView?.goBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            Column {
                TabStrip(
                    tabs = tabs,
                    activeTabId = activeTabId,
                    onSelectTab = { id -> activeTabId = id },
                    onCloseTab = { id ->
                        val index = tabs.indexOfFirst { it.id == id }
                        if (index != -1) {
                            val closed = tabs.removeAt(index)
                            closed.webView.destroy()
                            if (tabs.isEmpty()) {
                                activeTabId = createAndInsertTab(STARTER_URL, null).id
                            } else if (activeTabId == id) {
                                // Prefer returning to the tab that opened this one (a real
                                // page-triggered child); manually-opened tabs have no opener,
                                // so fall back to whichever tab is now immediately before this
                                // one's old position — "the previous tab in order".
                                val opener = closed.openerTabId?.let { openerId -> tabs.firstOrNull { it.id == openerId } }
                                activeTabId = opener?.id ?: tabs[(index - 1).coerceIn(0, tabs.size - 1)].id
                            }
                        }
                    },
                    onNewTab = {
                        activeTabId = createAndInsertTab("", null).id
                    }
                )
                if (activeTab != null) {
                    AddressBar(
                        value = activeTab.addressBarText,
                        onValueChange = { activeTab.addressBarText = it },
                        onFocusChanged = { focused -> activeTab.isAddressBarFocused = focused },
                        onSubmit = {
                            val resolved = UrlOrSearchResolver.resolve(activeTab.addressBarText.text)
                            if (resolved.isNotEmpty()) activeTab.webView.loadUrl(resolved)
                            keyboardController?.hide()
                            // Not focusManager.clearFocus(): loadUrl() makes the WebView request
                            // Android View focus for itself as a side effect of navigating, which
                            // was racing clearFocus() and winning. Direct focus to the WebView
                            // instead so the two cooperate rather than race.
                            activeTab.webView.requestFocus()
                        },
                        onReload = { activeTab.webView.reload() },
                        onStop = { activeTab.webView.stopLoading() },
                        isLoading = activeTab.isLoading,
                        loadProgress = activeTab.loadProgress,
                        detectedCount = activeTab.detectedMedia.size,
                        onOpenDetected = { showDetectedSheet = true }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Every tab's AndroidView stays composed (and thus its WebView stays alive) for as
            // long as the tab exists; only the active one is made visible. Conditionally
            // composing just the active tab would call AndroidView's onRelease — which destroys
            // the WebView — on every switch, which is exactly the "don't destroy on switch"
            // requirement this is here to avoid.
            tabs.forEach { tab ->
                key(tab.id) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { tab.webView },
                        update = { view -> view.visibility = if (tab.id == activeTabId) View.VISIBLE else View.INVISIBLE },
                        onRelease = { it.destroy() }
                    )
                }
            }
        }
    }

    if (showDetectedSheet && activeTab != null) {
        DetectedMediaSheet(
            items = activeTab.detectedMedia,
            onCopyUrl = { url ->
                coroutineScope.launch {
                    clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("url", url)))
                }
            },
            onDismiss = { showDetectedSheet = false }
        )
    }

    // Switching tabs (or the active tab navigating away) should close a sheet showing the
    // previous tab's results rather than silently keep showing stale ones.
    LaunchedEffect(activeTabId) {
        showDetectedSheet = false
    }

    // Select-all-on-focus, matching standard browser address-bar behavior. Deferred via
    // LaunchedEffect + a short delay rather than done synchronously in onFocusChanged: a tap
    // that both focuses the field and positions the cursor was racing a synchronous selection
    // update and winning — verified on-device via direct selection-state logging.
    LaunchedEffect(activeTab?.id, activeTab?.isAddressBarFocused) {
        if (activeTab?.isAddressBarFocused == true) {
            delay(50)
            activeTab.addressBarText = activeTab.addressBarText.copy(
                selection = TextRange(0, activeTab.addressBarText.text.length)
            )
        }
    }

    // Only the active tab's media should ever be playing. Resume it (and only it) whenever the
    // active tab changes or the app returns to the foreground; pause every tab when backgrounded.
    LaunchedEffect(activeTabId) {
        tabs.forEach { tab -> if (tab.id == activeTabId) tab.webView.onResume() else tab.webView.onPause() }
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> tabs.firstOrNull { it.id == activeTabId }?.webView?.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    tabs.forEach { it.webView.onPause() }
                    // The durability point for session restore: covers backgrounding + later
                    // process death, the overwhelmingly common real-world "app was gone" case.
                    TabSessionStore.save(context, tabs, activeTabId)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
