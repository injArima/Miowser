package com.miowser.app.downloader

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val PAUSABLE_BUFFER_SIZE = 64 * 1024

/** Reads network data cooperatively, blocking the worker whenever its download is paused. */
internal fun InputStream.readBytesPausable(download: Download): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(PAUSABLE_BUFFER_SIZE)
    while (true) {
        download.awaitIfPaused()
        if (download.isCanceled) break
        val read = read(buffer)
        if (read == -1) break
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}
