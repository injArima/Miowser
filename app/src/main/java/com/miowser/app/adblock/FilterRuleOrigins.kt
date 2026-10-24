package com.miowser.app.adblock

import java.io.DataInputStream
import java.io.DataOutputStream

data class FilterRuleSource(val id: String, val name: String, val url: String? = null)

data class BlockedPageInfo(val url: String, val rule: String, val sources: List<FilterRuleSource>)

/** Shared source groups; each network rule retains one integer, never a list of names/URLs. */
internal class FilterRuleOrigins private constructor(
    private val sources: MutableList<FilterRuleSource>,
    private val groups: MutableList<IntArray>,
    private var sourceIds: MutableMap<String, Int>?,
    private var unions: MutableMap<Long, Int>?
) {
    constructor() : this(mutableListOf(), mutableListOf(IntArray(0)), HashMap(), HashMap())

    fun register(source: FilterRuleSource): Int {
        val ids = checkNotNull(sourceIds) { "Published rule sources are immutable" }
        ids[source.id]?.let { return it }
        val index = sources.size
        sources.add(source.copy(name = source.name.take(512), url = source.url?.take(8_192)))
        groups.add(intArrayOf(index))
        return groups.lastIndex.also { ids[source.id] = it }
    }

    fun merge(existing: Int, sourceGroup: Int): Int {
        if (existing == 0) return sourceGroup
        val sourceIndex = groups[sourceGroup].single()
        val previous = groups[existing]
        if (previous.binarySearch(sourceIndex) >= 0) return existing
        val cache = checkNotNull(unions) { "Published rule sources are immutable" }
        val key = (existing.toLong() shl 32) or sourceIndex.toLong()
        return cache.getOrPut(key) {
            if (groups.size % 256 == 0) FilterMemoryGuard().check()
            groups.add((previous + sourceIndex).apply { sort() })
            groups.lastIndex
        }
    }

    fun freeze() { sourceIds = null; unions = null }

    fun containsGroup(id: Int): Boolean = id in groups.indices

    fun sourcesFor(groupIds: Set<Int>): List<FilterRuleSource> {
        val included = BooleanArray(sources.size)
        groupIds.forEach { id -> groups.getOrNull(id)?.forEach { included[it] = true } }
        return sources.filterIndexed { index, _ -> included[index] }
    }

    fun write(output: DataOutputStream) {
        output.writeInt(sources.size)
        sources.forEach { source ->
            output.writeUTF(source.id)
            output.writeUTF(source.name)
            output.writeBoolean(source.url != null)
            source.url?.let(output::writeUTF)
        }
        output.writeInt(groups.size)
        groups.forEach { indices ->
            output.writeInt(indices.size)
            indices.forEach(output::writeInt)
        }
    }

    companion object {
        fun read(input: DataInputStream, memory: FilterMemoryGuard): FilterRuleOrigins {
            val sourceCount = input.readInt()
            require(sourceCount >= 0) { "Invalid rule source count" }
            memory.check(sourceCount.toLong() * 64 + 64)
            val sources = ArrayList<FilterRuleSource>(sourceCount)
            val ids = HashSet<String>()
            repeat(sourceCount) {
                memory.check()
                val id = input.readUTF()
                check(id.isNotEmpty() && ids.add(id)) { "Duplicate or empty rule source" }
                val name = input.readUTF()
                val url = if (input.readBoolean()) input.readUTF() else null
                require(name.length <= 512 && (url == null || url.length <= 8_192)) { "Invalid source metadata" }
                sources.add(FilterRuleSource(id, name, url))
            }
            val groupCount = input.readInt()
            require(groupCount > 0) { "Invalid rule source group count" }
            memory.check(groupCount.toLong() * 32 + 64)
            val groups = ArrayList<IntArray>(groupCount)
            repeat(groupCount) { group ->
                if (group % 256 == 0) memory.check()
                val count = input.readInt()
                require(count in 0..sourceCount && (group == 0) == (count == 0)) { "Invalid source group size" }
                memory.check(count.toLong() * 4 + 32)
                var previous = -1
                groups.add(IntArray(count) {
                    input.readInt().also { index ->
                        require(index in 0 until sourceCount && index > previous) { "Invalid source reference" }
                        previous = index
                    }
                })
            }
            return FilterRuleOrigins(sources, groups, null, null)
        }
    }
}

/** Reuses the builder's deduplication map to attribute duplicate rules without storing them twice. */
internal class FilterRuleAccumulator(private val network: FilterEngine, private val origins: FilterRuleOrigins) {
    private var manual: HashMap<String, NetworkFilterRule?>? = HashMap()
    private var subscribed: HashMap<String, NetworkFilterRule?>? = HashMap()

    init { network.forEachRule { seen(it.source)[it.raw] = it } }

    private fun seen(source: FilterSource) = checkNotNull(if (source == FilterSource.MANUAL) manual else subscribed)

    fun release() { manual = null; subscribed = null }

    fun accept(line: String, source: FilterSource, sourceGroup: Int): Boolean {
        val seen = seen(source)
        if (seen.containsKey(line)) {
            seen[line]?.let { it.originGroup = origins.merge(it.originGroup, sourceGroup) }
            return false
        }
        seen[line] = null // Cosmetic/unsupported lines still participate in the existing dedupe.
        return true
    }

    fun add(rule: NetworkFilterRule, sourceGroup: Int) {
        rule.originGroup = sourceGroup
        seen(rule.source)[rule.raw] = rule
        network.addRule(rule)
    }
}
