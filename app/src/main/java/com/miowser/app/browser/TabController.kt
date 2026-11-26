package com.miowser.app.browser

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.runtime.*
import com.miowser.app.persistence.Persistence

/** Activity-owned records and live engines have separate lifetimes. Switching only detaches/hides. */
class TabController {
    val tabs = mutableStateListOf<Tab>()
    private var revision by mutableIntStateOf(0)
    private var disposed = false
    internal fun touch(id: Long) { engines.touch(id) }
    private val engines = RetainedTabEngines<WebView> { view ->
        (view.parent as? ViewGroup)?.removeView(view)
        view.destroy()
    }
    val liveEngineCount: Int get() { revision; return engines.size }
    internal fun peek(id: Long): WebView? { revision; return engines.peek(id) }
    internal fun acquire(id: Long, factory: () -> WebView): WebView {
        Persistence.requireOwner()
        check(!disposed && tabs.any { it.id == id }) { "Cannot open a closed tab" }
        engines.peek(id)?.let { return it }
        return engines.acquire(id, factory).also {
            revision++
            com.miowser.app.diagnostics.DevResourceDiagnostics.report("browser-acquire", engines.size)
        }
    }
    internal fun release(id: Long) {
        Persistence.requireOwner()
        if (engines.peek(id) != null) {
            engines.release(id); revision++
            com.miowser.app.diagnostics.DevResourceDiagnostics.report("browser-release", engines.size)
        }
    }
    internal fun release(id: Long, expected: WebView) {
        Persistence.requireOwner()
        if (engines.release(id, expected)) {
            revision++
            com.miowser.app.diagnostics.DevResourceDiagnostics.report("browser-release", engines.size)
        }
    }
    fun dispose() {
        Persistence.requireOwner()
        if (disposed) return
        disposed = true
        tabs.toList().forEach(Tab::destroy)
        engines.releaseAll()
        com.miowser.app.diagnostics.DevResourceDiagnostics.report("browser-dispose", engines.size)
    }
}
