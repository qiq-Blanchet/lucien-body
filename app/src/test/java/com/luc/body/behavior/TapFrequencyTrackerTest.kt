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
        val tracker = TapFrequencyTracker(coordinator(sink, scheduler)) { clock.millis() }

        repeat(4) {
            tracker.onTap()
            clock.advanceBy(2_500L)
        }
        assertTrue(sink.states.isEmpty())

        tracker.onTap()
        assertEquals(Expression.ANGRY, sink.states.last().expression)

        scheduler.advanceBy(3_999L)
        assertEquals(Expression.ANGRY, sink.states.last().expression)
        scheduler.advanceBy(1L)
        assertEquals(Expression.IDLE, sink.states.last().expression)
    }

    @Test
    fun exactPhysicalTapTimesTriggerAngryAfterTheFifthTap() {
        val scheduler = BehaviorScheduler(MutableClock())
        val sink = BehaviorSink()
        val tracker = TapFrequencyTracker(coordinator(sink, scheduler)) { 0L }

        listOf(100L, 350L, 1_000L, 1_200L).forEach(tracker::onTap)
        assertTrue(sink.states.isEmpty())
        tracker.onTap(1_400L)

        assertEquals(Expression.ANGRY, sink.states.last().expression)
    }

    @Test
    fun tapsOlderThanTenSecondsDoNotCount() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val tracker = TapFrequencyTracker(coordinator(sink, scheduler)) { clock.millis() }

        repeat(4) {
            tracker.onTap()
            clock.advanceBy(3_000L)
        }
        tracker.onTap()

        assertTrue(sink.states.isEmpty())
    }

    @Test
    fun exactPhysicalTapTimesDoNotCreateADoubleTapBoundaryFalsePositive() {
        val sink = BehaviorSink()
        val tracker = TapFrequencyTracker(
            coordinator(sink, BehaviorScheduler(MutableClock())),
        ) { 0L }

        listOf(0L, 300L, 10_100L, 10_200L, 10_300L).forEach(tracker::onTap)

        assertTrue(sink.states.isEmpty())
    }
}
