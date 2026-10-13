package com.miowser.app.startup

/** Completed stages survive cancellation; concurrent warm/explicit requests share each stage. */
internal class PreparationPipeline(private val stages: List<suspend () -> Unit>) {
    private val receipts = stages.map { StartupTask<Unit>() }
    @Volatile var ready = false
        private set

    suspend fun prepare(checkpoint: suspend () -> Unit = {}) {
        stages.forEachIndexed { index, stage ->
            receipts[index].get { checkpoint(); stage() }
        }
        ready = true
    }
}
