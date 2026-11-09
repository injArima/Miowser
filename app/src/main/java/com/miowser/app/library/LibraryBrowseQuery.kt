package com.miowser.app.library

import androidx.sqlite.db.SimpleSQLiteQuery
import com.miowser.app.browser.HubLibrarySort

internal const val LIBRARY_PAGE_SIZE = 48

internal data class LibraryCardId(val identityKey: String, val occurrence: Int = 0) {
    val gridKey: String get() = "video:$occurrence:$identityKey"
}

internal data class LibraryFolderCount(val folderId: String?, val count: Int)

internal data class LibraryBrowseRevision(val layout: Long = 0, val progress: Long = 0) {
    fun forSort(sort: HubLibrarySort): LibraryBrowseRevision =
        if (sort == HubLibrarySort.PROGRESS) this else copy(progress = 0)
}

internal data class LibraryWindow(
    val start: Int,
    val ids: List<LibraryCardId>,
    val count: Int,
    val anchorIndex: Int?
) {
    operator fun get(index: Int): LibraryCardId? = ids.getOrNull(index - start)
}

/** The same per-character case folding Kotlin uses for contains(ignoreCase = true). */
internal fun librarySearchKey(value: String): String =
    buildString(value.length) { value.forEach { append(it.uppercaseChar().lowercaseChar()) } }

/** SQLite compares BLOBs bytewise. UTF-16BE preserves the order of Kotlin's lowercase strings. */
internal fun libraryTitleSortKey(title: String): ByteArray = title.lowercase().toByteArray(Charsets.UTF_16BE)

internal fun mediaWatchProgress(positionMs: Long, durationMs: Long): Float {
    if (durationMs <= 0L) return 0f
    return (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
}

internal data class LibraryBrowseQuery(val folderId: String?, val sort: HubLibrarySort, val search: String) {
    private val searchKey = librarySearchKey(search.trim())
    private val folderFilter = if (folderId == null) "m.folderId IS NULL" else "m.folderId = ?"
    private val filter = if (searchKey.isEmpty()) folderFilter else """
        $folderFilter AND (instr(m.titleSearch, ?) > 0 OR instr(m.sourceSearch, ?) > 0 OR instr(m.uriSearch, ?) > 0)
    """.trimIndent()
    private val arguments = buildList {
        folderId?.let(::add)
        if (searchKey.isNotEmpty()) repeat(3) { add(searchKey) }
    }
    private val source = if (sort == HubLibrarySort.PROGRESS)
        "library_media m JOIN library_watch_state w ON w.rowId = m.rowId"
    else "library_media m"
    private val order = when (sort) {
        HubLibrarySort.RECENT -> listOf("m.lastPlayedAt" to true, "m.rowId" to false)
        HubLibrarySort.TITLE -> listOf("m.titleSortKey" to false, "m.lastPlayedAt" to true, "m.rowId" to false)
        HubLibrarySort.PROGRESS -> listOf("w.progress" to true, "m.lastPlayedAt" to true, "m.rowId" to false)
    }
    private val orderedBy = order.joinToString { (column, descending) -> "$column ${if (descending) "DESC" else "ASC"}" }

    fun count() = sql("SELECT COUNT(*) FROM library_media m WHERE $filter", arguments)

    fun keys() = sql("SELECT m.identityKey FROM $source WHERE $filter ORDER BY $orderedBy", arguments)

    fun page(offset: Int, limit: Int) = sql(
        """
        SELECT m.identityKey, (
            SELECT COUNT(*) FROM library_media d WHERE d.identityKey = m.identityKey AND d.rowId < m.rowId
        ) AS occurrence
        FROM $source WHERE $filter ORDER BY $orderedBy LIMIT ? OFFSET ?
        """, arguments + listOf(limit, offset)
    )

    /** Find the current rank of the first visible card when an edit changes the ordering. */
    fun rank(anchor: LibraryCardId): SimpleSQLiteQuery {
        val columns = order.joinToString { (column, _) -> column }
        val before = order.indices.joinToString(" OR ") { index ->
            val (column, descending) = order[index]
            val equals = order.take(index).map { (key, _) -> "$key = a.${key.substringAfter('.')}" }
            val inequality = "$column ${if (descending) ">" else "<"} a.${column.substringAfter('.')}"
            (equals + inequality).joinToString(" AND ", "(", ")")
        }
        return sql(
            """
            WITH anchor AS (
                SELECT $columns FROM $source
                WHERE $filter AND m.rowId = (
                    SELECT rowId FROM library_media WHERE identityKey = ? ORDER BY rowId LIMIT 1 OFFSET ?
                )
            )
            SELECT CASE WHEN EXISTS (SELECT 1 FROM anchor) THEN (
                SELECT COUNT(*) FROM $source CROSS JOIN anchor a WHERE $filter AND ($before)
            ) ELSE -1 END
            """, arguments + listOf(anchor.identityKey, anchor.occurrence) + arguments
        )
    }

    private fun sql(query: String, args: List<Any>) = SimpleSQLiteQuery(query.trimIndent(), args.toTypedArray())
}
