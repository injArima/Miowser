package com.miowser.app.settings

/** Experience changes visibility, never the saved data or individual feature preferences. */
enum class AppExperience(val label: String) {
    BROWSER_ONLY("Browser only"), PLAYER_ONLY("Player only"), BOTH("Both")
}
enum class ExperiencePriority(val label: String) {
    BROWSER_FIRST("Browser first"), PLAYER_FIRST("Player first")
}
enum class ExperienceStep { INTRO, PRIORITY, FEATURES, HISTORY, REVIEW }

fun ExperienceStep.next(mode: AppExperience): ExperienceStep = when (this) {
    ExperienceStep.INTRO -> ExperienceStep.PRIORITY
    ExperienceStep.PRIORITY -> ExperienceStep.FEATURES
    ExperienceStep.FEATURES -> if (mode == AppExperience.PLAYER_ONLY) ExperienceStep.REVIEW else ExperienceStep.HISTORY
    ExperienceStep.HISTORY, ExperienceStep.REVIEW -> ExperienceStep.REVIEW
}
fun ExperienceStep.previous(mode: AppExperience): ExperienceStep? = when (this) {
    ExperienceStep.INTRO -> null
    ExperienceStep.PRIORITY -> ExperienceStep.INTRO
    ExperienceStep.FEATURES -> ExperienceStep.PRIORITY
    ExperienceStep.HISTORY -> ExperienceStep.FEATURES
    ExperienceStep.REVIEW -> if (mode == AppExperience.PLAYER_ONLY) ExperienceStep.FEATURES else ExperienceStep.HISTORY
}
fun Settings.withExperience(mode: AppExperience, priority: ExperiencePriority, history: LibraryBehavior): Settings = copy(
    experience = mode, experiencePriority = priority, experienceCustomized = true,
    // A temporary device-only experience must not overwrite the user's online catalog preference.
    libraryBehavior = if (mode == AppExperience.PLAYER_ONLY) libraryBehavior else history
)
val Settings.experienceTitle: String get() = when {
    !experienceCustomized -> "Miowser, your way"
    experience == AppExperience.PLAYER_ONLY -> "Your device-player experience"
    experience == AppExperience.BOTH -> "Your complete Miowser experience"
    watchCatalogAvailable -> "Browser with a watch catalog"
    else -> "Your browser experience"
}
val Settings.experienceSummary: String get() = when (experience) {
    AppExperience.BROWSER_ONLY -> "Start in the browser, with Rich player, WebView player, and subtitle tools alongside. Device library is hidden."
    AppExperience.PLAYER_ONLY -> "Device videos and downloads, with playback and subtitle tools. Browser and online watch catalog are hidden."
    AppExperience.BOTH -> "Browser, device videos, and downloads together. Start with ${if (startsInBrowser) "the browser" else "Device videos"}."
} + when {
    !browserAvailable -> " Local playback progress stays available."
    libraryBehavior == LibraryBehavior.AUTOMATIC -> " Online videos are saved to your watch catalog when played."
    libraryBehavior == LibraryBehavior.MANUAL_ONLY -> " Your watch catalog contains only videos you choose to save."
    else -> " Your watch catalog is hidden; existing records are kept for later."
}
