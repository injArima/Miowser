package com.miowser.app.downloader

import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val PAUSABLE_BUFFER_SIZE = 64 * 1024

/** Reads bounded metadata cooperatively, yielding ownership when paused. */
internal fun InputStream.readBytesPausable(download: Download, maximumBytes: Int = 4 * 1024 * 1024): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(PAUSABLE_BUFFER_SIZE)
    while (true) {
        download.awaitIfPaused()
        if (download.isCanceled) break
        val read = read(buffer)
        if (read == -1) break
        require(read <= maximumBytes - output.size()) { "Response exceeds its metadata limit" }
        output.write(buffer, 0, read)
    }
    return output.toByteArray()
}

/** Cooperates between fixed-size chunks, including local concatenation before muxing. */
internal fun InputStream.copyPausableTo(output: java.io.OutputStream, download: Download): Long {
    val buffer = ByteArray(PAUSABLE_BUFFER_SIZE)
    var total = 0L
    while (true) {
        download.checkTransferActive()
        val count = read(buffer)
        if (count < 0) return total
        output.write(buffer, 0, count)
        total += count
    }
}
