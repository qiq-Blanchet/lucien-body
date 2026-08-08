package com.luc.body.state

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class StateCoordinatorTest {
    @Test
    fun allTwentyEightNamedExpressionsHaveTheSpecifiedPriorityAndParse() {
        val expected = linkedMapOf(
            "dizzy" to 10,
            "grabbed" to 9,
            "stuck_grab" to 9,
            "love" to 8,
            "dancing" to 8,
            "thinking" to 7,
            "talking" to 7,
            "angry" to 6,
            "shocked" to 6,
            "happy" to 5,
            "smug" to 5,
            "confused" to 5,
            "shy" to 5,
            "proud" to 5,
            "sulky" to 5,
            "stuck_tap" to 5,
            "waving" to 4,
            "clingy" to 4,
            "morning" to 3,
            "night" to 3,
            "sleepy" to 3,
            "lonely_1" to 2,
            "lonely_2" to 2,
            "lonely_3" to 2,
            "eating" to 2,
            "peeking" to 1,
            "stuck" to 1,
            "idle" to 0,
        )

        assertEquals(28, Expression.entries.size)
        assertEquals(expected, Expression.entries.associate { it.id to it.priority })
        expected.keys.forEach { id ->
            assertEquals(id, Expression.fromRemote("  ${id.uppercase()}  ").id)
        }
        assertEquals(Expression.IDLE, Expression.fromRemote("not_a_state"))
        assertEquals(Expression.IDLE, Expression.fromRemote(null))
    }

    @Test
    fun sleepyBubbleStyleAndOptionalRemoteEmotionFieldsAreSupported() {
        assertEquals(BubbleStyle.SLEEPY, BubbleStyle.fromRemote("SLEEPY"))
        assertEquals(BubbleStyle.NORMAL, BubbleStyle.fromRemote(null))

        val compatible = remote(revision = REVISION_1)
        assertNull(compatible.valence)
        assertNull(compatible.arousal)
        assertNull(compatible.heat)
        assertEquals(
            compatible.copy(valence = 0.75, arousal = 0.25, heat = 8),
            RemoteState(
                expression = Expression.ANGRY,
                bubbleText = null,
                bubbleStyle = BubbleStyle.NORMAL,
                updatedAt = REVISION_1,
                valence = 0.75,
                arousal = 0.25,
                heat = 8,
            ),
        )
    }

    @Test
    fun higherPriorityWinsAndLaterStateWinsAtTheSamePriority() {
        val sink = RecordingSink()
        val coordinator = coordinator(sink)

        coordinator.requestState(Expression.HAPPY)
        coordinator.requestState(Expression.PEEKING)
        assertEquals(Expression.HAPPY, sink.states.last().expression)

        coordinator.requestState(Expression.SHY)
        assertEquals(Expression.SHY, sink.states.last().expression)

        coordinator.requestState(Expression.SHOCKED)
        assertEquals(Expression.SHOCKED, sink.states.last().expression)
    }

    @Test
    fun tapDoubleTapAndStuckTapLockRemoteUpdatesForOnePointTwoSeconds() {
        val reactions = listOf<(StateCoordinator) -> Unit>(
            { it.onLocalTap() },
            { it.onDoubleTap("double") },
            {
                it.setStuck(true)
                it.onStuckTap("stuck")
            },
        )

        reactions.forEachIndexed { index, react ->
            val sink = RecordingSink()
            val scheduler = FakeScheduler()
            val coordinator = coordinator(sink, scheduler)
            coordinator.onRemoteState(remote(Expression.IDLE, revision = REVISION_1))

            react(coordinator)
            assertEquals(Expression.TALKING, sink.states.last().expression)
            assertTrue(sink.states.last().bubbleText?.isNotBlank() == true)
            coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_2))
            scheduler.advanceBy(1_199)
            assertEquals("reaction $index ended early", Expression.TALKING, sink.states.last().expression)

            scheduler.advanceBy(1)
            val expectedAfterLock = if (index == 2) Expression.STUCK else Expression.LOVE
            assertEquals(expectedAfterLock, sink.states.last().expression)
            assertNull(sink.states.last().bubbleText)
            if (index == 2) {
                coordinator.setStuck(false)
                assertEquals(Expression.LOVE, sink.states.last().expression)
            }
        }
    }

    @Test
    fun localBubbleUsesTalkingArbitrationButNeverOverridesLove() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)

        coordinator.startLocalReaction(Expression.LOVE, "high priority")
        assertEquals(Expression.LOVE, sink.states.last().expression)
        assertEquals("high priority", sink.states.last().bubbleText)

        scheduler.advanceBy(1_200)
        assertEquals(Expression.IDLE, sink.states.last().expression)
        assertNull(sink.states.last().bubbleText)
    }

    @Test
    fun onlyTheNewestRemoteUpdateIsAppliedAfterALocalLock() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)

        coordinator.onLocalTap()
        coordinator.onRemoteState(remote(Expression.ANGRY, revision = REVISION_1))
        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_2))
        scheduler.advanceBy(1_200)

        assertEquals(Expression.LOVE, sink.states.last().expression)
    }

    @Test
    fun bufferedRemoteStateCannotLeakThroughAnUnrelatedRenderDuringLocalLock() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)

        coordinator.onLocalTap()
        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_1))
        coordinator.setContextState(ContextSource.TIME_SLOT, Expression.SLEEPY)

        assertEquals(Expression.TALKING, sink.states.last().expression)
        scheduler.advanceBy(1_200)
        assertEquals(Expression.LOVE, sink.states.last().expression)
    }

    @Test
    fun rejectedLowPriorityReactionPreservesRemoteBubble() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)
        coordinator.onRemoteState(remote(Expression.LOVE, "remote", revision = REVISION_1))

        coordinator.onLocalTap()

        assertEquals(Expression.LOVE, sink.states.last().expression)
        assertEquals("remote", sink.states.last().bubbleText)
    }

    @Test
    fun independentContextSourcesDoNotClearOrHideEachOther() {
        val sink = RecordingSink()
        val coordinator = coordinator(sink)

        coordinator.setContextState(ContextSource.APP, Expression.PEEKING)
        coordinator.setContextState(ContextSource.LONELINESS, Expression.SLEEPY)
        assertEquals(Expression.SLEEPY, sink.states.last().expression)

        coordinator.setContextState(ContextSource.LONELINESS, null)
        assertEquals(Expression.PEEKING, sink.states.last().expression)
    }

    @Test
    fun grabbedAndStuckGrabCannotBeInterruptedUntilDragEnds() {
        val sink = RecordingSink()
        val coordinator = coordinator(sink)

        coordinator.beginDrag(fromStuck = false)
        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_1))
        coordinator.onTransientState(Expression.DIZZY)
        coordinator.showBubble("cannot interrupt")
        assertEquals(Expression.GRABBED, sink.states.last().expression)

        coordinator.endDrag(isStuck = false)
        assertEquals(Expression.LOVE, sink.states.last().expression)

        coordinator.setStuck(true)
        coordinator.beginDrag(fromStuck = true)
        coordinator.requestState(Expression.DIZZY)
        assertEquals(Expression.STUCK_GRAB, sink.states.last().expression)

        coordinator.endDrag(isStuck = true)
        assertEquals(Expression.STUCK, sink.states.last().expression)
    }

    @Test
    fun transientDurationsAreTwoThreeTwoAndFiveSeconds() {
        val cases = listOf(
            Expression.DIZZY to 2_000L,
            Expression.WAVING to 3_000L,
            Expression.CLINGY to 2_000L,
            Expression.MORNING to 5_000L,
        )

        cases.forEach { (expression, durationMs) ->
            val sink = RecordingSink()
            val scheduler = FakeScheduler()
            val coordinator = coordinator(sink, scheduler)
            coordinator.onTransientState(expression)
            scheduler.advanceBy(durationMs - 1)
            assertEquals(expression, sink.states.last().expression)
            scheduler.advanceBy(1)
            assertEquals(Expression.IDLE, sink.states.last().expression)
        }
    }

    @Test
    fun transientFallbackIsStrictlyStuckThenRemoteThenContextThenIdle() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)
        coordinator.setContextState(Expression.SLEEPY)
        coordinator.onRemoteState(remote(Expression.HAPPY, revision = REVISION_1))
        coordinator.setStuck(true)

        coordinator.onTransientState(Expression.DIZZY)
        scheduler.advanceBy(2_000)
        assertEquals(Expression.STUCK, sink.states.last().expression)

        coordinator.setStuck(false)
        assertEquals(Expression.HAPPY, sink.states.last().expression)

        coordinator.clearRemoteState()
        assertEquals(Expression.SLEEPY, sink.states.last().expression)

        coordinator.setContextState(null)
        assertEquals(Expression.IDLE, sink.states.last().expression)
    }

    @Test
    fun bubbleRequestsTalkingAndReturnsToThePreviousStateWhenItHides() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)
        coordinator.requestState(Expression.HAPPY)

        coordinator.showBubble("hello", BubbleStyle.SLEEPY)
        assertEquals(Expression.TALKING, sink.states.last().expression)
        assertEquals("hello", sink.states.last().bubbleText)
        assertEquals(BubbleStyle.SLEEPY, sink.states.last().bubbleStyle)

        scheduler.advanceBy(3_999)
        assertEquals(Expression.TALKING, sink.states.last().expression)
        scheduler.advanceBy(1)
        assertEquals(Expression.HAPPY, sink.states.last().expression)
        assertNull(sink.states.last().bubbleText)
    }

    @Test
    fun remoteBubblePreservesTheSuppliedExpression() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)

        coordinator.onRemoteState(remote(Expression.HAPPY, "hello", revision = REVISION_1))
        assertEquals(Expression.HAPPY, sink.states.last().expression)
        assertEquals("hello", sink.states.last().bubbleText)

        coordinator.onRemoteState(remote(Expression.LOVE, "love", revision = REVISION_2))
        assertEquals(Expression.LOVE, sink.states.last().expression)
        assertEquals("love", sink.states.last().bubbleText)
    }

    @Test
    fun remoteExpressionExpiresWithItsBubbleAndRestoresLocalContext() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)
        coordinator.setContextState(ContextSource.TIME_SLOT, Expression.SLEEPY)

        coordinator.onRemoteState(remote(Expression.HAPPY, "hello", revision = REVISION_1))
        assertEquals(Expression.HAPPY, sink.states.last().expression)

        scheduler.advanceBy(3_999)
        assertEquals(Expression.HAPPY, sink.states.last().expression)
        scheduler.advanceBy(1)
        assertEquals(Expression.SLEEPY, sink.states.last().expression)
        assertNull(sink.states.last().bubbleText)
    }

    @Test
    fun remoteExpressionWithoutBubbleExpiresAfterTheBaseBubbleDuration() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)

        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_1))
        scheduler.advanceBy(3_999)
        assertEquals(Expression.LOVE, sink.states.last().expression)
        scheduler.advanceBy(1)
        assertEquals(Expression.IDLE, sink.states.last().expression)
    }

    @Test
    fun canceledRemoteExpiryCannotClearANewerRemoteState() {
        val sink = RecordingSink()
        val scheduler = UnreliableScheduler()
        val coordinator = coordinator(sink, scheduler)

        coordinator.onRemoteState(remote(Expression.HAPPY, revision = REVISION_1))
        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_2))
        scheduler.runEvenIfCanceled(0)

        assertEquals(Expression.LOVE, sink.states.last().expression)
    }

    @Test
    fun bubbleDurationUsesCodePointsFullTensAndATenSecondCap() {
        assertEquals(4_000L, StateCoordinator.bubbleDurationMs("x".repeat(9)))
        assertEquals(5_000L, StateCoordinator.bubbleDurationMs("x".repeat(10)))
        assertEquals(5_000L, StateCoordinator.bubbleDurationMs("🦀".repeat(10)))
        assertEquals(10_000L, StateCoordinator.bubbleDurationMs("x".repeat(60)))
        assertEquals(10_000L, StateCoordinator.bubbleDurationMs("x".repeat(1_000)))
        assertEquals(4_000L, StateCoordinator.bubbleDurationMs("x".repeat(20), baseDurationSeconds = 2))
    }

    @Test
    fun bubbleTextIsNotTruncatedByTheStateCore() {
        val text = "🦀".repeat(121)
        val sink = RecordingSink()
        val coordinator = coordinator(sink)

        coordinator.showBubble(text)

        assertEquals(text, sink.states.last().bubbleText)
    }

    @Test
    fun bubbleBaseDurationCanBeSetFromTwoThroughTenSeconds() {
        val sink = RecordingSink()
        val scheduler = FakeScheduler()
        val coordinator = coordinator(sink, scheduler)
        coordinator.setBubbleBaseDurationSeconds(2)
        coordinator.showBubble("x".repeat(10))

        scheduler.advanceBy(2_999)
        assertEquals(Expression.TALKING, sink.states.last().expression)
        scheduler.advanceBy(1)
        assertNull(sink.states.last().bubbleText)

        listOf(1, 11).forEach { invalid ->
            try {
                coordinator.setBubbleBaseDurationSeconds(invalid)
                fail("Expected base duration $invalid to be rejected")
            } catch (_: IllegalArgumentException) {
                Unit
            }
        }
    }

    @Test
    fun invalidAndDuplicateRemoteRevisionsAreIgnored() {
        val sink = RecordingSink()
        val coordinator = coordinator(sink)
        coordinator.onRemoteState(remote(Expression.HAPPY, revision = REVISION_2))
        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_1))
        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_2))
        coordinator.onRemoteState(remote(Expression.LOVE, revision = "not-an-instant"))

        assertEquals(1, sink.states.size)
        assertEquals(Expression.HAPPY, sink.states.last().expression)
    }

    @Test
    fun canceledCallbacksCannotExpireNewerStateAndCloseCancelsEverything() {
        val sink = RecordingSink()
        val scheduler = UnreliableScheduler()
        val coordinator = coordinator(sink, scheduler)

        coordinator.onLocalTap()
        coordinator.onDoubleTap()
        scheduler.runEvenIfCanceled(0)
        assertEquals(Expression.TALKING, sink.states.last().expression)

        coordinator.showBubble("visible")
        coordinator.close()
        val renderCount = sink.states.size
        scheduler.runAllEvenIfCanceled()
        coordinator.requestState(Expression.DIZZY)
        coordinator.onRemoteState(remote(Expression.LOVE, revision = REVISION_1))
        assertEquals(renderCount, sink.states.size)
        assertEquals(0, scheduler.activeTaskCount)
    }

    @Test
    fun coordinatorAndDelayedCallbacksRemainBoundToOneOwnerThread() {
        val scheduler = UnreliableScheduler()
        val coordinator = coordinator(RecordingSink(), scheduler)
        coordinator.onLocalTap()

        assertTrue(invokeOnAnotherThread { coordinator.requestState(Expression.HAPPY) } is IllegalStateException)
        assertTrue(invokeOnAnotherThread { scheduler.runEvenIfCanceled(0) } is IllegalStateException)
    }

    private fun coordinator(
        sink: UiSink,
        scheduler: DelayScheduler = FakeScheduler(),
    ) = StateCoordinator(sink, scheduler) { Expression.HAPPY }

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

        fun runAllEvenIfCanceled() {
            scheduled.forEach { it.action() }
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
        const val REVISION_1 = "2026-08-04T12:00:01Z"
        const val REVISION_2 = "2026-08-04T12:00:02Z"
    }
}
