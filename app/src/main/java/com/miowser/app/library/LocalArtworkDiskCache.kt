package com.miowser.app.library

import java.io.File
import java.io.OutputStream

/** Regenerable previews only. The durable Library artwork has its own backup-managed directory. */
internal class LocalArtworkDiskCache(
    private val directory: File,
    private val maxBytes: Long = 64L * 1024 * 1024,
    private val maxEntries: Int = 2_048
) {
    private var bytes = 0L
    private class CacheEntry(val length: Long)
    private val entries = linkedMapOf<String, CacheEntry>()

    init {
        directory.listFiles()?.sortedBy(File::lastModified)?.forEach { file ->
            if (file.extension == "part") file.delete()
            if (file.extension == "jpg" && file.isFile) {
                val length = file.length()
                entries[file.name] = CacheEntry(length)
                bytes += length
            }
        }
        trim()
    }

    fun <T : Any> read(name: String, decode: (File) -> T?): T? {
        val file = File(directory, name)
        val entry = synchronized(this) { entries[name] } ?: return null
        val value = if (file.isFile) decode(file) else null
        synchronized(this) {
            if (entries[name] === entry) {
                if (value == null) {
                    if (!file.exists() || file.delete()) forget(name)
                } else {
                    entries.remove(name)
                    entries[name] = entry
                    file.setLastModified(System.currentTimeMillis())
                }
            }
        }
        return value
    }

    fun write(name: String, encode: (OutputStream) -> Boolean) {
        if (!directory.isDirectory && !directory.mkdirs() && !directory.isDirectory) return
        val target = File(directory, name)
        val temporary = File.createTempFile("preview-", ".part", directory)
        try {
            if (!temporary.outputStream().buffered().use(encode)) return
            val length = temporary.length()
            if (length !in 1..maxBytes) return
            synchronized(this) {
                if (!temporary.renameTo(target)) return
                forget(name)
                entries[name] = CacheEntry(length)
                bytes += length
                trim()
            }
        } finally {
            temporary.delete()
        }
    }

    private fun forget(name: String) {
        bytes -= entries.remove(name)?.length ?: 0
    }

    private fun trim() {
        val iterator = entries.iterator()
        while ((bytes > maxBytes || entries.size > maxEntries) && iterator.hasNext()) {
            val (name, entry) = iterator.next()
            val file = File(directory, name)
            if (!file.exists() || file.delete()) {
                bytes -= entry.length
                iterator.remove()
            }
        }
    }
}
