package com.miowser.app.browser

import android.webkit.WebView
import org.json.JSONObject
import java.net.URI

/** Anikoto's episode list is rendered by its own scripts. Read that DOM, not its private AJAX tokens. */
internal object AnikotoAdapter {
    private const val HOST = "anikototv.to"
    private val WATCH_PATH = Regex("^/watch/[a-zA-Z0-9-]+(?:/ep-[a-zA-Z0-9-]+)?/?$")

    fun accepts(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.host?.lowercase() == HOST &&
            WATCH_PATH.matches(uri.path.orEmpty())
    }.getOrDefault(false)

    private fun showUrl(url: String): String? = runCatching {
        val uri = URI(url)
        if (!accepts(url)) return null
        "https://$HOST" + uri.path.substringBefore("/ep-").trimEnd('/')
    }.getOrNull()

    // Keep the result bounded: One Piece currently renders more than a thousand episodes.
    val snapshotScript = """(function() {
      var root = document.querySelector('#watch-main');
      if (!root || !document.querySelector('#w-episodes')) return null;
      var episodes = Array.from(document.querySelectorAll('#w-episodes a[data-num][href]')).slice(0, 2500).map(function(a) {
        return { number: a.dataset.num || '', title: a.closest('li')?.getAttribute('title') || '',
          url: a.href, sub: a.dataset.sub === '1', dub: a.dataset.dub === '1',
          active: a.classList.contains('active') };
      });
      var servers = Array.from(document.querySelectorAll('#w-servers .type[data-type] li[data-sv-id]')).slice(0, 60).map(function(li) {
        return { language: li.closest('.type')?.dataset.type || '', id: li.dataset.svId || '',
          episodeId: li.dataset.epId || '', linkId: li.dataset.linkId || '',
          label: li.textContent.trim().slice(0, 80),
          active: li.classList.contains('active') };
      });
      var seasons = Array.from(document.querySelectorAll('#ani-seasons a[href*="/watch/"]')).slice(0, 40).map(function(a) {
        return { title: a.textContent.trim().slice(0, 120), url: a.href };
      });
      return { pageUrl: location.href, showUrl: root.dataset.url || '',
        showTitle: document.querySelector('h1')?.textContent.trim().slice(0, 160) || document.title,
        episodes: episodes, servers: servers, seasons: seasons };
    })();""".trimIndent()

    fun parse(raw: String?): AnikotoCatalog? = runCatching {
        if (raw.isNullOrBlank() || raw == "null" || raw.length > 900_000) return null
        val json = JSONObject(raw)
        val pageUrl = json.optString("pageUrl")
        val base = showUrl(pageUrl) ?: return null
        if (showUrl(json.optString("showUrl")) != base) return null
        val episodesJson = json.optJSONArray("episodes") ?: return null
        val episodes = buildList {
            for (index in 0 until minOf(episodesJson.length(), 2500)) {
                val episode = episodesJson.optJSONObject(index) ?: continue
                val number = episode.optString("number").take(24)
                val url = episode.optString("url")
                if (number.isBlank() || showUrl(url) != base ||
                    !URI(url).path.startsWith(URI(base).path.trimEnd('/') + "/ep-")) continue
                add(AnikotoEpisode(number, episode.optString("title").take(160), url,
                    episode.optBoolean("sub"), episode.optBoolean("dub"), episode.optBoolean("active")))
            }
        }.distinctBy { it.url }
        val serversJson = json.optJSONArray("servers")
        val servers = buildList {
            for (index in 0 until minOf(serversJson?.length() ?: 0, 60)) {
                val server = serversJson?.optJSONObject(index) ?: continue
                val language = server.optString("language")
                val id = server.optString("id")
                val episodeId = server.optString("episodeId")
                val linkId = server.optString("linkId")
                if (language !in setOf("sub", "dub") || !id.matches(Regex("[a-zA-Z0-9_-]{1,40}")) ||
                    !episodeId.matches(Regex("[0-9]{1,20}")) ||
                    !linkId.matches(Regex("[a-zA-Z0-9+/_=-]{1,512}"))) continue
                add(AnikotoServer(language, id, episodeId, linkId,
                    server.optString("label").take(80),
                    server.optBoolean("active")))
            }
        }.distinctBy { Triple(it.language, it.episodeId, it.linkId) }
        val seasonsJson = json.optJSONArray("seasons")
        val seasons = buildList {
            for (index in 0 until minOf(seasonsJson?.length() ?: 0, 40)) {
                val season = seasonsJson?.optJSONObject(index) ?: continue
                val url = season.optString("url")
                if (showUrl(url) == null) continue
                add(AnikotoSeason(season.optString("title").take(120), url))
            }
        }.distinctBy { it.url }
        AnikotoCatalog(base, json.optString("showTitle").take(160), episodes, servers, seasons)
    }.getOrNull()

    fun selectServer(view: WebView, server: AnikotoServer, onResult: (Boolean) -> Unit) {
        val language = server.language
        val id = server.id
        val episodeId = server.episodeId
        val linkId = server.linkId
        if (language !in setOf("sub", "dub") || !id.matches(Regex("[a-zA-Z0-9_-]{1,40}")) ||
            !episodeId.matches(Regex("[0-9]{1,20}")) ||
            !linkId.matches(Regex("[a-zA-Z0-9+/_=-]{1,512}"))) {
            onResult(false)
            return
        }
        view.evaluateJavascript("""(function() {
          var root = document.querySelector('#w-servers .type[data-type=${JSONObject.quote(language)}]');
          var item = root && Array.from(root.querySelectorAll('li[data-link-id]')).find(function(li) {
            return li.dataset.svId === ${JSONObject.quote(id)} &&
              li.dataset.epId === ${JSONObject.quote(episodeId)} &&
              li.dataset.linkId === ${JSONObject.quote(linkId)};
          });
          if (!item) return false;
          item.click();
          return true;
        })();""".trimIndent()) { onResult(it == "true") }
    }
}

internal data class AnikotoCatalog(
    val showUrl: String,
    val showTitle: String,
    val episodes: List<AnikotoEpisode>,
    val servers: List<AnikotoServer>,
    val seasons: List<AnikotoSeason>
)

internal data class AnikotoEpisode(
    val number: String,
    val title: String,
    val url: String,
    val hasSub: Boolean,
    val hasDub: Boolean,
    val active: Boolean
)

internal data class AnikotoServer(
    val language: String,
    val id: String,
    val episodeId: String,
    val linkId: String,
    val label: String,
    val active: Boolean
)

internal data class AnikotoSeason(val title: String, val url: String)
