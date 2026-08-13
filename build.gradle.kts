// Top-level build file where you can add configuration options common to all sub-projects/modules.
//
// Kotlin support is built into AGP 9+ — no separate org.jetbrains.kotlin.android plugin is
// applied or needed (see https://kotl.in/gradle/agp-built-in-kotlin). The Compose compiler
// plugin is still applied explicitly since it's maintained separately by JetBrains.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
