package com.miowser.app.browser

import android.content.Context
import java.nio.charset.StandardCharsets
import java.util.Base64

enum class SiteDecision { ASK, ALLOW, BLOCK }
data class SavedSitePermission(val origin: String, val resource: String, val decision: SiteDecision)

/** Persist only decisions the user explicitly made for an exact HTTPS origin and resource. */
object SitePermissionStore {
    private const val PREFS = "site_permissions"
    private val pending = java.util.concurrent.ConcurrentHashMap<String, String>()
    @Volatile private var cleared = false

    fun get(context: Context, origin: String, resource: String): SiteDecision {
        val raw = pending[key(origin, resource)] ?: if (cleared) null else
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(key(origin, resource), null)
        return raw?.let { runCatching { SiteDecision.valueOf(it) }.getOrNull() } ?: SiteDecision.ASK
    }

    fun set(context: Context, origin: String, resource: String, decision: SiteDecision) {
        pending[key(origin, resource)] = decision.name
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("site-permissions") {
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        if (decision == SiteDecision.ASK) editor.remove(key(origin, resource))
        else editor.putString(key(origin, resource), decision.name)
        editor.commit()
        }
    }

    fun clear(context: Context) {
        pending.clear(); cleared = true
        val app = context.applicationContext
        com.miowser.app.persistence.Persistence.submit("site-permissions") {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    fun all(context: Context): List<SavedSitePermission> =
        ((if (cleared) emptyMap() else context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) + pending).mapNotNull { (key, value) ->
            val decoded = runCatching {
                String(Base64.getUrlDecoder().decode(key), StandardCharsets.UTF_8)
            }.getOrNull() ?: return@mapNotNull null
            val separator = decoded.indexOf('\n')
            if (separator <= 0) return@mapNotNull null
            val decision = runCatching { SiteDecision.valueOf(value as String) }.getOrNull()
                ?: return@mapNotNull null
            if (decision == SiteDecision.ASK) return@mapNotNull null
            SavedSitePermission(decoded.substring(0, separator), decoded.substring(separator + 1), decision)
        }.sortedWith(compareBy(SavedSitePermission::origin, SavedSitePermission::resource))

    private fun key(origin: String, resource: String): String = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("$origin\n$resource".toByteArray(StandardCharsets.UTF_8))
}
