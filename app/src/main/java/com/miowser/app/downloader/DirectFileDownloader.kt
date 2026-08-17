package com.miowser.app.downloader

import android.content.Context
import android.util.Log
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "Miowser/Downloader"
private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 15_000
private const val BUFFER_SIZE = 64 * 1024
private const val PROGRESS_UPDATE_INTERVAL_BYTES = 256 * 1024L // avoid hammering Compose state every 64KB

/** Straight streamed download for a direct MP4/WEBM/audio/subtitle file — no manifest, no muxing. */
object DirectFileDownloader {
    fun run(context: Context, download: Download) {
        download.status = DownloadStatus.DOWNLOADING
        val connection = try {
            (URL(download.sourceUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", download.userAgent)
                setRequestProperty("Referer", download.referer)
                download.cookies?.let { setRequestProperty("Cookie", it) }
            }
        } catch (e: Exception) {
            fail(download, "Connection failed: ${e.message}")
            return
        }

        var opened: MediaStoreOutput.Opened? = null
        try {
            if (connection.responseCode !in 200..299) {
                fail(download, "HTTP ${connection.responseCode}")
                return
            }
            download.totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: -1L

            val extension = extensionFor(download.sourceUrl, connection.getHeaderField("Content-Type"))
            val fileName = "${download.outputBaseName}.$extension"
            val mimeType = mimeTypeFor(extension)
            opened = MediaStoreOutput.open(context, fileName, mimeType)
            if (opened == null) {
                fail(download, "Could not create output file")
                return
            }

            var totalRead = 0L
            var lastReported = 0L
            BufferedInputStream(connection.inputStream).use { input ->
                opened.stream.use { out ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        if (download.isCanceled) {
                            MediaStoreOutput.abandon(context, opened.uri)
                            download.status = DownloadStatus.CANCELED
                            return
                        }
                        val read = input.read(buffer)
                        if (read == -1) break
                        out.write(buffer, 0, read)
                        totalRead += read
                        if (totalRead - lastReported >= PROGRESS_UPDATE_INTERVAL_BYTES) {
                            download.bytesDownloaded = totalRead
                            lastReported = totalRead
                        }
                    }
                    download.bytesDownloaded = totalRead
                }
            }
            MediaStoreOutput.finish(context, opened.uri, mimeType)
            download.status = DownloadStatus.COMPLETED
        } catch (e: Exception) {
            Log.d(TAG, "download failed for ${download.sourceUrl}: ${e.message}")
            opened?.let { MediaStoreOutput.abandon(context, it.uri) }
            fail(download, e.message ?: "Unknown error")
        } finally {
            connection.disconnect()
        }
    }

    private fun fail(download: Download, message: String) {
        download.errorMessage = message
        download.status = DownloadStatus.FAILED
    }

    private fun extensionFor(url: String, contentType: String?): String {
        val fromUrl = url.substringBefore('?').substringAfterLast('.', "").lowercase()
        if (fromUrl.length in 2..4 && fromUrl.all { it.isLetterOrDigit() }) return fromUrl
        return when {
            contentType?.contains("mp4") == true -> "mp4"
            contentType?.contains("webm") == true -> "webm"
            contentType?.contains("mpeg") == true -> "mp3"
            contentType?.contains("vtt") == true -> "vtt"
            else -> "bin"
        }
    }

    private fun mimeTypeFor(extension: String): String = when (extension) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "m4a", "aac" -> "audio/mp4"
        "vtt" -> "text/vtt"
        "srt" -> "application/x-subrip"
        else -> "application/octet-stream"
    }
}
