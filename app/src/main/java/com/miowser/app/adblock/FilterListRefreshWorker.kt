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
        AdBlockFilters.refreshAllBlocking(applicationContext)
        return Result.success()
    }
}
