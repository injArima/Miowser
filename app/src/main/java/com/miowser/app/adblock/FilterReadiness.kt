package com.miowser.app.adblock

import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.selects.select
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** One startup attempt shared by passive warmup and navigation. Observers never own the build. */
internal class FilterReadiness(
    private val usable: () -> Boolean,
    private val load: () -> CompletableFuture<Boolean>,
    private val failure: () -> String?,
    private val maxAttempts: Int = 3
) {
    private var pending: CompletableFuture<Unit>? = null

    @Synchronized fun start(): CompletableFuture<Unit> {
        if (usable()) return CompletableFuture.completedFuture(Unit)
        val shared = pending?.takeUnless { it.isDone } ?: CompletableFuture<Unit>().also {
            pending = it
            attempt(it, 1)
        }
        // A cancelled observer cannot cancel the shared attempt or another observer.
        return shared.thenApply { }
    }

    private fun attempt(shared: CompletableFuture<Unit>, number: Int) {
        val loading = try { load() } catch (error: Exception) {
            shared.completeExceptionally(error)
            return
        }
        loading.whenComplete { _, error ->
            when {
                usable() -> shared.complete(Unit)
                error != null -> shared.completeExceptionally(error)
                failure() != null || number >= maxAttempts -> shared.completeExceptionally(
                    IllegalStateException(failure() ?: "Saved filter inputs kept changing; retry browser preparation"))
                else -> attempt(shared, number + 1)
            }
        }
    }

    suspend fun await(timeoutMillis: Long = 60_000) {
        if (usable()) return
        val observer = start()
        val ready = withTimeoutOrNull(timeoutMillis) {
            suspendCancellableCoroutine<Unit> { continuation ->
                observer.whenComplete { _, error ->
                    if (error == null) continuation.resume(Unit)
                    else {
                        var cause = error
                        while ((cause is CompletionException || cause is ExecutionException) && cause.cause != null) {
                            cause = cause.cause!!
                        }
                        continuation.resumeWithException(cause)
                    }
                }
                continuation.invokeOnCancellation { observer.cancel(false) }
            }
            true
        }
        check(ready == true) { failure() ?: "Saved filter loading timed out; retry browser preparation" }
    }

    /** Turning protection off releases navigation, while shared restoration remains independent. */
    suspend fun awaitWhileEnabled(enabled: Flow<Boolean>, timeoutMillis: Long = 60_000) = coroutineScope {
        val disabled = async(start = CoroutineStart.UNDISPATCHED) { enabled.first { !it }; Unit }
        val loading = async(start = CoroutineStart.LAZY) { await(timeoutMillis) }
        try {
            select<Unit> {
                disabled.onAwait { }
                loading.onAwait { }
            }
        } finally {
            disabled.cancel()
            loading.cancel()
        }
    }
}
