package com.luc.body.behavior

import com.luc.body.state.Expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TapFrequencyTrackerTest {
    @Test
    fun fiveTapsWithinTenSecondsTriggerOnlyAngry() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val tracker = TapFrequencyTracker(coordinator(sink, scheduler), clock)

        repeat(4) {
            tracker.onTap()
            clock.advanceBy(2_500L)
        }
        assertTrue(sink.states.isEmpty())

        tracker.onTap()
        assertEquals(Expression.ANGRY, sink.states.last().expression)
    }

    @Test
    fun tapsOlderThanTenSecondsDoNotCount() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val tracker = TapFrequencyTracker(coordinator(sink, scheduler), clock)

        repeat(4) {
            tracker.onTap()
            clock.advanceBy(3_000L)
        }
        tracker.onTap()

        assertTrue(sink.states.isEmpty())
    }
}
