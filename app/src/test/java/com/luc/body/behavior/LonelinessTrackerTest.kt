package com.luc.body.behavior

import com.luc.body.state.Expression
import org.junit.Assert.assertEquals
import org.junit.Test

class LonelinessTrackerTest {
    @Test
    fun exactThresholdsSelectTheSpecifiedExpressionAndSelfTalkMode() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val selfTalk = SelfTalkManager(coordinator(sink, scheduler), scheduler, clock, enabled = false)
        val manager = LonelinessTracker(coordinator(sink, scheduler), selfTalk, scheduler, clock)

        manager.start()
        scheduler.advanceBy(30 * 60_000L)
        assertEquals(Expression.LONELY_1, sink.states.last().expression)
        assertEquals(LonelinessMode.FASTER, selfTalk.lonelinessMode)

        scheduler.advanceBy(30 * 60_000L)
        assertEquals(Expression.LONELY_2, sink.states.last().expression)
        assertEquals(LonelinessMode.SIGH, selfTalk.lonelinessMode)

        scheduler.advanceBy(60 * 60_000L)
        assertEquals(Expression.LONELY_3, sink.states.last().expression)
        assertEquals(LonelinessMode.SHORT, selfTalk.lonelinessMode)

        scheduler.advanceBy(60 * 60_000L)
        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals(LonelinessMode.SLEEPY, selfTalk.lonelinessMode)
    }

    @Test
    fun interactionImmediatelyResetsTheTimerExpressionAndMode() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val selfTalk = SelfTalkManager(coordinator(sink, scheduler), scheduler, clock, enabled = false)
        val manager = LonelinessTracker(coordinator(sink, scheduler), selfTalk, scheduler, clock)

        manager.start()
        scheduler.advanceBy(120 * 60_000L)
        manager.onInteraction()

        assertEquals(Expression.IDLE, sink.states.last().expression)
        assertEquals(LonelinessMode.NORMAL, selfTalk.lonelinessMode)
        assertEquals(30 * 60_000L, scheduler.nextDelayMs)
    }
}
