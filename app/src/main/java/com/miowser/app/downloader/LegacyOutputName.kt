package com.miowser.app.downloader

/** Candidate names for a legacy public Downloads file that must never replace an existing file. */
internal fun legacyOutputName(requestedName: String, copyNumber: Int): String {
    require(copyNumber >= 1)
    val dot = requestedName.lastIndexOf('.')
    val hasExtension = dot > 0 && dot < requestedName.lastIndex
    val base = if (hasExtension) requestedName.substring(0, dot) else requestedName
    val extension = if (hasExtension) requestedName.substring(dot) else ""
    return if (copyNumber == 1) requestedName else "$base ($copyNumber)$extension"
}
