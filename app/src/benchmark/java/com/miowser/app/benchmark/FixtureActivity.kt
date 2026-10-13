package com.miowser.app.benchmark

import android.app.Activity
import android.os.Bundle
import android.widget.TextView
import com.miowser.app.browser.SavedTab
import com.miowser.app.browser.TabSessionCodec
import com.miowser.app.downloader.*
import com.miowser.app.history.HistoryCodec
import com.miowser.app.history.HistoryEntry
import com.miowser.app.library.*

/** Runs in its own process; tests kill the main process before replacing persisted fixtures. */
class FixtureActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        check(packageName == "com.miowser.app.benchmark")
        val tabs = intent.getIntExtra("tabs", 10).also { require(it in listOf(1, 10, 30)) }
        val downloads = intent.getIntExtra("downloads", 200).also { require(it in listOf(200, 1000)) }
        val librarySize = intent.getIntExtra("library", 500).also { require(it in listOf(500, 1000, 5000)) }
        val server = intent.getStringExtra("fixture_server")?.trimEnd('/')
        val active = intent.getBooleanExtra("active_transfers", false).also { require(!it || server != null) }
        listOf("hub_settings", "player_track_preferences", "player_subtitle_style", "player_display_preferences")
            .forEach { check(getSharedPreferences(it, MODE_PRIVATE).edit().clear().commit()) }
        val epoch = 1_790_000_000_000L
        val media = java.io.File(filesDir, "fixture.mp4")
        assets.open("fixture.mp4").use { input -> media.outputStream().use { input.copyTo(it) } }
        fun write(store: String, key: String, value: String) {
            check(getSharedPreferences(store, MODE_PRIVATE).edit().clear().putString(key, value).commit())
        }
        val artwork = java.io.File(filesDir, "library_thumbnails").apply { mkdirs() }
        val bitmap = android.graphics.Bitmap.createBitmap(640, 360, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE; textSize = 36f
        }
        val entries = List(librarySize) { i ->
            canvas.drawColor(android.graphics.Color.rgb(30 + i % 80, 40 + i % 90, 70 + i % 100))
            canvas.drawText("Fixture video %03d".format(i), 32f, 180f, paint)
            val image = java.io.File(artwork, "v3_middle_fixture_$i.jpg")
            image.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 86, it) }
            val remote = i == 1 && server != null
            LibraryEntry(
                if (remote) "$server/fixture.mp4" else android.net.Uri.fromFile(media).buildUpon()
                    .apply { if (i != 0) appendQueryParameter("fixture", i.toString()) }.build().toString(),
                if (remote) "$server/page" else null, "Fixture video %03d".format(i), "Benchmark",
                if (remote) LibrarySourceKind.REMOTE_STREAM else LibrarySourceKind.LOCAL_FILE,
                if (remote) com.miowser.app.grabber.MediaType.MP4 else null, null, android.net.Uri.fromFile(image).toString(),
                epoch - i, 0, 60_000,
                pageTitle = "Fixture Series Steel Ball Run", videoName = "Fixture episode $i",
                selectedSubtitle = if (remote) LibrarySubtitle("$server/fixture.vtt", "$server/page", "Fixture subtitles", null, null) else null
            )
        }
        bitmap.recycle()
        // 23-field format lets the same persisted workload be used by the P1 comparison APK.
        write("media_library", "entries", LibraryCodec.encode(entries).lineSequence()
            .joinToString("\n") { it.substringBeforeLast('\t') })
        write("library_folders", "folders", LibraryFolderCodec.encode(listOf(
            LibraryFolder("series", "Fixture Series", titleRule = LibraryTitleRule(LibraryTitleSource.COMBINED,
                LibraryRuleOperator.ALL, listOf("Fixture", "Steel ball run"))),
            LibraryFolder("episodes", "Episodes", parentId = "series", titleRule =
                LibraryTitleRule(LibraryTitleSource.MEDIA, LibraryRuleOperator.ANY, listOf("episode")))
        )))
        write("history", "entries", HistoryCodec.encode(List(2000) { i ->
            HistoryEntry("https://example.invalid/fixture/$i", "Fixture page $i", epoch - i, 1)
        }))
        write("downloads", "downloads", DownloadCodec.encode(List(downloads) { i ->
            SavedDownload(i.toLong() + 1, "https://example.invalid/file/$i", "", DownloadKind.DIRECT_FILE,
                "Fixture download %04d.mp4".format(i), "fixture-$i", if (i % 4 == 0 && !(active && i == 0)) DownloadStatus.COMPLETED else DownloadStatus.FAILED,
                if (i % 4 == 0 && !(active && i == 0)) media.length() else 0, media.length(),
                if (i % 4 != 0) "Deterministic unfinished record" else null,
                savedUri = if (i % 4 == 0 && !(active && i == 0)) android.net.Uri.fromFile(media).toString() else null
            ).let { record -> if (active && i == 0) record.copy(sourceUrl = "$server/slow/transfer.bin", displayName = "Fixture active transfer.bin") else record }
        }))
        write("browser_session", "tabs", TabSessionCodec.encode(List(tabs) { i ->
            val html = """<html><head><title>Fixture tab $i</title></head><body><p id="status">Checking bridges</p>
                <script>try {
                MiowserAdBlock.cssFor('example.invalid');
                MiowserGrabber.reportVideoElement('', 0, 0, 0);
                if (MiowserDownloadBridge.begin('benchmark-invalid-token', 'video/mp4', '0') !== false) throw Error('token');
                document.getElementById('status').textContent = 'Bridge ready';
                } catch(e) { document.getElementById('status').textContent = 'Bridge failed'; }</script></body></html>"""
            SavedTab("data:text/html;base64," + android.util.Base64.encodeToString(html.toByteArray(), android.util.Base64.NO_WRAP),
                -1, title = "Fixture tab $i", lastActiveAt = epoch)
        }))
        setContentView(TextView(this).apply { text = "Fixture ready: library=$librarySize history=2000 downloads=$downloads tabs=$tabs" })
    }
}
