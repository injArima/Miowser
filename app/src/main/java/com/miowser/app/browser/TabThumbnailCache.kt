package com.miowser.app.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.ConcurrentHashMap

private const val MAX_CACHE_BYTES = 12L * 1024L * 1024L
private const val MAX_CACHE_FILES = 30

/** Small preview files live in cache, never in the session codec or user-visible storage. */
object TabThumbnailCache {
    private val executor = Executors.newSingleThreadExecutor()
    private val removedKeys = ConcurrentHashMap.newKeySet<String>()

    fun load(context: Context, key: String): Bitmap? {
        val source = file(context, key)?.takeIf(File::isFile) ?: return null
        val bitmap = BitmapFactory.decodeFile(source.absolutePath) ?: run {
            source.delete()
            return null
        }
        if (!isUsablePreview(bitmap)) {
            bitmap.recycle()
            source.delete()
            return null
        }
        source.setLastModified(System.currentTimeMillis())
        return bitmap
    }

    /** Invisible/hardware WebViews can occasionally draw one flat color; never replace a good preview with that. */
    fun isUsablePreview(bitmap: Bitmap): Boolean {
        if (bitmap.width < 2 || bitmap.height < 2) return false
        val colors = linkedSetOf<Int>()
        for (yStep in 0..10) for (xStep in 0..6) {
            val pixel = bitmap.getPixel(
                xStep * (bitmap.width - 1) / 6,
                yStep * (bitmap.height - 1) / 10
            )
            // Ignore tiny compression noise while still requiring real visual structure.
            colors += pixel and 0xFFF0F0F0.toInt()
            if (colors.size >= 4) return true
        }
        return false
    }

    fun save(context: Context, key: String, bitmap: Bitmap) {
        val target = file(context, key) ?: return
        executor.execute {
            if (key in removedKeys) return@execute
            runCatching {
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, "${target.name}.tmp")
                temporary.outputStream().buffered().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 72, it) }
                if (key in removedKeys) {
                    temporary.delete()
                    target.delete()
                } else if (!temporary.renameTo(target)) {
                    target.delete()
                    temporary.renameTo(target)
                }
                target.setLastModified(System.currentTimeMillis())
                trim(target.parentFile)
            }
        }
    }

    fun remove(context: Context, key: String) {
        removedKeys.add(key)
        executor.execute { file(context.applicationContext, key)?.delete() }
    }

    fun retainOnly(context: Context, keys: Set<String>) {
        executor.execute {
            directory(context).listFiles()?.forEach { cached ->
                if (cached.extension == "jpg" && cached.nameWithoutExtension !in keys) cached.delete()
            }
            trim(directory(context))
        }
    }

    private fun trim(directory: File?) {
        val files = directory?.listFiles()?.filter { it.extension == "jpg" }
            ?.sortedByDescending(File::lastModified)?.toMutableList() ?: return
        var total = files.sumOf(File::length)
        while (files.size > MAX_CACHE_FILES || total > MAX_CACHE_BYTES) {
            val oldest = files.removeAt(files.lastIndex)
            total -= oldest.length()
            oldest.delete()
        }
    }

    private fun directory(context: Context) = File(context.cacheDir, "tab_thumbnails")

    private fun file(context: Context, key: String): File? {
        if (!key.matches(Regex("[a-zA-Z0-9_-]{8,80}"))) return null
        return File(directory(context), "$key.jpg")
    }
}

/** Keeps no more than 30 decoded previews alive even if the user opens far more tabs. */
internal object TabThumbnailMemoryRegistry {
    private const val MAX_PREVIEWS = 30
    private val entries = LinkedHashMap<String, () -> Unit>()

    fun touch(key: String, onEvict: () -> Unit) {
        val evictions = mutableListOf<() -> Unit>()
        synchronized(entries) {
            entries.remove(key)
            entries[key] = onEvict
            while (entries.size > MAX_PREVIEWS) {
                val oldest = entries.entries.first()
                entries.remove(oldest.key)
                evictions += oldest.value
            }
        }
        evictions.forEach { it() }
    }

    fun remove(key: String) {
        synchronized(entries) { entries.remove(key) }
    }
}
