package com.miowser.app.library

import org.junit.Assert.*
import org.junit.Test

class P2LibraryRetentionTest {
    private fun item(id: Int, saved: Boolean = false, manual: Boolean = false) = LibraryEntry(
        "https://example.org/$id", null, "Video $id", "Example", LibrarySourceKind.REMOTE_STREAM,
        null, null, null, id.toLong(), 0, 10_000, explicitlySaved = saved, manualFolder = manual)
    @Test fun automaticRecentEvictionCannotRemoveExplicitSavesOrManualPlacements() {
        val saved = item(0, saved = true); val manual = item(1, manual = true)
        val retained = retainLibraryRecords((2..1002).map { item(it) } + saved + manual)
        assertEquals(502, retained.size); assertTrue(saved in retained); assertTrue(manual in retained)
    }
    @Test fun savedVideo501AndLegacyRecordsSurviveRoundTripAndRetention() {
        val records = (0..500).map { item(it, saved = true) }
        assertEquals(records, retainLibraryRecords(LibraryCodec.decode(LibraryCodec.encode(records))))
        val legacy = LibraryCodec.encode(listOf(item(9))).substringBeforeLast('\t')
        assertTrue(LibraryCodec.decode(legacy).single().explicitlySaved)
    }
    @Test fun migrationDoesNotTurnP1AutomaticFolderPlacementsIntoManualMoves() {
        val legacy = LibraryCodec.encode(listOf(item(9).copy(folderId = "rule"))).substringBeforeLast('\t')
        assertFalse(LibraryCodec.decode(legacy).single().manualFolder)
    }
    @Test fun reorganizationsRespectManualPlacementsAndParentScope() {
        val rule = LibraryFolder("target", "Target", parentId = "parent", titleRule =
            LibraryTitleRule(LibraryTitleSource.MEDIA, LibraryRuleOperator.ALL, listOf("video")))
        val records = listOf(item(1).copy(folderId = "parent"), item(2).copy(folderId = "other"),
            item(3, manual = true).copy(folderId = "parent"))
        assertEquals(mapOf(records[0].identityKey to "target"), planLibraryReorganization(records,
            CompiledLibraryRouter(listOf(rule), null), "parent", true))
    }
}
