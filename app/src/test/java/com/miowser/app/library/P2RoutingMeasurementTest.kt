package com.miowser.app.library

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.lang.management.ManagementFactory

class P2RoutingMeasurementTest {
    @Test fun routingLargerLibrariesMatchesBaselineAndReportsCpuAndAllocations() {
        val folders = (0 until 24).map { i -> LibraryFolder("r$i", "Rule $i", titleRule =
            LibraryTitleRule(LibraryTitleSource.COMBINED, LibraryRuleOperator.ALL, listOf("Series $i", "episode"))) }
        val router = CompiledLibraryRouter(folders, null)
        val bean = ManagementFactory.getThreadMXBean() as com.sun.management.ThreadMXBean
        bean.isThreadAllocatedMemoryEnabled = true
        val thread = Thread.currentThread().id
        val output = mutableListOf("implementation\titems\tpasses\tcpu_ns\telapsed_ns\tallocated_bytes")
        for (size in listOf(500, 1000, 5000)) {
            val facts = List(size) { i -> LibraryRuleFacts("https://example.org/$i", "https://cdn.example.org/$i", "Series ${i % 24} Episode $i", "Page Series ${i % 24}") }
            fun old() = facts.map { baselineRouteLibraryVideo(folders, null, null, it) }
            fun candidate() = facts.map { router.route(null, it) }
            assertEquals(old(), candidate())
            repeat(2) { old(); candidate() }
            fun measure(label: String, run: () -> List<String?>) {
                val passes = 10_000 / size
                val bytes = bean.getThreadAllocatedBytes(thread); val cpu = bean.currentThreadCpuTime; val elapsed = System.nanoTime()
                repeat(passes) { assertEquals(size, run().size) }
                output += "$label\t$size\t$passes\t${bean.currentThreadCpuTime - cpu}\t${System.nanoTime() - elapsed}\t${bean.getThreadAllocatedBytes(thread) - bytes}"
            }
            repeat(6) { round ->
                if (round % 2 == 0) { measure("before", ::old); measure("after", ::candidate) }
                else { measure("after", ::candidate); measure("before", ::old) }
            }
        }
        File("../.local/optimization/p2-routing-measurements.tsv").apply { parentFile?.mkdirs() }.writeText(output.joinToString("\n"))
    }
}
