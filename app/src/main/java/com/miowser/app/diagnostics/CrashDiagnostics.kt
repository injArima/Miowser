package com.miowser.app.diagnostics

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Process
import android.os.SystemClock
import com.miowser.app.BuildConfig
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

private const val MAX_EVENTS = 40
private const val MAX_REPORTS = 20
private const val MAX_REPORT_BYTES = 192 * 1024L
private const val MAX_STACK_FRAMES = 180

private val privateUri = Regex("""(?i)(?:https?|content|file)://[^\s<>\"']+""")
private val externalPath = Regex("""(?i)(?<![\w.])/(?:storage|sdcard|mnt|data/user|data/data)/[^\s:]+""")

internal fun redactCrashValue(value: String): String = externalPath.replace(
    privateUri.replace(value, "[url-or-uri]"),
    "[local-path]"
).take(2_048)

data class CrashReportSummary(
    val fileName: String,
    val createdAt: Long,
    val bytes: Long
)

data class StoredCrashReport(val fileName: String, val text: String)

private data class DiagnosticEvent(
    val wallTime: Long,
    val elapsedTime: Long,
    val area: String,
    val description: String
)

private object DiagnosticTrail {
    // Browser and player callbacks must never wait for optional diagnostics. If another thread is
    // touching the tiny trail, dropping one breadcrumb is preferable to stalling a WebView.
    private val lock = ReentrantLock()
    private val events = ArrayDeque<DiagnosticEvent>(MAX_EVENTS)
    @Volatile var area: String = "startup"
        private set

    fun setArea(value: String) {
        val safe = value.take(48).ifBlank { "unknown" }
        if (safe == area) return
        area = safe
        add("entered $safe")
    }

    fun add(description: String) {
        if (!CrashDiagnostics.enabled || !lock.tryLock()) return
        try {
            if (events.size == MAX_EVENTS) events.removeFirst()
            events.addLast(DiagnosticEvent(
                wallTime = System.currentTimeMillis(),
                elapsedTime = SystemClock.elapsedRealtime(),
                area = area,
                description = description.take(96)
            ))
        } finally {
            lock.unlock()
        }
    }

    fun snapshot(): List<DiagnosticEvent> {
        if (!lock.tryLock()) return emptyList()
        return try { events.toList() } finally { lock.unlock() }
    }

    fun clear() {
        lock.lock()
        try { events.clear() } finally { lock.unlock() }
    }
}

object CrashReportStore {
    private const val DIRECTORY = "crash_diagnostics"

    private fun directory(context: Context): File = File(context.filesDir, DIRECTORY)

    fun list(context: Context): List<CrashReportSummary> = directory(context).listFiles()
        .orEmpty().asSequence()
        .filter { it.isFile && it.name.startsWith("crash-") && it.name.endsWith(".txt") }
        .mapNotNull { file ->
            val timestamp = file.name.removePrefix("crash-").removeSuffix(".txt").toLongOrNull()
                ?: return@mapNotNull null
            CrashReportSummary(file.name, timestamp, file.length())
        }
        .sortedByDescending(CrashReportSummary::createdAt)
        .toList()

    fun read(context: Context, fileName: String): String? {
        if (!isReportName(fileName)) return null
        val file = File(directory(context), fileName)
        if (!file.isFile || file.length() !in 1..MAX_REPORT_BYTES) return null
        return runCatching { file.readText() }.getOrNull()
    }

    fun clear(context: Context): List<StoredCrashReport> = list(context).mapNotNull { summary ->
        val text = read(context, summary.fileName) ?: return@mapNotNull null
        val file = File(directory(context), summary.fileName)
        if (file.delete()) StoredCrashReport(summary.fileName, text) else null
    }

    fun restore(context: Context, reports: List<StoredCrashReport>) {
        val directory = directory(context)
        if (reports.isNotEmpty()) directory.mkdirs()
        reports.forEach { report ->
            if (isReportName(report.fileName) && report.text.toByteArray().size <= MAX_REPORT_BYTES) {
                runCatching { File(directory, report.fileName).writeText(report.text) }
            }
        }
        trim(directory)
    }

    @Synchronized
    internal fun create(context: Context, timestamp: Long, write: (OutputStreamWriter) -> Unit): Boolean {
        val directory = directory(context)
        if (!directory.isDirectory && !directory.mkdirs()) return false
        var fileTimestamp = timestamp
        while (File(directory, "crash-$fileTimestamp.txt").exists()) fileTimestamp++
        val temporary = File(directory, "crash-$fileTimestamp.tmp")
        val target = File(directory, "crash-$fileTimestamp.txt")
        try {
            FileOutputStream(temporary).use { output ->
                val writer = OutputStreamWriter(output, Charsets.UTF_8)
                write(writer)
                writer.flush()
                output.fd.sync()
            }
            if (temporary.length() !in 1..MAX_REPORT_BYTES || !temporary.renameTo(target)) return false
            // The report is already durable. Trimming must not turn a saved report into a failure.
            runCatching { trim(directory) }
            return true
        } finally {
            temporary.delete()
        }
    }

    private fun trim(directory: File) {
        directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.startsWith("crash-") && it.name.endsWith(".txt") }
            .sortedByDescending(File::lastModified)
            .drop(MAX_REPORTS)
            .forEach(File::delete)
    }

    private fun isReportName(value: String): Boolean =
        value.matches(Regex("""crash-\d{1,20}\.txt"""))
}

/** Local-only crash diagnostics. The Settings switch controls capture; nothing is uploaded. */
object CrashDiagnostics {
    @Volatile internal var enabled: Boolean = false
        private set
    private val installed = AtomicBoolean(false)
    private val handling = AtomicBoolean(false)
    private val lastRendererReportAt = AtomicLong(0L)
    private var applicationContext: Context? = null
    private var previousHandler: Thread.UncaughtExceptionHandler? = null
    // Released before writing a report so an OOM has some working room for the handler itself.
    @Volatile private var emergencyReserve: ByteArray? = ByteArray(512 * 1024)

    fun install(context: Context, initiallyEnabled: Boolean) {
        applicationContext = context.applicationContext
        enabled = initiallyEnabled
        if (!installed.compareAndSet(false, true)) return
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            val previous = previousHandler
            if (handling.compareAndSet(false, true)) {
                emergencyReserve = null
                if (enabled) {
                    try {
                        if (error is OutOfMemoryError) captureMinimal(thread, error)
                        else capture(thread, error)
                    } catch (_: Throwable) {
                        // Keep one small report even when a full report failed under heap pressure.
                        try { captureMinimal(thread, error) } catch (_: Throwable) { }
                    }
                }
            }
            if (previous != null) previous.uncaughtException(thread, error)
            else {
                Process.killProcess(Process.myPid())
                kotlin.system.exitProcess(10)
            }
        }
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        if (!value) DiagnosticTrail.clear()
        else DiagnosticTrail.add("diagnostics enabled")
    }

    fun setActiveArea(area: String) = DiagnosticTrail.setArea(area)

    /** One renderer process can notify several tabs; keep one report without page details. */
    fun recordRendererCrash(context: Context) {
        if (!enabled) return
        val now = System.currentTimeMillis()
        val previous = lastRendererReportAt.get()
        if (now - previous < 5_000L || !lastRendererReportAt.compareAndSet(previous, now)) return
        val app = context.applicationContext
        runCatching {
            Thread({
                runCatching {
                    CrashReportStore.create(app, now) { writer ->
                        writer.write("Miowser WebView renderer crash\n")
                        writer.write("created_at_ms=$now\n")
                        writer.write("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                        writer.write("The page renderer stopped. No page URL or title was retained.\n")
                    }
                }
            }, "miowser-renderer-report").apply { isDaemon = true }.start()
        }
    }

    /**
     * Records only uncommon warning/error labels—never messages, URLs, titles or search text.
     * Info/debug logs are deliberately excluded because chatty pages can produce them at frame
     * rate and diagnostics must not participate in browser performance.
     */
    internal fun recordLog(tag: String, level: String, errorType: String?) {
        if (!enabled || (level != "warning" && level != "error")) return
        val safeTag = tag.filter { it.isLetterOrDigit() || it in "._-" }.take(40).ifBlank { "app" }
        val safeType = errorType?.filter { it.isLetterOrDigit() || it in "._$" }?.take(48)
        DiagnosticTrail.add(buildString {
            append(level.take(8)); append(" from "); append(safeTag)
            if (!safeType.isNullOrBlank()) { append(" ("); append(safeType); append(')') }
        })
    }

    private fun capture(thread: Thread, error: Throwable) {
        val context = applicationContext ?: return
        val timestamp = System.currentTimeMillis()
        val events = DiagnosticTrail.snapshot()
        CrashReportStore.create(context, timestamp) { writer ->
            fun line(value: String = "") { writer.write(redactCrashValue(value)); writer.write('\n'.code) }
            val activity = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memory = ActivityManager.MemoryInfo().also { info -> runCatching { activity?.getMemoryInfo(info) } }
            val runtime = Runtime.getRuntime()

            line("Miowser crash diagnostics")
            line("created_at_ms=$timestamp")
            line("pid=${Process.myPid()}")
            line("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            line("android_sdk=${Build.VERSION.SDK_INT}")
            line("device=${Build.MANUFACTURER} ${Build.MODEL}")
            line("abis=${Build.SUPPORTED_ABIS.joinToString()}")
            line("low_ram=${activity?.isLowRamDevice}")
            line("memory_class_mb=${activity?.memoryClass}")
            line("large_memory_class_mb=${activity?.largeMemoryClass}")
            line("system_available_bytes=${memory.availMem}")
            line("system_low_memory=${memory.lowMemory}")
            line("heap_max_bytes=${runtime.maxMemory()}")
            line("heap_total_bytes=${runtime.totalMemory()}")
            line("heap_free_bytes=${runtime.freeMemory()}")
            line("active_area=${DiagnosticTrail.area}")
            line("thread=${thread.name.take(80)}")
            line()
            line("Recent redacted events (no URLs, titles, searches, or history):")
            events.forEach { event ->
                line("${event.wallTime} +${event.elapsedTime}ms [${event.area}] ${event.description}")
            }
            line()
            line("Exception:")
            var cause: Throwable? = error
            var causeCount = 0
            var frames = 0
            val seen = HashSet<Throwable>(8)
            while (cause != null && causeCount < 8 && seen.add(cause)) {
                line(if (causeCount == 0) {
                    cause.javaClass.name
                } else {
                    "Caused by ${cause.javaClass.name}"
                })
                cause.stackTrace.forEach { frame ->
                    if (frames++ < MAX_STACK_FRAMES) line("    at $frame")
                }
                cause = cause.cause
                causeCount++
            }
        }
    }

    private fun captureMinimal(thread: Thread, error: Throwable) {
        val context = applicationContext ?: return
        val timestamp = System.currentTimeMillis()
        CrashReportStore.create(context, timestamp) { writer ->
            writer.write("Miowser crash diagnostics (limited report)\n")
            writer.write("created_at_ms=$timestamp\n")
            writer.write("pid=${Process.myPid()}\n")
            writer.write("version=${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            writer.write("thread=${thread.name.take(80)}\n")
            writer.write("exception=${error.javaClass.name}\n")
            // Class, method, and source line contain no exception message or browsing data.
            error.stackTrace.take(16).forEach { frame ->
                writer.write("at ${frame.className}.${frame.methodName}:${frame.lineNumber}\n")
            }
        }
    }
}
