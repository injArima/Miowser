package com.miowser.app.downloader

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import java.io.File

/** Extracts an APK's own launcher icon without installing or executing the package. */
object ApkIconLoader {
    private val cache = object : LinkedHashMap<String, Bitmap>(24, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>?): Boolean = size > 24
    }

    fun load(context: Context, uri: Uri): Bitmap? {
        val cacheKey = uri.toString()
        synchronized(cache) { cache[cacheKey] }?.let { return it }

        // PackageManager can parse the already-open descriptor path without copying a potentially
        // hundreds-of-megabytes APK merely to read its manifest/icon. Keep the descriptor alive
        // until the Drawable has been converted into an independent Bitmap.
        runCatching { context.contentResolver.openFileDescriptor(uri, "r") }.getOrNull()
            ?.use { descriptor ->
                runCatching { loadFromPath(context, "/proc/self/fd/${descriptor.fd}") }
                    .getOrNull()?.let { bitmap ->
                        synchronized(cache) { cache[cacheKey] = bitmap }
                        return bitmap
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
            loadFromPath(context, temporaryApk.absolutePath)?.also { bitmap ->
                synchronized(cache) { cache[cacheKey] = bitmap }
            }
        } catch (_: Exception) {
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
