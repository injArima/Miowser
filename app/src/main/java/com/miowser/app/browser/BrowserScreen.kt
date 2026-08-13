package com.miowser.app.browser

import android.annotation.SuppressLint
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private const val STARTER_URL = "https://en.wikipedia.org"
private const val CONSOLE_TAG = "Miowser/Console"

/**
 * Phase-1 browser shell: WebView + a proof-of-concept request interceptor
 * ([MiowserWebViewClient]) and document-start script injection
 * ([installDocumentStartProofScript]) — confirming the interception pipeline itself works
 * against a real site before the real grabber/ad-block engines are built on top of it.
 */
@Composable
fun BrowserScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var canGoBack by remember { mutableStateOf(false) }

    // Created exactly once via `remember` — mutating Compose state from inside AndroidView's
    // factory instead of this caused disposal churn that destroyed the WebView prematurely.
    val webView = remember {
        WebView(context).apply {
            configureForBrowsing()
            installDocumentStartProofScript() // must run before loadUrl()
            webViewClient = MiowserWebViewClient(onCanGoBackChanged = { canGoBack = it })
            webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                    Log.d(CONSOLE_TAG, "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                    return true
                }
            }
            loadUrl(STARTER_URL)
        }
    }

    BackHandler(enabled = canGoBack) {
        webView.goBack()
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { webView },
        onRelease = { it.destroy() }
    )

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
