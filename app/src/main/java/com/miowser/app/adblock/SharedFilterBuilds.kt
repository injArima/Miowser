package com.miowser.app.adblock

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

/** Deduplicate by actual saved inputs. A later observer cannot obsolete the same build. */
internal class SharedFilterBuilds<K>(private val executor: Executor) {
    private val inFlight = mutableMapOf<K, CompletableFuture<Boolean>>()

    @Synchronized fun submit(inputs: K, build: () -> Boolean): CompletableFuture<Boolean> {
        val shared = inFlight[inputs] ?: CompletableFuture<Boolean>().also { receipt ->
            inFlight[inputs] = receipt
            try {
                executor.execute {
                    val result = runCatching(build)
                    synchronized(this) { if (inFlight[inputs] === receipt) inFlight.remove(inputs) }
                    result.fold(receipt::complete, receipt::completeExceptionally)
                }
            } catch (error: Exception) {
                inFlight.remove(inputs)
                receipt.completeExceptionally(error)
            }
        }
        return shared.thenApply { it }
    }
}
