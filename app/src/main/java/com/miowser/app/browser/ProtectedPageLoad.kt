package com.miowser.app.browser

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** A tab can be created immediately; its first network navigation waits for saved protection. */
internal class ProtectedPageLoad(
    private val scope: CoroutineScope,
    private val ready: () -> Boolean,
    private val prepare: suspend () -> Unit
) {
    private var generation = 0L
    private var pending: Job? = null
    val waiting: Boolean get() = pending?.isActive == true

    fun execute(alive: () -> Boolean, failure: (Exception) -> Unit, navigate: () -> Unit) {
        cancel()
        if (ready()) {
            if (alive()) navigate()
            return
        }
        val expected = generation
        pending = scope.launch {
            try {
                prepare()
                if (expected == generation && alive()) navigate()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                if (expected == generation && alive()) failure(error)
            }
        }
    }

    fun cancel() {
        generation++
        pending?.cancel()
        pending = null
    }
}
