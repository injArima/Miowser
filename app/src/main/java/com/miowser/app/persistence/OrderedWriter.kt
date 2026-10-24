package com.miowser.app.persistence

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor

data class WriteState(val pending: Int = 0, val failedKeys: Set<String> = emptySet())
data class WriterMetrics(val submitted: Long, val executed: Long, val coalesced: Long,
    val encodedBytes: Long, val peakPending: Int)

/** One disk owner. Only complete immutable snapshots replace queued work.
 * Commands and barriers seal a segment; superseded receipts await replacement durability. */
class OrderedWriter(private val executor: Executor, private val onState: (WriteState) -> Unit = {}) {
    private data class Write(val key: String, val revision: Long, val action: () -> Boolean,
        var receipts: MutableList<CompletableFuture<Boolean>> = mutableListOf())
    private val lock = Any()
    private var published = WriteState()
    private var revision = 0L
    private var pending = 0
    private var running = false
    private var submitted = 0L
    private var executed = 0L
    private var coalesced = 0L
    private var encodedBytes = 0L
    private var peakPending = 0
    private val queue = ArrayDeque<Any>()
    private val replaceable = mutableMapOf<String, Write>()
    private val latest = mutableMapOf<String, Long>()
    private val committed = mutableMapOf<String, Long>()
    private val failed = linkedMapOf<String, Write>()
    private val acknowledgements = mutableListOf<Pair<Map<String, Long>, CompletableFuture<Unit>>>()

    fun metrics(): WriterMetrics = synchronized(lock) {
        WriterMetrics(submitted, executed, coalesced, encodedBytes, peakPending)
    }
    fun recordEncodedBytes(bytes: Long) = synchronized(lock) { encodedBytes += bytes.coerceAtLeast(0) }
    fun submit(key: String, action: () -> Boolean) = enqueue(key, action, snapshot = false)
    fun submitSnapshot(key: String, action: () -> Boolean) = enqueue(key, action, snapshot = true)
    private fun enqueue(key: String, action: () -> Boolean, snapshot: Boolean): CompletableFuture<Boolean> = synchronized(lock) {
        val receipt = CompletableFuture<Boolean>()
        val write = Write(key, ++revision, action, mutableListOf(receipt))
        submitted++
        latest[key] = write.revision
        // Adjacent snapshots only: another store can depend on an earlier folder/settings
        // snapshot. Reordering across keys would weaken crash-time referential integrity.
        if (!snapshot || replaceable.keys.singleOrNull() != key) replaceable.clear()
        val replaced = if (snapshot) replaceable.remove(key) else null
        if (replaced != null) {
            check(queue.remove(replaced))
            replaced.receipts.add(receipt)
            write.receipts = replaced.receipts
            coalesced++
        } else pending++
        if (snapshot) replaceable[key] = write
        queue.addLast(write)
        peakPending = maxOf(peakPending, pending)
        publish()
        start()
        receipt
    }
    private fun start() {
        if (running || queue.isEmpty()) return
        running = true
        executor.execute {
            val work = synchronized(lock) {
                queue.removeFirst().also {
                    if (it is Write && replaceable[it.key] === it) replaceable.remove(it.key)
                }
            }
            if (work is Write) {
                val success = runCatching(work.action).getOrDefault(false)
                synchronized(lock) {
                    executed++
                    if (success) committed[work.key] = work.revision
                    if (latest[work.key] == work.revision) {
                        if (success) failed.remove(work.key) else failed[work.key] = work.copy(receipts = mutableListOf())
                    }
                    pending--
                    val ready = acknowledgements.filter { isCommitted(it.first) }
                    acknowledgements.removeAll(ready.toSet())
                    ready.forEach { it.second.complete(Unit) }
                    publish()
                }
                work.receipts.forEach { it.complete(success) }
            } else {
                @Suppress("UNCHECKED_CAST")
                (work as () -> Unit).invoke()
            }
            synchronized(lock) { running = false; start() }
        }
    }
    fun retryFailed() = synchronized(lock) {
        failed.values.filter { latest[it.key] == it.revision }.toList().forEach {
            enqueue(it.key, it.action, snapshot = false)
        }
    }
    fun barrier(): CompletableFuture<Boolean> = synchronized(lock) {
        val receipt = CompletableFuture<Boolean>()
        val targets = latest.toMap()
        replaceable.clear()
        queue.addLast({ synchronized(lock) { receipt.complete(isCommitted(targets)); Unit } })
        start()
        receipt
    }
    /** Failure waits for a retry or equally-new snapshot; callers never acknowledge submission. */
    fun awaitCommitted(): CompletableFuture<Unit> = synchronized(lock) {
        val receipt = CompletableFuture<Unit>()
        val targets = latest.toMap()
        replaceable.clear()
        if (isCommitted(targets)) receipt.complete(Unit) else acknowledgements += targets to receipt
        receipt
    }
    private fun isCommitted(targets: Map<String, Long>) =
        targets.all { (key, revision) -> (committed[key] ?: 0L) >= revision }
    private fun publish() {
        val next = WriteState(pending, failed.keys.toSet())
        if (next != published) { published = next; onState(next) }
    }
}
