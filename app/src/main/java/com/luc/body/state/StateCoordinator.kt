package com.luc.body.state

import java.time.Instant
import java.time.format.DateTimeParseException

class StateCoordinator(
    private val uiSink: UiSink,
    private val scheduler: DelayScheduler,
    private val localBubbleText: String = "Hi!",
    private val localExpression: () -> Expression,
) : AutoCloseable {
    private val lock = Any()
    private val recentUnorderedRevisions = LinkedHashSet<String>()

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

    fun onRemoteState(state: RemoteState) = synchronized(lock) {
        if (closed || !acceptsRevision(state.updatedAt)) return@synchronized

        val normalized = state.copy(bubbleText = normalizeBubbleText(state.bubbleText))
        latestRemoteState = normalized
        if (localOverrideActive) {
            bufferedRemoteState = normalized
            return@synchronized
        }
        renderRemoteLocked(normalized)
    }

    fun onLocalTap() = synchronized(lock) {
        if (closed) return@synchronized

        cancelLocalOverrideLocked()
        cancelBubbleHideLocked()
        localOverrideActive = true
        localRevision += 1
        val revision = "local-$localRevision"
        val generation = ++localOverrideGeneration
        val task = scheduler.schedule(LOCAL_OVERRIDE_DURATION_MS) {
            synchronized(lock) {
                onLocalOverrideExpiredLocked(generation, revision)
            }
        }
        if (closed || generation != localOverrideGeneration || !localOverrideActive) {
            task.cancel()
            return@synchronized
        }
        localOverrideTask = task
        uiSink.render(
            VisibleState(
                expression = localExpression(),
                bubbleText = localBubbleText,
                bubbleStyle = BubbleStyle.NORMAL,
                revision = revision,
            ),
        )
    }

    override fun close() = synchronized(lock) {
        if (closed) return@synchronized

        closed = true
        cancelLocalOverrideLocked()
        cancelBubbleHideLocked()
        bufferedRemoteState = null
    }

    private fun onLocalOverrideExpiredLocked(generation: Long, revision: String) {
        if (closed || generation != localOverrideGeneration || !localOverrideActive) return

        localOverrideTask = null
        localOverrideActive = false
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
            renderRemoteLocked(stateToRender)
        }
    }

    private fun renderRemoteLocked(state: RemoteState) {
        cancelBubbleHideLocked()
        if (state.bubbleText != null) {
            val generation = ++bubbleGeneration
            val task = scheduler.schedule(REMOTE_BUBBLE_DURATION_MS) {
                synchronized(lock) {
                    onBubbleHideExpiredLocked(generation, state)
                }
            }
            if (closed || generation != bubbleGeneration || localOverrideActive) {
                task.cancel()
                return
            }
            bubbleHideTask = task
        }
        if (closed) return

        uiSink.render(
            VisibleState(
                expression = state.expression,
                bubbleText = state.bubbleText,
                bubbleStyle = state.bubbleStyle,
                revision = state.updatedAt,
            ),
        )
    }

    private fun onBubbleHideExpiredLocked(generation: Long, state: RemoteState) {
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

    private fun cancelLocalOverrideLocked() {
        localOverrideGeneration += 1
        localOverrideTask?.cancel()
        localOverrideTask = null
    }

    private fun cancelBubbleHideLocked() {
        bubbleGeneration += 1
        bubbleHideTask?.cancel()
        bubbleHideTask = null
    }

    private fun acceptsRevision(revision: String): Boolean {
        val isoRevision = parseIsoRevision(revision)
        if (isoRevision != null) {
            val latest = newestIsoRevision
            if (latest != null && !isoRevision.isAfter(latest)) return false

            newestIsoRevision = isoRevision
            return true
        }

        if (!recentUnorderedRevisions.add(revision)) return false
        if (recentUnorderedRevisions.size > MAX_RECENT_UNORDERED_REVISIONS) {
            recentUnorderedRevisions.iterator().run {
                next()
                remove()
            }
        }
        return true
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
        const val MAX_RECENT_UNORDERED_REVISIONS = 64
    }
}
