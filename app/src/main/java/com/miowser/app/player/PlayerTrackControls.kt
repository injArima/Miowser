package com.miowser.app.player

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

internal enum class PlayerTrackKind(val media3Type: Int, val title: String) {
    SUBTITLE(C.TRACK_TYPE_TEXT, "Subtitles"),
    AUDIO(C.TRACK_TYPE_AUDIO, "Audio"),
    VIDEO(C.TRACK_TYPE_VIDEO, "Sources")
}

internal enum class PlayerTrackMode { AUTO, OFF, SPECIFIC }

internal data class PlayerTrackSelection(
    val mode: PlayerTrackMode = PlayerTrackMode.AUTO,
    val optionKey: String? = null
)

internal data class PlayerTrackPreferences(
    val subtitles: PlayerTrackSelection = PlayerTrackSelection(),
    val audio: PlayerTrackSelection = PlayerTrackSelection(),
    val video: PlayerTrackSelection = PlayerTrackSelection()
) {
    fun selection(kind: PlayerTrackKind): PlayerTrackSelection = when (kind) {
        PlayerTrackKind.SUBTITLE -> subtitles
        PlayerTrackKind.AUDIO -> audio
        PlayerTrackKind.VIDEO -> video
    }

    fun withSelection(kind: PlayerTrackKind, selection: PlayerTrackSelection): PlayerTrackPreferences = when (kind) {
        PlayerTrackKind.SUBTITLE -> copy(subtitles = selection)
        PlayerTrackKind.AUDIO -> copy(audio = selection)
        PlayerTrackKind.VIDEO -> copy(video = selection)
    }
}

internal data class PlayerTrackOption(
    val key: String,
    val kind: PlayerTrackKind,
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val detail: String
)

internal data class PlayerTrackSnapshot(
    val subtitles: List<PlayerTrackOption> = emptyList(),
    val audio: List<PlayerTrackOption> = emptyList(),
    val video: List<PlayerTrackOption> = emptyList()
) {
    fun options(kind: PlayerTrackKind): List<PlayerTrackOption> = when (kind) {
        PlayerTrackKind.SUBTITLE -> subtitles
        PlayerTrackKind.AUDIO -> audio
        PlayerTrackKind.VIDEO -> video
    }
}

internal fun playerTrackButtonLabel(
    kind: PlayerTrackKind,
    selection: PlayerTrackSelection,
    snapshot: PlayerTrackSnapshot
): String = when (selection.mode) {
    PlayerTrackMode.OFF -> "Off"
    PlayerTrackMode.SPECIFIC -> snapshot.options(kind)
        .firstOrNull { it.key == selection.optionKey }?.label
        ?: when (kind) {
            PlayerTrackKind.SUBTITLE -> "Subs"
            PlayerTrackKind.AUDIO -> "Audio"
            PlayerTrackKind.VIDEO -> "Sources"
        }
    PlayerTrackMode.AUTO -> when (kind) {
        PlayerTrackKind.SUBTITLE -> "Subs"
        PlayerTrackKind.AUDIO -> "Audio"
        PlayerTrackKind.VIDEO -> "Sources"
    }
}

internal fun buildPlayerTrackSnapshot(tracks: Tracks): PlayerTrackSnapshot {
    val subtitles = mutableListOf<PlayerTrackOption>()
    val audio = mutableListOf<PlayerTrackOption>()
    val video = mutableListOf<PlayerTrackOption>()
    tracks.groups.forEachIndexed { groupIndex, group ->
        val kind = when (group.type) {
            C.TRACK_TYPE_TEXT -> PlayerTrackKind.SUBTITLE
            C.TRACK_TYPE_AUDIO -> PlayerTrackKind.AUDIO
            C.TRACK_TYPE_VIDEO -> PlayerTrackKind.VIDEO
            else -> null
        } ?: return@forEachIndexed
        repeat(group.length) { trackIndex ->
            if (!group.isTrackSupported(trackIndex)) return@repeat
            val format = group.getTrackFormat(trackIndex)
            val (label, detail) = describeTrack(kind, format, trackIndex)
            val option = PlayerTrackOption(
                key = stableTrackKey(kind, group.mediaTrackGroup.id, format, trackIndex),
                kind = kind,
                groupIndex = groupIndex,
                trackIndex = trackIndex,
                label = label,
                detail = detail
            )
            when (kind) {
                PlayerTrackKind.SUBTITLE -> subtitles += option
                PlayerTrackKind.AUDIO -> audio += option
                PlayerTrackKind.VIDEO -> video += option
            }
        }
    }
    return PlayerTrackSnapshot(subtitles, audio, video)
}

/** Applies an override without rebuilding the MediaItem, so playback continues in place. */
internal fun applyPlayerTrackSelection(
    player: Player,
    snapshot: PlayerTrackSnapshot,
    kind: PlayerTrackKind,
    selection: PlayerTrackSelection
): Boolean {
    val builder = player.trackSelectionParameters.buildUpon()
        .clearOverridesOfType(kind.media3Type)
        .setTrackTypeDisabled(kind.media3Type, kind == PlayerTrackKind.SUBTITLE && selection.mode == PlayerTrackMode.OFF)
    if (selection.mode == PlayerTrackMode.SPECIFIC) {
        val option = snapshot.options(kind).firstOrNull { it.key == selection.optionKey } ?: return false
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return false
        if (group.type != kind.media3Type || !group.isTrackSupported(option.trackIndex)) return false
        builder.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
    }
    player.trackSelectionParameters = builder.build()
    return true
}

internal fun describeTrack(
    kind: PlayerTrackKind,
    format: Format,
    ordinal: Int
): Pair<String, String> {
    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }?.let(::displayLanguage)
    val declaredLabel = format.label?.takeIf(String::isNotBlank)
    return when (kind) {
        PlayerTrackKind.SUBTITLE -> {
            val label = declaredLabel ?: language ?: "Subtitle ${ordinal + 1}"
            val detail = listOfNotNull(
                language?.takeUnless { it.equals(label, ignoreCase = true) },
                "Forced".takeIf { format.selectionFlags and C.SELECTION_FLAG_FORCED != 0 },
                format.sampleMimeType?.let(::shortCodecName)
            ).joinToString(" • ")
            label to detail
        }
        PlayerTrackKind.AUDIO -> {
            val label = declaredLabel ?: language ?: "Audio ${ordinal + 1}"
            val channels = when (format.channelCount) {
                1 -> "Mono"
                2 -> "Stereo"
                in 3..Int.MAX_VALUE -> "${format.channelCount} channels"
                else -> null
            }
            val detail = listOfNotNull(
                language?.takeUnless { it.equals(label, ignoreCase = true) },
                channels,
                trackBitrateLabel(effectiveBitrate(format)),
                format.sampleMimeType?.let(::shortCodecName)
            ).joinToString(" • ")
            label to detail
        }
        PlayerTrackKind.VIDEO -> {
            val resolution = when {
                format.height > 0 -> "${format.height}p"
                format.width > 0 -> "${format.width}px"
                else -> null
            }
            val label = resolution ?: declaredLabel ?: "Quality ${ordinal + 1}"
            val dimensions = if (format.width > 0 && format.height > 0) "${format.width}×${format.height}" else null
            val detail = listOfNotNull(
                dimensions?.takeUnless { resolution == null },
                trackBitrateLabel(effectiveBitrate(format)),
                format.codecs?.takeIf(String::isNotBlank) ?: format.sampleMimeType?.let(::shortCodecName)
            ).joinToString(" • ")
            label to detail
        }
    }
}

internal fun trackBitrateLabel(bitrate: Int): String? = when {
    bitrate <= 0 -> null
    bitrate >= 1_000_000 -> "%.1f Mbps".format(Locale.US, bitrate / 1_000_000.0).replace(".0 Mbps", " Mbps")
    else -> "${bitrate / 1_000} kbps"
}

private fun effectiveBitrate(format: Format): Int = when {
    format.averageBitrate > 0 -> format.averageBitrate
    format.peakBitrate > 0 -> format.peakBitrate
    else -> Format.NO_VALUE
}

private fun shortCodecName(mimeType: String): String = mimeType.substringAfter('/').uppercase(Locale.US)

private fun displayLanguage(tag: String): String = Locale.forLanguageTag(tag).displayLanguage
    .takeIf(String::isNotBlank) ?: tag

private fun stableTrackKey(kind: PlayerTrackKind, groupId: String, format: Format, trackIndex: Int): String =
    sha256(
        listOf(
            kind.name, groupId, format.id.orEmpty(), format.language.orEmpty(), format.label.orEmpty(),
            format.width.toString(), format.height.toString(), effectiveBitrate(format).toString(),
            format.sampleMimeType.orEmpty(), format.codecs.orEmpty(), trackIndex.toString()
        ).joinToString("\u0000")
    )

internal object PlayerTrackPreferenceCodec {
    fun encode(preferences: PlayerTrackPreferences): String = listOf(
        preferences.subtitles.mode.name,
        encodeText(preferences.subtitles.optionKey),
        preferences.audio.mode.name,
        encodeText(preferences.audio.optionKey),
        preferences.video.mode.name,
        encodeText(preferences.video.optionKey)
    ).joinToString("\t")

    fun decode(raw: String): PlayerTrackPreferences? = runCatching {
        val parts = raw.split('\t')
        require(parts.size == 6)
        PlayerTrackPreferences(
            subtitles = PlayerTrackSelection(PlayerTrackMode.valueOf(parts[0]), decodeText(parts[1])),
            audio = PlayerTrackSelection(PlayerTrackMode.valueOf(parts[2]), decodeText(parts[3])),
            video = PlayerTrackSelection(PlayerTrackMode.valueOf(parts[4]), decodeText(parts[5]))
        ).also { preferences ->
            preferences.let { listOf(it.subtitles, it.audio, it.video) }.forEach { selection ->
                require(selection.mode != PlayerTrackMode.SPECIFIC || !selection.optionKey.isNullOrBlank())
            }
        }
    }.getOrNull()

    private fun encodeText(value: String?): String = value?.toByteArray(Charsets.UTF_8)?.let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }.orEmpty()

    private fun decodeText(value: String): String? = value.takeIf(String::isNotBlank)?.let {
        String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
    }
}

internal object PlayerTrackPreferenceStore {
    private const val PREFS_NAME = "player_track_preferences"
    private const val GLOBAL_SUBTITLE_MODE = "global_subtitle_mode"

    fun load(context: Context, identityKey: String): PlayerTrackPreferences {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val item = prefs.getString(itemKey(identityKey), null)?.let(PlayerTrackPreferenceCodec::decode)
        if (item != null) return item
        val globalMode = prefs.getString(GLOBAL_SUBTITLE_MODE, null)
            ?.let { runCatching { PlayerTrackMode.valueOf(it) }.getOrNull() }
            ?.takeIf { it == PlayerTrackMode.AUTO || it == PlayerTrackMode.OFF }
            ?: PlayerTrackMode.AUTO
        return PlayerTrackPreferences(subtitles = PlayerTrackSelection(globalMode))
    }

    fun save(context: Context, identityKey: String, preferences: PlayerTrackPreferences) {
        val editor = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putString(itemKey(identityKey), PlayerTrackPreferenceCodec.encode(preferences))
        if (preferences.subtitles.mode != PlayerTrackMode.SPECIFIC) {
            editor.putString(GLOBAL_SUBTITLE_MODE, preferences.subtitles.mode.name)
        }
        editor.apply()
    }

    private fun itemKey(identityKey: String): String = "item_${sha256(identityKey)}"
}

private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray(Charsets.UTF_8))
    .joinToString("") { "%02x".format(it) }
