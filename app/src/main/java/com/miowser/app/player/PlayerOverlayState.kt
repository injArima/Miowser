package com.miowser.app.player

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal enum class PlayerOverlay(val title: String) { PLAYLIST("Playlist"), SUBTITLES("Subtitles") }

/** Retained per playback request. Old exits cannot remove a reopened or replacement panel. */
internal class PlayerOverlayState {
    var rendered by mutableStateOf<PlayerOverlay?>(null)
        private set
    var target by mutableStateOf<PlayerOverlay?>(null)
        private set
    var revision by mutableLongStateOf(0L)
        private set
    val isPresent: Boolean get() = rendered != null

    fun open(panel: PlayerOverlay) {
        ++revision
        target = panel
        if (rendered == null) rendered = panel
    }

    fun dismiss() {
        if (target != null) { ++revision; target = null }
    }

    /** True only when a current exit leaves the player with no modal present. */
    fun finishExit(panel: PlayerOverlay, exitRevision: Long): Boolean {
        if (rendered != panel || revision != exitRevision || target == panel) return false
        rendered = target
        return rendered == null
    }
}
