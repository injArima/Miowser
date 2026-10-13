package com.miowser.app.browser

import java.net.URI

const val INACTIVE_TAB_AGE_MILLIS = 14L * 24L * 60L * 60L * 1_000L
const val INACTIVE_TAB_RETENTION_MILLIS = 30L * 24L * 60L * 60L * 1_000L

enum class InactiveTabReason { UNUSED, DUPLICATE }

data class LiveTabSummary(
    val id: Long,
    val url: String,
    val lastActiveAt: Long,
    val isPrivate: Boolean,
    val isHome: Boolean,
    val isActive: Boolean
)

data class InactiveTabDecision(val tabId: Long, val reason: InactiveTabReason)

/** Pure tab-aging and duplicate policy, kept separate from WebView ownership and persistence. */
object InactiveTabPolicy {
    fun decisions(tabs: List<LiveTabSummary>, now: Long): List<InactiveTabDecision> {
        val eligible = tabs.filterNot { it.isPrivate || it.isHome || it.url.isBlank() }
        val decisions = linkedMapOf<Long, InactiveTabReason>()

        eligible.filterNot(LiveTabSummary::isActive)
            .filter { it.lastActiveAt > 0L && now - it.lastActiveAt >= INACTIVE_TAB_AGE_MILLIS }
            .forEach { decisions[it.id] = InactiveTabReason.UNUSED }

        eligible.groupBy { normalizeTabUrl(it.url) }
            .filterKeys(String::isNotBlank)
            .values
            .filter { it.size > 1 }
            .forEach { duplicates ->
                val keeper = duplicates.firstOrNull(LiveTabSummary::isActive)
                    ?: duplicates.maxBy { it.lastActiveAt }
                duplicates.filterNot { it.id == keeper.id || it.isActive }
                    .forEach { decisions[it.id] = InactiveTabReason.DUPLICATE }
            }

        return decisions.map { (id, reason) -> InactiveTabDecision(id, reason) }
    }

    fun isExpired(archivedAt: Long, now: Long): Boolean =
        archivedAt > 0L && now - archivedAt >= INACTIVE_TAB_RETENTION_MILLIS
}

/** Makes harmless URL spelling differences compare as the same page without changing queries. */
fun normalizeTabUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return ""
    return runCatching {
        val uri = URI(trimmed)
        val scheme = uri.scheme?.lowercase() ?: return@runCatching trimmed.substringBefore('#')
        val host = uri.host?.lowercase() ?: return@runCatching trimmed.substringBefore('#')
        val port = uri.port.takeUnless {
            it == -1 || (scheme == "http" && it == 80) || (scheme == "https" && it == 443)
        }
        val authority = if (port == null) host else "$host:$port"
        val path = uri.rawPath?.ifBlank { "/" } ?: "/"
        buildString {
            append(scheme).append("://").append(authority).append(path)
            uri.rawQuery?.let { append('?').append(it) }
        }
    }.getOrElse { trimmed.substringBefore('#') }
}
