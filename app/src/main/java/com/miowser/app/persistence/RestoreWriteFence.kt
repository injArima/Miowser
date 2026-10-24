package com.miowser.app.persistence

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/** Coordinates independent asset writers with snapshot staging and process-ending restore.
 * Freeze runs on IO, waits for current writers, and prevents late maintenance from overwriting
 * the restored generation. It stays frozen after successful restore until the process exits. */
object RestoreWriteFence {
    private val lock = ReentrantReadWriteLock(true)
    private val changes = AtomicLong()
    @Volatile var frozen = false
        private set
    val revision: Long get() = changes.get()
    fun <T> write(action: () -> T): T? = lock.read {
        if (frozen) return@read null
        changes.incrementAndGet()
        try { action() } finally { changes.incrementAndGet() }
    }
    fun <T> stage(action: () -> T): T = lock.write(action)
    fun freeze() = lock.write { frozen = true }
    fun thawAfterPreflightFailure() = lock.write { frozen = false }
}
