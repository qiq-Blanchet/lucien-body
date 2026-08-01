package com.luc.body.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

        coordinator.onRemoteState(remote(text = "  ", revision = REVISION_1))
        assertNull(sink.states.last().bubbleText)

        coordinator.onRemoteState(remote(text = "x".repeat(121), revision = REVISION_2))
        assertEquals("x".repeat(120), sink.states.last().bubbleText)
    }

    @Test
    fun duplicateUpdatedAtDoesNotRenderTwiceOrRestartBubbleTimer() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        val state = remote(text = "Hey", revision = REVISION_1)

        coordinator.onRemoteState(state)
        coordinator.onRemoteState(state.copy(expression = Expression.SLEEPY, bubbleText = "new text"))
        scheduler.advanceBy(5_000)

        assertEquals(2, sink.states.size)
        assertEquals(Expression.ANGRY, sink.states.first().expression)
        assertEquals(null, sink.states.last().bubbleText)
        assertEquals(REVISION_1, sink.states.last().revision)
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
        coordinator.onRemoteState(remote(expression = Expression.IDLE, revision = REVISION_1))

        coordinator.onLocalTap()
        coordinator.onRemoteState(remote(Expression.ANGRY, "A", BubbleStyle.SHOUT, REVISION_2))
        coordinator.onRemoteState(remote(Expression.SLEEPY, "B", BubbleStyle.WHISPER, REVISION_3))
        scheduler.advanceBy(1_200)

        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("B", sink.states.last().bubbleText)
        assertEquals(REVISION_3, sink.states.last().revision)
    }

    @Test
    fun remoteUpdatesStayBufferedUntilTheLocalOverrideEnds() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onLocalTap()
        coordinator.onRemoteState(remote(expression = Expression.ANGRY, text = "A", revision = REVISION_1))

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
        coordinator.onRemoteState(remote(expression = Expression.SLEEPY, text = "restored", revision = REVISION_1))

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
        coordinator.onRemoteState(remote(expression = Expression.IDLE, revision = REVISION_1))

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

        coordinator.onRemoteState(remote(expression = Expression.ANGRY, text = "Five seconds", revision = REVISION_1))
        scheduler.advanceBy(4_999)
        assertEquals("Five seconds", sink.states.last().bubbleText)

        scheduler.advanceBy(1)
        assertEquals(Expression.ANGRY, sink.states.last().expression)
        assertNull(sink.states.last().bubbleText)
        assertEquals(REVISION_1, sink.states.last().revision)
    }

    @Test
    fun closeCancelsPendingTasksAndPreventsFutureRendering() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        coordinator.onRemoteState(remote(text = "Visible", revision = REVISION_1))
        coordinator.onLocalTap()

        coordinator.close()
        scheduler.advanceBy(10_000)

        assertEquals(2, sink.states.size)
        coordinator.onRemoteState(remote(expression = Expression.SLEEPY, revision = REVISION_2))
        coordinator.onLocalTap()
        assertEquals(2, sink.states.size)
    }

    @Test
    fun olderIsoRevisionAndNewerReplayDoNotReplaceNewerRemoteState() {
        val sink = RecordingSink()
        val coordinator = StateCoordinator(sink, FakeScheduler()) { Expression.HAPPY }

        coordinator.onRemoteState(remote(Expression.SLEEPY, "new", BubbleStyle.NORMAL, REVISION_3))
        coordinator.onRemoteState(remote(Expression.ANGRY, "old", BubbleStyle.SHOUT, REVISION_2))
        coordinator.onRemoteState(remote(Expression.IDLE, "replay", BubbleStyle.WHISPER, REVISION_3))

        assertEquals(1, sink.states.size)
        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("new", sink.states.last().bubbleText)
    }

    @Test
    fun olderIsoRevisionAndNewerReplayDoNotReplaceBufferedRemoteState() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onLocalTap()
        coordinator.onRemoteState(remote(Expression.SLEEPY, "new", BubbleStyle.NORMAL, REVISION_3))
        coordinator.onRemoteState(remote(Expression.ANGRY, "old", BubbleStyle.SHOUT, REVISION_2))
        coordinator.onRemoteState(remote(Expression.IDLE, "replay", BubbleStyle.WHISPER, REVISION_3))
        scheduler.advanceBy(1_200)

        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("new", sink.states.last().bubbleText)
    }

    @Test
    fun malformedRevisionDoesNotRenderOrReplaceTheLastValidState() {
        val sink = RecordingSink()
        val coordinator = StateCoordinator(sink, FakeScheduler()) { Expression.HAPPY }

        coordinator.onRemoteState(remote(Expression.SLEEPY, "valid", BubbleStyle.NORMAL, REVISION_1))
        coordinator.onRemoteState(remote(Expression.ANGRY, "invalid", BubbleStyle.SHOUT, "not-an-iso-timestamp"))

        assertEquals(1, sink.states.size)
        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("valid", sink.states.last().bubbleText)
    }

    @Test
    fun canceledLocalTimeoutCannotEndANewerOverride() {
        val sink = RecordingSink()
        val scheduler = UnreliableScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }
        coordinator.onRemoteState(remote(expression = Expression.IDLE, revision = REVISION_1))

        coordinator.onLocalTap()
        coordinator.onLocalTap()
        scheduler.runEvenIfCanceled(0)

        assertEquals(Expression.HAPPY, sink.states.last().expression)
        assertEquals("local-2", sink.states.last().revision)

        scheduler.runEvenIfCanceled(1)
        assertEquals(Expression.IDLE, sink.states.last().expression)
    }

    @Test
    fun canceledBubbleTimeoutCannotHideANewerRemoteBubble() {
        val sink = RecordingSink()
        val scheduler = UnreliableScheduler()
        val coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onRemoteState(remote(Expression.ANGRY, "A", BubbleStyle.SHOUT, REVISION_1))
        coordinator.onRemoteState(remote(Expression.SLEEPY, "B", BubbleStyle.WHISPER, REVISION_2))
        scheduler.runEvenIfCanceled(0)

        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("B", sink.states.last().bubbleText)

        scheduler.runEvenIfCanceled(1)
        assertNull(sink.states.last().bubbleText)
        assertEquals(REVISION_2, sink.states.last().revision)
    }

    @Test
    fun reentrantLocalTapCannotLeaveTheOuterTimeoutActive() {
        val scheduler = UnreliableScheduler()
        lateinit var coordinator: StateCoordinator
        val sink = ReentrantSink { state ->
            if (state.revision == "local-1") coordinator.onLocalTap()
        }
        coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onLocalTap()
        scheduler.runEvenIfCanceled(0)

        assertEquals("local-2", sink.states.last().revision)
        scheduler.runEvenIfCanceled(1)
        assertEquals("local-2-expired", sink.states.last().revision)
    }

    @Test
    fun reentrantNewerRemoteStateCancelsTheOuterBubbleTimeout() {
        val scheduler = UnreliableScheduler()
        lateinit var coordinator: StateCoordinator
        val sink = ReentrantSink { state ->
            if (state.revision == REVISION_1) {
                coordinator.onRemoteState(remote(Expression.SLEEPY, "B", BubbleStyle.WHISPER, REVISION_2))
            }
        }
        coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onRemoteState(remote(Expression.ANGRY, "A", BubbleStyle.SHOUT, REVISION_1))
        scheduler.runEvenIfCanceled(0)

        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertEquals("B", sink.states.last().bubbleText)
        scheduler.runEvenIfCanceled(1)
        assertNull(sink.states.last().bubbleText)
        assertEquals(REVISION_2, sink.states.last().revision)
    }

    @Test
    fun closeDuringRenderLeavesNoActiveTimer() {
        val scheduler = UnreliableScheduler()
        lateinit var coordinator: StateCoordinator
        val sink = ReentrantSink { coordinator.close() }
        coordinator = StateCoordinator(sink, scheduler) { Expression.HAPPY }

        coordinator.onRemoteState(remote(text = "visible", revision = REVISION_1))

        assertEquals(0, scheduler.activeTaskCount)
        scheduler.runEvenIfCanceled(0)
        assertEquals(1, sink.states.size)
    }

    @Test
    fun callsFromAnotherThreadAreRejectedAfterTheOwnerIsBound() {
        val coordinator = StateCoordinator(RecordingSink(), FakeScheduler()) { Expression.HAPPY }
        coordinator.onLocalTap()

        val failure = invokeOnAnotherThread { coordinator.onLocalTap() }

        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun scheduledCallbackFromAnotherThreadIsRejected() {
        val scheduler = UnreliableScheduler()
        val coordinator = StateCoordinator(RecordingSink(), scheduler) { Expression.HAPPY }
        coordinator.onLocalTap()

        val failure = invokeOnAnotherThread { scheduler.runEvenIfCanceled(0) }

        assertTrue(failure is IllegalStateException)
    }

    private fun remote(
        expression: Expression = Expression.ANGRY,
        text: String? = null,
        style: BubbleStyle = BubbleStyle.NORMAL,
        revision: String,
    ) = RemoteState(expression, text, style, revision)

    private fun invokeOnAnotherThread(action: () -> Unit): Throwable? {
        var failure: Throwable? = null
        val thread = Thread {
            try {
                action()
            } catch (error: Throwable) {
                failure = error
            }
        }
        thread.start()
        thread.join()
        return failure
    }

    private class RecordingSink : UiSink {
        val states = mutableListOf<VisibleState>()

        override fun render(state: VisibleState) {
            states += state
        }
    }

    private class ReentrantSink(
        private val onRender: (VisibleState) -> Unit,
    ) : UiSink {
        val states = mutableListOf<VisibleState>()

        override fun render(state: VisibleState) {
            states += state
            onRender(state)
        }
    }

    private class UnreliableScheduler : DelayScheduler {
        private data class Scheduled(
            val action: () -> Unit,
            var canceled: Boolean = false,
        )

        private val scheduled = mutableListOf<Scheduled>()

        val activeTaskCount: Int
            get() = scheduled.count { !it.canceled }

        override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
            val task = Scheduled(action)
            scheduled += task
            return Cancelable { task.canceled = true }
        }

        fun runEvenIfCanceled(index: Int) {
            scheduled[index].action()
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

    private companion object {
        const val REVISION_1 = "2026-07-29T12:00:01Z"
        const val REVISION_2 = "2026-07-29T12:00:02Z"
        const val REVISION_3 = "2026-07-29T12:00:03Z"
    }
}
