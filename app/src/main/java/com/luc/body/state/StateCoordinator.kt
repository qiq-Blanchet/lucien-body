package com.luc.body.state

import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicReference

/**
 * A single-thread state machine. The first public call binds its owner thread;
 * all later public calls and delayed callbacks must use that same thread.
 */
class StateCoordinator(
    private val uiSink: UiSink,
    private val scheduler: DelayScheduler,
    private val localBubbleText: String = "Hi!",
    private val localExpression: () -> Expression,
) : AutoCloseable {
    private val ownerThread = AtomicReference<Thread?>(null)

    private var closed = false
    private var localOverrideActive = false
    private var localRevision = 0L
    private var localOverrideGeneration = 0L
    private var bubbleGeneration = 0L
    private var newestIsoRevision: Instant? = null
    private var latestRemoteState: RemoteState? = null
    private var bufferedRemoteState: RemoteState? = null
    private var localOverrideTask: Cancelable? = null
    private var bubbleHideTask: Cancelable? = null

    fun onRemoteState(state: RemoteState) {
        requireOwnerThread()
        if (closed) return

        val instant = parseIsoRevision(state.updatedAt) ?: return
        val newest = newestIsoRevision
        if (newest != null && !instant.isAfter(newest)) return

        newestIsoRevision = instant
        val normalized = state.copy(bubbleText = normalizeBubbleText(state.bubbleText))
        latestRemoteState = normalized
        if (localOverrideActive) {
            bufferedRemoteState = normalized
            return
        }
        renderRemote(normalized)
    }

    fun onLocalTap() {
        requireOwnerThread()
        if (closed) return

        val previousLocalTask = invalidateLocalOverride()
        val previousBubbleTask = invalidateBubbleHide()
        localOverrideActive = true
        localRevision += 1
        val revision = "local-$localRevision"
        val generation = ++localOverrideGeneration
        val task = scheduler.schedule(LOCAL_OVERRIDE_DURATION_MS) {
            onLocalOverrideExpired(generation, revision)
        }
        if (closed || generation != localOverrideGeneration || !localOverrideActive) {
            task.cancel()
            previousLocalTask?.cancel()
            previousBubbleTask?.cancel()
            return
        }
        localOverrideTask = task
        previousLocalTask?.cancel()
        previousBubbleTask?.cancel()
        if (closed || generation != localOverrideGeneration || !localOverrideActive) return

        val expression = localExpression()
        if (closed || generation != localOverrideGeneration || !localOverrideActive) return
        uiSink.render(
            VisibleState(
                expression = expression,
                bubbleText = localBubbleText,
                bubbleStyle = BubbleStyle.NORMAL,
                revision = revision,
            ),
        )
    }

    override fun close() {
        requireOwnerThread()
        if (closed) return

        closed = true
        val localTask = invalidateLocalOverride()
        val bubbleTask = invalidateBubbleHide()
        bufferedRemoteState = null
        localTask?.cancel()
        bubbleTask?.cancel()
    }

    private fun onLocalOverrideExpired(generation: Long, revision: String) {
        requireOwnerThread()
        if (closed || generation != localOverrideGeneration || !localOverrideActive) return

        localOverrideTask = null
        localOverrideActive = false
        localOverrideGeneration += 1
        val stateToRender = bufferedRemoteState ?: latestRemoteState
        bufferedRemoteState = null
        if (stateToRender == null) {
            uiSink.render(
                VisibleState(
                    expression = Expression.IDLE,
                    bubbleText = null,
                    bubbleStyle = BubbleStyle.NORMAL,
                    revision = "$revision-expired",
                ),
            )
        } else {
            renderRemote(stateToRender)
        }
    }

    private fun renderRemote(state: RemoteState) {
        if (closed || localOverrideActive) return

        val previousBubbleTask = invalidateBubbleHide()
        var renderGeneration = bubbleGeneration
        if (state.bubbleText != null) {
            val bubbleTaskGeneration = ++bubbleGeneration
            renderGeneration = bubbleTaskGeneration
            val newBubbleTask = scheduler.schedule(REMOTE_BUBBLE_DURATION_MS) {
                onBubbleHideExpired(bubbleTaskGeneration, state)
            }
            if (closed || localOverrideActive || bubbleTaskGeneration != bubbleGeneration) {
                newBubbleTask.cancel()
                previousBubbleTask?.cancel()
                return
            }
            bubbleHideTask = newBubbleTask
        }
        previousBubbleTask?.cancel()
        if (closed || localOverrideActive || renderGeneration != bubbleGeneration) return

        uiSink.render(
            VisibleState(
                expression = state.expression,
                bubbleText = state.bubbleText,
                bubbleStyle = state.bubbleStyle,
                revision = state.updatedAt,
            ),
        )
    }

    private fun onBubbleHideExpired(generation: Long, state: RemoteState) {
        requireOwnerThread()
        if (closed || generation != bubbleGeneration || localOverrideActive) return

        bubbleHideTask = null
        bubbleGeneration += 1
        uiSink.render(
            VisibleState(
                expression = state.expression,
                bubbleText = null,
                bubbleStyle = state.bubbleStyle,
                revision = state.updatedAt,
            ),
        )
    }

    private fun invalidateLocalOverride(): Cancelable? {
        localOverrideGeneration += 1
        val task = localOverrideTask
        localOverrideTask = null
        return task
    }

    private fun invalidateBubbleHide(): Cancelable? {
        bubbleGeneration += 1
        val task = bubbleHideTask
        bubbleHideTask = null
        return task
    }

    private fun requireOwnerThread() {
        val current = Thread.currentThread()
        val owner = ownerThread.get()
        if (owner == null) {
            check(ownerThread.compareAndSet(null, current) || ownerThread.get() === current) {
                "StateCoordinator must be used from one owner thread"
            }
        }
        check(ownerThread.get() === current) {
            "StateCoordinator must be used from one owner thread"
        }
    }

    private fun parseIsoRevision(revision: String): Instant? = try {
        Instant.parse(revision)
    } catch (_: DateTimeParseException) {
        null
    }

    private fun normalizeBubbleText(text: String?): String? = when {
        text.isNullOrBlank() -> null
        else -> text.take(MAX_BUBBLE_TEXT_LENGTH)
    }

    private companion object {
        const val LOCAL_OVERRIDE_DURATION_MS = 1_200L
        const val REMOTE_BUBBLE_DURATION_MS = 5_000L
        const val MAX_BUBBLE_TEXT_LENGTH = 120
    }
}
