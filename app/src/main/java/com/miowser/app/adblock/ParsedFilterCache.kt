package com.miowser.app.adblock

import android.content.Context
import android.util.AtomicFile
import java.io.*
import java.security.MessageDigest

/** Content-addressed, disposable cache; identical lists at different URLs share one parse. */
internal object ParsedFilterCache {
    private const val SCHEMA = "parsed-rules-1"
    private const val MAX_ENTRY_BYTES = 32L * 1024 * 1024
    private const val MAX_CACHE_BYTES = 96L * 1024 * 1024
    private val lock = Any()

    fun loadOrParse(context: Context, text: String, source: FilterSource): FilterListParser.ParseResult = synchronized(lock) {
        val hash = MessageDigest.getInstance("SHA-256").apply {
            update("$SCHEMA:${source.name}:".toByteArray(Charsets.UTF_8))
        }.digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        val directory = File(context.cacheDir, "parsed-adblock")
        val target = File(directory, "$hash.bin")
        val cached = runCatching {
            if (!target.isFile || target.length() > MAX_ENTRY_BYTES) null else
                ObjectInputStream(target.inputStream().buffered()).use { input ->
                    if (input.readUTF() != hash) null else input.readObject() as FilterListParser.ParseResult
                }
        }.getOrNull()
        if (cached != null) {
            target.setLastModified(System.currentTimeMillis())
            return@synchronized cached
        }
        val parsed = FilterListParser.parse(text, source)
        val file = AtomicFile(target)
        var stream: FileOutputStream? = null
        try {
            directory.mkdirs()
            stream = file.startWrite()
            val output = ObjectOutputStream(BufferedOutputStream(stream))
            output.writeUTF(hash)
            output.writeObject(parsed)
            output.flush()
            file.finishWrite(stream)
            stream = null
            if (target.length() > MAX_ENTRY_BYTES) target.delete()
            val entries = directory.listFiles()?.filter { it.name.endsWith(".bin") }.orEmpty()
            var bytes = entries.sumOf { it.length() }
            for (entry in entries.sortedBy { it.lastModified() }) {
                if (bytes <= MAX_CACHE_BYTES) break
                val size = entry.length()
                if (entry.delete()) bytes -= size
            }
        } catch (_: Exception) {
            stream?.let(file::failWrite)
        }
        parsed // Cache eviction, serialization or disk-full errors never invalidate the source.
    }
}
