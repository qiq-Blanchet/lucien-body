package com.luc.body.behavior

import com.luc.body.state.Expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import kotlin.random.Random

class SelfTalkManagerTest {
    @Test
    fun contentDrivenExpressionsUseOnlyTheMappingsNamedByTheSpecification() {
        assertEquals(Expression.LONELY_1, SelfTalkManager.expressionFor("想你了"))
        assertEquals(Expression.SLEEPY, SelfTalkManager.expressionFor("困了…但不想睡"))
        assertEquals(Expression.SLEEPY, SelfTalkManager.expressionFor("zzZ"))
        assertEquals(null, SelfTalkManager.expressionFor("在呢。"))
    }

    @Test
    fun messagePoolsMatchTheSpecificationVerbatim() {
        assertEquals(listOf("早啊", "起床了吗", "今天也要加油", "（揉眼睛）"), SelfTalkManager.messagesFor(DayPeriod.MORNING))
        assertEquals(
            listOf("在呢。", "今天天气怎么样啊…", "有点无聊。", "（发呆中）", "想喝奶茶…", "你在忙吗", "（翻了个身）"),
            SelfTalkManager.messagesFor(DayPeriod.DAY),
        )
        assertEquals(
            listOf("吃晚饭了吗", "今天辛苦了", "想你了", "晚霞好看吗", "（伸了个懒腰）"),
            SelfTalkManager.messagesFor(DayPeriod.EVENING),
        )
        assertEquals(
            listOf("还没睡啊", "早点睡 ꒪¯꒳¯꒪", "困了…但不想睡", "晚安…", "（打了个哈欠）", "月亮出来了吗"),
            SelfTalkManager.messagesFor(DayPeriod.NIGHT),
        )
    }

    @Test
    fun schedulesOnlyBetweenFiveAndFifteenMinutesAndUsesTheCurrentPool() {
        val clock = MutableClock(Instant.parse("2026-08-04T06:30:00Z"))
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val manager = SelfTalkManager(coordinator(sink, scheduler), scheduler, clock, Random(7))

        manager.start()
        val delay = scheduler.nextDelayMs!!
        assertTrue(delay in 5 * 60_000L..15 * 60_000L)
        scheduler.advanceBy(delay)

        assertTrue(sink.states.last().bubbleText in SelfTalkManager.messagesFor(DayPeriod.MORNING))
        assertTrue(scheduler.nextDelayMs!! in 4_000L..15 * 60_000L)
    }

    @Test
    fun lonelinessModesUseOnlyTheirSpecifiedContentAndFrequency() {
        assertMode(LonelinessMode.FASTER, 150_000L..450_000L) {
            it in SelfTalkManager.messagesFor(DayPeriod.DAY)
        }
        assertMode(LonelinessMode.SIGH, 300_000L..900_000L) { it == "…" }
        assertMode(LonelinessMode.SHORT, 600_000L..1_800_000L) { it == "…" || it == "在吗" }
        assertMode(LonelinessMode.SLEEPY, 300_000L..900_000L) { it == "zzZ" }
    }

    @Test
    fun remotePresenceInteractionDisableAndCloseCancelOldCallbacks() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val manager = SelfTalkManager(coordinator(sink, scheduler), scheduler, clock, Random(3))

        manager.start()
        manager.setRemotePresent(true)
        scheduler.advanceBy(20 * 60_000L)
        assertTrue(sink.states.isEmpty())

        manager.setRemotePresent(false)
        manager.onInteraction()
        manager.setEnabled(false)
        scheduler.advanceBy(20 * 60_000L)
        assertTrue(sink.states.isEmpty())

        manager.setEnabled(true)
        manager.close()
        scheduler.runAllEvenIfCanceled()
        assertTrue(sink.states.isEmpty())
        assertEquals(0, scheduler.activeTaskCount)
    }

    private fun assertMode(
        mode: LonelinessMode,
        expectedDelay: LongRange,
        messageMatches: (String?) -> Boolean,
    ) {
        val clock = MutableClock(Instant.parse("2026-08-04T12:00:00Z"))
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val manager = SelfTalkManager(coordinator(sink, scheduler), scheduler, clock, Random(11))

        manager.setLonelinessMode(mode)
        val delay = scheduler.nextDelayMs!!
        assertTrue(delay in expectedDelay)
        scheduler.advanceBy(delay)

        assertTrue(messageMatches(sink.states.last().bubbleText))
    }
}
