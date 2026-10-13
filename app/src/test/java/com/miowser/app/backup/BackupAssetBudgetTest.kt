package com.miowser.app.backup

import org.junit.Assert.*
import org.junit.Test

class BackupAssetBudgetTest {
    @Test fun requiredCaptionsAndFiltersBeatRegenerableArtwork() {
        val assets = listOf(BackupAsset("files/library_subtitles/a.srt", 4),
            BackupAsset("files/adblock/manual-rules.txt", 4),
            BackupAsset("files/library_thumbnails/a.jpg", MAX_BACKUP_FILE_BYTES),
            BackupAsset("files/library_thumbnails/b.jpg", MAX_BACKUP_FILE_BYTES))
        val result = selectBackupAssets(assets, MAX_BACKUP_TOTAL_BYTES - MAX_BACKUP_FILE_BYTES - 8, 15)
        assertEquals(1, result.skippedArtwork)
        assertTrue(result.assets.any { it.path.endsWith(".srt") })
        assertTrue(result.assets.any { it.path.endsWith("manual-rules.txt") })
        assertEquals(3, result.assets.size)
    }
    @Test fun requiredDataNeverSilentlyDropsWhenTooLarge() {
        assertTrue(runCatching { selectBackupAssets(listOf(BackupAsset("files/adblock/list.txt",
            MAX_BACKUP_FILE_BYTES + 1)), 0, 15) }.isFailure)
    }
    @Test fun newestArtworkIsKeptWhenEntryCountIsFull() {
        val result = selectBackupAssets(listOf(BackupAsset("files/library_thumbnails/old.jpg", 1, 1),
            BackupAsset("files/library_thumbnails/new.jpg", 1, 2)), 0, MAX_BACKUP_FILES - 1)
        assertEquals(listOf("files/library_thumbnails/new.jpg"), result.assets.map { it.path })
        assertEquals(1, result.skippedArtwork)
    }
}
