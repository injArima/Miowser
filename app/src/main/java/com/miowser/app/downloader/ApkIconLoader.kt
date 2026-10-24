package com.miowser.app.downloader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.File
import com.miowser.app.library.LocalInspectionCache

/** Extracts an APK's own launcher icon without installing or executing the package. */
object ApkIconLoader {
    private fun newMemoryCache() = com.miowser.app.performance.WeightedCache<String, Bitmap>(24, 2 * 1024 * 1024) { key, value ->
        key.length * 2 + value.allocationByteCount
    }
    @Volatile private var cache = newMemoryCache()
    fun clearMemory() { cache = newMemoryCache() }
    fun forget(uri: String) { cache.remove(uri) }
    fun peek(uri: String?): Bitmap? = uri?.let {
        if (LocalInspectionCache.peek("apk-icon", it) == "ready") cache[it] else null
    }

    fun load(context: Context, uri: Uri): Bitmap? = LocalInspectionCache.inspect(uri.toString()) {
        loadLocked(context, uri)
    }

    private fun loadLocked(context: Context, uri: Uri): Bitmap? {
        val cacheKey = uri.toString()
        peek(cacheKey)?.let { return it }
        val directory = File(context.cacheDir, "media_inspections/apk_icons")
        val cachedFile = File(directory, LocalInspectionCache.key("apk-icon", cacheKey).substringAfter(':') + ".png")
        when (LocalInspectionCache.peek("apk-icon", cacheKey)) {
            "missing" -> return null
            "ready" -> android.graphics.BitmapFactory.decodeFile(cachedFile.absolutePath)?.let {
                cache[cacheKey] = it
                return it
            }
        }
        val ticket = LocalInspectionCache.ticket()
        fun save(bitmap: Bitmap): Bitmap {
            if (ticket == LocalInspectionCache.ticket()) {
                runCatching {
                    check(directory.isDirectory || directory.mkdirs())
                    val temporary = File(directory, cachedFile.name + ".tmp")
                    try {
                        temporary.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)) }
                        java.nio.file.Files.move(temporary.toPath(), cachedFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                        if (LocalInspectionCache.remember("apk-icon", cacheKey, "ready", ticket = ticket)) cache[cacheKey] = bitmap
                    } finally { temporary.delete() }
                    var total = 0L
                    directory.listFiles { file -> file.extension == "png" }?.sortedByDescending(File::lastModified)
                        ?.forEachIndexed { index, file ->
                            total += file.length()
                            if (index >= 64 || total > 4 * 1024 * 1024L) file.delete()
                        }
                }
            }
            return bitmap
        }

        // PackageManager can parse the already-open descriptor path without copying a potentially
        // hundreds-of-megabytes APK merely to read its manifest/icon. Keep the descriptor alive
        // until the Drawable has been converted into an independent Bitmap.
        runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            ?.use { descriptor ->
                runCatching { loadFromPath(context, "/proc/self/fd/${descriptor.fd}") }
                    .getOrNull()?.let { bitmap ->
                        return save(bitmap)
                    }
            }

        // Some vendor PackageManager builds refuse /proc file-descriptor paths. Retain a fallback
        // cache-file path for those devices, deleting it immediately after extracting the icon.
        val iconDir = File(context.cacheDir, "apk_icon_probe").apply { mkdirs() }
        val temporaryApk = File(iconDir, "${uri.toString().hashCode().toUInt()}.apk")
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                temporaryApk.outputStream().use(input::copyTo)
            } ?: return null
            loadFromPath(context, temporaryApk.absolutePath)?.let(::save).also {
                if (it == null) LocalInspectionCache.remember("apk-icon", cacheKey, "missing", LocalInspectionCache.RETRY_MS, ticket)
            }
        } catch (_: Exception) {
            LocalInspectionCache.remember("apk-icon", cacheKey, "missing", LocalInspectionCache.RETRY_MS, ticket)
            null
        } finally {
            temporaryApk.delete()
        }
    }

    private fun loadFromPath(context: Context, path: String): Bitmap? {
        val packageInfo = context.packageManager.getPackageArchiveInfo(path, 0) ?: return null
        val applicationInfo = packageInfo.applicationInfo ?: return null
        applicationInfo.sourceDir = path
        applicationInfo.publicSourceDir = path
        return applicationInfo.loadIcon(context.packageManager).toBitmap(width = 144, height = 144)
    }
}
