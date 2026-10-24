package com.miowser.app.downloader

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Remuxes a list of downloaded MPEG-TS segment files into a single playable .mp4 — stream-copy
 * only (MediaExtractor reads compressed samples straight through, MediaMuxer rewrites them into
 * an MP4 container; nothing is decoded or re-encoded). This is the same "-c copy" concat
 * ffmpeg does in 1DM's own pipeline (project memory: 1dm-hls-dash-mux-pipeline.md, traced from
 * its bytecode), reimplemented on Android's own framework APIs instead of FFmpeg — FFmpegKit is
 * dead (see ffmpegkit-retired-caveat.md), and MediaExtractor/MediaMuxer need no extra dependency.
 *
 * Audio and video share a timestamp origin within each segment. Their original relative offset
 * must survive remuxing, while EXTINF supplies the position of the next segment.
 *
 * [mux]'s scope: classic MPEG-TS segments only (HLS). CMAF/fMP4 segments on their own (an
 * #EXT-X-MAP initialization segment carrying the moov box separately from the media segments)
 * aren't handled here — MediaExtractor would fail to find valid tracks in a bare fMP4 media
 * segment without its init segment prepended. DASH's own fMP4 segments go through
 * DashDownloader's own init+media concatenation first (which produces a complete, directly
 * MediaExtractor-readable file per track) and then [muxTracks] below, not this function.
 */
object SegmentMuxer {
    class MuxException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun mux(segmentFiles: List<File>, outputPath: String, durationsSeconds: List<Double>, checkActive: () -> Unit = {}) {
        if (segmentFiles.isEmpty()) throw MuxException("No segments to mux")
        if (durationsSeconds.size != segmentFiles.size) throw MuxException("Segment durations are missing")

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var completed = false
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var expectedAdtsConfig: ByteArray? = null
        val timeline = HlsMuxTimeline()
        val bufferInfo = MediaCodec.BufferInfo()
        // 2MB is generous for one compressed TS sample (a single video frame or audio unit) —
        // this is a per-sample transfer buffer, not a whole-segment buffer.
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)

        try {
            for ((index, segmentFile) in segmentFiles.withIndex()) {
                checkActive()
                val probe = MediaExtractor()
                var segmentVideoTrack: Int
                var segmentAudioTrack: Int
                var originUs: Long
                var adtsConfig: ByteArray?
                var audioIsAac = false
                try {
                    try {
                        probe.setDataSource(segmentFile.absolutePath)
                    } catch (e: Exception) {
                        throw MuxException("Segment ${segmentFile.name} isn't a readable media container", e)
                    }
                    segmentVideoTrack = findTrack(probe, "video/")
                    segmentAudioTrack = findTrack(probe, "audio/")
                    audioIsAac = segmentAudioTrack != -1 &&
                        probe.getTrackFormat(segmentAudioTrack).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm"

                    if (index > 0 && ((segmentVideoTrack != -1) != (videoTrackIndex != -1) ||
                        (segmentAudioTrack != -1) != (audioTrackIndex != -1)
                    )) {
                        throw MuxException("Segment ${index + 1} changes audio/video tracks; cannot safely remux")
                    }

                    // Probe with both tracks selected. Two separate extractors can each choose a
                    // different MPEG-TS timestamp origin, destroying audio/video alignment.
                    val segmentProbe = firstSharedSampleTime(probe, segmentVideoTrack, segmentAudioTrack, index, buffer)
                    originUs = segmentProbe.originUs
                    adtsConfig = segmentProbe.adtsConfig
                    if (index == 0) {
                        expectedAdtsConfig = adtsConfig
                        if (segmentVideoTrack != -1) videoTrackIndex = muxer.addTrack(probe.getTrackFormat(segmentVideoTrack))
                        if (segmentAudioTrack != -1) {
                            val format = probe.getTrackFormat(segmentAudioTrack)
                            if (format.getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm" && adtsConfig != null) {
                                format.setByteBuffer("csd-0", ByteBuffer.wrap(adtsConfig))
                            }
                            audioTrackIndex = muxer.addTrack(format)
                        }
                        if (videoTrackIndex == -1 && audioTrackIndex == -1) {
                            throw MuxException("First segment has neither a video nor an audio track")
                        }
                        muxer.start()
                        muxerStarted = true
                    } else if ((adtsConfig == null) != (expectedAdtsConfig == null) ||
                        (adtsConfig != null && !adtsConfig.contentEquals(expectedAdtsConfig))
                    ) {
                        throw MuxException("AAC framing or format changed in segment ${index + 1}")
                    }
                } finally {
                    probe.release()
                }

                // Reopen at the start and copy in the extractor's interleaved sample order.
                // Writing a whole video segment before its audio produced poorly interleaved MP4s.
                val extractor = openExtractor(segmentFile)
                try {
                    if (segmentVideoTrack != -1) extractor.selectTrack(segmentVideoTrack)
                    if (segmentAudioTrack != -1) extractor.selectTrack(segmentAudioTrack)
                    var videoSamples = 0
                    var audioSamples = 0
                    while (true) {
                        checkActive()
                        buffer.clear()
                        val size = extractor.readSampleData(buffer, 0)
                        if (size < 0) break
                        val inputTrack = extractor.sampleTrackIndex
                        val video = inputTrack == segmentVideoTrack
                        val outputTrack = if (video) videoTrackIndex else audioTrackIndex
                        if (outputTrack < 0) throw MuxException("Unexpected track in segment ${index + 1}")
                        val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                        val frames = if (!video && audioIsAac) AacAdts.frames(buffer, size) else null
                        if (!video && audioIsAac && (frames != null) != (adtsConfig != null)) {
                            throw MuxException("AAC framing changed in segment ${index + 1}")
                        }
                        if (frames != null) {
                            var sampleUs = extractor.sampleTime
                            for (frame in frames) {
                                if (!frame.audioSpecificConfig.contentEquals(adtsConfig)) {
                                    throw MuxException("AAC format changed in segment ${index + 1}")
                                }
                                val timeUs = timeline.timestamp(sampleUs, originUs, video = false)
                                bufferInfo.set(frame.payloadOffset, frame.payloadSize, timeUs, flags)
                                muxer.writeSampleData(outputTrack, buffer, bufferInfo)
                                audioSamples++
                                sampleUs += frame.durationUs
                            }
                        } else {
                            val timeUs = timeline.timestamp(extractor.sampleTime, originUs, video)
                            bufferInfo.set(0, size, timeUs, flags)
                            muxer.writeSampleData(outputTrack, buffer, bufferInfo)
                            if (video) videoSamples++ else audioSamples++
                        }
                        extractor.advance()
                    }
                    if ((segmentVideoTrack != -1 && videoSamples == 0) ||
                        (segmentAudioTrack != -1 && audioSamples == 0)
                    ) throw MuxException("Segment ${index + 1} has an empty audio or video track")
                    timeline.finishSegment(durationsSeconds[index])
                } finally {
                    extractor.release()
                }
            }
            // Give MP4 a real end time for each final sample. Otherwise some readers infer a
            // zero-duration tail, which can leave playback waiting at the end of the file.
            if (videoTrackIndex != -1) writeEndOfTrack(muxer, videoTrackIndex, timeline.segmentStartUs, buffer, bufferInfo)
            if (audioTrackIndex != -1) writeEndOfTrack(muxer, audioTrackIndex, timeline.segmentStartUs, buffer, bufferInfo)
            completed = true
        } finally {
            // Nested so muxer.release() always runs — including when muxer.stop() itself throws
            // — rather than leaking the native muxer if finalization fails partway through.
            try {
                if (muxerStarted && completed) {
                    try {
                        muxer.stop()
                    } catch (e: Exception) {
                        throw MuxException("Muxer failed to finalize the output file", e)
                    }
                }
            } finally {
                muxer.release()
            }
        }
        // A successful MediaMuxer.stop() alone does not prove that the saved MP4 contains
        // readable samples. Reject it before the downloader publishes a completed file.
        val verified = openExtractor(File(outputPath))
        try {
            val video = findTrack(verified, "video/")
            val audio = findTrack(verified, "audio/")
            if ((video != -1) != (videoTrackIndex != -1) ||
                (audio != -1) != (audioTrackIndex != -1)
            ) throw MuxException("Muxed MP4 is missing an audio or video track")
            firstSharedSampleTime(verified, video, audio, 0, buffer)
        } finally {
            verified.release()
        }
    }

    private fun writeEndOfTrack(
        muxer: MediaMuxer,
        trackIndex: Int,
        timeUs: Long,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ) {
        buffer.clear()
        bufferInfo.set(0, 0, timeUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
        muxer.writeSampleData(trackIndex, buffer, bufferInfo)
    }

    private data class SegmentProbe(val originUs: Long, val adtsConfig: ByteArray?)

    private fun firstSharedSampleTime(
        extractor: MediaExtractor, videoTrack: Int, audioTrack: Int, index: Int, buffer: ByteBuffer
    ): SegmentProbe {
        if (videoTrack != -1) extractor.selectTrack(videoTrack)
        if (audioTrack != -1) extractor.selectTrack(audioTrack)
        var videoFirst = -1L
        var audioFirst = -1L
        var adtsConfig: ByteArray? = null
        while ((videoTrack != -1 && videoFirst < 0L) || (audioTrack != -1 && audioFirst < 0L)) {
            val track = extractor.sampleTrackIndex
            if (track < 0) break
            if (track == videoTrack && videoFirst < 0L) videoFirst = extractor.sampleTime
            if (track == audioTrack && audioFirst < 0L) {
                audioFirst = extractor.sampleTime
                if (extractor.getTrackFormat(audioTrack).getString(MediaFormat.KEY_MIME) == "audio/mp4a-latm") {
                    buffer.clear()
                    val size = extractor.readSampleData(buffer, 0)
                    if (size > 0) adtsConfig = AacAdts.frames(buffer, size)?.firstOrNull()?.audioSpecificConfig
                }
            }
            extractor.advance()
        }
        if ((videoTrack != -1 && videoFirst < 0L) || (audioTrack != -1 && audioFirst < 0L)) {
            throw MuxException("Segment ${index + 1} has an empty audio or video track")
        }
        val origin = listOf(videoFirst, audioFirst).filter { it >= 0L }.minOrNull()
            ?: throw MuxException("Segment ${index + 1} contains no media samples")
        return SegmentProbe(origin, adtsConfig)
    }

    /** Copies every sample of one track from one segment, rebased to start at [startOffsetUs]; returns the new offset for the next segment. */
    private fun copyTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        startOffsetUs: Long,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo,
        checkActive: () -> Unit
    ): Long {
        extractor.selectTrack(trackIndex)
        var firstSampleUs = -1L
        var lastSampleUs = startOffsetUs
        try {
            while (true) {
                checkActive()
                buffer.clear()
                val size = extractor.readSampleData(buffer, 0)
                if (size < 0) break
                if (firstSampleUs == -1L) firstSampleUs = extractor.sampleTime
                val rebasedTimeUs = startOffsetUs + (extractor.sampleTime - firstSampleUs)
                lastSampleUs = rebasedTimeUs
                val flags = if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                bufferInfo.set(0, size, rebasedTimeUs, flags)
                muxer.writeSampleData(muxerTrackIndex, buffer, bufferInfo)
                extractor.advance()
            }
        } finally {
            extractor.unselectTrack(trackIndex)
        }
        return lastSampleUs
    }

    private fun findTrack(extractor: MediaExtractor, mimePrefix: String): Int {
        for (i in 0 until extractor.trackCount) {
            val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith(mimePrefix)) return i
        }
        return -1
    }

    /**
     * Muxes up to one video-only and one audio-only container into a single .mp4 — the DASH
     * case (see DashDownloader), where each input file is already a complete, continuously
     * timestamped track on its own (a CMAF init segment concatenated with its media segments is
     * a valid fMP4 by construction). Simpler than [mux]: no cross-file timestamp rebasing is
     * needed, since there's exactly one "segment" per track here, not many to stitch together.
     */
    fun muxTracks(videoFile: File?, audioFile: File?, outputPath: String, checkActive: () -> Unit = {}) {
        if (videoFile == null && audioFile == null) throw MuxException("No tracks to mux")

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        val bufferInfo = MediaCodec.BufferInfo()
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)
        var videoExtractor: MediaExtractor? = null
        var audioExtractor: MediaExtractor? = null

        try {
            videoExtractor = videoFile?.let { openExtractor(it) }
            audioExtractor = audioFile?.let { openExtractor(it) }
            val videoTrackInFile = videoExtractor?.let { findTrack(it, "video/") }?.takeIf { it != -1 }
            val audioTrackInFile = audioExtractor?.let { findTrack(it, "audio/") }?.takeIf { it != -1 }

            val videoTrackIndex = if (videoExtractor != null && videoTrackInFile != null) {
                muxer.addTrack(videoExtractor.getTrackFormat(videoTrackInFile))
            } else -1
            val audioTrackIndex = if (audioExtractor != null && audioTrackInFile != null) {
                muxer.addTrack(audioExtractor.getTrackFormat(audioTrackInFile))
            } else -1
            if (videoTrackIndex == -1 && audioTrackIndex == -1) {
                throw MuxException("Neither file has a usable video or audio track")
            }
            muxer.start()
            muxerStarted = true

            if (videoExtractor != null && videoTrackInFile != null && videoTrackIndex != -1) {
                copyTrack(videoExtractor, videoTrackInFile, muxer, videoTrackIndex, 0L, buffer, bufferInfo, checkActive)
            }
            if (audioExtractor != null && audioTrackInFile != null && audioTrackIndex != -1) {
                copyTrack(audioExtractor, audioTrackInFile, muxer, audioTrackIndex, 0L, buffer, bufferInfo, checkActive)
            }
        } finally {
            videoExtractor?.release()
            audioExtractor?.release()
            // Nested so muxer.release() always runs — including when muxer.stop() itself throws.
            try {
                if (muxerStarted) {
                    try {
                        muxer.stop()
                    } catch (e: Exception) {
                        throw MuxException("Muxer failed to finalize the output file", e)
                    }
                }
            } finally {
                muxer.release()
            }
        }
    }

    private fun openExtractor(file: File): MediaExtractor {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
        } catch (e: Exception) {
            throw MuxException("${file.name} isn't a readable media container", e)
        }
        return extractor
    }
}
