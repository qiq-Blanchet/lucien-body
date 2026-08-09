package com.luc.body.behavior

import com.luc.body.state.Expression
import com.luc.body.state.StateCoordinator
import java.util.ArrayDeque

class TapFrequencyTracker(
    private val coordinator: StateCoordinator,
    private val monotonicClockMs: () -> Long = { System.nanoTime() / 1_000_000L },
) : AutoCloseable {
    private val taps = ArrayDeque<Long>()
    private var closed = false

    fun onTap(eventTimeMs: Long = monotonicClockMs()) {
        if (closed) return
        taps.addLast(eventTimeMs)
        val windowStart = eventTimeMs - TEN_SECONDS_MS
        while (taps.peekFirst()?.let { it < windowStart } == true) taps.removeFirst()

        if (taps.size >= 5) {
            taps.clear()
            coordinator.startLocalReaction(Expression.ANGRY)
        }
    }

    override fun close() {
        closed = true
        taps.clear()
    }

    companion object {
        private const val TEN_SECONDS_MS = 10_000L
    }
}
