package com.miowser.app.adblock

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilterTokenSnapshotTest {
    @Test
    fun `colliding token buckets survive restore and subsequent growth`() {
        val keys = (0..200).map { packFilterToken("t" + it.toString().padStart(4, '0')) }
            .groupBy { tokenSlot(it, 15) }.values.first { it.size >= 6 }.take(6)
        val original = FilterTokenIndex()
        keys.forEachIndexed { index, key ->
            original.add(key, rule("banner$index*first"))
            original.add(key, rule("banner$index*second"))
        }
        val bytes = ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { output ->
                original.writeSnapshot(output) { output.writeUTF(it.raw) }
            }
        }.toByteArray()
        val restored = FilterTokenIndex()
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            restored.readSnapshot(input, keys.size * 2, 5, FilterMemoryGuard()) { rule(input.readUTF()) }
        }
        keys.forEach { assertEquals(original.get(it), restored.get(it)) }
        assertEquals(original.firstMatch { true }, restored.firstMatch { true })
        // Restored tables must also remain valid if a caller adds more rules.
        repeat(100) { index ->
            val key = packFilterToken("g" + index.toString().padStart(4, '0'))
            val next = rule("growth$index*test")
            restored.add(key, next)
            assertEquals(listOf(next), restored.get(key))
        }
        keys.forEach { assertEquals(original.get(it), restored.get(it)) }
    }

    @Test
    fun `invalid table capacity is rejected before allocating or reading rules`() {
        listOf(-1, 0, 17, 1 shl 30).forEach { capacity ->
            val bytes = ByteArrayOutputStream().also { bytes ->
                DataOutputStream(bytes).use { it.writeInt(capacity); it.writeInt(0) }
            }.toByteArray()
            val error = runCatching {
                DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                    FilterTokenIndex().readSnapshot(input, 1, 5, FilterMemoryGuard()) {
                        error("No rule should be read")
                    }
                }
            }.exceptionOrNull()
            assertTrue("Invalid capacity accepted: $capacity", error is IllegalArgumentException)
        }
    }

    private fun rule(raw: String): NetworkFilterRule =
        FilterListParser.parseNetworkRule(raw, FilterSource.SUBSCRIBED)
}
