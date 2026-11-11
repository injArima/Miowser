package com.miowser.app.player

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.DataReader
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.DefaultExtractorInput
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.DiscardingTrackOutput
import androidx.media3.extractor.Extractor
import androidx.media3.extractor.ExtractorInput
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.PositionHolder
import androidx.media3.extractor.SeekMap
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.text.CueDecoder
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.extractor.text.SubtitleParser
import java.io.EOFException
import java.io.FileInputStream
import java.io.IOException
import java.util.TreeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Demux only the chosen local subtitle track, with no player or video decoder. */
@OptIn(UnstableApi::class)
internal suspend fun readEmbeddedSubtitles(
    context: Context,
    uri: Uri,
    formatId: String
): List<CuesWithTiming> = withContext(Dispatchers.IO) {
    val job = currentCoroutineContext()
    require(uri.scheme in setOf("content", "file", "android.resource")) { "Choose a local subtitle track to inspect." }
    val asset = checkNotNull(context.contentResolver.openAssetFileDescriptor(uri, "r")) {
        "Couldn’t open this video’s subtitles."
    }
    asset.use {
        ParcelFileDescriptor.AutoCloseInputStream(ParcelFileDescriptor.dup(asset.fileDescriptor)).use { stream ->
            val length = if (asset.declaredLength >= 0) asset.declaredLength else stream.channel.size() - asset.startOffset
            require(length > 0) { "This file does not support subtitle inspection." }
            val input = SubtitleFileInput(stream, asset.startOffset, length) { job.ensureActive() }
            val parserFactory = DefaultSubtitleParserFactory()
            val extractors = DefaultExtractorsFactory().setSubtitleParserFactory(object : SubtitleParser.Factory {
                override fun supportsFormat(format: Format) = format.id == formatId && parserFactory.supportsFormat(format)
                override fun getCueReplacementBehavior(format: Format) = parserFactory.getCueReplacementBehavior(format)
                override fun create(format: Format) = parserFactory.create(format)
            }).createExtractors(uri, emptyMap())
            try {
                val extractor = extractors.firstOrNull { candidate ->
                    input.seek(0)
                    candidate.sniff(input)
                } ?: error("This video format does not support subtitle inspection.")
                input.seek(0)
                val runtime = Runtime.getRuntime()
                val available = runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()
                val budget = minOf(runtime.maxMemory() / 32, available / 8, 8L * 1024 * 1024)
                require(budget >= 256 * 1024) { "Not enough free memory to inspect this subtitle track." }
                val outputs = ArrayList<SubtitleTrackOutput>()
                val tracks = HashMap<Int, TrackOutput>()
                val fallback = DiscardingTrackOutput()
                val discard = object : TrackOutput by fallback {
                    override fun sampleData(reader: DataReader, count: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
                        job.ensureActive()
                        val source = reader as? ExtractorInput
                            ?: return fallback.sampleData(reader, count, allowEndOfInput, sampleDataPart)
                        return if (source.skipFully(count, allowEndOfInput)) count else C.RESULT_END_OF_INPUT
                    }
                }
                extractor.init(object : ExtractorOutput {
                    override fun track(id: Int, type: Int): TrackOutput =
                        tracks.getOrPut(id) {
                            if (type == C.TRACK_TYPE_TEXT) {
                                SubtitleTrackOutput(formatId, budget, discard).also { outputs += it }
                            } else discard
                        }
                    override fun endTracks() = Unit
                    override fun seekMap(seekMap: SeekMap) = Unit
                })
                val seek = PositionHolder()
                while (true) {
                    job.ensureActive()
                    when (extractor.read(input, seek)) {
                        Extractor.RESULT_END_OF_INPUT -> break
                        Extractor.RESULT_SEEK -> input.seek(seek.position)
                    }
                }
                val output = outputs.firstOrNull { it.supported }
                    ?: error("This subtitle format cannot be inspected.")
                normalizeEmbeddedSubtitleCues(output.cues, output.replacementBehavior, budget)
            } finally {
                extractors.forEach { it.release() }
            }
        }
    }
}

@OptIn(UnstableApi::class)
private class SubtitleTrackOutput(
    private val formatId: String,
    private val budget: Long,
    private val discard: TrackOutput
) : TrackOutput {
    val cues = ArrayList<CuesWithTiming>()
    var supported = false
        private set
    var replacementBehavior = Format.CUE_REPLACEMENT_BEHAVIOR_MERGE
        private set
    private val decoder = CueDecoder()
    private var bytes = ByteArray(4096)
    private var used = 0
    private var retained = 0L

    override fun format(format: Format) {
        supported = format.id == formatId && format.sampleMimeType == MimeTypes.APPLICATION_MEDIA3_CUES
        replacementBehavior = format.cueReplacementBehavior
    }

    private fun reserve(count: Int) {
        require(count >= 0 && used.toLong() + count <= budget) { "This subtitle track is too large to inspect." }
        if (used + count > bytes.size) bytes = bytes.copyOf(maxOf(used + count, bytes.size * 2).coerceAtMost(budget.toInt()))
    }

    override fun sampleData(input: DataReader, length: Int, allowEndOfInput: Boolean, sampleDataPart: Int): Int {
        if (!supported) return discard.sampleData(input, length, allowEndOfInput, sampleDataPart)
        val count = minOf(length, 8192)
        reserve(count)
        val read = input.read(bytes, used, count)
        if (read == C.RESULT_END_OF_INPUT) {
            if (!allowEndOfInput) throw EOFException()
        } else used += read
        return read
    }

    override fun sampleData(data: ParsableByteArray, length: Int, sampleDataPart: Int) {
        if (!supported) { data.skipBytes(length); return }
        reserve(length)
        data.readBytes(bytes, used, length)
        used += length
    }

    override fun sampleMetadata(timeUs: Long, flags: Int, size: Int, offset: Int, cryptoData: TrackOutput.CryptoData?) {
        if (!supported) return
        require(cryptoData == null) { "Protected subtitle tracks cannot be inspected." }
        val start = used - offset - size
        require(start >= 0 && offset >= 0) { "Invalid subtitle sample." }
        val cue = decoder.decode(timeUs, bytes, start, size)
        retained += 192 + cue.cues.sumOf {
            128L + (it.text?.length ?: 0) * 2L + (it.bitmap?.allocationByteCount ?: 0)
        }
        require(retained <= budget) { "This subtitle track is too large to inspect." }
        cues += cue
        bytes.copyInto(bytes, 0, used - offset, used)
        used = offset
    }
}

/** Produce disjoint intervals so playback and Inspect share the same overlap handling. */
@OptIn(UnstableApi::class)
internal fun normalizeEmbeddedSubtitleCues(
    cues: List<CuesWithTiming>,
    replacementBehavior: Int,
    budget: Long = 8L * 1024 * 1024
): List<CuesWithTiming> {
    val sorted = cues.sortedBy { it.startTimeUs }
    if (replacementBehavior == Format.CUE_REPLACEMENT_BEHAVIOR_REPLACE) {
        return sorted.mapIndexedNotNull { index, cue ->
            val next = sorted.getOrNull(index + 1)?.startTimeUs ?: Long.MAX_VALUE
            val end = if (cue.durationUs == C.TIME_UNSET) next else minOf(cue.endTimeUs, next)
            if (end <= cue.startTimeUs) null
            else CuesWithTiming(cue.cues, cue.startTimeUs, end - cue.startTimeUs)
        }
    }
    val events = TreeMap<Long, MutableList<Pair<Int, Boolean>>>()
    sorted.forEachIndexed { index, cue ->
        val end = if (cue.durationUs == C.TIME_UNSET) sorted.getOrNull(index + 1)?.startTimeUs ?: Long.MAX_VALUE
            else cue.endTimeUs
        if (end > cue.startTimeUs && cue.cues.isNotEmpty()) {
            events.getOrPut(cue.startTimeUs) { ArrayList() }.add(index to true)
            events.getOrPut(end) { ArrayList() }.add(index to false)
        }
    }
    val active = linkedSetOf<Int>()
    val result = ArrayList<CuesWithTiming>()
    var previous = 0L
    var references = 0L
    for ((time, changes) in events) {
        if (time > previous && active.isNotEmpty()) {
            val current = active.flatMap { sorted[it].cues }
            references += 128 + current.size * 8L
            require(references <= budget) { "This subtitle track has too many overlapping lines to inspect." }
            result += CuesWithTiming(current, previous, time - previous)
        }
        changes.forEach { (index, add) -> if (add) active.add(index) else active.remove(index) }
        previous = time
    }
    return result
}

/** Large skipped samples seek over video/audio bytes instead of copying them into a buffer. */
@OptIn(UnstableApi::class)
internal class SubtitleFileInput(
    private val stream: FileInputStream,
    private val startOffset: Long,
    private val fileLength: Long,
    private val checkActive: () -> Unit
) : ExtractorInput {
    private var upstreamPosition = 0L
    private var skipTarget: Long? = null
    private var skipPositioned = false
    private val reader = DataReader { buffer, offset, count ->
        checkActive()
        val target = skipTarget
        if (target != null) {
            if (!skipPositioned) {
                stream.channel.position(startOffset + target)
                skipPositioned = true
            }
            val skipped = minOf(count.toLong(), target - upstreamPosition).toInt()
            upstreamPosition += skipped
            skipped
        } else {
            val remaining = fileLength - upstreamPosition
            val read = if (remaining <= 0) C.RESULT_END_OF_INPUT
                else stream.read(buffer, offset, minOf(count.toLong(), remaining).toInt())
            if (read > 0) upstreamPosition += read
            read
        }
    }
    private var input = DefaultExtractorInput(reader, 0, fileLength)

    fun seek(position: Long) {
        checkActive()
        require(position in 0..fileLength) { "Invalid subtitle file position." }
        try { stream.channel.position(startOffset + position) }
        catch (_: IOException) { error("This storage provider does not support subtitle inspection.") }
        upstreamPosition = position
        input = DefaultExtractorInput(reader, position, fileLength)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int) = input.read(buffer, offset, length)
    override fun readFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean) =
        input.readFully(target, offset, length, allowEndOfInput)
    override fun readFully(target: ByteArray, offset: Int, length: Int) = input.readFully(target, offset, length)
    override fun peek(target: ByteArray, offset: Int, length: Int) = input.peek(target, offset, length)
    override fun peekFully(target: ByteArray, offset: Int, length: Int, allowEndOfInput: Boolean) =
        input.peekFully(target, offset, length, allowEndOfInput)
    override fun peekFully(target: ByteArray, offset: Int, length: Int) = input.peekFully(target, offset, length)
    override fun advancePeekPosition(length: Int, allowEndOfInput: Boolean) = input.advancePeekPosition(length, allowEndOfInput)
    override fun advancePeekPosition(length: Int) = input.advancePeekPosition(length)
    override fun resetPeekPosition() = input.resetPeekPosition()
    override fun getPeekPosition() = input.peekPosition
    override fun getPosition() = input.position
    override fun getLength() = fileLength
    override fun <E : Throwable> setRetryPosition(position: Long, failure: E) {
        seek(position)
        throw failure
    }
    override fun skip(length: Int): Int {
        if (length <= 4096) return input.skip(length)
        val count = minOf(length.toLong(), fileLength - position).toInt()
        if (count == 0) return C.RESULT_END_OF_INPUT
        skipFully(count, false)
        return count
    }
    override fun skipFully(length: Int, allowEndOfInput: Boolean): Boolean {
        if (length <= 4096) return input.skipFully(length, allowEndOfInput)
        if (position == fileLength && allowEndOfInput) return false
        if (fileLength - position < length) throw EOFException()
        skipTarget = position + length
        skipPositioned = false
        return try { input.skipFully(length, allowEndOfInput) }
        finally { skipTarget = null }
    }
    override fun skipFully(length: Int) { skipFully(length, false) }
}
