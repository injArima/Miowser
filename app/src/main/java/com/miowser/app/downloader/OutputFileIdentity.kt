package com.miowser.app.downloader

/** Final safety gate shared by every path that publishes into public Downloads. */
object OutputFileIdentity {
    fun validFileName(name: String): Boolean =
        name.length in 1..240 && name == name.trim() && name != "." && name != ".." &&
            !name.startsWith('.') && !name.endsWith('.') &&
            name.none { it in "/\\<>:\"|?*" || Character.isISOControl(it) }

    fun validMimeType(mimeType: String): Boolean =
        mimeType.length in 3..127 &&
            mimeType.matches(Regex("[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+"))
}
