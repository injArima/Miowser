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
    private var visibleId: Long? = null
    private var protectedIds = emptySet<Long>()
    private var normalLimit = 6
    private var pressureLimit: Int? = null
    private val navigation = linkedMapOf<Long, android.os.Bundle>()
    private var navigationBytes = 0
    private val navigationSizes = mutableMapOf<Long, Int>()

    fun configureMemory(context: android.content.Context) {
        val manager = context.getSystemService(android.app.ActivityManager::class.java)
        normalLimit = if (manager?.isLowRamDevice == true || (manager?.memoryClass ?: 256) < 192) 3 else 6
    }
    fun setVisible(id: Long?, protected: Set<Long> = emptySet()) {
        if (visibleId != id) pressureLimit = null
        visibleId = id
        protectedIds = protected
        id?.let(engines::touch)
        trim()
    }
    internal fun touch(id: Long) { engines.touch(id) }
    fun onForeground() { pressureLimit = null; trim() }
    fun trimMemory(level: Int) {
        pressureLimit = if (level == android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) 2 else 1
        trim()
    }
    internal fun trim(protectAcquired: Long? = null) {
        Persistence.requireOwner()
        val limit = pressureLimit ?: normalLimit
        engines.retirementCandidates(limit) { id ->
            id != visibleId && id != protectAcquired && id !in protectedIds &&
                tabs.firstOrNull { it.id == id }?.canRetireEngine == true
        }.forEach { id -> tabs.firstOrNull { it.id == id }?.retireBackgroundEngine() }
    }
    internal fun rememberNavigation(id: Long, state: android.os.Bundle) {
        val parcel = android.os.Parcel.obtain()
        val bytes = try { parcel.writeBundle(state); parcel.dataSize() } finally { parcel.recycle() }
        if (bytes > 128 * 1024) return
        navigationSizes.remove(id)?.let { navigationBytes -= it }
        navigation.remove(id)
        navigation[id] = state
        navigationSizes[id] = bytes
        navigationBytes += bytes
        while (navigationBytes > 1024 * 1024) {
            val oldest = navigation.keys.first()
            navigation.remove(oldest)
            navigationBytes -= navigationSizes.remove(oldest) ?: 0
        }
    }
    internal fun takeNavigation(id: Long): android.os.Bundle? {
        navigationBytes -= navigationSizes.remove(id) ?: 0
        return navigation.remove(id)
    }
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
            revision++; trim(protectAcquired = id)
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
        navigation.clear(); navigationSizes.clear(); navigationBytes = 0
        com.miowser.app.diagnostics.DevResourceDiagnostics.report("browser-dispose", engines.size)
    }
}
