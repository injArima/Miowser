package com.miowser.app.browser

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewFeature
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class DesktopViewportTest {
    @Test fun desktopModeOverridesPageViewportAndSwitchingBackRestoresIt() {
        assumeTrue(WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val page = "<html><head><meta name='viewport' content='width=device-width, initial-scale=1'></head><body>Test</body></html>"
        val result = AtomicReference<String>()
        val done = AtomicReference(CountDownLatch(1))
        lateinit var view: WebView
        instrumentation.runOnMainSync {
            view = WebView(ApplicationProvider.getApplicationContext())
            view.settings.javaScriptEnabled = true
            view.webViewClient = object : WebViewClient() {
                override fun onPageFinished(webView: WebView, url: String) {
                    webView.evaluateJavascript(
                        "document.querySelector('meta[name=viewport]').content"
                    ) { value ->
                        result.set(value)
                        done.get().countDown()
                    }
                }
            }
        }
        try {
            lateinit var script: androidx.webkit.ScriptHandler
            instrumentation.runOnMainSync {
                script = requireNotNull(view.installDesktopViewportScript())
                view.loadDataWithBaseURL("https://desktop-test.invalid/", page, "text/html", "UTF-8", null)
            }
            org.junit.Assert.assertTrue(done.get().await(10, TimeUnit.SECONDS))
            assertEquals("\"width=980, initial-scale=1\"", result.get())

            done.set(CountDownLatch(1))
            instrumentation.runOnMainSync {
                script.remove()
                view.loadDataWithBaseURL("https://desktop-test.invalid/", page, "text/html", "UTF-8", null)
            }
            org.junit.Assert.assertTrue(done.get().await(10, TimeUnit.SECONDS))
            assertEquals("\"width=device-width, initial-scale=1\"", result.get())
        } finally {
            instrumentation.runOnMainSync { view.destroy() }
        }
    }
}
