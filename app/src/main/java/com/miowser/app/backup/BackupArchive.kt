package com.miowser.app.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import com.miowser.app.adblock.FilterSourceArchive

private const val ARCHIVE_VERSION = 2
internal const val MAX_BACKUP_FILES = 1_500
private const val MAX_FILES = MAX_BACKUP_FILES
internal const val MAX_BACKUP_FILE_BYTES = 32L * 1024 * 1024
internal const val MAX_BACKUP_TOTAL_BYTES = 256L * 1024 * 1024
private const val MAX_TOTAL_BYTES = MAX_BACKUP_TOTAL_BYTES
private const val MANIFEST_NAME = "manifest.properties"

/** Only compressed filter sources need a small allowance for ZIP/DEFLATE framing. */
internal fun backupFileByteLimit(path: String): Long =
    if (path.startsWith("files/adblock/list-") && path.endsWith(".zip")) FilterSourceArchive.MAX_ARCHIVE_BYTES
    else MAX_BACKUP_FILE_BYTES

internal val BACKUP_PREFERENCE_NAMES = listOf(
    "settings", "hub_settings", "bookmarks", "browser_speed_dials", "media_library",
    "library_folders", "downloads", "failed_video_log", "site_protection", "site_permissions",
    "adblock_filters", "browser_overflow_menu", "player_display_preferences",
    "player_subtitle_style", "player_track_preferences"
)

data class BackupSource(val name: String, val open: () -> InputStream)
data class BackupSummary(val createdAt: Long, val fileCount: Int, val skippedArtwork: Int = 0)

class PreparedBackup internal constructor(
    val directory: File,
    val summary: BackupSummary,
    val files: Map<String, File>
) {
    fun discard() { directory.deleteRecursively() }
}

/** Bounded ZIP format with exact entry names, sizes and SHA-256 digests. */
object BackupArchive {
    fun write(output: OutputStream, sources: List<BackupSource>, createdAt: Long, skippedArtwork: Int = 0): BackupSummary {
        require(sources.size <= MAX_FILES && sources.map { it.name }.toSet().size == sources.size)
        require(sources.all { allowedBackupPath(it.name) })
        val entries = mutableListOf<Triple<String, Long, String>>()
        var total = 0L
        ZipOutputStream(output).use { zip ->
            sources.sortedBy { it.name }.forEach { source ->
                zip.putNextEntry(ZipEntry(source.name))
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                source.open().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        size += read
                        total += read
                        require(size <= backupFileByteLimit(source.name) && total <= MAX_TOTAL_BYTES) { "Backup is too large." }
                        zip.write(buffer, 0, read)
                        digest.update(buffer, 0, read)
                    }
                }
                zip.closeEntry()
                entries += Triple(source.name, size, digest.hex())
            }
            val manifest = Properties().apply {
                setProperty("version", ARCHIVE_VERSION.toString())
                setProperty("createdAt", createdAt.toString())
                setProperty("count", entries.size.toString())
                setProperty("skippedArtwork", skippedArtwork.toString())
                entries.forEachIndexed { index, (name, size, hash) ->
                    setProperty("$index.name", name)
                    setProperty("$index.size", size.toString())
                    setProperty("$index.sha256", hash)
                }
            }
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            manifest.store(zip, "Miowser backup")
            zip.closeEntry()
        }
        return BackupSummary(createdAt, entries.size, skippedArtwork)
    }

    fun read(input: InputStream, destination: File): PreparedBackup {
        require(destination.isDirectory || destination.mkdirs())
        val files = linkedMapOf<String, File>()
        val metadata = linkedMapOf<String, Pair<Long, String>>()
        var manifest: Properties? = null
        var total = 0L
        try {
            ZipInputStream(input).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    require(!entry.isDirectory) { "Unexpected backup folder." }
                    if (entry.name == MANIFEST_NAME) {
                        require(manifest == null)
                        val bytes = readBounded(zip, 256 * 1024 + 1)
                        require(bytes.size <= 256 * 1024) { "Backup manifest is too large." }
                        manifest = Properties().apply { bytes.inputStream().use(::load) }
                    } else {
                        require(allowedBackupPath(entry.name) && entry.name !in files && files.size < MAX_FILES) {
                            "Backup contains an unexpected file."
                        }
                        val target = File(destination, entry.name)
                        require(target.canonicalPath.startsWith(destination.canonicalPath + File.separator))
                        require(target.parentFile?.mkdirs() == true || target.parentFile?.isDirectory == true)
                        val digest = MessageDigest.getInstance("SHA-256")
                        var size = 0L
                        target.outputStream().use { output ->
                            val buffer = ByteArray(32 * 1024)
                            while (true) {
                                val read = zip.read(buffer)
                                if (read < 0) break
                                size += read
                                total += read
                                require(size <= backupFileByteLimit(entry.name) && total <= MAX_TOTAL_BYTES) { "Backup is too large." }
                                output.write(buffer, 0, read)
                                digest.update(buffer, 0, read)
                            }
                        }
                        files[entry.name] = target
                        metadata[entry.name] = size to digest.hex()
                    }
                    zip.closeEntry()
                }
            }
            val properties = requireNotNull(manifest) { "This is not a Miowser backup." }
            require(properties.getProperty("version")?.toIntOrNull() in 1..ARCHIVE_VERSION) {
                "This backup version is not supported."
            }
            val count = properties.getProperty("count")?.toIntOrNull() ?: -1
            require(count == files.size && count in 0..MAX_FILES) { "Backup file list is incomplete." }
            val expected = (0 until count).associate { index ->
                val name = requireNotNull(properties.getProperty("$index.name"))
                val size = properties.getProperty("$index.size")?.toLongOrNull() ?: -1
                val hash = requireNotNull(properties.getProperty("$index.sha256"))
                name to (size to hash)
            }
            require(expected.size == count && expected == metadata) { "Backup failed integrity verification." }
            // Verify nested source containers before a restore journal can become authoritative.
            files.filterKeys { it.startsWith("files/adblock/") && it.endsWith(".zip") }
                .values.forEach { FilterSourceArchive.verify(it) }
            val createdAt = properties.getProperty("createdAt")?.toLongOrNull() ?: 0L
            require(createdAt > 0)
            return PreparedBackup(destination, BackupSummary(createdAt, count, properties.getProperty("skippedArtwork")?.toIntOrNull()?.coerceAtLeast(0) ?: 0), files)
        } catch (error: Exception) {
            destination.deleteRecursively()
            throw error
        }
    }
}

private fun MessageDigest.hex(): String = digest().joinToString("") { "%02x".format(it) }

/** Fixed paths prevent archive extraction outside the app's staged restore directory. */
internal fun allowedBackupPath(path: String): Boolean {
    if (path.contains("..") || path.contains('\\') || path.contains('\u0000')) return false
    val parts = path.split('/')
    if (parts.size == 2 && parts[0] == "prefs") {
        return parts[1].removeSuffix(".json") in BACKUP_PREFERENCE_NAMES && parts[1].endsWith(".json")
    }
    if (parts.size != 3 || parts[0] != "files") return false
    val file = parts[2]
    return when (parts[1]) {
        "adblock" -> file == "manual-rules.txt" || file.matches(Regex("list-[A-Za-z0-9_-]+\\.(txt|zip)"))
        "speed-dial-icons" -> file.matches(Regex("[A-Za-z0-9_-]+\\.png"))
        "library_thumbnails", "download_thumbnails" -> file.matches(Regex("[A-Za-z0-9_-]+\\.jpg"))
        "library_subtitles" -> file.matches(Regex("[a-f0-9]{64}\\.(vtt|srt)"))
        else -> false
    }
}

private fun readBounded(input: InputStream, limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8 * 1024)
    while (output.size() < limit) {
        val read = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
        if (read < 0) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
