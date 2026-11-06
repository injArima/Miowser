package com.miowser.app.browser

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest

/** Episode metadata and progress live beside Library records in the existing backed-up store. */
internal object AnikotoSeriesStore {
    private const val PREFS = "media_library"
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun key(showUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(showUrl.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
    private val revisionState = mutableIntStateOf(0)
    val revision: Int get() = revisionState.intValue

    fun saveCatalog(context: Context, catalog: AnikotoCatalog) {
        val encoded = JSONObject().put("url", catalog.showUrl).put("title", catalog.showTitle)
            .put("episodes", JSONArray().also { array ->
                catalog.episodes.forEach { episode ->
                    array.put(JSONObject().put("number", episode.number).put("title", episode.title)
                        .put("url", episode.url).put("sub", episode.hasSub).put("dub", episode.hasDub))
                }
            }).toString()
        val name = "series:${key(catalog.showUrl)}"
        if (prefs(context).getString(name, null) == encoded) return
        prefs(context).edit().putString(name, encoded).apply()
        revisionState.intValue++
    }

    fun load(context: Context, showUrl: String): AnikotoCatalog? = runCatching {
        val root = JSONObject(prefs(context).getString("series:${key(showUrl)}", null) ?: return null)
        if (root.optString("url") != showUrl || !AnikotoAdapter.accepts(showUrl)) return null
        val array = root.getJSONArray("episodes")
        val episodes = buildList {
            for (index in 0 until minOf(array.length(), 2500)) {
                val item = array.optJSONObject(index) ?: continue
                val url = item.optString("url")
                if (!AnikotoAdapter.accepts(url) || !url.startsWith("$showUrl/ep-")) continue
                add(AnikotoEpisode(item.optString("number").take(24),
                    item.optString("title").take(160), url,
                    item.optBoolean("sub"), item.optBoolean("dub"), false))
            }
        }
        AnikotoCatalog(showUrl, root.optString("title").take(160), episodes,
            emptyList(), emptyList())
    }.getOrNull()

    fun choice(context: Context, showUrl: String): Pair<String, String?> {
        val p = prefs(context)
        val id = key(showUrl)
        return (p.getString("language:$id", "sub")?.takeIf { it in setOf("sub", "dub") }
            ?: "sub") to p.getString("server:$id", null)
    }

    fun saveChoice(context: Context, showUrl: String, language: String, serverId: String?) {
        if (!AnikotoAdapter.accepts(showUrl) || language !in setOf("sub", "dub")) return
        val id = key(showUrl)
        prefs(context).edit().putString("language:$id", language)
            .putString("server:$id", serverId).apply()
    }

    fun progress(context: Context, showUrl: String, episodeUrl: String): Pair<Long, Long> {
        val raw = prefs(context).getString("progress:${key(showUrl)}", null) ?: return 0L to 0L
        return runCatching {
            val item = JSONObject(raw).optJSONArray(episodeUrl) ?: return 0L to 0L
            item.optLong(0).coerceAtLeast(0L) to item.optLong(1).coerceAtLeast(0L)
        }.getOrDefault(0L to 0L)
    }

    fun saveProgress(context: Context, showUrl: String, episodeUrl: String,
                     positionMs: Long, durationMs: Long) {
        if (!AnikotoAdapter.accepts(showUrl) || !AnikotoAdapter.accepts(episodeUrl)) return
        val name = "progress:${key(showUrl)}"
        val root = runCatching { JSONObject(prefs(context).getString(name, "{}") ?: "{}") }
            .getOrDefault(JSONObject())
        val previous = root.optJSONArray(episodeUrl)
        if (previous != null && previous.optLong(0) == positionMs &&
            previous.optLong(1) == durationMs) return
        root.put(episodeUrl, JSONArray().put(positionMs.coerceAtLeast(0L))
            .put(durationMs.coerceAtLeast(0L)))
        prefs(context).edit().putString(name, root.toString()).apply()
        revisionState.intValue++
    }
}
