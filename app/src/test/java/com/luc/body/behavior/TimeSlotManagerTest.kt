package com.luc.body.behavior

import com.luc.body.state.Expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset
import kotlin.random.Random

class TimeSlotManagerTest {
    @Test
    fun fourDayPeriodsUseExactBoundaries() {
        assertEquals(DayPeriod.NIGHT, TimeSlotManager.periodAt(LocalTime.of(5, 59, 59)))
        assertEquals(DayPeriod.MORNING, TimeSlotManager.periodAt(LocalTime.of(6, 0)))
        assertEquals(DayPeriod.MORNING, TimeSlotManager.periodAt(LocalTime.of(8, 59, 59)))
        assertEquals(DayPeriod.DAY, TimeSlotManager.periodAt(LocalTime.of(9, 0)))
        assertEquals(DayPeriod.DAY, TimeSlotManager.periodAt(LocalTime.of(16, 59, 59)))
        assertEquals(DayPeriod.EVENING, TimeSlotManager.periodAt(LocalTime.of(17, 0)))
        assertEquals(DayPeriod.EVENING, TimeSlotManager.periodAt(LocalTime.of(20, 59, 59)))
        assertEquals(DayPeriod.NIGHT, TimeSlotManager.periodAt(LocalTime.of(21, 0)))
    }

    @Test
    fun contextUsesExactMealNightAndSleepBoundaries() {
        assertEquals(Expression.NIGHT, refreshAt(LocalTime.of(0, 59, 59)))
        assertEquals(Expression.SLEEPY, refreshAt(LocalTime.of(1, 0)))
        assertEquals(Expression.SLEEPY, refreshAt(LocalTime.of(5, 59, 59)))
        assertNull(refreshAt(LocalTime.of(9, 0)))
        assertNull(refreshAt(LocalTime.of(11, 29, 59)))
        assertEquals(Expression.EATING, refreshAt(LocalTime.of(11, 30)))
        assertEquals(Expression.EATING, refreshAt(LocalTime.of(12, 59, 59)))
        assertNull(refreshAt(LocalTime.of(13, 0), chance = false))
        assertNull(refreshAt(LocalTime.of(17, 29, 59)))
        assertEquals(Expression.EATING, refreshAt(LocalTime.of(17, 30)))
        assertEquals(Expression.EATING, refreshAt(LocalTime.of(18, 59, 59)))
        assertNull(refreshAt(LocalTime.of(19, 0)))
        assertNull(refreshAt(LocalTime.of(22, 59, 59)))
        assertEquals(Expression.NIGHT, refreshAt(LocalTime.of(23, 0)))
    }

    @Test
    fun probabilisticSlotsCanRemainIdleOrActivate() {
        assertNull(refreshAt(LocalTime.of(11, 30), chance = false))
        assertEquals(Expression.EATING, refreshAt(LocalTime.of(11, 30), chance = true))
        assertNull(refreshAt(LocalTime.of(13, 0), chance = false))
        assertEquals(Expression.SLEEPY, refreshAt(LocalTime.of(13, 0), chance = true))
        assertNull(refreshAt(LocalTime.of(17, 30), chance = false))
        assertEquals(Expression.EATING, refreshAt(LocalTime.of(17, 30), chance = true))
    }

    @Test
    fun morningAppearsOnlyOncePerCalendarDay() {
        val clock = MutableClock(Instant.parse("2026-08-04T06:00:00Z"))
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val manager = TimeSlotManager(coordinator(sink, scheduler), clock, FixedBooleanRandom(true))

        assertEquals(Expression.MORNING, manager.refresh())
        assertEquals(TimeSlotManager.MORNING_BUBBLE, sink.states.last().bubbleText)
        scheduler.advanceBy(5_000L)
        assertNull(manager.refresh())
        assertNull(sink.states.last().bubbleText)

        clock.advanceBy(24 * 60 * 60_000L)
        assertEquals(Expression.MORNING, manager.refresh())
        assertEquals(TimeSlotManager.MORNING_BUBBLE, sink.states.last().bubbleText)
    }

    @Test
    fun specifiedBubblesAreUsedForMealAndNightSlots() {
        assertEquals(TimeSlotManager.LUNCH_BUBBLE, bubbleAt(LocalTime.of(12, 0)))
        assertEquals(TimeSlotManager.DINNER_BUBBLE, bubbleAt(LocalTime.of(18, 0)))
        assertEquals(TimeSlotManager.NIGHT_BUBBLE, bubbleAt(LocalTime.of(23, 0)))
        assertEquals(TimeSlotManager.LATE_NIGHT_BUBBLE, bubbleAt(LocalTime.of(1, 0)))
    }

    @Test
    fun closePreventsFurtherWrites() {
        val clock = Clock.fixed(Instant.parse("2026-08-04T11:30:00Z"), ZoneOffset.UTC)
        val scheduler = BehaviorScheduler(MutableClock())
        val sink = BehaviorSink()
        val manager = TimeSlotManager(coordinator(sink, scheduler), clock, FixedBooleanRandom(true))

        assertEquals(Expression.EATING, manager.refresh())
        assertEquals(Expression.TALKING, sink.states.last().expression)
        manager.close()
        val renderCount = sink.states.size

        assertNull(manager.refresh())
        assertEquals(renderCount, sink.states.size)
    }

    private fun refreshAt(time: LocalTime, chance: Boolean = true): Expression? {
        val clock = Clock.fixed(
            Instant.parse("2026-08-04T00:00:00Z").plusSeconds(time.toSecondOfDay().toLong()),
            ZoneOffset.UTC,
        )
        return TimeSlotManager(
            coordinator(BehaviorSink(), BehaviorScheduler(MutableClock())),
            clock,
            FixedBooleanRandom(chance),
        ).refresh()
    }

    private fun bubbleAt(time: LocalTime): String? {
        val clock = Clock.fixed(
            Instant.parse("2026-08-04T00:00:00Z").plusSeconds(time.toSecondOfDay().toLong()),
            ZoneOffset.UTC,
        )
        val sink = BehaviorSink()
        TimeSlotManager(
            coordinator(sink, BehaviorScheduler(MutableClock())),
            clock,
            FixedBooleanRandom(true),
        ).refresh()
        return sink.states.last().bubbleText
    }

    private class FixedBooleanRandom(private val value: Boolean) : Random() {
        override fun nextBits(bitCount: Int): Int = if (value) -1 ushr (32 - bitCount) else 0
    }
}
