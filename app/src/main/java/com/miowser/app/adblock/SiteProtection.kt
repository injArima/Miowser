package com.miowser.app.adblock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.miowser.app.persistence.Persistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI

enum class SiteProtectionMode(val label: String) { STANDARD("Standard"), STRICT("Strict"), OFF("Off for this site") }

/** Exact-host overrides; never silently exempt sibling sites or write browsing history. */
object SiteProtection {
    @Volatile private var overrides: Map<String, SiteProtectionMode> = emptyMap()
    @Volatile private var sameTabHosts: Set<String> = emptySet()
    @Volatile private var mediaMinimums: Map<String, Int> = emptyMap()
    private var loaded = false
    var revision by mutableIntStateOf(0)
        private set
    fun host(url: String): String? = runCatching { URI(url).host }.getOrNull()?.let(::canonicalHostname)
    fun mode(host: String): SiteProtectionMode = overrides[host] ?: SiteProtectionMode.STANDARD
    fun openPageLinksHere(host: String): Boolean = host in sameTabHosts
    fun mediaMinimumOverride(host: String): Int? = canonicalHostname(host)?.let(mediaMinimums::get)
    fun mediaMinimumFor(url: String?, globalSeconds: Int): Int =
        resolveSiteMediaMinimum(url?.let(::host), mediaMinimums, globalSeconds)
    suspend fun load(context: Context) {
        Persistence.requireOwner()
        if (loaded) return
        val saved = withContext(Dispatchers.IO) {
            val prefs = context.applicationContext.getSharedPreferences("site_protection", Context.MODE_PRIVATE)
            val modes = prefs.getStringSet("overrides", emptySet()).orEmpty().mapNotNull { value ->
                    val parts = value.split('|', limit = 2)
                    if (parts.size != 2) null else {
                        val host = canonicalHostname(parts[0])
                        val mode = runCatching { SiteProtectionMode.valueOf(parts[1]) }.getOrNull()
                        if (host == null || mode == null) null else host to mode
                    }
                }.toMap()
            val sameTab = prefs.getStringSet("same_tab_hosts", emptySet()).orEmpty()
                .mapNotNull(::canonicalHostname).toSet()
            val minimums = parseSiteMediaMinimums(prefs.getStringSet("media_minimums", emptySet()).orEmpty())
            Triple(modes, sameTab, minimums)
        }
        if (!loaded) {
            overrides = saved.first
            sameTabHosts = saved.second
            mediaMinimums = saved.third
            loaded = true
            revision++
        }
    }
    fun set(context: Context, host: String, mode: SiteProtectionMode) {
        Persistence.requireOwner()
        val canonical = canonicalHostname(host) ?: return
        overrides = overrides.toMutableMap().apply {
            if (mode == SiteProtectionMode.STANDARD) remove(canonical) else put(canonical, mode)
        }.toMap()
        revision++
        val saved = overrides.map { "${it.key}|${it.value.name}" }.toSet()
        val app = context.applicationContext
        Persistence.submit("site-protection") {
            app.getSharedPreferences("site_protection", Context.MODE_PRIVATE).edit().putStringSet("overrides", saved).commit()
        }
    }
    fun setOpenPageLinksHere(context: Context, host: String, enabled: Boolean) {
        Persistence.requireOwner()
        val canonical = canonicalHostname(host) ?: return
        sameTabHosts = sameTabHosts.toMutableSet().apply {
            if (enabled) add(canonical) else remove(canonical)
        }.toSet()
        revision++
        val saved = sameTabHosts
        val app = context.applicationContext
        Persistence.submit("site-navigation") {
            app.getSharedPreferences("site_protection", Context.MODE_PRIVATE).edit()
                .putStringSet("same_tab_hosts", saved).commit()
        }
    }

    /** Null inherits the global setting; zero explicitly shows every media item on this host. */
    fun setMediaMinimum(context: Context, host: String, seconds: Int?) {
        Persistence.requireOwner()
        val canonical = canonicalHostname(host) ?: return
        val updated = mediaMinimums.toMutableMap().apply {
            if (seconds == null) remove(canonical) else put(canonical, seconds.coerceIn(0, 1_800))
        }.toMap()
        if (updated == mediaMinimums) return
        mediaMinimums = updated
        revision++
        val saved = updated.map { "${it.key}|${it.value}" }.toSet()
        val app = context.applicationContext
        Persistence.submit("site-media-minimum") {
            app.getSharedPreferences("site_protection", Context.MODE_PRIVATE).edit()
                .putStringSet("media_minimums", saved).commit()
        }
    }
}

internal fun parseSiteMediaMinimums(values: Set<String>): Map<String, Int> = values.mapNotNull { value ->
    val parts = value.split('|', limit = 2)
    if (parts.size != 2) return@mapNotNull null
    val host = canonicalHostname(parts[0]) ?: return@mapNotNull null
    val seconds = parts[1].toIntOrNull()?.takeIf { it in 0..1_800 } ?: return@mapNotNull null
    host to seconds
}.toMap()

internal fun resolveSiteMediaMinimum(host: String?, overrides: Map<String, Int>, globalSeconds: Int): Int =
    host?.let(overrides::get) ?: globalSeconds
