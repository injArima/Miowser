package com.miowser.app.browser

import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NorthWest
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.miowser.app.bookmarks.Bookmark
import com.miowser.app.bookmarks.BookmarkList
import com.miowser.app.history.HistoryEntry
import com.miowser.app.history.HistoryLog
import com.miowser.app.settings.SearchEngine
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Search-only suggestions: never send an address or a partially typed URL to a provider. */
private val partialDomain = Regex("(?i)^(?:[a-z0-9-]+\\.)+[a-z0-9-]*(?::\\d*)?(?:/.*)?$")

internal fun shouldRequestSuggestions(input: String, engine: SearchEngine): Boolean {
    val query = input.trim()
    return engine != SearchEngine.BRAVE && engine != SearchEngine.CUSTOM &&
        query.length in 2..150 &&
        (query.contains(' ') || !partialDomain.matches(query)) &&
        !query.startsWith("http:", ignoreCase = true) &&
        !query.startsWith("https:", ignoreCase = true) &&
        UrlOrSearchResolver.resolve(query, engine.queryUrlPrefix).startsWith(engine.queryUrlPrefix)
}

internal fun suggestionUrl(engine: SearchEngine, query: String): String {
    val encoded = URLEncoder.encode(query, "UTF-8")
    return when (engine) {
        SearchEngine.DUCKDUCKGO -> "https://duckduckgo.com/ac/?q=$encoded&type=list"
        SearchEngine.GOOGLE -> "https://www.google.com/complete/search?client=firefox&q=$encoded"
        SearchEngine.BING -> "https://www.bing.com/osjson.aspx?query=$encoded"
        SearchEngine.BRAVE, SearchEngine.CUSTOM -> ""
    }
}

private object SearchSuggestionService {
    suspend fun fetch(engine: SearchEngine, query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(suggestionUrl(engine, query)).openConnection() as HttpURLConnection).apply {
                connectTimeout = 2_500
                readTimeout = 2_500
                useCaches = false
                setRequestProperty("Accept", "application/json")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@runCatching emptyList()
                val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val buffer = CharArray(4_096)
                    val body = StringBuilder()
                    while (body.length < 64_000) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 64_000 - body.length))
                        if (count < 0) break
                        body.append(buffer, 0, count)
                    }
                    body.toString()
                }
                val root = JSONArray(response)
                val items = root.optJSONArray(1) ?: root
                (0 until items.length()).asSequence().mapNotNull { index ->
                    when (val item = items.opt(index)) {
                        is String -> item
                        is JSONObject -> item.optString("phrase")
                        else -> null
                    }?.trim()?.takeIf(String::isNotEmpty)
                }.distinctBy(String::lowercase).take(6).toList()
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(emptyList())
    }
}

private data class SuggestionResult(
    val input: String,
    val engine: SearchEngine,
    val items: List<String>
)

internal enum class BrowserSuggestionKind { BOOKMARK, HISTORY, SEARCH }

internal data class BrowserSuggestion(
    val title: String,
    val value: String,
    val secondaryText: String? = null,
    val kind: BrowserSuggestionKind
)

private data class RankedPageSuggestion(
    val suggestion: BrowserSuggestion,
    val matchRank: Int,
    val sourceRank: Int,
    val timestamp: Long,
    val visitCount: Int
)

private fun searchableUrl(url: String): String = url.trim().lowercase(Locale.ROOT)
    .removePrefix("https://")
    .removePrefix("http://")
    .removePrefix("www.")

private fun canonicalSuggestionUrl(url: String): String =
    searchableUrl(url).trimEnd('/')

private fun pageMatchRank(query: String, title: String, url: String): Int? {
    val normalizedTitle = title.lowercase(Locale.ROOT)
    val normalizedUrl = searchableUrl(url)
    val titleMatchIndex = normalizedTitle.indexOf(query)
    return when {
        normalizedTitle == query || normalizedUrl == query -> 0
        normalizedTitle.startsWith(query) -> 1
        normalizedUrl.startsWith(query) -> 2
        titleMatchIndex > 0 && !normalizedTitle[titleMatchIndex - 1].isLetterOrDigit() -> 3
        titleMatchIndex >= 0 -> 4
        normalizedUrl.contains(query) -> 5
        else -> null
    }
}

/** Local matches are ranked ahead of provider suggestions and deduped by normalized URL. */
internal fun localPageSuggestions(
    input: String,
    bookmarks: List<Bookmark>,
    history: List<HistoryEntry>,
    limit: Int = 6
): List<BrowserSuggestion> {
    val query = input.trim().lowercase(Locale.ROOT)
    if (query.isEmpty() || limit <= 0) return emptyList()

    val candidates = ArrayList<RankedPageSuggestion>(bookmarks.size + history.size)
    val seenUrls = HashSet<String>()
    fun add(title: String, url: String, kind: BrowserSuggestionKind,
            sourceRank: Int, timestamp: Long, visitCount: Int) {
        val canonicalUrl = canonicalSuggestionUrl(url)
        if (canonicalUrl.isEmpty() || canonicalUrl in seenUrls) return
        val rank = pageMatchRank(query, title, url) ?: return
        seenUrls += canonicalUrl
        val displayTitle = title.trim().takeUnless { it.isEmpty() || it.equals(url, ignoreCase = true) }
            ?: searchableUrl(url)
        candidates += RankedPageSuggestion(
            suggestion = BrowserSuggestion(
                title = displayTitle,
                value = url,
                secondaryText = url.takeUnless { it.equals(displayTitle, ignoreCase = true) },
                kind = kind
            ),
            matchRank = rank,
            sourceRank = sourceRank,
            timestamp = timestamp,
            visitCount = visitCount
        )
    }

    bookmarks.forEach { add(it.title, it.url, BrowserSuggestionKind.BOOKMARK, 0, it.addedAt, 0) }
    history.forEach { add(it.title, it.url, BrowserSuggestionKind.HISTORY, 1,
        it.lastVisitedAt, it.visitCount) }

    return candidates.sortedWith(
        compareBy<RankedPageSuggestion> { it.matchRank }
            .thenBy { it.sourceRank }
            .thenByDescending { it.visitCount }
            .thenByDescending { it.timestamp }
    ).take(limit).map(RankedPageSuggestion::suggestion)
}

@Composable
private fun rememberRemoteSearchSuggestions(
    input: String,
    engine: SearchEngine,
    focused: Boolean
): List<String> {
    var result by remember { mutableStateOf(SuggestionResult("", engine, emptyList())) }
    LaunchedEffect(input, engine, focused) {
        if (!focused || !shouldRequestSuggestions(input, engine)) return@LaunchedEffect
        delay(100)
        result = SuggestionResult(input, engine,
            SearchSuggestionService.fetch(engine, input.trim()))
    }
    if (!focused || !shouldRequestSuggestions(input, engine) || result.engine != engine) return emptyList()
    return when {
        result.input == input -> result.items
        input.startsWith(result.input, ignoreCase = true) ->
            result.items.filter { it.startsWith(input, ignoreCase = true) }
        else -> emptyList()
    }
}

@Composable
internal fun rememberBrowserSuggestions(
    input: String,
    engine: SearchEngine,
    focused: Boolean
): List<BrowserSuggestion> {
    val remote = rememberRemoteSearchSuggestions(input, engine, focused)
    if (!focused || input.isBlank()) return emptyList()

    // These are snapshot-backed lists; reading them here updates open suggestion popups when a
    // bookmark is toggled or history is cleared without maintaining a second cache.
    val local = localPageSuggestions(input, BookmarkList.bookmarks, HistoryLog.entries)
    val localValues = local.mapTo(HashSet()) { it.value.lowercase(Locale.ROOT) }
    val remoteItems = remote.asSequence()
        .filterNot { it.lowercase(Locale.ROOT) in localValues }
        .map { BrowserSuggestion(it, it, kind = BrowserSuggestionKind.SEARCH) }
        .toList()
    return local + remoteItems
}

/** A non-focusable popup leaves the keyboard and cursor under the text field's control. */
@Composable
internal fun SearchSuggestionPopup(
    suggestions: List<BrowserSuggestion>,
    anchorSize: IntSize,
    onSelect: (BrowserSuggestion) -> Unit,
    onComplete: (BrowserSuggestion) -> Unit
) {
    if (suggestions.isEmpty() || anchorSize.width == 0) return
    val width = with(LocalDensity.current) { anchorSize.width.toDp() }
    Popup(
        alignment = Alignment.TopStart,
        offset = IntOffset(0, anchorSize.height),
        onDismissRequest = {},
        properties = PopupProperties(
            focusable = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier.width(width).heightIn(max = 320.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp
        ) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                suggestions.forEach { suggestion ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                                .clickable { onSelect(suggestion) }
                                .padding(start = 16.dp, end = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when (suggestion.kind) {
                                    BrowserSuggestionKind.BOOKMARK -> Icons.Rounded.Bookmark
                                    BrowserSuggestionKind.HISTORY -> Icons.Rounded.History
                                    BrowserSuggestionKind.SEARCH -> Icons.Rounded.Search
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (suggestion.kind == BrowserSuggestionKind.SEARCH)
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                else MaterialTheme.colorScheme.primary
                            )
                            Column(Modifier.weight(1f)) {
                                Text(suggestion.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                suggestion.secondaryText?.let { secondary ->
                                    Text(
                                        secondary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { onComplete(suggestion) }) {
                            Icon(Icons.Rounded.NorthWest,
                                contentDescription = "Fill search field with ${suggestion.value}")
                        }
                    }
                }
            }
        }
    }
}
