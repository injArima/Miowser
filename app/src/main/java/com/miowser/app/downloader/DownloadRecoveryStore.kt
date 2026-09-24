package com.miowser.app.downloader

import android.content.Context
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.Base64

private const val RECOVERY_VERSION = 1
private const val PLAN_FILE_NAME = "plan.v1"

enum class RecoveryTransferKind { DIRECT_RANGES, DIRECT_STREAM, HLS_SEGMENTS, DASH_SEGMENTS }

data class RecoveryRange(val start: Long, val endInclusive: Long, val downloadedBytes: Long)

data class DownloadRecoveryPlan(
    val version: Int = RECOVERY_VERSION,
    val downloadId: Long,
    val transferKind: RecoveryTransferKind,
    val sourceFingerprint: String?,
    val etag: String?,
    val lastModified: String?,
    val totalLength: Long,
    val outputFileName: String,
    val mimeType: String,
    val itemFingerprint: String?,
    val itemCount: Int,
    val elapsedActiveMillis: Long = 0L,
    val ranges: List<RecoveryRange> = emptyList()
)

val DownloadRecoveryPlan.progressTotal: Long
    get() = when (transferKind) {
        RecoveryTransferKind.DIRECT_RANGES, RecoveryTransferKind.DIRECT_STREAM -> totalLength
        RecoveryTransferKind.HLS_SEGMENTS, RecoveryTransferKind.DASH_SEGMENTS -> itemCount.toLong()
    }

fun DownloadRecoveryPlan.completedUnits(context: Context): Long = when (transferKind) {
    RecoveryTransferKind.DIRECT_RANGES -> ranges.sumOf(RecoveryRange::downloadedBytes)
    RecoveryTransferKind.DIRECT_STREAM -> DownloadRecoveryStore.payloadFile(context, downloadId)
        .takeIf(File::isFile)?.length()?.coerceAtMost(totalLength.takeIf { it >= 0L } ?: Long.MAX_VALUE) ?: 0L
    RecoveryTransferKind.HLS_SEGMENTS, RecoveryTransferKind.DASH_SEGMENTS ->
        DownloadRecoveryStore.directory(context, downloadId).walkTopDown()
            .count { it.isFile && it.name.startsWith("item_") && it.name.endsWith(".part") &&
                DownloadRecoveryStore.isVerifiedItem(it) }
            .toLong()
}

/** Pure, versioned codec. Text fields are URL-safe Base64 so URLs/header values stay lossless. */
object DownloadRecoveryCodec {
    fun encode(plan: DownloadRecoveryPlan): String = listOf(
        plan.version.toString(),
        plan.downloadId.toString(),
        plan.transferKind.name,
        encodeText(plan.sourceFingerprint),
        encodeText(plan.etag),
        encodeText(plan.lastModified),
        plan.totalLength.toString(),
        encodeText(plan.outputFileName),
        encodeText(plan.mimeType),
        encodeText(plan.itemFingerprint),
        plan.itemCount.toString(),
        plan.elapsedActiveMillis.coerceAtLeast(0L).toString(),
        plan.ranges.joinToString(",") { "${it.start}:${it.endInclusive}:${it.downloadedBytes}" }
    ).joinToString("\t")

    fun decode(raw: String): DownloadRecoveryPlan? = runCatching {
        val parts = raw.trim().split('\t')
        if (parts.size != 13) return null
        val version = parts[0].toInt()
        if (version != RECOVERY_VERSION) return null
        val ranges = if (parts[12].isBlank()) emptyList() else parts[12].split(',').map { encoded ->
            val values = encoded.split(':')
            require(values.size == 3)
            RecoveryRange(values[0].toLong(), values[1].toLong(), values[2].toLong())
        }
        DownloadRecoveryPlan(
            version = version,
            downloadId = parts[1].toLong(),
            transferKind = RecoveryTransferKind.valueOf(parts[2]),
            sourceFingerprint = decodeText(parts[3]),
            etag = decodeText(parts[4]),
            lastModified = decodeText(parts[5]),
            totalLength = parts[6].toLong(),
            outputFileName = decodeText(parts[7]).orEmpty(),
            mimeType = decodeText(parts[8]).orEmpty(),
            itemFingerprint = decodeText(parts[9]),
            itemCount = parts[10].toInt(),
            elapsedActiveMillis = parts[11].toLong(),
            ranges = ranges
        ).also(::validate)
    }.getOrNull()

    private fun validate(plan: DownloadRecoveryPlan) {
        require(plan.downloadId >= 0L)
        require(plan.totalLength >= -1L)
        require(plan.itemCount >= 0)
        require(plan.elapsedActiveMillis >= 0L)
        require(plan.outputFileName.isNotBlank())
        require(plan.ranges.all { range ->
            range.start >= 0L && range.endInclusive >= range.start &&
                range.downloadedBytes in 0..(range.endInclusive - range.start + 1L)
        })
    }

    private fun encodeText(value: String?): String = value?.toByteArray(Charsets.UTF_8)?.let {
        Base64.getUrlEncoder().withoutPadding().encodeToString(it)
    }.orEmpty()

    private fun decodeText(value: String): String? = value.takeIf(String::isNotEmpty)?.let {
        String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
    }
}

/** Durable private artifacts. A temp plan is renamed only after its contents reach disk. */
object DownloadRecoveryStore {
    fun directory(context: Context, downloadId: Long): File =
        File(context.filesDir, "download_recovery/$downloadId")

    fun payloadFile(context: Context, downloadId: Long): File =
        File(directory(context, downloadId), "payload.part")

    fun itemFile(context: Context, downloadId: Long, index: Int): File =
        File(directory(context, downloadId), "item_${index.toString().padStart(6, '0')}.part")

    /**
     * A segment becomes reusable only after the payload and its digest marker have both landed.
     * A process death during transfer leaves a .tmp file, while a death between the two commits
     * leaves an unverified part; either case is redownloaded rather than trusted.
     */
    fun isVerifiedItem(file: File, verifyDigest: Boolean = false): Boolean {
        if (!file.isFile || file.length() <= 0L) return false
        val marker = runCatching { File(file.parentFile, "${file.name}.sha256").readText().trim() }.getOrNull()
            ?: return false
        val separator = marker.indexOf(':')
        if (separator <= 0) return false
        val expectedLength = marker.substring(0, separator).toLongOrNull() ?: return false
        val expectedDigest = marker.substring(separator + 1)
        if (expectedLength != file.length() || expectedDigest.length != 64) return false
        return !verifyDigest || expectedDigest.equals(sha256(file), ignoreCase = true)
    }

    fun commitVerifiedItem(temporary: File, target: File) {
        target.parentFile?.mkdirs()
        moveReplacing(temporary, target)
        val digestFile = File(target.parentFile, "${target.name}.sha256")
        val digestTemporary = File(target.parentFile, "${digestFile.name}.tmp")
        digestTemporary.writeText("${target.length()}:${sha256(target)}")
        moveReplacing(digestTemporary, digestFile)
    }

    @Synchronized
    fun load(context: Context, downloadId: Long): DownloadRecoveryPlan? {
        val file = File(directory(context, downloadId), PLAN_FILE_NAME)
        if (!file.isFile) return null
        return DownloadRecoveryCodec.decode(runCatching { file.readText() }.getOrNull() ?: return null)
    }

    @Synchronized
    fun save(context: Context, plan: DownloadRecoveryPlan): Boolean {
        val directory = directory(context, plan.downloadId)
        if (!directory.exists() && !directory.mkdirs()) return false
        val target = File(directory, PLAN_FILE_NAME)
        val temporary = File(directory, "$PLAN_FILE_NAME.tmp")
        return runCatching {
            temporary.outputStream().bufferedWriter().use { writer ->
                writer.write(DownloadRecoveryCodec.encode(plan))
                writer.flush()
            }
            moveReplacing(temporary, target)
            true
        }.getOrDefault(false)
    }

    @Synchronized
    fun clear(context: Context, downloadId: Long) {
        directory(context, downloadId).deleteRecursively()
    }

    private fun moveReplacing(source: File, target: File) {
        try {
            Files.move(
                source.toPath(), target.toPath(),
                StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

internal fun recoveryFingerprint(parts: Iterable<String>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    parts.forEach { part ->
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(0)
        digest.update(bytes)
        digest.update(0)
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

class DownloadRecoverySession(
    private val context: Context,
    initialPlan: DownloadRecoveryPlan
) {
    private var current = initialPlan

    init {
        check(DownloadRecoveryStore.save(context, initialPlan)) { "Could not persist recovery plan" }
    }

    @Synchronized
    fun updateRange(index: Int, downloadedBytes: Long, elapsedActiveMillis: Long) {
        val ranges = current.ranges.toMutableList()
        val old = ranges[index]
        ranges[index] = old.copy(downloadedBytes = downloadedBytes.coerceIn(0L, old.endInclusive - old.start + 1L))
        current = current.copy(ranges = ranges, elapsedActiveMillis = elapsedActiveMillis.coerceAtLeast(0L))
        check(DownloadRecoveryStore.save(context, current)) { "Could not persist download progress" }
    }

    @Synchronized
    fun updateElapsed(elapsedActiveMillis: Long) {
        current = current.copy(elapsedActiveMillis = elapsedActiveMillis.coerceAtLeast(0L))
        check(DownloadRecoveryStore.save(context, current)) { "Could not persist download elapsed time" }
    }

    @Synchronized
    fun snapshot(): DownloadRecoveryPlan = current
}
