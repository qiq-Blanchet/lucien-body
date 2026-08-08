package com.luc.body.state

import java.time.Instant
import java.time.format.DateTimeParseException
import java.util.concurrent.atomic.AtomicReference

/**
 * Pure Kotlin state arbitration. The first public call binds the owner thread;
 * every later call and every scheduled callback must run on that same thread.
 */
class StateCoordinator(
    private val uiSink: UiSink,
    private val scheduler: DelayScheduler,
    private val localBubbleText: String = "Hi!",
    bubbleBaseDurationSeconds: Int = DEFAULT_BUBBLE_DURATION_SECONDS,
    private val localExpression: () -> Expression,
) : AutoCloseable {
    private data class Candidate(
        val expression: Expression,
        val sequence: Long,
        val revision: String,
    )

    private data class ActiveBubble(
        val text: String,
        val style: BubbleStyle,
        val revision: String,
        val generation: Long,
    )

    private val ownerThread = AtomicReference<Thread?>(null)
    private val idleCandidate = Candidate(Expression.IDLE, 0L, "idle")

    private var closed = false
    private var sequence = 0L
    private var localRevision = 0L
    private var bubbleBaseSeconds = checkedBubbleBaseDuration(bubbleBaseDurationSeconds)

    private var newestIsoRevision: Instant? = null
    private var latestRemoteState: RemoteState? = null
    private var remoteSequence = 0L
    private var bufferedRemoteState: RemoteState? = null
    private var remoteExpiryGeneration = 0L
    private var remoteExpiryTask: Cancelable? = null

    private var stuck = false
    private var stuckSequence = 0L
    private val contextCandidates = mutableMapOf<ContextSource, Candidate>()
    private var requestedCandidate: Candidate? = null
    private var dragCandidate: Candidate? = null

    private var localReactionCandidate: Candidate? = null
    private var localReactionGeneration = 0L
    private var localReactionTask: Cancelable? = null

    private var transientCandidate: Candidate? = null
    private var transientGeneration = 0L
    private var transientTask: Cancelable? = null

    private var activeBubble: ActiveBubble? = null
    private var bubbleTalkingCandidate: Candidate? = null
    private var bubbleGeneration = 0L
    private var bubbleHideTask: Cancelable? = null

    fun onRemoteState(state: RemoteState) {
        requireOwnerThread()
        if (closed) return

        val instant = parseIsoRevision(state.updatedAt) ?: return
        val newest = newestIsoRevision
        if (newest != null && !instant.isAfter(newest)) return

        val normalized = state.copy(bubbleText = normalizeBubbleText(state.bubbleText))
        newestIsoRevision = instant
        if (localReactionCandidate != null || dragCandidate != null) {
            bufferedRemoteState = normalized
            return
        }
        applyRemoteState(normalized)
    }

    fun clearRemoteState() {
        requireOwnerThread()
        if (closed) return

        cancelRemoteExpiry()
        latestRemoteState = null
        bufferedRemoteState = null
        clearActiveBubble()
        renderCurrent()
    }

    /** A persistent local state, for callers such as tap-frequency behavior. */
    fun requestState(expression: Expression) {
        requireOwnerThread()
        if (closed || dragCandidate != null) return
        if (expression.priority < selectedCandidate().expression.priority) return

        requestedCandidate = candidate(expression, "state")
        renderCurrent()
    }

    fun clearRequestedState() {
        requireOwnerThread()
        if (closed || requestedCandidate == null) return

        requestedCandidate = null
        renderCurrent()
    }

    /** Sets the default local context while preserving source-specific contexts. */
    fun setContextState(expression: Expression?) = setContextState(ContextSource.DEFAULT, expression)

    /** Sets one time-slot, loneliness, foreground-app, or self-talk context. */
    fun setContextState(source: ContextSource, expression: Expression?) {
        requireOwnerThread()
        if (closed) return

        if (expression == null) {
            contextCandidates.remove(source)
        } else {
            contextCandidates[source] = candidate(expression, "context-${source.name.lowercase()}")
        }
        renderCurrent()
    }

    fun setStuck(isStuck: Boolean) {
        requireOwnerThread()
        if (closed) return

        stuck = isStuck
        stuckSequence = nextSequence()
        renderCurrent()
    }

    fun beginDrag(fromStuck: Boolean = stuck) {
        requireOwnerThread()
        if (closed || dragCandidate != null) return

        cancelLocalReaction()
        cancelTransient()
        clearActiveBubble()
        requestedCandidate = null
        dragCandidate = candidate(
            if (fromStuck) Expression.STUCK_GRAB else Expression.GRABBED,
            "drag",
        )
        renderCurrent()
    }

    fun endDrag(isStuck: Boolean) {
        requireOwnerThread()
        if (closed || dragCandidate == null) return

        dragCandidate = null
        stuck = isStuck
        stuckSequence = nextSequence()
        val buffered = bufferedRemoteState
        bufferedRemoteState = null
        if (buffered != null) {
            applyRemoteState(buffered)
        } else {
            renderCurrent()
        }
    }

    fun onLocalTap() {
        requireOwnerThread()
        if (closed) return
        startLocalReaction(localExpression(), localBubbleText, BubbleStyle.NORMAL)
    }

    fun onDoubleTap(bubbleText: String? = localBubbleText) {
        startLocalReaction(Expression.HAPPY, bubbleText, BubbleStyle.LOVE)
    }

    fun onStuckTap(bubbleText: String? = localBubbleText) {
        startLocalReaction(Expression.STUCK_TAP, bubbleText, BubbleStyle.NORMAL)
    }

    fun startLocalReaction(
        expression: Expression,
        bubbleText: String? = null,
        bubbleStyle: BubbleStyle = BubbleStyle.NORMAL,
    ) {
        requireOwnerThread()
        if (closed || dragCandidate != null) return

        if (expression.priority < selectedCandidate(includeBubble = false).expression.priority) return

        clearActiveBubble()
        cancelLocalReaction()
        requestedCandidate = null
        val reaction = candidate(expression, "local")
        localReactionCandidate = reaction
        val generation = ++localReactionGeneration
        val task = scheduler.schedule(LOCAL_REACTION_DURATION_MS) {
            onLocalReactionExpired(generation)
        }
        if (closed || generation != localReactionGeneration || localReactionCandidate !== reaction) {
            task.cancel()
            return
        }
        localReactionTask = task
        val normalizedBubble = normalizeBubbleText(bubbleText)
        if (normalizedBubble == null) {
            renderCurrent()
        } else {
            installBubble(
                text = normalizedBubble,
                style = bubbleStyle,
                revision = reaction.revision,
                durationMs = null,
            )
        }
    }

    fun onTransientState(expression: Expression) {
        requireOwnerThread()
        if (closed || dragCandidate != null) return
        val durationMs = TRANSIENT_DURATIONS_MS[expression]
            ?: throw IllegalArgumentException("$expression is not a timed transient state")
        if (expression.priority < selectedCandidate().expression.priority) return

        cancelTransient()
        requestedCandidate = null
        val transient = candidate(expression, "transient")
        transientCandidate = transient
        val generation = ++transientGeneration
        val task = scheduler.schedule(durationMs) {
            onTransientExpired(generation)
        }
        if (closed || generation != transientGeneration || transientCandidate !== transient) {
            task.cancel()
            return
        }
        transientTask = task
        renderCurrent()
    }

    fun showBubble(
        text: String,
        style: BubbleStyle = BubbleStyle.NORMAL,
        revision: String? = null,
    ) {
        requireOwnerThread()
        if (closed) return

        val normalized = normalizeBubbleText(text)
        if (normalized == null) {
            if (clearActiveBubble()) renderCurrent()
            return
        }
        installBubble(
            text = normalized,
            style = style,
            revision = revision ?: nextLocalRevision("bubble"),
            durationMs = bubbleDurationMs(normalized, bubbleBaseSeconds),
        )
    }

    fun hideBubble() {
        requireOwnerThread()
        if (closed || !clearActiveBubble()) return

        renderCurrent()
    }

    fun setBubbleBaseDurationSeconds(seconds: Int) {
        requireOwnerThread()
        if (closed) return

        bubbleBaseSeconds = checkedBubbleBaseDuration(seconds)
    }

    fun currentExpression(): Expression {
        requireOwnerThread()
        return selectedCandidate().expression
    }

    fun refresh() {
        requireOwnerThread()
        renderCurrent()
    }

    override fun close() {
        requireOwnerThread()
        if (closed) return

        closed = true
        cancelLocalReaction()
        cancelTransient()
        cancelRemoteExpiry()
        clearActiveBubble()
        bufferedRemoteState = null
        requestedCandidate = null
        contextCandidates.clear()
        dragCandidate = null
    }

    private fun applyRemoteState(state: RemoteState) {
        cancelRemoteExpiry()
        latestRemoteState = state
        remoteSequence = nextSequence()
        clearActiveBubble()
        val text = state.bubbleText
        val durationMs = bubbleDurationMs(text.orEmpty(), bubbleBaseSeconds)
        if (text == null) {
            renderCurrent()
        } else {
            installBubble(
                text = text,
                style = state.bubbleStyle,
                revision = state.updatedAt,
                durationMs = null,
                requestTalking = false,
            )
        }
        val generation = ++remoteExpiryGeneration
        val task = scheduler.schedule(durationMs) {
            onRemoteStateExpired(generation, state.updatedAt)
        }
        if (closed || generation != remoteExpiryGeneration || latestRemoteState !== state) {
            task.cancel()
            return
        }
        remoteExpiryTask = task
    }

    private fun installBubble(
        text: String,
        style: BubbleStyle,
        revision: String,
        durationMs: Long?,
        requestTalking: Boolean = true,
    ) {
        clearActiveBubble()
        val previous = selectedCandidate()
        val generation = ++bubbleGeneration
        val bubble = ActiveBubble(text, style, revision, generation)
        activeBubble = bubble
        bubbleTalkingCandidate = if (
            requestTalking &&
            dragCandidate == null &&
            Expression.TALKING.priority >= previous.expression.priority
        ) {
            Candidate(Expression.TALKING, nextSequence(), revision)
        } else {
            null
        }
        if (durationMs != null) {
            val task = scheduler.schedule(durationMs) {
                onBubbleExpired(generation)
            }
            if (closed || generation != bubbleGeneration || activeBubble !== bubble) {
                task.cancel()
                return
            }
            bubbleHideTask = task
        }
        renderCurrent()
    }

    private fun onLocalReactionExpired(generation: Long) {
        requireOwnerThread()
        if (closed || generation != localReactionGeneration || localReactionCandidate == null) return

        localReactionTask = null
        localReactionCandidate = null
        localReactionGeneration += 1
        clearActiveBubble()
        val buffered = bufferedRemoteState
        bufferedRemoteState = null
        if (buffered != null) {
            applyRemoteState(buffered)
        } else {
            renderCurrent()
        }
    }

    private fun onTransientExpired(generation: Long) {
        requireOwnerThread()
        if (closed || generation != transientGeneration || transientCandidate == null) return

        transientTask = null
        transientCandidate = null
        transientGeneration += 1
        renderCurrent()
    }

    private fun onRemoteStateExpired(generation: Long, revision: String) {
        requireOwnerThread()
        if (closed || generation != remoteExpiryGeneration || latestRemoteState?.updatedAt != revision) return

        remoteExpiryTask = null
        remoteExpiryGeneration += 1
        latestRemoteState = null
        if (activeBubble?.revision == revision) clearActiveBubble()
        renderCurrent()
    }

    private fun onBubbleExpired(generation: Long) {
        requireOwnerThread()
        if (closed || generation != bubbleGeneration || activeBubble == null) return

        bubbleHideTask = null
        activeBubble = null
        bubbleTalkingCandidate = null
        bubbleGeneration += 1
        renderCurrent()
    }

    private fun cancelLocalReaction() {
        localReactionGeneration += 1
        localReactionTask?.cancel()
        localReactionTask = null
        localReactionCandidate = null
    }

    private fun cancelTransient() {
        transientGeneration += 1
        transientTask?.cancel()
        transientTask = null
        transientCandidate = null
    }

    private fun cancelRemoteExpiry() {
        remoteExpiryGeneration += 1
        remoteExpiryTask?.cancel()
        remoteExpiryTask = null
    }

    private fun clearActiveBubble(): Boolean {
        val existed = activeBubble != null || bubbleTalkingCandidate != null
        bubbleGeneration += 1
        bubbleHideTask?.cancel()
        bubbleHideTask = null
        activeBubble = null
        bubbleTalkingCandidate = null
        return existed
    }

    private fun selectedCandidate(includeBubble: Boolean = true): Candidate {
        dragCandidate?.let { return it }

        val contextCandidate = contextCandidates.values.maxWithOrNull(
            compareBy<Candidate> { it.expression.priority }.thenBy { it.sequence },
        )
        val fallback = when {
            stuck -> Candidate(Expression.STUCK, stuckSequence, "stuck-$stuckSequence")
            latestRemoteState != null -> Candidate(
                latestRemoteState!!.expression,
                remoteSequence,
                latestRemoteState!!.updatedAt,
            )
            contextCandidate != null -> contextCandidate!!
            else -> idleCandidate
        }
        return listOfNotNull(
            fallback,
            requestedCandidate,
            localReactionCandidate,
            transientCandidate,
            bubbleTalkingCandidate.takeIf { includeBubble },
        ).maxWithOrNull(
            compareBy<Candidate> { it.expression.priority }.thenBy { it.sequence },
        ) ?: idleCandidate
    }

    private fun renderCurrent() {
        if (closed) return
        val selected = selectedCandidate()
        val bubble = activeBubble
        uiSink.render(
            VisibleState(
                expression = selected.expression,
                bubbleText = bubble?.text,
                bubbleStyle = bubble?.style ?: BubbleStyle.NORMAL,
                revision = bubble?.revision ?: selected.revision,
            ),
        )
    }

    private fun candidate(expression: Expression, revisionPrefix: String): Candidate =
        Candidate(expression, nextSequence(), nextLocalRevision(revisionPrefix))

    private fun nextSequence(): Long = ++sequence

    private fun nextLocalRevision(prefix: String): String = "$prefix-${++localRevision}"

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
        else -> text
    }

    companion object {
        private const val LOCAL_REACTION_DURATION_MS = 1_200L
        private const val DEFAULT_BUBBLE_DURATION_SECONDS = 4
        private const val MAX_BUBBLE_DURATION_SECONDS = 10
        private val TRANSIENT_DURATIONS_MS = mapOf(
            Expression.DIZZY to 2_000L,
            Expression.WAVING to 3_000L,
            Expression.CLINGY to 2_000L,
            Expression.MORNING to 5_000L,
        )

        fun bubbleDurationMs(
            text: String,
            baseDurationSeconds: Int = DEFAULT_BUBBLE_DURATION_SECONDS,
        ): Long {
            val base = checkedBubbleBaseDuration(baseDurationSeconds)
            val codePoints = text.codePointCount(0, text.length)
            return (base + codePoints / 10)
                .coerceAtMost(MAX_BUBBLE_DURATION_SECONDS) * 1_000L
        }

        private fun checkedBubbleBaseDuration(seconds: Int): Int {
            require(seconds in 2..MAX_BUBBLE_DURATION_SECONDS) {
                "Bubble base duration must be between 2 and 10 seconds"
            }
            return seconds
        }
    }
}
