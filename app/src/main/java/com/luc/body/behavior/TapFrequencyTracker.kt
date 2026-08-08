package com.luc.body.behavior

import com.luc.body.state.Expression
import com.luc.body.state.StateCoordinator
import java.time.Clock
import java.time.Instant
import java.util.ArrayDeque

class TapFrequencyTracker(
    private val coordinator: StateCoordinator,
    private val clock: Clock = Clock.systemDefaultZone(),
) : AutoCloseable {
    private val taps = ArrayDeque<Instant>()
    private var closed = false

    fun onTap() {
        if (closed) return
        val now = clock.instant()
        taps.addLast(now)
        val windowStart = now.minusMillis(TEN_SECONDS_MS)
        while (taps.peekFirst()?.isBefore(windowStart) == true) taps.removeFirst()

        if (taps.size >= 5) {
            taps.clear()
            coordinator.requestState(Expression.ANGRY)
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
