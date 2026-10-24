package com.miowser.app.adblock

import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.zip.CRC32
import java.util.zip.CheckedInputStream
import java.util.zip.CheckedOutputStream

/** Compact, disposable cache of the installed matcher. A bad cache is rebuilt from saved lists. */
internal object CompiledFilterSnapshot {
    const val FILE_NAME = "compiled-v10.bin"
    private const val MAGIC = 0x4d574631 // MWF1
    private const val VERSION = 4
    private val emptyOptions = FilterOptions()

    data class Loaded(
        val network: FilterEngine,
        val domains: MutableMap<String, MutableList<CosmeticRule>>,
        val generic: MutableList<CosmeticRule>,
        val skippedSources: Int,
        val origins: FilterRuleOrigins
    )

    fun write(output: DataOutputStream, fingerprint: String, network: FilterEngine,
              domains: Map<String, List<CosmeticRule>>, generic: List<CosmeticRule>,
              skippedSources: Int, origins: FilterRuleOrigins = FilterRuleOrigins()) {
        val checksum = CRC32()
        val payload = DataOutputStream(CheckedOutputStream(output, checksum))
        writePayload(payload, fingerprint, network, domains, generic, skippedSources, origins)
        payload.flush()
        output.writeLong(checksum.value)
    }

    private fun writePayload(output: DataOutputStream, fingerprint: String, network: FilterEngine,
                             domains: Map<String, List<CosmeticRule>>, generic: List<CosmeticRule>,
                             skippedSources: Int, origins: FilterRuleOrigins) {
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeUTF(fingerprint)
        output.writeInt(skippedSources)
        origins.write(output)
        var networkCount = 0
        network.forEachRule { networkCount++ }
        output.writeInt(networkCount)
        network.writeIndexedSnapshot(output) { rule -> writeNetworkRule(output, rule) }
        output.writeInt(generic.size)
        generic.forEach { writeCosmeticRule(output, it) }
        output.writeInt(domains.size)
        domains.forEach { (domain, rules) ->
            output.writeUTF(domain)
            output.writeInt(rules.size)
            rules.forEach { writeCosmeticRule(output, it) }
        }
    }

    fun read(input: DataInputStream, fingerprint: String): Loaded? {
        val checksum = CRC32()
        val payload = DataInputStream(CheckedInputStream(input, checksum))
        val loaded = readPayload(payload, fingerprint) ?: return null
        check(input.readLong() == checksum.value) { "Filter snapshot checksum mismatch" }
        check(input.read() == -1) { "Trailing bytes in filter snapshot" }
        return loaded
    }

    private fun readPayload(input: DataInputStream, fingerprint: String): Loaded? {
        val memory = FilterMemoryGuard()
        memory.check()
        if (input.readInt() != MAGIC || input.readInt() != VERSION || input.readUTF() != fingerprint) return null
        val skippedSources = input.readInt()
        val origins = FilterRuleOrigins.read(input, memory)
        val network = FilterEngine()
        val networkCount = nonnegative(input.readInt())
        network.readIndexedSnapshot(input, networkCount) {
            readNetworkRule(input).also { check(origins.containsGroup(it.originGroup)) { "Invalid rule source group" } }
        }
        val generic = ArrayList<CosmeticRule>()
        repeat(nonnegative(input.readInt())) { index ->
            if (index % 256 == 0) memory.check()
            generic.add(readCosmeticRule(input, null))
        }
        val domains = HashMap<String, MutableList<CosmeticRule>>()
        repeat(nonnegative(input.readInt())) {
            memory.check()
            val domain = input.readUTF()
            val rules = ArrayList<CosmeticRule>()
            repeat(nonnegative(input.readInt())) { index ->
                if (index % 256 == 0) memory.check()
                rules.add(readCosmeticRule(input, domain))
            }
            check(domains.put(domain, rules) == null) { "Duplicate cosmetic domain in snapshot" }
        }
        return Loaded(network, domains, generic, skippedSources, origins)
    }

    private fun writeNetworkRule(output: DataOutputStream, rule: NetworkFilterRule) {
        output.writeUTF(rule.raw)
        output.writeBoolean(rule.isException)
        output.writeBoolean(rule.source == FilterSource.MANUAL)
        output.writeInt(rule.originGroup)
        writeNullable(output, rule.anchoredDomain)
        writeNullable(output, rule.regexPattern)
        val options = rule.options
        output.writeInt(typeMask(options.includedResourceTypes))
        output.writeInt(typeMask(options.excludedResourceTypes))
        output.writeByte(when (options.thirdPartyOnly) { null -> 0; true -> 1; false -> 2 })
        writeStrings(output, options.includedDomains)
        writeStrings(output, options.excludedDomains)
    }

    private fun readNetworkRule(input: DataInputStream): NetworkFilterRule {
        val raw = input.readUTF()
        val isException = input.readBoolean()
        val source = if (input.readBoolean()) FilterSource.MANUAL else FilterSource.SUBSCRIBED
        val originGroup = input.readInt()
        val domain = readNullable(input)
        val regex = readNullable(input)
        val included = readTypes(input.readInt())
        val excluded = readTypes(input.readInt())
        val thirdParty = when (input.readUnsignedByte()) {
            0 -> null
            1 -> true
            2 -> false
            else -> error("Invalid third-party option in snapshot")
        }
        val includedDomains = readStrings(input)
        val excludedDomains = readStrings(input)
        val options = if (included.isEmpty() && excluded.isEmpty() && thirdParty == null &&
            includedDomains.isEmpty() && excludedDomains.isEmpty()) emptyOptions
            else FilterOptions(included, excluded, thirdParty, includedDomains, excludedDomains)
        check(domain != null || regex != null) { "Rule has neither domain nor pattern" }
        return NetworkFilterRule(raw, isException, domain, regex, options, source).also { it.originGroup = originGroup }
    }

    private fun writeCosmeticRule(output: DataOutputStream, rule: CosmeticRule) {
        output.writeUTF(rule.selector)
        output.writeBoolean(rule.isException)
        output.writeBoolean(rule.source == FilterSource.MANUAL)
        writeStrings(output, rule.excludedDomains)
    }

    private fun readCosmeticRule(input: DataInputStream, domain: String?): CosmeticRule = CosmeticRule(
        domain = domain,
        selector = input.readUTF(),
        isException = input.readBoolean(),
        source = if (input.readBoolean()) FilterSource.MANUAL else FilterSource.SUBSCRIBED,
        excludedDomains = readStringList(input)
    )

    private fun typeMask(types: Set<ResourceType>): Int = types.fold(0) { mask, type -> mask or (1 shl type.ordinal) }

    private fun readTypes(mask: Int): Set<ResourceType> {
        val allowed = (1 shl ResourceType.entries.size) - 1
        check(mask and allowed.inv() == 0) { "Invalid resource types in snapshot" }
        if (mask == 0) return emptySet()
        return ResourceType.entries.filterTo(HashSet()) { mask and (1 shl it.ordinal) != 0 }
    }

    private fun writeNullable(output: DataOutputStream, text: String?) {
        output.writeBoolean(text != null)
        if (text != null) output.writeUTF(text)
    }

    private fun readNullable(input: DataInputStream): String? = if (input.readBoolean()) input.readUTF() else null

    private fun writeStrings(output: DataOutputStream, strings: Collection<String>) {
        output.writeInt(strings.size)
        strings.forEach(output::writeUTF)
    }

    private fun readStrings(input: DataInputStream): Set<String> {
        val count = nonnegative(input.readInt())
        if (count == 0) return emptySet()
        return HashSet<String>().also { values -> repeat(count) { values.add(input.readUTF()) } }
    }

    private fun readStringList(input: DataInputStream): List<String> {
        val count = nonnegative(input.readInt())
        if (count == 0) return emptyList()
        return ArrayList<String>().also { values -> repeat(count) { values.add(input.readUTF()) } }
    }

    private fun nonnegative(value: Int): Int {
        require(value >= 0) { "Invalid count in filter snapshot" }
        return value
    }
}
