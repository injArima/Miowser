package com.miowser.app.player

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import com.miowser.app.library.LibraryCardId
import com.miowser.app.library.MediaLibrary
import com.miowser.app.persistence.Persistence
import com.miowser.app.settings.AppSettings
import java.security.MessageDigest

/** A playback bookmark, never a Network Library card. No title, folder or artwork is stored. */
internal data class LocalPlaybackProgress(val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
    fun encode(): String = "$positionMs:$durationMs:$updatedAt"

    companion object {
        fun decode(value: String?): LocalPlaybackProgress? {
            val parts = value?.takeIf { it.length <= 64 }?.split(':') ?: return null
            if (parts.size != 3) return null
            val numbers = parts.map { it.toLongOrNull()?.takeIf { n -> n >= 0L } ?: return null }
            return LocalPlaybackProgress(numbers[0], numbers[1], numbers[2])
        }
    }
}

internal object LocalPlaybackProgressStore {
    private const val PREFS = "local_playback_progress"
    private const val LIMIT = 1_000
    private var revision by mutableLongStateOf(0L)
    private var preferences: SharedPreferences? = null
    private val keys = object : LinkedHashMap<String, String>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > 512
    }
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        Persistence.onOwner { revision++ }
    }

    @Synchronized
    private fun preferences(context: Context): SharedPreferences = preferences ?: context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE).also {
            it.registerOnSharedPreferenceChangeListener(listener)
            preferences = it
        }

    fun load(context: Context, uri: String): LocalPlaybackProgress? {
        revision // Refresh visible Device/Downloads progress after a save or backup restore.
        if (uri.isBlank() || uri.length > 8192) return null
        return LocalPlaybackProgress.decode(runCatching { preferences(context).getString(key(uri), null) }.getOrNull())
    }

    fun save(context: Context, uri: String, positionMs: Long, durationMs: Long) {
        if (uri.isBlank() || uri.length > 8192) return
        val duration = durationMs.coerceAtLeast(0L)
        val position = positionMs.coerceAtLeast(0L).let { if (duration > 0) it.coerceAtMost(duration) else it }
        val value = LocalPlaybackProgress(position, duration, System.currentTimeMillis()).encode()
        val prefs = preferences(context)
        val itemKey = key(uri)
        Persistence.submitSnapshot("local-progress:$itemKey") {
            val all = prefs.all
            val obsolete = if (itemKey !in all && all.size >= LIMIT) {
                all.entries.sortedBy { LocalPlaybackProgress.decode(it.value as? String)?.updatedAt ?: 0 }
                    .take(all.size - LIMIT + 1).map { it.key }
            } else emptyList()
            prefs.edit().apply {
                obsolete.forEach(::remove)
                putString(itemKey, value)
            }.commit()
        }
    }

    @Synchronized
    private fun key(uri: String): String = keys.getOrPut(uri) {
        MessageDigest.getInstance("SHA-256").digest(uri.toByteArray(Charsets.UTF_8))
            .joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }
    }
}

internal fun PlaybackRequest.Local.resumePosition(context: Context): Long {
    if (!rememberProgress || !AppSettings.current.resumeSavedVideos) return 0L
    val saved = LocalPlaybackProgressStore.load(context, uri.toString())
    if (saved != null) return libraryResumePosition(saved.positionMs, saved.durationMs)
    // Read old saved progress for compatibility; subsequent saves use bookmarks exclusively.
    val legacy = MediaLibrary.entryForCard(LibraryCardId("LOCAL_FILE:$uri"))
    return if (legacy != null) libraryResumePosition(legacy.positionMs, legacy.durationMs) else startPositionMs
}
