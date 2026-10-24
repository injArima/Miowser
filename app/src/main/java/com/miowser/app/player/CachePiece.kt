package com.miowser.app.player

internal data class CachePiece(val position: Long, val length: Long)

/** Cached spans can be disjoint or evicted. Find a hole from the cache index on every pass. */
internal fun firstMissingCachePiece(position: Long, length: Long, cap: Long,
    cachedLength: (Long, Long) -> Long): CachePiece? {
    require(position >= 0 && length >= -1 && cap > 0)
    val end = if (length >= 0) position + length else Long.MAX_VALUE
    require(end >= position)
    var cursor = position
    while (cursor < end) {
        val amount = minOf(cap, end - cursor)
        val span = cachedLength(cursor, amount)
        require(span != 0L)
        if (span < 0) return CachePiece(cursor, if (length < 0) -1 else minOf(amount, -span))
        require(span <= amount)
        cursor += span
    }
    return null
}
