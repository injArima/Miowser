package com.miowser.app.downloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import org.json.JSONObject

private const val DATA_URL_MAX_BYTES = 32 * 1024 * 1024
private const val BLOB_MAX_BYTES = 256L * 1024L * 1024L
private const val BLOB_CHUNK_BYTES = 48 * 1024
private const val WRITE_CHUNK_BYTES = 64 * 1024
private const val BLOB_INACTIVITY_TIMEOUT_NANOS = 120L * 1_000_000_000L

/**
 * Per-WebView bridge for renderer-owned downloads. Every transfer is authorized by an unguessable
 * native-created token and bound to the current page generation. A navigation invalidates all of
 * that page's tokens and removes partial output, so stale or page-forged bridge messages cannot
 * continue writing.
 */
class RendererResourceDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pageGeneration = AtomicLong(0L)
    private val sessions = ConcurrentHashMap<String, BlobSession>()

    fun invalidatePage() {
        pageGeneration.incrementAndGet()
        sessions.values.toList().forEach { session ->
            session.download.requestCancel() // wakes a paused bridge callback before cleanup
            failSession(session, "Page changed before this download finished")
        }
    }

    /** Called on DownloadQueue's worker, and deliberately blocks that worker until this transfer ends. */
    fun run(webView: WebView, sourceUrl: String, download: Download) {
        when (rendererResourceKind(sourceUrl)) {
            RendererResourceKind.DATA -> runDataUrl(sourceUrl, download)
            RendererResourceKind.BLOB -> runBlobUrl(webView, sourceUrl, download)
            null -> fail(download, "Unsupported page-generated URL")
        }
    }

    private fun runDataUrl(sourceUrl: String, download: Download) {
        download.enterStage(DownloadStatus.DOWNLOADING)
        when (val decoded = DataUrlDecoder.decode(sourceUrl, DATA_URL_MAX_BYTES)) {
            is DataUrlDecodeResult.Failure -> fail(download, decoded.message)
            is DataUrlDecodeResult.Success -> writeNativeBytes(download, decoded.value)
        }
    }

    private fun writeNativeBytes(download: Download, decoded: DecodedDataUrl) {
        if (decoded.bytes.isEmpty()) {
            fail(download, "Page-generated download is empty")
            return
        }
        download.totalBytes = decoded.bytes.size.toLong()
        val identity = resolveIdentity(download, decoded.mimeType, decoded.bytes.take(64 * 1024).toByteArray())
        download.displayName = identity.fileName
        val opened = MediaStoreOutput.open(appContext, identity.fileName, identity.mimeType)
        if (opened == null) {
            fail(download, "Could not create output file")
            return
        }
        MediaStoreOutput.legacyFileName(opened.uri)?.let { download.displayName = it }
        try {
            opened.stream.use { output ->
                var offset = 0
                while (offset < decoded.bytes.size) {
                    download.awaitIfPaused()
                    if (download.isCanceled) {
                        MediaStoreOutput.abandon(appContext, opened.uri)
                        download.status = DownloadStatus.CANCELED
                        return
                    }
                    val count = minOf(WRITE_CHUNK_BYTES, decoded.bytes.size - offset)
                    output.write(decoded.bytes, offset, count)
                    offset += count
                    download.bytesDownloaded = offset.toLong()
                }
            }
            MediaStoreOutput.finish(appContext, opened.uri, identity.mimeType)
            download.savedUri = opened.uri
            download.status = DownloadStatus.COMPLETED
        } catch (e: Exception) {
            MediaStoreOutput.abandon(appContext, opened.uri)
            fail(download, e.message ?: "Could not save page-generated download")
        }
    }

    private fun runBlobUrl(webView: WebView, sourceUrl: String, download: Download) {
        download.enterStage(DownloadStatus.DOWNLOADING)
        val token = UUID.randomUUID().toString()
        val session = BlobSession(token, pageGeneration.get(), download)
        sessions[token] = session
        mainHandler.post {
            if (session.generation != pageGeneration.get()) {
                failSession(session, "Page changed before this download started")
                return@post
            }
            webView.evaluateJavascript(blobTransferScript(token, sourceUrl), null)
        }

        while (!session.finished.await(1, TimeUnit.SECONDS)) {
            when {
                download.isCanceled -> cancelSession(session)
                session.generation != pageGeneration.get() -> failSession(session, "Page changed before this download finished")
                System.nanoTime() - session.lastActivityNanos > BLOB_INACTIVITY_TIMEOUT_NANOS ->
                    failSession(session, "The page stopped sending download data")
            }
        }
    }

    @JavascriptInterface
    fun begin(token: String, mimeType: String, totalBytes: String): Boolean {
        val session = validSession(token) ?: return false
        val size = totalBytes.toLongOrNull() ?: return failSession(session, "Invalid blob size")
        synchronized(session) {
            if (session.terminal || session.begun) return false
            if (!session.ledger.begin(size)) {
                return failSession(
                    session,
                    if (size > BLOB_MAX_BYTES) "Page-generated download exceeds the 256 MiB safety limit"
                    else "Invalid blob size"
                )
            }
            session.begun = true
            session.mimeType = mimeType.substringBefore(';').trim().take(200).ifBlank { "application/octet-stream" }
            session.download.totalBytes = size
            session.lastActivityNanos = System.nanoTime()
        }
        return true
    }

    @JavascriptInterface
    fun chunk(token: String, index: Int, encodedBytes: String): Boolean {
        val session = validSession(token) ?: return false
        if (!session.begun || index < 0 || encodedBytes.length > 70_000) {
            return failSession(session, "Invalid blob download chunk")
        }
        session.download.awaitIfPaused()
        if (session.download.isCanceled) return cancelSession(session)
        val bytes = try {
            Base64.decode(encodedBytes, Base64.NO_WRAP)
        } catch (_: IllegalArgumentException) {
            return failSession(session, "Invalid blob download data")
        }
        if (bytes.isEmpty() || bytes.size > BLOB_CHUNK_BYTES) {
            return failSession(session, "Invalid blob download chunk size")
        }

        synchronized(session) {
            if (session.terminal || index != session.nextChunkIndex) return false
            if (!session.ledger.acceptChunk(index, bytes.size)) {
                return failSession(session, "Blob download chunks were out of order or exceeded the declared size")
            }

            if (session.opened == null) {
                val identity = resolveIdentity(session.download, session.mimeType, bytes)
                session.identityMimeType = identity.mimeType
                session.download.displayName = identity.fileName
                session.opened = MediaStoreOutput.open(appContext, identity.fileName, identity.mimeType)
                    ?: return failSession(session, "Could not create output file")
                MediaStoreOutput.legacyFileName(session.opened!!.uri)?.let {
                    session.download.displayName = it
                }
            }
            try {
                session.opened!!.stream.write(bytes)
            } catch (e: Exception) {
                return failSession(session, e.message ?: "Could not save blob data")
            }
            session.nextChunkIndex++
            session.download.bytesDownloaded = session.ledger.receivedBytes
            session.lastActivityNanos = System.nanoTime()
        }
        return true
    }

    @JavascriptInterface
    fun finish(token: String): Boolean {
        val session = validSession(token) ?: return false
        synchronized(session) {
            if (session.terminal || !session.begun) return false
            if (!session.ledger.isComplete() || session.opened == null) {
                return failSession(session, "Blob download ended before all bytes arrived")
            }
            return try {
                session.opened!!.stream.close()
                MediaStoreOutput.finish(appContext, session.opened!!.uri, session.identityMimeType)
                session.download.savedUri = session.opened!!.uri
                session.download.status = DownloadStatus.COMPLETED
                completeSession(session)
                true
            } catch (e: Exception) {
                failSession(session, e.message ?: "Could not finish blob download")
            }
        }
    }

    @JavascriptInterface
    fun fail(token: String, message: String) {
        val session = validSession(token) ?: return
        val detail = message.take(300)
        failSession(
            session,
            "This blob cannot be read as a file (MediaSource streams require segment capture)" +
                if (detail.isBlank()) "" else ": $detail"
        )
    }

    private fun validSession(token: String): BlobSession? = sessions[token]?.takeIf {
        !it.terminal && it.generation == pageGeneration.get()
    }

    private fun cancelSession(session: BlobSession): Boolean {
        synchronized(session) {
            if (session.terminal) return false
            closeAndAbandon(session)
            session.download.status = DownloadStatus.CANCELED
            completeSession(session)
        }
        return false
    }

    private fun failSession(session: BlobSession, message: String): Boolean {
        synchronized(session) {
            if (session.terminal) return false
            closeAndAbandon(session)
            session.download.errorMessage = message
            session.download.status = DownloadStatus.FAILED
            completeSession(session)
        }
        return false
    }

    private fun completeSession(session: BlobSession) {
        session.terminal = true
        sessions.remove(session.token, session)
        session.finished.countDown()
    }

    private fun closeAndAbandon(session: BlobSession) {
        session.opened?.let { opened ->
            runCatching { opened.stream.close() }
            runCatching { MediaStoreOutput.abandon(appContext, opened.uri) }
        }
    }

    private fun resolveIdentity(download: Download, mimeType: String, signature: ByteArray): ResolvedDownloadIdentity =
        DownloadIdentityResolver.resolve(
            DownloadIdentityInput(
                originalUrl = download.sourceUrl,
                finalUrl = null,
                contentDisposition = null,
                declaredMimeType = mimeType,
                // A page title is only a basename hint, never a trustworthy extension (titles
                // such as "example.com" would otherwise beat the Blob's real video/mp4 MIME).
                // DownloadListener's genuine suggested extension is safe to reattach.
                browserSuggestedName = download.suggestedExtension?.let {
                    "${download.outputBaseName}.$it"
                },
                fallbackBaseName = download.outputBaseName,
                preferredBaseName = download.preferredOutputBaseName,
                signature = signature
            )
        )

    private fun fail(download: Download, message: String) {
        download.errorMessage = message
        download.status = DownloadStatus.FAILED
    }

    private fun blobTransferScript(token: String, sourceUrl: String): String = """
        (function() {
            const token = ${JSONObject.quote(token)};
            const sourceUrl = ${JSONObject.quote(sourceUrl)};
            const bridge = window.MiowserDownloadBridge;
            if (!bridge) return;

            function base64Of(blob) {
                return new Promise(function(resolve, reject) {
                    const reader = new FileReader();
                    reader.onerror = function() { reject(reader.error || new Error('Could not read blob chunk')); };
                    reader.onload = function() {
                        const value = String(reader.result || '');
                        const comma = value.indexOf(',');
                        if (comma < 0) reject(new Error('Invalid blob chunk encoding'));
                        else resolve(value.substring(comma + 1));
                    };
                    reader.readAsDataURL(blob);
                });
            }

            (async function() {
                try {
                    const response = await fetch(sourceUrl);
                    const blob = await response.blob();
                    if (!bridge.begin(token, blob.type || 'application/octet-stream', String(blob.size))) return;
                    let index = 0;
                    for (let offset = 0; offset < blob.size; offset += $BLOB_CHUNK_BYTES) {
                        const encoded = await base64Of(blob.slice(offset, Math.min(offset + $BLOB_CHUNK_BYTES, blob.size)));
                        if (!bridge.chunk(token, index++, encoded)) return;
                    }
                    bridge.finish(token);
                } catch (error) {
                    bridge.fail(token, String(error && error.message ? error.message : error));
                }
            })();
        })();
    """.trimIndent()

    private class BlobSession(
        val token: String,
        val generation: Long,
        val download: Download
    ) {
        val finished = CountDownLatch(1)
        @Volatile var lastActivityNanos: Long = System.nanoTime()
        @Volatile var terminal = false
        var begun = false
        var mimeType = "application/octet-stream"
        var identityMimeType = "application/octet-stream"
        val ledger = BlobTransferLedger(BLOB_MAX_BYTES, BLOB_CHUNK_BYTES)
        var nextChunkIndex = 0
        var opened: MediaStoreOutput.Opened? = null
    }
}
