package com.miowser.app.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.media3.common.Player
import com.miowser.app.ui.theme.PlayerUiColors
import com.miowser.app.ui.theme.SubtitlePanelTokens

/** Compact controls for the subtitle header; no fullscreen gesture or overlay side effects. */
@Composable
internal fun SubtitleInspectionTransport(
    playing: Boolean,
    enabled: Boolean,
    canSeek: Boolean,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit
) {
    val colors = IconButtonDefaults.iconButtonColors(contentColor = PlayerUiColors.content,
        disabledContentColor = PlayerUiColors.disabledControl)
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onSeek(-SUBTITLE_INSPECTION_SEEK_MS) }, enabled = enabled && canSeek,
            colors = colors, modifier = Modifier.size(SubtitlePanelTokens.transportButtonSize)) {
            Icon(Icons.Rounded.Replay10, "Rewind 10 seconds", Modifier.size(SubtitlePanelTokens.transportIconSize))
        }
        IconButton(onClick = onPlayPause, enabled = enabled,
            colors = colors, modifier = Modifier.size(SubtitlePanelTokens.transportButtonSize)) {
            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause" else "Play", Modifier.size(SubtitlePanelTokens.transportIconSize))
        }
        IconButton(onClick = { onSeek(SUBTITLE_INSPECTION_SEEK_MS) }, enabled = enabled && canSeek,
            colors = colors, modifier = Modifier.size(SubtitlePanelTokens.transportButtonSize)) {
            Icon(Icons.Rounded.Forward10, "Forward 10 seconds", Modifier.size(SubtitlePanelTokens.transportIconSize))
        }
    }
}

/** Observe transport events only while the rich-player inspector is visible. */
@Composable
internal fun RichSubtitleInspectionTransport(player: Player) {
    var transport by remember(player) { mutableStateOf(subtitleTransportState(player)) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { transport = subtitleTransportState(player) }
        }
        player.addListener(listener)
        transport = subtitleTransportState(player)
        onDispose { player.removeListener(listener) }
    }
    SubtitleInspectionTransport(transport.playing, transport.enabled, transport.canSeek, onPlayPause = {
        if (player.playWhenReady) player.pause() else {
            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
            player.play()
        }
    }, onSeek = { delta ->
        player.seekTo(subtitleInspectionSeekTarget(player.currentPosition, delta, player.knownDurationMs()))
    })
}

private data class SubtitleTransportState(val playing: Boolean, val enabled: Boolean, val canSeek: Boolean)

private fun subtitleTransportState(player: Player) = SubtitleTransportState(
    player.playWhenReady && player.playbackState != Player.STATE_ENDED,
    player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
    player.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) && player.isCurrentMediaItemSeekable
)

private const val SUBTITLE_INSPECTION_SEEK_MS = 10_000L
