// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Kotlin support is built into AGP 9+ — no separate org.jetbrains.kotlin.android plugin is
// applied or needed (see https://kotl.in/gradle/agp-built-in-kotlin). The Compose compiler
// plugin is still applied explicitly since it's maintained separately by JetBrains.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

subprojects {
    dependencyLocking {
        lockAllConfigurations()
    }
}

// Check only mechanical whitespace rules; avoid reformatting the existing source tree.
tasks.register("checkKotlinFormatting") {
    group = "verification"
    description = "Checks Kotlin files for tabs, trailing whitespace, and a final newline."
    val sources = listOf("app/src", "benchmark/src")
    doLast {
        val problems = sources.flatMap { source ->
            fileTree(source) { include("**/*.kt", "**/*.kts") }.files.mapNotNull { file ->
                val content = file.readText(Charsets.UTF_8)
                val issues = buildList {
                    if ('\t' in content) add("tab")
                    if (!content.endsWith("\n")) add("missing final newline")
                    if (content.lineSequence().any { it.trimEnd('\r').endsWith(' ') || it.trimEnd('\r').endsWith('\t') }) {
                        add("trailing whitespace")
                    }
                }
                if (issues.isEmpty()) null else "${file.relativeTo(rootDir)}: ${issues.joinToString()}"
            }
        }
        check(problems.isEmpty()) { problems.joinToString("\n") }
    }
}
