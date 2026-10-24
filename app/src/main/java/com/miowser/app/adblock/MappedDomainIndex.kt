package com.miowser.app.adblock

import android.util.AtomicFile
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/** Disposable, memory-mapped candidate index. A native miss skips domain buckets; a hit is
 * always verified by the existing matcher, including options, exceptions and rule precedence. */
internal class MappedDomainIndex private constructor(private val bytes: MappedByteBuffer, private val bits: Int) {
    fun candidateMask(host: String): Int? {
        if (host.any { it.code > 127 || it in 'A'..'Z' }) return null
        // Most requests never cross JNI. Reject all suffixes directly from the mapped Bloom bits.
        var start = 0
        while (true) {
            if (maybePresent(host, start)) return NativeDomainLookup.match(bytes, host).takeIf { it >= 0 }
            val dot = host.indexOf('.', start)
            if (dot < 0) return 0
            start = dot + 1
        }
    }

    private fun maybePresent(host: String, start: Int): Boolean {
        var h1 = 0x811c9dc5.toInt()
        var h2 = h1 xor 0x9e3779b9.toInt()
        for (index in start until host.length) {
            val value = host[index].code
            h1 = (h1 xor value) * 0x01000193
            h2 = (h2 xor value) * 0x01000193
        }
        h2 = h2 or 1
        repeat(7) { i ->
            val bit = (h1 + i * h2) and (bits - 1)
            if (bytes.get(HEADER_BYTES + (bit ushr 3)).toInt() and (1 shl (bit and 7)) == 0) return false
        }
        return true
    }

    companion object {
        private const val NAME = "compiled-domains-v1.bin"
        private const val MAGIC = "MWDOM001"
        private const val VERSION = 1
        private const val HEADER_BYTES = 88

        fun openOrBuild(directory: File, fingerprint: String, engine: FilterEngine): MappedDomainIndex? {
            if (!NativeDomainLookup.available || fingerprint.length != 64) return null
            val target = File(directory, NAME)
            open(target, fingerprint)?.let { return it }
            runCatching { write(target, fingerprint, engine.anchoredDomainMasks()) }
            return open(target, fingerprint)
        }

        private fun open(file: File, fingerprint: String): MappedDomainIndex? = runCatching {
            if (!file.isFile || file.length() < HEADER_BYTES || file.length() > Int.MAX_VALUE) return null
            file.inputStream().channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
                mapped.order(ByteOrder.BIG_ENDIAN)
                val magic = ByteArray(8).also(mapped::get)
                if (!magic.contentEquals(MAGIC.toByteArray(Charsets.US_ASCII))) return null
                if (mapped.getInt(8) != VERSION) return null
                val bits = mapped.getInt(12)
                val count = mapped.getInt(16)
                val recordsOffset = mapped.getInt(20)
                val saved = ByteArray(64).also { mapped.position(24); mapped.get(it) }
                if (!saved.contentEquals(fingerprint.toByteArray(Charsets.US_ASCII))) return null
                if (bits < 1024 || bits and (bits - 1) != 0 || count < 0) return null
                val expected = HEADER_BYTES.toLong() + bits / 8L + (count.toLong() + 1) * 4L
                if (expected != recordsOffset.toLong() || expected > mapped.capacity()) return null
                if (mapped.getInt(recordsOffset - 4) < 0 ||
                    recordsOffset.toLong() + mapped.getInt(recordsOffset - 4) > mapped.capacity()) return null
                MappedDomainIndex(mapped, bits)
            }
        }.getOrNull()

        private fun write(file: File, fingerprint: String, masks: Map<String, Int>) {
            var bits = 1024L
            while (bits < masks.size.toLong() * 12L) bits *= 2
            require(bits <= Int.MAX_VALUE) { "Domain index is too large for one memory mapping" }
            val memory = FilterMemoryGuard()
            // Sort references, rather than allocating another tree node for every domain.
            // Account for the key array, sorting workspace, offsets and Bloom array upfront.
            memory.check(masks.size.toLong() * 20 + bits / 8 + 128)
            val sorted = masks.keys.toTypedArray().apply { sort() }
            val bitCount = bits.toInt()
            val bloom = ByteArray(bitCount / 8)
            val offsets = IntArray(sorted.size + 1)
            var total = 0L
            var index = 0
            for (domain in sorted) {
                if (index % 256 == 0) memory.check()
                require(domain.length in 1..253 && domain.all { it.code in 33..126 })
                addBloom(bloom, bitCount, domain)
                total += domain.length + 2L
                require(total <= Int.MAX_VALUE) { "Domain records are too large for one memory mapping" }
                offsets[++index] = total.toInt()
            }
            val recordsOffset = HEADER_BYTES.toLong() + bloom.size + offsets.size.toLong() * 4
            require(recordsOffset + total <= Int.MAX_VALUE)
            val atomic = AtomicFile(file)
            file.parentFile?.mkdirs()
            var stream: java.io.FileOutputStream? = null
            try {
                stream = atomic.startWrite()
                val output = java.io.DataOutputStream(java.io.BufferedOutputStream(stream))
                output.writeBytes(MAGIC)
                output.writeInt(VERSION)
                output.writeInt(bitCount)
                output.writeInt(sorted.size)
                output.writeInt(recordsOffset.toInt())
                output.writeBytes(fingerprint)
                output.write(bloom)
                offsets.forEach(output::writeInt)
                for ((position, domain) in sorted.withIndex()) {
                    if (position % 256 == 0) memory.check()
                    output.writeByte(domain.length)
                    output.writeByte(masks.getValue(domain))
                    output.writeBytes(domain)
                }
                output.flush()
                atomic.finishWrite(stream)
            } catch (error: Exception) {
                stream?.let(atomic::failWrite)
                throw error
            }
        }

        private fun addBloom(bytes: ByteArray, bits: Int, domain: String) {
            var h1 = 0x811c9dc5.toInt()
            var h2 = h1 xor 0x9e3779b9.toInt()
            for (char in domain) {
                val value = char.code
                h1 = (h1 xor value) * 0x01000193
                h2 = (h2 xor value) * 0x01000193
            }
            h2 = h2 or 1
            repeat(7) { i ->
                val bit = (h1 + i * h2) and (bits - 1)
                val byte = bit ushr 3
                bytes[byte] = (bytes[byte].toInt() or (1 shl (bit and 7))).toByte()
            }
        }
    }
}

/** Loading failures never change filter decisions; the Kotlin index remains the fallback. */
internal object NativeDomainLookup {
    val available: Boolean by lazy { runCatching { System.loadLibrary("miowser_adblock") }.isSuccess }

    fun match(bytes: ByteBuffer, host: String): Int =
        if (available) runCatching { nativeMatch(bytes, host) }.getOrDefault(-1) else -1

    private external fun nativeMatch(bytes: ByteBuffer, host: String): Int
}
