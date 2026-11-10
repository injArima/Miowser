package com.miowser.app.player

import android.app.Instrumentation
import android.content.ContextWrapper
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(UnstableApi::class)
class EmbeddedSubtitlePlaybackTest {
    @Test fun localMkvPlaysUnflaggedSubtitlesAndCanSwitchToLegacySsa() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = object : ContextWrapper(instrumentation.targetContext) {
            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("embedded-subtitle-test-$name", mode)
        }
        val file = File.createTempFile("embedded-subtitles", ".mkv", context.cacheDir)
        val captions = LinkedBlockingQueue<List<String>>()
        var session: PlaybackSession? = null
        try {
            // A synthetic clip with SubRip and legacy S_TEXT/SSA tracks, both without default flags.
            instrumentation.context.assets.open("embedded-subtitles.mkv").use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
            instrumentation.onMain {
                val playback = PlaybackSession.create(context,
                    PlaybackRequest.Local(Uri.fromFile(file), "Embedded subtitles"), null, false)
                session = playback
                playback.player.addListener(object : Player.Listener {
                    override fun onCues(cueGroup: CueGroup) {
                        captions.offer(cueGroup.cues.mapNotNull { it.text?.toString() })
                    }
                })
            }
            val autoCaption = checkNotNull(captions.pollUntil { it.isNotEmpty() })
            assertTrue(autoCaption.any { it == "Embedded SubRip" || it == "Embedded SSA" })

            captions.clear()
            instrumentation.onMain {
                val player = checkNotNull(session).player
                val snapshot = buildPlayerTrackSnapshot(player.currentTracks)
                assertEquals(setOf("SubRip", "SSA"), snapshot.subtitles.map { it.label }.toSet())
                val ssa = snapshot.subtitles.single { it.label == "SSA" }
                val selection = PlayerTrackSelection(PlayerTrackMode.SPECIFIC, ssa.key)
                assertTrue(applyPlayerTrackSelection(player, snapshot, PlayerTrackKind.SUBTITLE, selection))
                player.seekTo(1_000)
            }
            assertTrue("Expected a cue from the selected SSA track",
                captions.pollUntil { "Embedded SSA" in it } != null)

            instrumentation.onMain {
                val player = checkNotNull(session).player
                val snapshot = buildPlayerTrackSnapshot(player.currentTracks)
                assertTrue(applyPlayerTrackSelection(player, snapshot, PlayerTrackKind.SUBTITLE,
                    PlayerTrackSelection(PlayerTrackMode.OFF)))
                assertTrue(C.TRACK_TYPE_TEXT in player.trackSelectionParameters.disabledTrackTypes)
            }
        } finally {
            instrumentation.onMain { session?.close() }
            file.delete()
        }
    }

    private fun <T> Instrumentation.onMain(block: () -> T): T {
        var result: Result<T>? = null
        runOnMainSync { result = runCatching(block) }
        return checkNotNull(result).getOrThrow()
    }

    private fun LinkedBlockingQueue<List<String>>.pollUntil(matches: (List<String>) -> Boolean): List<String>? {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
        while (true) {
            val captions = poll((deadline - System.nanoTime()).coerceAtLeast(0), TimeUnit.NANOSECONDS)
                ?: return null
            if (matches(captions)) return captions
        }
    }
}
