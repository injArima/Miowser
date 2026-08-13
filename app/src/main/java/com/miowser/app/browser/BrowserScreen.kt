package com.miowser.app.browser

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.miowser.app.adblock.AdBlock
import com.miowser.app.adblock.CosmeticBridge
import kotlinx.coroutines.delay

private const val STARTER_URL = "https://en.wikipedia.org"
private const val CONSOLE_TAG = "Miowser/Console"

/**
 * Browser shell: an [AddressBar] (URL/search entry, reload/stop, load progress) driving a
 * WebView wired to the real ad-block engine ([AdBlock.engine]) via [MiowserWebViewClient]
 * (network-level blocking) and [installCosmeticInjectionScript] (cosmetic CSS injection,
 * running before any page script).
 */
@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val keyboardController = LocalSoftwareKeyboardController.current

    var canGoBack by remember { mutableStateOf(false) }
    var addressBarText by remember { mutableStateOf(TextFieldValue(STARTER_URL)) }
    var isAddressBarFocused by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var isLoading by remember { mutableStateOf(false) }

    // Created exactly once via `remember` — mutating Compose state from inside AndroidView's
    // factory instead of this caused disposal churn that destroyed the WebView prematurely.
    val webView = remember {
        AdBlock.engine.loadEasyListInBackground(context)
        WebView(context).apply {
            configureForBrowsing()
            addJavascriptInterface(CosmeticBridge(AdBlock.engine), "MiowserAdBlock")
            installCosmeticInjectionScript() // must run before loadUrl()
            webViewClient = MiowserWebViewClient(
                adBlockEngine = AdBlock.engine,
                onCanGoBackChanged = { canGoBack = it },
                onUrlChanged = { url ->
                    // Don't clobber the user's in-progress typing with the WebView's own
                    // navigation URL — only sync when the field isn't actively being edited.
                    if (!isAddressBarFocused) addressBarText = TextFieldValue(url)
                }
            )
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(CONSOLE_TAG, "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                    return true
                }

                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    loadProgress = newProgress / 100f
                    isLoading = newProgress < 100
                }
            }
            loadUrl(STARTER_URL)
        }
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            AddressBar(
                value = addressBarText,
                onValueChange = { addressBarText = it },
                onFocusChanged = { focused -> isAddressBarFocused = focused },
                onSubmit = {
                    val resolved = UrlOrSearchResolver.resolve(addressBarText.text)
                    if (resolved.isNotEmpty()) webView.loadUrl(resolved)
                    keyboardController?.hide()
                    // Not focusManager.clearFocus(): the WebView requests Android View focus
                    // for itself as a side effect of loadUrl() starting a new navigation, which
                    // was racing clearFocus()'s target and winning (confirmed via logging — a
                    // spurious false-then-true refocus happened *before* clearFocus() even ran).
                    // Explicitly moving focus to the WebView cooperates with that instead.
                    webView.requestFocus()
                },
                onReload = { webView.reload() },
                onStop = { webView.stopLoading() },
                isLoading = isLoading,
                loadProgress = loadProgress
            )
        }
    ) { innerPadding ->
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            factory = { webView },
            onRelease = { it.destroy() }
        )
    }

    // Select-all-on-focus, matching standard browser address-bar behavior. This has to run as
    // a LaunchedEffect (after composition settles) rather than directly inside onFocusChanged:
    // a tap that both focuses the field *and* positions the cursor applies its own cursor
    // placement as part of the same gesture, which was winning the race against a selection
    // set synchronously inside the focus callback — verified on-device (the field showed a
    // focus ring but an unselected caret, not a full selection, until this was deferred).
    LaunchedEffect(isAddressBarFocused) {
        if (isAddressBarFocused) {
            // A short delay so this reliably lands *after* the platform's own tap-driven
            // cursor placement from the same gesture, rather than racing it — a zero-delay
            // LaunchedEffect wasn't consistently late enough (verified on-device: the field
            // sometimes still showed an unselected caret instead of a full selection).
            delay(50)
            addressBarText = addressBarText.copy(selection = TextRange(0, addressBarText.text.length))
        }
    }

    // Pause/resume WebView content (including any media) with the host lifecycle.
    val currentWebView by rememberUpdatedState(webView)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> currentWebView.onResume()
                Lifecycle.Event.ON_PAUSE -> currentWebView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.configureForBrowsing() {
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
}
