package com.miowser.app.downloader

/** One shared clock for both tracks of every HLS segment. */
internal class HlsMuxTimeline {
    var segmentStartUs: Long = 0L
        private set
    private var lastVideoUs = -1L
    private var lastAudioUs = -1L

    fun timestamp(sampleUs: Long, segmentOriginUs: Long, video: Boolean): Long {
        val proposed = segmentStartUs + (sampleUs - segmentOriginUs).coerceAtLeast(0L)
        // Video samples can arrive in decode order with out-of-order presentation times
        // (B-frames). Clamping every video PTS destroys their composition timing.
        val result = if (video) proposed else maxOf(proposed, lastAudioUs + 1L)
        if (video) lastVideoUs = maxOf(lastVideoUs, result) else lastAudioUs = result
        return result
    }

    fun finishSegment(durationSeconds: Double) {
        val declaredUs = if (durationSeconds.isFinite() && durationSeconds > 0.0) {
            (durationSeconds * 1_000_000.0).toLong()
        } else 0L
        segmentStartUs = maxOf(segmentStartUs + declaredUs, lastVideoUs + 1L, lastAudioUs + 1L)
    }
}
