package com.miowser.app.adblock

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.miowser.app.persistence.Persistence
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class ElementRuleAction(val label: String) {
    HIDE("Hide with CSS"),
    DELETE("Delete from page")
}

data class SiteElementRule(
    val id: String,
    val host: String,
    val selector: String,
    val action: ElementRuleAction,
    val createdAt: Long
)

/** User-created, exact-site DOM changes produced by the visual element picker. */
object SiteElementRules {
    private const val PREFS = "site_element_rules"
    private const val KEY_RULES = "rules"
    private const val MAX_RULES = 2_000
    private const val MAX_RULES_PER_SITE = 100
    private const val MAX_SELECTOR_LENGTH = 1_024

    @Volatile private var snapshot: List<SiteElementRule> = emptyList()
    private var loaded = false

    var revision by mutableIntStateOf(0)
        private set

    val rules: List<SiteElementRule> get() = snapshot

    suspend fun load(context: Context) {
        Persistence.requireOwner()
        if (loaded) return
        val restored = withContext(Dispatchers.IO) {
            val raw = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_RULES, null)
            decode(raw)
        }
        if (!loaded) {
            snapshot = restored
            loaded = true
            revision++
        }
    }

    fun forHost(host: String?): List<SiteElementRule> {
        val canonical = host?.let(::canonicalHostname) ?: return emptyList()
        return snapshot.filter { it.host == canonical }
    }

    fun add(
        context: Context,
        host: String,
        selector: String,
        action: ElementRuleAction
    ): SiteElementRule? {
        Persistence.requireOwner()
        val canonical = canonicalHostname(host) ?: return null
        val cleanSelector = selector.trim().takeIf(::validSelector) ?: return null
        snapshot.firstOrNull { it.host == canonical && it.selector == cleanSelector }?.let { existing ->
            return if (existing.action == action) existing else updateAction(context, existing.id, action)
        }
        if (snapshot.size >= MAX_RULES || snapshot.count { it.host == canonical } >= MAX_RULES_PER_SITE) return null
        val rule = SiteElementRule(
            id = UUID.randomUUID().toString(),
            host = canonical,
            selector = cleanSelector,
            action = action,
            createdAt = System.currentTimeMillis()
        )
        publish(context, snapshot + rule, "site-element-add")
        return rule
    }

    fun updateAction(context: Context, id: String, action: ElementRuleAction): SiteElementRule? {
        Persistence.requireOwner()
        val existing = snapshot.firstOrNull { it.id == id } ?: return null
        if (existing.action == action) return existing
        val updated = existing.copy(action = action)
        publish(context, snapshot.map { if (it.id == id) updated else it }, "site-element-update")
        return updated
    }

    fun remove(context: Context, id: String): SiteElementRule? {
        Persistence.requireOwner()
        val removed = snapshot.firstOrNull { it.id == id } ?: return null
        publish(context, snapshot.filterNot { it.id == id }, "site-element-remove")
        return removed
    }

    fun removeHost(context: Context, host: String): List<SiteElementRule> {
        Persistence.requireOwner()
        val canonical = canonicalHostname(host) ?: return emptyList()
        val removed = snapshot.filter { it.host == canonical }
        if (removed.isNotEmpty()) {
            publish(context, snapshot.filterNot { it.host == canonical }, "site-element-remove-host")
        }
        return removed
    }

    fun restore(context: Context, rules: List<SiteElementRule>) {
        Persistence.requireOwner()
        if (rules.isEmpty()) return
        val byId = snapshot.associateByTo(linkedMapOf(), SiteElementRule::id)
        rules.filter { canonicalHostname(it.host) == it.host && validSelector(it.selector) }
            .forEach { byId[it.id] = it }
        publish(context, byId.values.take(MAX_RULES), "site-element-restore")
    }

    private fun publish(context: Context, updated: List<SiteElementRule>, task: String) {
        snapshot = updated.toList()
        revision++
        val encoded = encode(snapshot)
        val app = context.applicationContext
        Persistence.submitSnapshot(task) {
            app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY_RULES, encoded)
                .commit()
        }
    }

    private fun validSelector(selector: String): Boolean = selector.isNotBlank() &&
        selector.length <= MAX_SELECTOR_LENGTH &&
        selector.none { it == '\u0000' || it == '\n' || it == '\r' || it == '{' || it == '}' }

    private fun encode(rules: List<SiteElementRule>): String = JSONArray().apply {
        rules.forEach { rule ->
            put(JSONObject().apply {
                put("id", rule.id)
                put("host", rule.host)
                put("selector", rule.selector)
                put("action", rule.action.name)
                put("createdAt", rule.createdAt)
            })
        }
    }.toString()

    private fun decode(raw: String?): List<SiteElementRule> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            require(array.length() <= MAX_RULES)
            buildList<SiteElementRule> {
                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val host = canonicalHostname(item.getString("host")) ?: continue
                    val selector = item.getString("selector").trim()
                    if (!validSelector(selector)) continue
                    val action = runCatching {
                        ElementRuleAction.valueOf(item.getString("action"))
                    }.getOrNull() ?: continue
                    val id = item.getString("id").takeIf { it.length in 1..100 } ?: continue
                    if (count { it.host == host } >= MAX_RULES_PER_SITE) continue
                    add(SiteElementRule(id, host, selector, action, item.optLong("createdAt", 0L)))
                }
            }.distinctBy(SiteElementRule::id)
        }.getOrDefault(emptyList())
    }
}
