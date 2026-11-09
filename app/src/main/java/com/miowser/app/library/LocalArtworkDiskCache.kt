package com.miowser.app.library

import java.io.File
import java.io.OutputStream

/** Regenerable previews only. The durable Library artwork stays in filesDir. */
internal class LocalArtworkDiskCache(
    private val directory: File,
    private val maxBytes: Long = 64L * 1024 * 1024,
    private val maxEntries: Int = 2_048
) {
    private var bytes = 0L
    private val entries = linkedMapOf<String, Long>()

    init {
        directory.listFiles()?.sortedBy(File::lastModified)?.forEach { file ->
            if (file.extension == "part") file.delete()
            if (file.extension == "jpg" && file.isFile) {
                val length = file.length()
                entries[file.name] = length
                bytes += length
            }
        }
        trim()
    }

    @Synchronized
    fun <T : Any> read(name: String, decode: (File) -> T?): T? {
        val file = File(directory, name)
        val length = entries[name] ?: return null
        val value = if (file.isFile) decode(file) else null
        if (value == null) {
            if (!file.exists() || file.delete()) forget(name)
        } else {
            entries.remove(name)
            entries[name] = length
            file.setLastModified(System.currentTimeMillis())
        }
        return value
    }

    @Synchronized
    fun write(name: String, encode: (OutputStream) -> Boolean) {
        if (!directory.isDirectory && !directory.mkdirs()) return
        val target = File(directory, name)
        val temporary = File(directory, "$name.part")
        try {
            if (!temporary.outputStream().buffered().use(encode)) return
            val length = temporary.length()
            if (length !in 1..maxBytes || !temporary.renameTo(target)) return
            forget(name)
            entries[name] = length
            bytes += length
            trim()
        } finally {
            temporary.delete()
        }
    }

    private fun forget(name: String) {
        bytes -= entries.remove(name) ?: 0
    }

    private fun trim() {
        val iterator = entries.iterator()
        while ((bytes > maxBytes || entries.size > maxEntries) && iterator.hasNext()) {
            val (name, length) = iterator.next()
            val file = File(directory, name)
            if (!file.exists() || file.delete()) {
                bytes -= length
                iterator.remove()
            }
        }
    }
}
