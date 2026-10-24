package com.miowser.app.player

import android.os.SystemClock
import android.view.MotionEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real MotionEvents, not semantics clicks, so an ancestor consuming Initial pass fails these. */
@RunWith(AndroidJUnit4::class)
class PlayerInteractionLayersTest {
    @Test fun modalButtonsReceiveTouchesWhileCoveredPlayerButtonsDoNot() {
        val underlying = AtomicInteger()
        val modal = AtomicInteger()
        val showing = mutableStateOf(true)
        val bounds = ConcurrentHashMap<String, Rect>()
        withFixture(bounds, listOf("player", "modal"), {
            PlayerInteractionLayers(showing.value,
                playerContent = {
                    Button(onClick = { underlying.incrementAndGet() },
                        modifier = Modifier.align(Alignment.CenterStart).record("player", bounds)) { Text("Play") }
                }, overlayContent = {
                    if (showing.value) Button(onClick = { modal.incrementAndGet() },
                        modifier = Modifier.align(Alignment.CenterEnd).record("modal", bounds)) { Text("Next video") }
                })
        }) { scenario ->
            tap(scenario, bounds.getValue("modal").center)
            tap(scenario, bounds.getValue("player").center)
            assertEquals(1, modal.get())
            assertEquals(0, underlying.get())
            scenario.onActivity { showing.value = false }
            settle(scenario)
            tap(scenario, bounds.getValue("player").center)
            assertEquals(1, underlying.get())
        }
    }

    @Test fun modalSliderAndTextFieldKeepTheirPointerAndFocusInput() {
        val value = AtomicReference(0f)
        val focused = CountDownLatch(1)
        val bounds = ConcurrentHashMap<String, Rect>()
        withFixture(bounds, listOf("slider", "field"), {
            PlayerInteractionLayers(true, playerContent = {}, overlayContent = {
                Column(Modifier.align(Alignment.Center).width(240.dp)) {
                    val slider = androidx.compose.runtime.remember { mutableStateOf(0f) }
                    Slider(value = slider.value, onValueChange = { slider.value = it; value.set(it) },
                        modifier = Modifier.fillMaxWidth().record("slider", bounds))
                    BasicTextField("0", onValueChange = {}, modifier = Modifier.fillMaxWidth().height(48.dp)
                        .record("field", bounds).onFocusChanged { if (it.isFocused) focused.countDown() })
                }
            })
        }) { scenario ->
            val slider = bounds.getValue("slider")
            drag(scenario, Offset(slider.left + slider.width * 0.1f, slider.center.y),
                Offset(slider.left + slider.width * 0.9f, slider.center.y))
            assertTrue("Slider did not receive touch input", value.get() > 0.6f)
            tap(scenario, bounds.getValue("field").center)
            assertTrue("Text field could not receive focus", focused.await(5, TimeUnit.SECONDS))
        }
    }

    @Test fun modalListCanScrollWhileVideoInputIsBlocked() {
        val moved = CountDownLatch(1)
        val bounds = ConcurrentHashMap<String, Rect>()
        withFixture(bounds, listOf("list"), {
            PlayerInteractionLayers(true, playerContent = {}, overlayContent = {
                val state = rememberLazyListState()
                LaunchedEffect(state) {
                    snapshotFlow { state.firstVisibleItemIndex }.collect { if (it > 0) moved.countDown() }
                }
                LazyColumn(state = state, modifier = Modifier.align(Alignment.CenterEnd)
                    .width(220.dp).height(240.dp).record("list", bounds)) {
                    items(40) { Text("Video $it", Modifier.fillMaxWidth().height(56.dp).padding(8.dp)) }
                }
            })
        }) { scenario ->
            val list = bounds.getValue("list")
            drag(scenario, Offset(list.center.x, list.bottom - 12), Offset(list.center.x, list.top + 12))
            assertTrue("Playlist-style list did not scroll", moved.await(5, TimeUnit.SECONDS))
        }
    }

    private fun Modifier.record(name: String, bounds: ConcurrentHashMap<String, Rect>): Modifier =
        onGloballyPositioned { bounds[name] = it.boundsInWindow() }

    private fun withFixture(bounds: ConcurrentHashMap<String, Rect>, required: List<String>,
        content: @Composable () -> Unit, check: (ActivityScenario<PlayerTouchFixtureActivity>) -> Unit) {
        ActivityScenario.launch(PlayerTouchFixtureActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.setContent {
                    MaterialTheme {
                        androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) { content() }
                    }
                }
            }
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (!required.all(bounds::containsKey) && SystemClock.uptimeMillis() < deadline) settle(scenario)
            assertTrue("Fixture did not lay out", required.all(bounds::containsKey))
            settle(scenario)
            check(scenario)
        }
    }

    private fun settle(scenario: ActivityScenario<PlayerTouchFixtureActivity>) {
        val frame = CountDownLatch(1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        scenario.onActivity { it.window.decorView.postOnAnimation { frame.countDown() } }
        assertTrue(frame.await(5, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
    }

    private fun tap(scenario: ActivityScenario<PlayerTouchFixtureActivity>, point: Offset) =
        gesture(scenario, listOf(point, point))

    private fun drag(scenario: ActivityScenario<PlayerTouchFixtureActivity>, start: Offset, end: Offset) =
        gesture(scenario, (0..12).map { start + (end - start) * (it / 12f) })

    private fun gesture(scenario: ActivityScenario<PlayerTouchFixtureActivity>, points: List<Offset>) {
        scenario.onActivity { activity ->
            val down = SystemClock.uptimeMillis()
            points.forEachIndexed { index, point ->
                val action = when (index) { 0 -> MotionEvent.ACTION_DOWN; points.lastIndex -> MotionEvent.ACTION_UP; else -> MotionEvent.ACTION_MOVE }
                val event = MotionEvent.obtain(down, down + index * 16L, action, point.x, point.y, 0)
                try { activity.window.decorView.dispatchTouchEvent(event) } finally { event.recycle() }
            }
        }
        settle(scenario)
    }
}
