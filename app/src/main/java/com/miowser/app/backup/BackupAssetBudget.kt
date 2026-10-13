package com.miowser.app.backup

internal data class BackupAsset(val path: String, val bytes: Long, val modifiedAt: Long = 0)
internal data class BackupAssetSelection(val assets: List<BackupAsset>, val skippedArtwork: Int)

/** Only regenerable artwork can be omitted. Settings, filters and selected subtitles are required. */
internal fun selectBackupAssets(assets: List<BackupAsset>, preferenceBytes: Long,
    preferenceCount: Int): BackupAssetSelection {
    fun artwork(asset: BackupAsset) = asset.path.substringAfter("files/").substringBefore('/') in
        setOf("library_thumbnails", "download_thumbnails", "speed-dial-icons")
    val required = assets.filterNot(::artwork)
    var bytes = preferenceBytes + required.sumOf { it.bytes }
    var count = preferenceCount + required.size
    require(required.all { it.bytes in 0..MAX_BACKUP_FILE_BYTES } && bytes <= MAX_BACKUP_TOTAL_BYTES &&
        count <= MAX_BACKUP_FILES) { "Settings, filters or selected subtitles exceed the backup safety limits." }
    val chosen = required.toMutableList()
    var skipped = 0
    assets.filter(::artwork).sortedByDescending { it.modifiedAt }.forEach { asset ->
        if (asset.bytes in 0..MAX_BACKUP_FILE_BYTES && count < MAX_BACKUP_FILES &&
            asset.bytes <= MAX_BACKUP_TOTAL_BYTES - bytes) {
            chosen += asset; bytes += asset.bytes; count++
        } else skipped++
    }
    return BackupAssetSelection(chosen, skipped)
}
