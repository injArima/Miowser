package com.miowser.app.browser.video

import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.miowser.app.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.MutableStateFlow

/** Native input ownership plus a real Chromium trusted-click regression. */
@RunWith(AndroidJUnit4::class)
class NativeVideoAssistantDisplayTest {
    @Test fun releaseDetachesTheEngineEvenWhenSwipeRefreshOwnsTheFirstChild() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val engine = View(context)
            val display = NativeVideoAssistantDisplay(context)
            val content = FrameLayout(context).apply {
                addView(engine); addView(display)
            }
            val host = SwipeRefreshLayout(context).apply { addView(content) }
            assertTrue(host.childCount > 1)
            var detached = 0
            releaseVideoAssistantHost(host) { detached++; it.clear() }
            assertEquals(1, detached); assertNull(engine.parent); assertEquals(0, host.childCount)
            FrameLayout(context).addView(engine)
        }
    }
    @Test fun nativeButtonOwnsItsGestureAndOnlyForwardsAnAcknowledgedTap() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val ready = CountDownLatch(1)
            lateinit var display: NativeVideoAssistantDisplay
            lateinit var host: FrameLayout
            val received = mutableListOf<Triple<Int, Float, Float>>()
            var request = 0L
            scenario.onActivity { activity ->
                val underlying = object : View(activity) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        received += Triple(event.actionMasked, event.x, event.y)
                        return true
                    }
                }
                display = NativeVideoAssistantDisplay(activity)
                display.connectInput(underlying) { type, _, id -> if (type == "nativePress") request = id }
                host = FrameLayout(activity).apply {
                    addView(underlying, FrameLayout.LayoutParams(-1, -1))
                    addView(display, FrameLayout.LayoutParams(-1, -1))
                }
                activity.setContentView(host, FrameLayout.LayoutParams(200, 200))
                fun layout() {
                    val exact = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
                    host.measure(exact, exact); host.layout(0, 0, 200, 200)
                }
                layout()
                display.present(VideoAssistantDisplayGeometry(1, .5, .5, .24, .24, 200.0, 200.0),
                    Color.BLUE, Color.WHITE, true) { if (it) ready.countDown() }
                layout()
                assertFalse(display.isClickable)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, display.importantForAccessibility)
                // A quick first tap can finish before the two-frame display acknowledgement.
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now, action, 124f, 124f, 0)
                    try { assertTrue(host.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
                assertTrue(received.isEmpty())
            }
            assertTrue("Native face must be laid out", ready.await(5, TimeUnit.SECONDS))
            scenario.onActivity {
                assertTrue("The initial tap must be prepared after native rendering", request > 0)
                display.acknowledgePress(1, request, true)
                assertEquals(2, received.size); received.clear()
                fun event(action: Int, x: Float = 124f, y: Float = 124f) {
                    val now = SystemClock.uptimeMillis()
                    val event = MotionEvent.obtain(now, now, action, x, y, 0)
                    try { assertTrue(host.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
                // UP may arrive before the page acknowledgement. Nothing leaks through.
                event(MotionEvent.ACTION_DOWN); event(MotionEvent.ACTION_UP)
                assertTrue(received.isEmpty())
                display.acknowledgePress(1, request - 1, true); assertTrue(received.isEmpty())
                display.acknowledgePress(1, request, true)
                assertEquals(listOf(Triple(MotionEvent.ACTION_DOWN, 124f, 124f),
                    Triple(MotionEvent.ACTION_UP, 124f, 124f)), received)
                received.clear()
                // A drag that began on the native face never scrolls/clicks the page.
                event(MotionEvent.ACTION_DOWN); display.acknowledgePress(1, request, true)
                event(MotionEvent.ACTION_MOVE, 170f); event(MotionEvent.ACTION_UP, 170f)
                assertTrue(received.isEmpty())
                event(MotionEvent.ACTION_DOWN); display.acknowledgePress(1, request, false)
                event(MotionEvent.ACTION_UP); assertTrue(received.isEmpty())
                display.dismiss(true)
                event(MotionEvent.ACTION_DOWN); event(MotionEvent.ACTION_UP)
                assertTrue("A visible fading face must not click through", received.isEmpty())
                event(MotionEvent.ACTION_DOWN); display.clear()
                event(MotionEvent.ACTION_UP); assertTrue(received.isEmpty())
                // Outside the native target, normal page input remains unchanged.
                event(MotionEvent.ACTION_DOWN, 20f, 20f); event(MotionEvent.ACTION_UP, 20f, 20f)
                assertEquals(2, received.size)
                display.clear()
            }
        }
    }

    @Test fun nativeTapStillGeneratesATrustedWebViewClickAfterScrollEvenWithoutADomHitTarget() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val loaded = CountDownLatch(1)
            lateinit var web: WebView
            lateinit var display: NativeVideoAssistantDisplay
            lateinit var tracker: LiveVideoTracker
            lateinit var host: FrameLayout
            scenario.onActivity { activity ->
                web = WebView(activity).apply { settings.javaScriptEnabled = true }
                display = NativeVideoAssistantDisplay(activity)
                host = FrameLayout(activity).apply {
                    addView(web, FrameLayout.LayoutParams(-1, -1)); addView(display, FrameLayout.LayoutParams(-1, -1))
                }
                activity.setContentView(host)
                tracker = LiveVideoTracker(987, web, MutableStateFlow(emptyList()), VideoAssistantButtonPolicy(enabled = true))
                assertTrue(tracker.supported)
                tracker.install(); tracker.attachNativeDisplay(display); tracker.onNavigationStarted()
                web.webChromeClient = object : WebChromeClient() {
                    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                        host.addView(view, FrameLayout.LayoutParams(-1, -1))
                    }
                    override fun onHideCustomView() { if (host.childCount > 2) host.removeViewAt(2) }
                }
                web.webViewClient = object : WebViewClient() {
                    override fun onPageCommitVisible(view: WebView, url: String) { tracker.onPageCommitted() }
                    override fun onPageFinished(view: WebView, url: String) { tracker.onPageCommitted(); loaded.countDown() }
                }
                // No network/media dependency: the real HTML video exposes controlled readiness,
                // but calls Chromium's actual fullscreen API in the production trusted-click path.
                web.loadDataWithBaseURL("https://assistant.test/", """
                    <meta name="viewport" content="width=device-width,initial-scale=1">
                    <style>body{margin:0;height:4000px}video{position:sticky;top:20px;width:100%;height:200px}</style>
                    <video id="v"></video><a href="#wrong" style="position:fixed;inset:0" onclick="window.behind=(window.behind||0)+1">behind</a>
                    <script>
                    let v=document.querySelector('video');
                    for(let [k,x] of Object.entries({paused:false,ended:false,readyState:4,videoWidth:640,videoHeight:360,
                      duration:60,currentTime:12,currentSrc:'blob:https://assistant.test/fixture'}))Object.defineProperty(v,k,{get:()=>x});
                    let enter=v.requestFullscreen.bind(v);v.requestFullscreen=function(){window.opened={trusted:navigator.userActivation.isActive,time:v.currentTime};return enter();};
                    </script>
                """.trimIndent(), "text/html", "UTF-8", null)
            }
            assertTrue(loaded.await(10, TimeUnit.SECONDS))
            fun evaluate(script: String): String {
                val done = CountDownLatch(1); var result = ""
                scenario.onActivity { web.evaluateJavascript(script) { result = it; done.countDown() } }
                assertTrue(done.await(5, TimeUnit.SECONDS)); return result
            }
            fun awaitState(script: String) {
                val until = SystemClock.uptimeMillis() + 5000
                while (evaluate(script) != "true") {
                    if (SystemClock.uptimeMillis() >= until) fail("Timed out: $script; " + evaluate("JSON.stringify({factory:typeof __miowserCreateVideoButton,live:typeof __miowserLiveVideos,fullscreen:document.fullscreenEnabled,v:[...document.querySelectorAll('video')].map(v=>({paused:v.paused,ready:v.readyState,w:v.videoWidth,rect:v.getBoundingClientRect().toJSON()}))})"))
                    SystemClock.sleep(40)
                }
            }
            awaitState("!!document.querySelector('[data-miowser-video-assistant]')")
            evaluate("scrollTo(0,100)")
            val settled = CountDownLatch(1)
            scenario.onActivity { host.postDelayed({ settled.countDown() }, 500) }
            assertTrue(settled.await(2, TimeUnit.SECONDS))
            // The native visual remains while the page no longer has a web hit target.
            // Its tap must enter fullscreen, rather than following the link behind it.
            evaluate("document.querySelector('[data-miowser-video-assistant]').style.display='none'")
            scenario.onActivity {
                val face = display.getChildAt(0)
                assertEquals(View.VISIBLE, face.visibility)
                val x = face.x + face.width / 2; val y = face.y + face.height / 2
                val now = SystemClock.uptimeMillis()
                for ((action, offset) in listOf(MotionEvent.ACTION_DOWN to 0, MotionEvent.ACTION_UP to 30)) {
                    val event = MotionEvent.obtain(now, now + offset, action, x, y, 0)
                    event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
                    try { assertTrue(host.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
            }
            awaitState("!!window.opened && !!document.fullscreenElement")
            assertEquals("true", evaluate("window.opened.trusted === true && window.opened.time === 12 && !window.behind && location.hash !== '#wrong'"))
            scenario.onActivity { tracker.retire(); host.removeAllViews(); web.destroy() }
        }
    }
}
