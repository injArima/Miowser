package com.miowser.app.downloader

/** A refreshed URL is intentionally absent: only the response and saved bytes establish identity. */
internal fun canReuseDirectRanges(
    plan: DownloadRecoveryPlan?,
    total: Long,
    fingerprint: String,
    etag: String?,
    lastModified: String?,
    fileName: String,
    payloadLength: Long
): Boolean {
    if (plan == null || plan.transferKind != RecoveryTransferKind.DIRECT_RANGES ||
        plan.totalLength != total || plan.sourceFingerprint != fingerprint ||
        plan.outputFileName != fileName || payloadLength < total || plan.ranges.isEmpty()
    ) return false
    return validatorsCompatible(plan.etag, etag, plan.lastModified, lastModified)
}

private fun validatorsCompatible(
    oldEtag: String?, newEtag: String?, oldModified: String?, newModified: String?
): Boolean {
    if (oldEtag != null || newEtag != null) return oldEtag != null && oldEtag == newEtag
    if (oldModified != null || newModified != null) return oldModified != null && oldModified == newModified
    return true
}
