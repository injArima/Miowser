package com.miowser.app.adblock

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.miowser.app.network.NativeConnections
import java.net.HttpURLConnection
import java.util.concurrent.CancellationException

/** Refreshes enabled subscriptions; catch-up jobs can target only stale sources. */
class FilterListRefreshWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {
    @Volatile private var activeConnection: HttpURLConnection? = null

    override fun doWork(): Result {
        return try {
            val requestedIds = inputData.getStringArray("subscription_ids")?.toSet()
            val queuedAt = inputData.getLong("queued_at", 0L)
            val remainingIds = requestedIds?.let { ids ->
                FilterSubscriptionStore.loadSubscriptions(applicationContext)
                    .filter { it.id in ids && it.enabled && (it.lastError != null || it.lastUpdatedAt < queuedAt) }
                    .mapTo(HashSet()) { it.id }
            }
            if (remainingIds != null && remainingIds.isEmpty()) return Result.success()
            val refreshed = AdBlockFilters.refreshAllBlocking(applicationContext,
                checkActive = { if (isStopped) throw CancellationException("Filter refresh stopped") },
                onConnection = { connection ->
                    activeConnection = connection
                    if (isStopped) connection?.let(NativeConnections::close)
                },
                onlyIds = remainingIds)
            if (refreshed.failed > 0 || !refreshed.applied) retryOrFail() else Result.success()
        } catch (_: Exception) {
            retryOrFail()
        } finally {
            activeConnection = null
        }
    }

    override fun onStopped() {
        activeConnection?.let(NativeConnections::close)
    }

    private fun retryOrFail(): Result = if (runAttemptCount < 4) Result.retry() else Result.failure()
}
