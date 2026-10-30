package com.miowser.app.downloader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import java.util.UUID
import java.util.WeakHashMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONObject

private const val DATA_URL_MAX_BYTES = 32 * 1024 * 1024
private const val BLOB_MAX_BYTES = 256L * 1024L * 1024L
private const val BLOB_CHUNK_BYTES = 48 * 1024
private const val WRITE_CHUNK_BYTES = 64 * 1024
private const val BLOB_INACTIVITY_TIMEOUT_NANOS = 120L * 1_000_000_000L

/** The listener is inert until a user-selected blob is sent to its owning frame. */
fun WebView.installRendererBlobFrameScript() {
    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
        WebViewCompat.addDocumentStartJavaScript(this, BLOB_FRAME_SCRIPT, setOf("*"))
    }
}

/**
 * Per-WebView bridge for renderer-owned downloads. Every transfer is authorized by an unguessable
 * native-created token and bound to the current page generation. A navigation invalidates all of
 * that page's tokens and removes partial output, so stale or page-forged bridge messages cannot
 * continue writing.
 */
class RendererResourceDownloader(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private val pageLifetime = RendererPageLifetime()
    private val sessions = ConcurrentHashMap<String, BlobSession>()
    private val boundDownloads = WeakHashMap<Download, Long>()

    fun invalidatePage() {
        pageLifetime.invalidate()
        invalidateSessions()
    }

    fun capturePageToken(download: Download): Long {
        val token = pageLifetime.capture()
        synchronized(boundDownloads) { boundDownloads[download] = token }
        return token
    }

    fun isCurrentPageToken(token: Long): Boolean = pageLifetime.failureFor(token) == null

    fun retire(reason: String) {
        pageLifetime.retire(reason)
        invalidateSessions()
    }

    private fun invalidateSessions() {
        val bound = synchronized(boundDownloads) { boundDownloads.entries.map { it.key to it.value } }
        var changed = false
        bound.forEach { (download, token) ->
            if (!download.rendererSourceUnavailable) pageLifetime.failureFor(token)?.let { reason ->
                download.invalidateRendererSource(reason)
                changed = true
            }
        }
        sessions.values.toList().forEach { session ->
            val reason = pageLifetime.failureFor(session.generation)
                ?: "The originating page is no longer available"
            session.download.invalidateRendererSource(reason)
            failSession(session, reason)
        }
        if (changed) DownloadQueue.persist(appContext)
    }

    /** Called on DownloadQueue's worker, and deliberately blocks that worker until this transfer ends. */
    fun run(webView: WebView, sourceUrl: String, download: Download, pageToken: Long) {
        pageLifetime.failureFor(pageToken)?.let { fail(download, it); return }
        when (rendererResourceKind(sourceUrl)) {
            RendererResourceKind.DATA -> runDataUrl(sourceUrl, download)
            RendererResourceKind.BLOB -> runBlobUrl(webView, sourceUrl, download, pageToken)
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
        MediaStoreOutput.actualFileName(appContext, opened.uri)?.let { download.displayName = it }
        try {
            opened.stream.use { output ->
                var offset = 0
                while (offset < decoded.bytes.size) {
                    download.awaitIfPaused()
                    check(!download.rendererSourceUnavailable) {
                        download.errorMessage ?: "The originating page is no longer available"
                    }
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
            check(!download.rendererSourceUnavailable) {
                download.errorMessage ?: "The originating page is no longer available"
            }
            MediaStoreOutput.finish(appContext, opened.uri, identity.mimeType)
            download.savedUri = opened.uri
            download.status = DownloadStatus.COMPLETED
        } catch (e: Exception) {
            MediaStoreOutput.abandon(appContext, opened.uri)
            download.awaitIfPaused()
            fail(download, e.message ?: "Could not save page-generated download")
        }
    }

    private fun runBlobUrl(webView: WebView, sourceUrl: String, download: Download, pageToken: Long) {
        download.enterStage(DownloadStatus.DOWNLOADING)
        val token = UUID.randomUUID().toString()
        val session = BlobSession(token, pageToken, download)
        sessions[token] = session
        mainHandler.post {
            val failure = pageLifetime.failureFor(session.generation)
            if (failure != null || session.terminal) {
                failSession(session, failure ?: "This download is no longer active")
                return@post
            }
            try {
                webView.evaluateJavascript(blobTransferScript(token, sourceUrl), null)
            } catch (_: Exception) {
                failSession(session, "The originating page is no longer available")
            }
        }

        while (!session.finished.await(1, TimeUnit.SECONDS)) {
            when {
                pageLifetime.failureFor(session.generation) != null ->
                    failSession(session, pageLifetime.failureFor(session.generation)!!)
                download.yieldRequested -> yieldSession(session)
                download.isCanceled -> cancelSession(session)
                System.nanoTime() - session.lastActivityNanos > BLOB_INACTIVITY_TIMEOUT_NANOS ->
                    failSession(session, "The page stopped sending download data")
            }
        }
        download.awaitIfPaused()
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
        if (session.download.yieldRequested) return yieldSession(session)
        if (session.download.rendererSourceUnavailable) {
            return failSession(session, session.download.errorMessage ?: "The originating page is no longer available")
        }
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
            pageLifetime.failureFor(session.generation)?.let { return failSession(session, it) }
            if (!session.ledger.acceptChunk(index, bytes.size)) {
                return failSession(session, "Blob download chunks were out of order or exceeded the declared size")
            }

            if (session.opened == null) {
                val identity = resolveIdentity(session.download, session.mimeType, bytes)
                session.identityMimeType = identity.mimeType
                session.download.displayName = identity.fileName
                session.opened = MediaStoreOutput.open(appContext, identity.fileName, identity.mimeType)
                    ?: return failSession(session, "Could not create output file")
                MediaStoreOutput.actualFileName(appContext, session.opened!!.uri)?.let {
                    session.download.displayName = it
                }
            }
            try {
                session.opened!!.stream.write(bytes)
            } catch (e: Exception) {
                return failSession(session, e.message ?: "Could not save blob data")
            }
            session.nextChunkIndex++
            val now = System.nanoTime()
            if (now - session.lastUiProgressNanos >= 250_000_000L ||
                (session.ledger.expectedBytes >= 0L && session.ledger.isComplete())) {
                session.download.bytesDownloaded = session.ledger.receivedBytes
                session.lastUiProgressNanos = now
            }
            session.lastActivityNanos = now
        }
        return true
    }

    @JavascriptInterface
    fun finish(token: String): Boolean {
        val session = validSession(token) ?: return false
        synchronized(session) {
            if (session.terminal || !session.begun) return false
            if (session.download.yieldRequested) return yieldSession(session)
            pageLifetime.failureFor(session.generation)?.let { return failSession(session, it) }
            if (!session.ledger.isComplete() || session.opened == null) {
                return failSession(session, "Blob download ended before all bytes arrived")
            }
            return try {
                session.opened!!.stream.close()
                MediaStoreOutput.finish(appContext, session.opened!!.uri, session.identityMimeType)
                session.download.bytesDownloaded = session.ledger.receivedBytes
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

    @JavascriptInterface
    fun mayStart(token: String): Boolean = validSession(token)?.let { session ->
        synchronized(session) { !session.begun }
    } ?: false

    private fun validSession(token: String): BlobSession? = sessions[token]?.takeIf {
        !it.terminal && pageLifetime.failureFor(it.generation) == null
    }

    private fun yieldSession(session: BlobSession): Boolean {
        synchronized(session) {
            if (!session.terminal) { closeAndAbandon(session); completeSession(session) }
        }
        return false
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

    private fun blobTransferScript(token: String, sourceUrl: String): String = BLOB_FRAME_SCRIPT + "\n" + """
        (function() {
            const token = ${JSONObject.quote(token)};
            const sourceUrl = ${JSONObject.quote(sourceUrl)};
            const bridge = window.MiowserDownloadBridge;
            if (!bridge) return;
            window.postMessage({__miowserBlobRequest: true, token: token, sourceUrl: sourceUrl}, '*');
            // If no injected frame claims this URL, try the main frame. This also supports
            // WebViews without document-start script support, where only this frame has a hook.
            setTimeout(function() {
                if (bridge.mayStart(token)) window.__miowserTransferBlob(token, sourceUrl, true);
            }, window.frames.length ? 3000 : 0);
        })();
    """.trimIndent()

    private class BlobSession(
        val token: String,
        val generation: Long,
        val download: Download
    ) {
        val finished = CountDownLatch(1)
        @Volatile var lastActivityNanos: Long = System.nanoTime()
        var lastUiProgressNanos: Long = 0L
        @Volatile var terminal = false
        var begun = false
        var mimeType = "application/octet-stream"
        var identityMimeType = "application/octet-stream"
        val ledger = BlobTransferLedger(BLOB_MAX_BYTES, BLOB_CHUNK_BYTES)
        var nextChunkIndex = 0
        var opened: MediaStoreOutput.Opened? = null
    }
}

private val BLOB_FRAME_SCRIPT = """
    (function() {
        if (window.__miowserBlobFrameReady) return;
        const bridge = window.MiowserDownloadBridge;
        if (!bridge) return;
        window.__miowserBlobFrameReady = true;

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

        window.__miowserTransferBlob = async function(token, sourceUrl, reportFailure) {
            let started = false;
            try {
                const response = await fetch(sourceUrl);
                if (!response.ok) throw new Error('Blob fetch failed: ' + response.status);
                const reader = response.body && response.body.getReader && response.body.getReader();
                if (!reader) throw new Error('This WebView cannot stream this blob safely');
                const rawLength = response.headers.get('content-length');
                const length = rawLength && /^\d+$/.test(rawLength) ? rawLength : '-1';
                const mime = response.headers.get('content-type') || 'application/octet-stream';
                if (!bridge.begin(token, mime, length)) { await reader.cancel(); return; }
                started = true;
                let index = 0;
                while (true) {
                    const next = await reader.read();
                    if (next.done) break;
                    const bytes = next.value;
                    for (let offset = 0; offset < bytes.length; offset += $BLOB_CHUNK_BYTES) {
                        const part = bytes.subarray(offset, Math.min(offset + $BLOB_CHUNK_BYTES, bytes.length));
                        const encoded = await base64Of(new Blob([part]));
                        if (!bridge.chunk(token, index++, encoded)) { await reader.cancel(); return; }
                    }
                }
                bridge.finish(token);
            } catch (error) {
                const detail = String(error && error.message ? error.message : error);
                if (started) {
                    bridge.fail(token, detail);
                } else if (reportFailure) {
                    // Give a slower embedded frame time to claim its own URL first.
                    setTimeout(function() {
                        if (bridge.mayStart(token)) bridge.fail(token, detail);
                    }, window.frames.length ? 3000 : 0);
                }
            }
        };

        window.addEventListener('message', function(event) {
            const request = event.data;
            if (!request || request.__miowserBlobRequest !== true ||
                typeof request.token !== 'string' || typeof request.sourceUrl !== 'string') return;
            const ownsVideo = Array.from(document.querySelectorAll('video, audio')).some(function(media) {
                return media.currentSrc === request.sourceUrl || media.src === request.sourceUrl;
            });
            let blobOrigin = null;
            try { blobOrigin = new URL(request.sourceUrl.substring(5)).origin; } catch (_) {}
            // DownloadListener can report a blob that is not attached to a video element.
            // Same-origin frames may attempt it; only the first successful begin owns the token.
            if (ownsVideo || (blobOrigin && location.origin === blobOrigin)) {
                window.__miowserTransferBlob(request.token, request.sourceUrl, false);
            }
            for (let i = 0; i < window.frames.length; i++) {
                window.frames[i].postMessage(request, '*');
            }
        });
    })();
""".trimIndent()
