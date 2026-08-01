package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.RemoteState
import okhttp3.Call

class PollingLoop(
    private val client: SupabaseClient,
    private val scheduler: DelayScheduler,
    private val onState: (RemoteState) -> Unit,
) {
    private val lock = Any()
    private var started = false
    private var activeCall: Call? = null
    private var scheduledPoll: Cancelable? = null

    fun start() {
        synchronized(lock) {
            if (started) return
            started = true
            fetchLocked()
        }
    }

    fun stop() {
        val scheduled: Cancelable?
        val call: Call?
        synchronized(lock) {
            if (!started) return
            started = false
            scheduled = scheduledPoll
            call = activeCall
            scheduledPoll = null
            activeCall = null
        }
        scheduled?.cancel()
        call?.cancel()
    }

    private fun fetchLocked() {
        check(started)
        activeCall = client.fetchLatest { result -> onFetchComplete(result) }
    }

    private fun onFetchComplete(result: Result<RemoteState?>) {
        synchronized(lock) {
            if (!started) return
            activeCall = null
        }
        result.getOrNull()?.let(onState)
        synchronized(lock) {
            if (!started) return
            scheduledPoll = scheduler.schedule(POLL_INTERVAL_MS) {
                synchronized(lock) {
                    if (!started) return@schedule
                    scheduledPoll = null
                    fetchLocked()
                }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
