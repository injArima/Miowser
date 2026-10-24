package com.miowser.app.library

import android.content.Context
import android.util.AtomicFile
import com.miowser.app.grabber.DetectedMedia
import com.miowser.app.grabber.MediaType
import com.miowser.app.persistence.Persistence
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Only the selected track's request context is kept with a Library video; never cookies. */
data class LibrarySubtitle(
    val url: String,
    val pageUrl: String,
    val label: String?,
    val requestReferer: String?,
    val requestOrigin: String?
) {
    fun toDetectedMedia(pageTitle: String?): DetectedMedia = DetectedMedia(
        url = url, pageUrl = pageUrl, type = MediaType.SUBTITLE,
        isConfirmed = false, sizeBytes = null, isSizeApproximate = false,
        durationSeconds = null, variantCount = null, resolution = null,
        isEncrypted = false, pageTitle = pageTitle, responseFileName = label,
        requestReferer = requestReferer, requestOrigin = requestOrigin
    )
}

fun DetectedMedia.toLibrarySubtitle(): LibrarySubtitle = LibrarySubtitle(
    url = url, pageUrl = pageUrl, label = responseFileName,
    requestReferer = requestReferer, requestOrigin = requestOrigin
)

/** A durable private copy, capped to the same size as the player fetch. */
object LibrarySubtitleStore {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private const val DIRECTORY = "library_subtitles"

    private fun file(context: Context, identityKey: String, url: String, extension: String = "vtt"): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("$identityKey\u0000$url".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(File(context.filesDir, DIRECTORY), "$digest.$extension")
    }

    suspend fun read(context: Context, identityKey: String, url: String): String? = withContext(Dispatchers.IO) {
        val target = listOf("vtt", "srt").map { file(context.applicationContext, identityKey, url, it) }
            .firstOrNull { it.isFile } ?: return@withContext null
        if (!target.isFile || target.length() !in 1..MAX_BYTES.toLong()) return@withContext null
        runCatching { AtomicFile(target).openRead().use { it.readBytes().toString(Charsets.UTF_8) } }
            .getOrNull()?.takeIf { durableSubtitleFormat(it) != null }
    }

    fun save(context: Context, identityKey: String, url: String, text: String) {
        val format = durableSubtitleFormat(text) ?: return
        if (text.length !in 1..MAX_BYTES) return
        val target = file(context.applicationContext, identityKey, url, format)
        Persistence.submit("subtitle:${target.name}") {
            val bytes = text.toByteArray(Charsets.UTF_8)
            if (bytes.size !in 1..MAX_BYTES) return@submit false
            val directory = target.parentFile ?: return@submit false
            if (!directory.exists() && !directory.mkdirs()) return@submit false
            val atomic = AtomicFile(target)
            val stream = atomic.startWrite()
            try {
                stream.write(bytes)
                atomic.finishWrite(stream)
                val oldFormat = if (format == "srt") "vtt" else "srt"
                android.util.AtomicFile(file(context.applicationContext, identityKey, url, oldFormat)).delete()
                true
            } catch (_: Exception) {
                atomic.failWrite(stream)
                false
            }
        }
    }

    fun delete(context: Context, identityKey: String, url: String) {
        listOf("vtt", "srt").forEach { format ->
            val target = file(context.applicationContext, identityKey, url, format)
            Persistence.submit("subtitle:${target.name}") { !target.exists() || target.delete() }
        }
    }

    /** Reclaim copies left by removed Library cards after the next app restore. */
    fun pruneAsync(context: Context, retainedEntries: Collection<LibraryEntry>, stillCurrent: () -> Boolean = { true }) {
        val startedAt = System.currentTimeMillis()
        val retained = retainedEntries.flatMap { entry ->
            entry.selectedSubtitle?.let { track -> listOf("vtt", "srt").map {
                file(context.applicationContext, entry.identityKey, track.url, it).name
            } }.orEmpty()
        }.toSet()
        Persistence.submit("subtitle-prune") {
            if (!stillCurrent()) return@submit true
            File(context.applicationContext.filesDir, DIRECTORY).listFiles()
                ?.filter { it.isFile && it.extension in setOf("vtt", "srt") && it.name !in retained &&
                    it.lastModified() < startedAt && stillCurrent() }
                ?.forEach { if (stillCurrent()) it.delete() }
            true
        }
    }
}
