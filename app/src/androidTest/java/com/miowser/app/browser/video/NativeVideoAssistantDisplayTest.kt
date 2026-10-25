package com.miowser.app.browser.video

import android.graphics.Color
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.miowser.app.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Compiled locally; actual device execution is left to the user. */
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
    @Test fun nativeDecorationPassesTheOriginalTouchThroughToTheUnderlyingView() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val received = mutableListOf<Triple<Int, Float, Float>>()
                val underlying = object : View(activity) {
                    override fun onTouchEvent(event: MotionEvent): Boolean {
                        received += Triple(event.actionMasked, event.x, event.y)
                        return true
                    }
                }
                val display = NativeVideoAssistantDisplay(activity)
                val host = FrameLayout(activity).apply {
                    addView(underlying, FrameLayout.LayoutParams(-1, -1))
                    addView(display, FrameLayout.LayoutParams(-1, -1))
                }
                activity.setContentView(host)
                fun layout() {
                    val exact = View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY)
                    host.measure(exact, exact); host.layout(0, 0, 200, 200)
                }
                layout()
                display.present(VideoAssistantDisplayGeometry(1, .5, .5, .24, .24, 200.0, 200.0),
                    Color.BLUE, Color.WHITE, true) {}
                layout()
                assertFalse(display.isClickable)
                assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS, display.importantForAccessibility)
                val now = SystemClock.uptimeMillis()
                for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                    val event = MotionEvent.obtain(now, now + action, action, 124f, 124f, 0)
                    try { assertTrue(host.dispatchTouchEvent(event)) } finally { event.recycle() }
                }
                assertEquals(listOf(Triple(MotionEvent.ACTION_DOWN, 124f, 124f),
                    Triple(MotionEvent.ACTION_UP, 124f, 124f)), received)
                display.clear()
            }
        }
    }
}
