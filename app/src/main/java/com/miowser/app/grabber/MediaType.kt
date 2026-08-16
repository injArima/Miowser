package com.miowser.app.grabber

enum class MediaType { HLS, DASH, MP4, WEBM, MATROSKA, AUDIO, SUBTITLE, UNKNOWN }

/** One detected candidate. See [com.miowser.app.grabber.MediaProbe] for how [isConfirmed] is decided. */
data class DetectedMedia(
    val url: String,
    val pageUrl: String,
    val type: MediaType,
    val isConfirmed: Boolean, // true = verified by fetching+sniffing the body/headers; false = a guess we couldn't check
    val sizeBytes: Long?, // exact for a direct file's Content-Length/-Range; for HLS an *estimate* extrapolated
                          // from one real sampled segment — see MediaProbe and isSizeApproximate
    val isSizeApproximate: Boolean, // true = sizeBytes is the sampled-segment estimate, not a verified total;
                                     // ported from 1DM's own RemoteFile.getApproxLengthFromThreadInfo, which
                                     // shows the same kind of number with a "~" prefix rather than claiming exactness
    val durationSeconds: Double?,
    val variantCount: Int?,
    val resolution: String?, // "WIDTHxHEIGHT" — from the manifest's best variant, or a sniffed <video> element's
                              // own dimensions; never known for a direct file from headers alone
    val isEncrypted: Boolean,
    val pageTitle: String?, // the tab's page title at detection time — the closest thing to a "video name"
                             // this layer can offer without deeper introspection; see DetectedMediaSheet's
                             // displayName() for how a blank/generic title falls back to the URL's filename
    val detectedAt: Long = System.currentTimeMillis()
)
