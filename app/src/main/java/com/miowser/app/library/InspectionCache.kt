package com.miowser.app.library

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Disposable, bounded facts. No Android calls; disk operations belong to a worker. */
internal class InspectionCache(private val directory: File, private val now: () -> Long = System::currentTimeMillis) {
    private data class Fact(val value: String, val expiresAt: Long)
    private val lock = Any()
    private val diskLock = Any()
    private val facts = LinkedHashMap<String, Fact>(16, 0.75f, true)
    private var namespace: String? = null
    private var revision = 0L
    @Volatile var epoch = 0L
        private set
    @Volatile var loaded = false
        private set
    private val marker get() = File(directory, "generation")
    private val index get() = File(directory, "facts.bin")

    fun prepare(): Long = synchronized(diskLock) {
        val existing = readMarker()
        if (loaded && existing != null && existing == namespace) return@synchronized epoch
        val token = existing ?: UUID.randomUUID().toString().also {
            check(directory.isDirectory || directory.mkdirs())
            marker.writeText(it)
        }
        val restored = runCatching {
            require(index.length() <= MAX_DISK_BYTES)
            DataInputStream(index.inputStream().buffered()).use { input ->
                require(input.readInt() == 1 && input.readUTF() == token)
                val count = input.readInt().also { require(it in 0..MAX_ENTRIES) }
                buildList {
                    repeat(count) {
                        val key = input.readUTF()
                        val value = input.readUTF()
                        val expiry = input.readLong()
                        require(key.length <= MAX_FIELD_CHARS && value.length <= MAX_FIELD_CHARS)
                        if (expiry > now()) add(key to Fact(value, expiry))
                    }
                }
            }
        }.getOrDefault(emptyList())
        synchronized(lock) {
            facts.clear()
            restored.forEach { (key, fact) -> facts[key] = fact }
            trim()
            namespace = token
            revision++
            epoch++
            loaded = true
            epoch
        }
    }

    fun peek(key: String): String? = synchronized(lock) {
        facts[key]?.takeIf { it.expiresAt > now() }?.value
    }

    fun ticket(): Long = synchronized(lock) { revision }

    fun put(key: String, value: String, lifetimeMs: Long, ticket: Long): Boolean = synchronized(lock) {
        if (!loaded || ticket != revision || lifetimeMs <= 0 ||
            key.length > MAX_FIELD_CHARS || value.length > MAX_FIELD_CHARS) return@synchronized false
        facts[key] = Fact(value, now() + lifetimeMs)
        trim()
        true
    }

    fun invalidate(keys: Collection<String>, ticket: Long? = null): Long? = synchronized(lock) {
        if (ticket != null && ticket != revision) return@synchronized null
        revision++ // Also retire outstanding probes before they can repopulate old facts.
        keys.forEach(facts::remove)
        revision
    }

    fun invalidateValue(value: String) = synchronized(lock) {
        revision++
        facts.entries.removeAll { it.value.value == value }
    }

    fun flush() = synchronized(diskLock) {
        val snapshot = synchronized(lock) { Triple(namespace, revision, facts.toList()) }
        val token = snapshot.first ?: return@synchronized
        if (readMarker() != token) return@synchronized // Clear cache must not be undone by late writes.
        val temporary = File(directory, "facts.tmp")
        try {
            DataOutputStream(temporary.outputStream().buffered()).use { output ->
                output.writeInt(1)
                output.writeUTF(token)
                output.writeInt(snapshot.third.size)
                snapshot.third.forEach { (key, fact) ->
                    output.writeUTF(key); output.writeUTF(fact.value); output.writeLong(fact.expiresAt)
                }
            }
            synchronized(lock) commit@ {
                if (revision != snapshot.second || readMarker() != token) return@commit
                try {
                    Files.move(temporary.toPath(), index.toPath(), StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING)
                } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                    Files.move(temporary.toPath(), index.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally { temporary.delete() }
    }

    private fun readMarker(): String? = runCatching {
        marker.takeIf { it.isFile && it.length() in 1..64 }?.readText()
            ?.takeIf { runCatching { UUID.fromString(it) }.isSuccess }
    }.getOrNull()

    private fun trim() {
        facts.entries.removeAll { it.value.expiresAt <= now() }
        var bytes = facts.entries.sumOf { (it.key.length + it.value.value.length) * 2 + 64 }
        val iterator = facts.entries.iterator()
        while ((facts.size > MAX_ENTRIES || bytes > MAX_MEMORY_BYTES) && iterator.hasNext()) {
            val entry = iterator.next()
            bytes -= (entry.key.length + entry.value.value.length) * 2 + 64
            iterator.remove()
        }
    }

    companion object {
        const val MAX_ENTRIES = 2_048
        private const val MAX_FIELD_CHARS = 4_096
        private const val MAX_MEMORY_BYTES = 2 * 1024 * 1024
        private const val MAX_DISK_BYTES = 4L * 1024 * 1024
    }
}
