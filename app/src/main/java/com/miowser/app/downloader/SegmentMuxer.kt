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
 * Each segment's sample timestamps are rebased to continue exactly where the previous segment's
 * last sample (per track) left off, rather than trusting each segment's own PTS to already be
 * globally continuous across the concatenation boundary — true for most VOD HLS but not
 * guaranteed, and wrong here would show up as an audio/video sync jump or a stutter at every
 * segment join.
 *
 * Scope: classic MPEG-TS segments only. CMAF/fMP4 segments (an #EXT-X-MAP initialization segment
 * carrying the moov box separately from the media segments) aren't handled — MediaExtractor would
 * fail to find valid tracks in a bare fMP4 media segment without its init segment prepended, and
 * this doesn't attempt that. The test stream this was verified against uses plain .ts segments.
 */
object SegmentMuxer {
    class MuxException(message: String, cause: Throwable? = null) : Exception(message, cause)

    fun mux(segmentFiles: List<File>, outputPath: String) {
        if (segmentFiles.isEmpty()) throw MuxException("No segments to mux")

        val muxer = MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        var muxerStarted = false
        var videoTrackIndex = -1
        var audioTrackIndex = -1
        var videoTimeOffsetUs = 0L
        var audioTimeOffsetUs = 0L
        val bufferInfo = MediaCodec.BufferInfo()
        // 2MB is generous for one compressed TS sample (a single video frame or audio unit) —
        // this is a per-sample transfer buffer, not a whole-segment buffer.
        val buffer = ByteBuffer.allocate(2 * 1024 * 1024)

        try {
            for ((index, segmentFile) in segmentFiles.withIndex()) {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(segmentFile.absolutePath)
                } catch (e: Exception) {
                    throw MuxException("Segment ${segmentFile.name} isn't a readable media container", e)
                }

                val segmentVideoTrack = findTrack(extractor, "video/")
                val segmentAudioTrack = findTrack(extractor, "audio/")

                if (index == 0) {
                    if (segmentVideoTrack != -1) videoTrackIndex = muxer.addTrack(extractor.getTrackFormat(segmentVideoTrack))
                    if (segmentAudioTrack != -1) audioTrackIndex = muxer.addTrack(extractor.getTrackFormat(segmentAudioTrack))
                    if (videoTrackIndex == -1 && audioTrackIndex == -1) {
                        throw MuxException("First segment has neither a video nor an audio track")
                    }
                    muxer.start()
                    muxerStarted = true
                }

                if (segmentVideoTrack != -1 && videoTrackIndex != -1) {
                    videoTimeOffsetUs = copyTrack(extractor, segmentVideoTrack, muxer, videoTrackIndex, videoTimeOffsetUs, buffer, bufferInfo)
                }
                if (segmentAudioTrack != -1 && audioTrackIndex != -1) {
                    audioTimeOffsetUs = copyTrack(extractor, segmentAudioTrack, muxer, audioTrackIndex, audioTimeOffsetUs, buffer, bufferInfo)
                }

                extractor.release()
            }
        } finally {
            // Nested so muxer.release() always runs — including when muxer.stop() itself throws
            // — rather than leaking the native muxer if finalization fails partway through.
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

    /** Copies every sample of one track from one segment, rebased to start at [startOffsetUs]; returns the new offset for the next segment. */
    private fun copyTrack(
        extractor: MediaExtractor,
        trackIndex: Int,
        muxer: MediaMuxer,
        muxerTrackIndex: Int,
        startOffsetUs: Long,
        buffer: ByteBuffer,
        bufferInfo: MediaCodec.BufferInfo
    ): Long {
        extractor.selectTrack(trackIndex)
        var firstSampleUs = -1L
        var lastSampleUs = startOffsetUs
        try {
            while (true) {
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
}
