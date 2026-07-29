package com.luc.body.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StateCoordinatorTest {
    @Test
    fun unsupportedExpressionsFallBackToIdle() {
        listOf("shy", "excited", "sad", "surprised", null).forEach { value ->
            assertEquals(Expression.IDLE, Expression.fromRemote(value))
        }
    }

    @Test
    fun remoteValuesMapToSupportedExpressionsAndBubbleStyles() {
        assertEquals(Expression.HAPPY, Expression.fromRemote("HAPPY"))
        assertEquals(Expression.ANGRY, Expression.fromRemote("angry"))
        assertEquals(Expression.SLEEPY, Expression.fromRemote("sleepy"))
        assertEquals(BubbleStyle.NORMAL, BubbleStyle.fromRemote("unknown"))
        assertEquals(BubbleStyle.WHISPER, BubbleStyle.fromRemote("whisper"))
        assertEquals(BubbleStyle.SHOUT, BubbleStyle.fromRemote("shout"))
        assertEquals(BubbleStyle.LOVE, BubbleStyle.fromRemote("love"))
    }

    @Test
    fun blankRemoteBubbleTextIsHiddenAndLongTextIsTruncated() {
        val sink = RecordingSink()
        val coordinator = StateCoordinator(sink, FakeScheduler()) { Expression.HAPPY }

        coordinator.onRemoteState(remote(text = "  ", revision = "blank"))
        assertNull(sink.states.last().bubbleText)

        coordinator.onRemoteState(remote(text = "x".repeat(121), revision = "long"))
        assertEquals("x".repeat(120), sink.states.last().bubbleText)
    }

    @Test
    fun duplicateUpdatedAtDoesNotRenderTwiceOrRestartBubbleTimer() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        val state = remote(text = "Hey", revision = "2026-07-29T12:00:00Z")

        coordinator.onRemoteState(state)
        coordinator.onRemoteState(state.copy(expression = Expression.SLEEPY, bubbleText = "new text"))
        scheduler.advanceBy(5_000)

        assertEquals(2, sink.states.size)
        assertEquals(Expression.ANGRY, sink.states.first().expression)
        assertEquals(null, sink.states.last().bubbleText)
        assertEquals("2026-07-29T12:00:00Z", sink.states.last().revision)
    }

    @Test
    fun localTapImmediatelyShowsHappyWithLocalTextForOnePointTwoSeconds() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onLocalTap()

        assertEquals(Expression.HAPPY, sink.states.last().expression)
        assertEquals("Hi!", sink.states.last().bubbleText)
        assertEquals("local-1", sink.states.last().revision)

        scheduler.advanceBy(1_199)
        assertEquals(1, sink.states.size)
    }

    @Test
    fun newestRemoteStateWinsAfterLocalOverride() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        coordinator.onRemoteState(remote(expression = Expression.IDLE, revision = "1"))

        coordinator.onLocalTap()
        coordinator.onRemoteState(remote(Expression.ANGRY, "A", BubbleStyle.SHOUT, "2"))
        coordinator.onRemoteState(remote(Expression.SLEEPY, "B", BubbleStyle.WHISPER, "3"))
        scheduler.advanceBy(1_200)

        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("B", sink.states.last().bubbleText)
        assertEquals("3", sink.states.last().revision)
    }

    @Test
    fun remoteUpdatesStayBufferedUntilTheLocalOverrideEnds() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onLocalTap()
        coordinator.onRemoteState(remote(expression = Expression.ANGRY, text = "A", revision = "1"))

        assertEquals(1, sink.states.size)
        assertEquals(Expression.HAPPY, sink.states.last().expression)

        scheduler.advanceBy(1_200)
        assertEquals(Expression.ANGRY, sink.states.last().expression)
    }

    @Test
    fun localTapWithoutBufferedRemoteRestoresThePriorRemoteState() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        coordinator.onRemoteState(remote(expression = Expression.SLEEPY, text = "restored", revision = "1"))

        coordinator.onLocalTap()
        scheduler.advanceBy(1_200)

        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("restored", sink.states.last().bubbleText)
    }

    @Test
    fun repeatedLocalTapCancelsThePreviousOverrideTimeout() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        coordinator.onRemoteState(remote(expression = Expression.IDLE, revision = "1"))

        coordinator.onLocalTap()
        scheduler.advanceBy(1_000)
        coordinator.onLocalTap()
        scheduler.advanceBy(200)

        assertEquals(Expression.HAPPY, sink.states.last().expression)
        scheduler.advanceBy(1_000)
        assertEquals(Expression.IDLE, sink.states.last().expression)
    }

    @Test
    fun remoteBubbleHidesFiveSecondsAfterItIsShown() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onRemoteState(remote(expression = Expression.ANGRY, text = "Five seconds", revision = "1"))
        scheduler.advanceBy(4_999)
        assertEquals("Five seconds", sink.states.last().bubbleText)

        scheduler.advanceBy(1)
        assertEquals(Expression.ANGRY, sink.states.last().expression)
        assertNull(sink.states.last().bubbleText)
        assertEquals("1", sink.states.last().revision)
    }

    @Test
    fun closeCancelsPendingTasksAndPreventsFutureRendering() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        coordinator.onRemoteState(remote(text = "Visible", revision = "1"))
        coordinator.onLocalTap()

        coordinator.close()
        scheduler.advanceBy(10_000)

        assertEquals(2, sink.states.size)
        coordinator.onRemoteState(remote(expression = Expression.SLEEPY, revision = "2"))
        coordinator.onLocalTap()
        assertEquals(2, sink.states.size)
    }

    private fun remote(
        expression: Expression = Expression.ANGRY,
        text: String? = null,
        style: BubbleStyle = BubbleStyle.NORMAL,
        revision: String,
    ) = RemoteState(expression, text, style, revision)

    private class RecordingSink : UiSink {
        val states = mutableListOf<VisibleState>()

        override fun render(state: VisibleState) {
            states += state
        }
    }

    private class FakeScheduler : DelayScheduler {
        private data class Scheduled(
            val dueAtMs: Long,
            val action: () -> Unit,
            var canceled: Boolean = false,
        )

        private val scheduled = mutableListOf<Scheduled>()
        private var nowMs = 0L

        override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
            val task = Scheduled(nowMs + delayMs, action)
            scheduled += task
            return Cancelable { task.canceled = true }
        }

        fun advanceBy(delayMs: Long) {
            val targetMs = nowMs + delayMs
            while (true) {
                val next = scheduled
                    .filter { !it.canceled && it.dueAtMs <= targetMs }
                    .minByOrNull { it.dueAtMs }
                    ?: break
                scheduled.remove(next)
                nowMs = next.dueAtMs
                next.action()
            }
            nowMs = targetMs
        }
    }
}
