package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.RemoteState
import java.util.concurrent.Executor
import okhttp3.Call

class PollingLoop(
    private val client: SupabaseClient,
    private val scheduler: DelayScheduler,
    private val ownerExecutor: Executor,
    private val onState: (RemoteState) -> Unit,
) {
    private val lock = Any()
    private var started = false
    private var generation = 0L
    private var activeCall: Call? = null
    private var scheduledPoll: Cancelable? = null

    fun fetchOnce() {
        client.fetchLatest { result ->
            ownerExecutor.execute {
                result.getOrNull()?.let(onState)
            }
        }
    }

    fun start() {
        val runGeneration = synchronized(lock) {
            if (started) return
            started = true
            ++generation
        }
        fetch(runGeneration)
    }

    fun stop() {
        val scheduled: Cancelable?
        val call: Call?
        synchronized(lock) {
            if (!started) return
            started = false
            generation += 1
            scheduled = scheduledPoll
            call = activeCall
            scheduledPoll = null
            activeCall = null
        }
        scheduled?.cancel()
        call?.cancel()
    }

    private fun fetch(runGeneration: Long) {
        if (!isCurrentRun(runGeneration)) return
        val call = client.fetchLatest { result ->
            ownerExecutor.execute { onFetchComplete(runGeneration, result) }
        }
        val cancelCall = synchronized(lock) {
            if (isCurrentRunLocked(runGeneration)) {
                activeCall = call
                false
            } else {
                true
            }
        }
        if (cancelCall) call.cancel()
    }

    private fun onFetchComplete(runGeneration: Long, result: Result<RemoteState?>) {
        synchronized(lock) {
            if (!isCurrentRunLocked(runGeneration)) return
            activeCall = null
        }
        result.getOrNull()?.let { state ->
            if (isCurrentRun(runGeneration)) onState(state)
        }
        if (!isCurrentRun(runGeneration)) return
        val scheduled = scheduler.schedule(POLL_INTERVAL_MS) {
            val fetchNext = synchronized(lock) {
                if (!isCurrentRunLocked(runGeneration)) {
                    false
                } else {
                    scheduledPoll = null
                    true
                }
            }
            if (fetchNext) fetch(runGeneration)
        }
        val cancelScheduled = synchronized(lock) {
            if (isCurrentRunLocked(runGeneration) && scheduledPoll == null) {
                scheduledPoll = scheduled
                false
            } else {
                true
            }
        }
        if (cancelScheduled) scheduled.cancel()
    }

    private fun isCurrentRun(runGeneration: Long): Boolean = synchronized(lock) {
        isCurrentRunLocked(runGeneration)
    }

    private fun isCurrentRunLocked(runGeneration: Long): Boolean = started && generation == runGeneration

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
