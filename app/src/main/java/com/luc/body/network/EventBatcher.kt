package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import okhttp3.Call

class EventBatcher(
    private val client: SupabaseClient,
    private val scheduler: DelayScheduler,
) : AutoCloseable {
    private data class PendingPost(val id: Long, val events: List<ClawdEvent>)

    private val lock = Any()
    private val pending = ArrayDeque<ClawdEvent>()
    private var activeCall: Call? = null
    private var flushTask: Cancelable? = null
    private var closed = false
    private var generation = 0L
    private var requestSequence = 0L
    private var activeRequestId: Long? = null

    fun enqueue(event: ClawdEvent) {
        val batch = synchronized(lock) {
            if (closed) return
            pending += event
            if (activeRequestId == null && pending.size >= MAX_BATCH_SIZE) {
                cancelFlushLocked()
                takeBatchLocked()
            } else {
                scheduleFlushLocked()
                null
            }
        }
        batch?.let(::post)
    }

    fun flush() {
        val batch = synchronized(lock) {
            if (closed || activeRequestId != null || pending.isEmpty()) return
            cancelFlushLocked()
            takeBatchLocked()
        }
        post(batch)
    }

    override fun close() {
        val call = synchronized(lock) {
            if (closed) return
            closed = true
            generation += 1
            cancelFlushLocked()
            pending.clear()
            activeRequestId = null
            activeCall.also { activeCall = null }
        }
        call?.cancel()
    }

    private fun post(batch: PendingPost) {
        val requestGeneration = synchronized(lock) { generation }
        val call = client.postEvents(batch.events) { result ->
            onPostComplete(requestGeneration, batch, result)
        }
        val cancel = synchronized(lock) {
            if (closed || generation != requestGeneration || activeRequestId != batch.id) {
                true
            } else {
                activeCall = call
                false
            }
        }
        if (cancel) call.cancel()
    }

    private fun onPostComplete(
        requestGeneration: Long,
        batch: PendingPost,
        result: Result<Unit>,
    ) {
        val nextBatch = synchronized(lock) {
            if (closed || generation != requestGeneration || activeRequestId != batch.id) return
            activeRequestId = null
            activeCall = null
            if (result.isFailure) {
                batch.events.asReversed().forEach(pending::addFirst)
                scheduleFlushLocked()
                null
            } else if (pending.size >= MAX_BATCH_SIZE) {
                cancelFlushLocked()
                takeBatchLocked()
            } else {
                scheduleFlushLocked()
                null
            }
        }
        nextBatch?.let(::post)
    }

    private fun takeBatchLocked(): PendingPost {
        check(activeRequestId == null)
        val id = ++requestSequence
        activeRequestId = id
        return PendingPost(
            id,
            buildList {
                repeat(minOf(MAX_BATCH_SIZE, pending.size)) { add(pending.removeFirst()) }
            },
        )
    }

    private fun scheduleFlushLocked() {
        if (closed || activeRequestId != null || pending.isEmpty() || flushTask != null) return
        val taskGeneration = generation
        val task = scheduler.schedule(FLUSH_INTERVAL_MS) {
            val batch = synchronized(lock) {
                if (closed || generation != taskGeneration || activeRequestId != null || pending.isEmpty()) {
                    null
                } else {
                    flushTask = null
                    takeBatchLocked()
                }
            }
            batch?.let(::post)
        }
        if (closed || generation != taskGeneration || flushTask != null) {
            task.cancel()
        } else {
            flushTask = task
        }
    }

    private fun cancelFlushLocked() {
        flushTask?.cancel()
        flushTask = null
    }

    private companion object {
        const val MAX_BATCH_SIZE = 3
        const val FLUSH_INTERVAL_MS = 10_000L
    }
}
