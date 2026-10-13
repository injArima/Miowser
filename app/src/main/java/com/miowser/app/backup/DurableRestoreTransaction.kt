package com.miowser.app.backup

import java.io.File
import java.io.FileOutputStream

/** Once ready is synced, the verified archive is the authoritative generation. A process
 * interrupted during application rolls forward from the same archive before repositories load. */
internal class DurableRestoreTransaction(private val root: File) {
    val needsRecovery: Boolean get() = File(root, "ready").isFile
    fun prepare(sources: List<BackupSource>, createdAt: Long) {
        check(!needsRecovery) { "A previous restore needs recovery first." }
        root.mkdirs()
        val archive = File(root, "generation.zip")
        FileOutputStream(archive).use { output ->
            // BackupArchive closes its stream; keep the file descriptor open for fsync.
            val wrapper = object : java.io.FilterOutputStream(output) {
                override fun close() { flush() }
            }
            BackupArchive.write(wrapper, sources, createdAt)
            output.fd.sync()
        }
        archive.inputStream().use { BackupArchive.read(it, File(root, "verify")).discard() }
        FileOutputStream(File(root, "ready")).use { it.write(1); it.fd.sync() }
    }
    fun recover(apply: (PreparedBackup) -> Unit) {
        if (!needsRecovery) { root.deleteRecursively(); return }
        val prepared = File(root, "generation.zip").inputStream().use {
            BackupArchive.read(it, File(root, "apply"))
        }
        try {
            apply(prepared)
            // Removing ready only after every preference commit and file publication succeeded
            // makes replay idempotent, including termination between the last commit and cleanup.
            check(File(root, "ready").delete()) { "Could not finish the restore journal." }
            root.deleteRecursively()
        } finally { prepared.discard() }
    }
}
