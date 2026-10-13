package com.miowser.app.adblock

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

/** Refreshes every enabled default/custom subscription; old cached rules survive failures. */
class FilterListRefreshWorker(
    context: Context,
    workerParameters: WorkerParameters
) : Worker(context, workerParameters) {
    override fun doWork(): Result {
        return try {
            val refreshed = AdBlockFilters.refreshAllBlocking(applicationContext)
            if (refreshed.failed > 0 || !refreshed.applied) retryOrFail() else Result.success()
        } catch (_: Exception) {
            retryOrFail()
        }
    }

    private fun retryOrFail(): Result = if (runAttemptCount < 4) Result.retry() else Result.failure()
}
