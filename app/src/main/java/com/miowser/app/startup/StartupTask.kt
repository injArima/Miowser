package com.miowser.app.startup

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** One successful initialization per process; failed/cancelled attempts can be retried. */
class StartupTask<T : Any> {
    private val mutex = Mutex()
    private var value: T? = null
    suspend fun get(load: suspend () -> T): T = mutex.withLock {
        value ?: load().also { value = it }
    }
}
