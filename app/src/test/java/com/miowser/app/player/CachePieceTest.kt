package com.miowser.app.player

import org.junit.Assert.*
import org.junit.Test

class CachePieceTest {
    @Test fun cappedRequestsContinueAtFirstUncachedByte() {
        assertEquals(CachePiece(12, 12), firstMissingCachePiece(0, 30, 12) { position, amount ->
            if (position < 12) minOf(amount, 12 - position) else -amount
        })
    }
    @Test fun evictionMakesAnEarlierRangeEligibleAgain() {
        assertNull(firstMissingCachePiece(0, 10, 12) { _, amount -> amount })
        assertEquals(CachePiece(0, 10), firstMissingCachePiece(0, 10, 12) { _, amount -> -amount })
    }
    @Test fun disjointSpansFillOnlyTheirFirstHole() {
        assertEquals(CachePiece(4, 3), firstMissingCachePiece(0, 10, 12) { position, _ ->
            if (position == 0L) 4 else -3
        })
    }
    @Test fun unknownLengthRetainsEofDiscoveryInsteadOfInventingCompletion() {
        assertEquals(CachePiece(12, -1), firstMissingCachePiece(0, -1, 12) { position, amount ->
            if (position < 12) amount else -amount
        })
    }
    @Test fun emptyResourceNeedsNoRequest() { assertNull(firstMissingCachePiece(5, 0, 12) { _, _ -> error("no query") }) }
}
