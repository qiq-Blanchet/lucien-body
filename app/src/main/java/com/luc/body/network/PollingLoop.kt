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

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            val runGeneration = ++generation
            fetchLocked(runGeneration)
        }
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

    private fun fetchLocked(runGeneration: Long) {
        check(started && generation == runGeneration)
        activeCall = client.fetchLatest { result ->
            ownerExecutor.execute { onFetchComplete(runGeneration, result) }
        }
    }

    private fun onFetchComplete(runGeneration: Long, result: Result<RemoteState?>) {
        synchronized(lock) {
            if (!isCurrentRun(runGeneration)) return
            activeCall = null
        }
        result.getOrNull()?.let(onState)
        synchronized(lock) {
            if (!isCurrentRun(runGeneration)) return
            scheduledPoll = scheduler.schedule(POLL_INTERVAL_MS) {
                synchronized(lock) {
                    if (!isCurrentRun(runGeneration)) return@schedule
                    scheduledPoll = null
                    fetchLocked(runGeneration)
                }
            }
        }
    }

    private fun isCurrentRun(runGeneration: Long): Boolean = started && generation == runGeneration

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
