package com.luc.body.state

class StateCoordinator(
    private val uiSink: UiSink,
    private val scheduler: DelayScheduler,
    private val localExpression: () -> Expression,
    private val localBubbleText: String = "Hi!",
) : AutoCloseable {
    private var closed = false
    private var localOverrideActive = false
    private var localRevision = 0L
    private var lastRemoteRevision: String? = null
    private var latestRemoteState: RemoteState? = null
    private var bufferedRemoteState: RemoteState? = null
    private var localOverrideTask: Cancelable? = null
    private var bubbleHideTask: Cancelable? = null

    fun onRemoteState(state: RemoteState) {
        if (closed || state.updatedAt == lastRemoteRevision) return

        val normalized = state.copy(bubbleText = normalizeBubbleText(state.bubbleText))
        lastRemoteRevision = normalized.updatedAt
        latestRemoteState = normalized
        if (localOverrideActive) {
            bufferedRemoteState = normalized
            return
        }
        renderRemote(normalized)
    }

    fun onLocalTap() {
        if (closed) return

        localOverrideTask?.cancel()
        bubbleHideTask?.cancel()
        bubbleHideTask = null
        localOverrideActive = true
        localRevision += 1
        val revision = "local-$localRevision"
        uiSink.render(
            VisibleState(
                expression = localExpression(),
                bubbleText = localBubbleText,
                bubbleStyle = BubbleStyle.NORMAL,
                revision = revision,
            ),
        )
        localOverrideTask = scheduler.schedule(LOCAL_OVERRIDE_DURATION_MS) {
            localOverrideTask = null
            if (closed) return@schedule

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
                renderRemote(stateToRender)
            }
        }
    }

    override fun close() {
        if (closed) return

        closed = true
        localOverrideTask?.cancel()
        bubbleHideTask?.cancel()
        localOverrideTask = null
        bubbleHideTask = null
        bufferedRemoteState = null
    }

    private fun renderRemote(state: RemoteState) {
        bubbleHideTask?.cancel()
        bubbleHideTask = null
        uiSink.render(
            VisibleState(
                expression = state.expression,
                bubbleText = state.bubbleText,
                bubbleStyle = state.bubbleStyle,
                revision = state.updatedAt,
            ),
        )
        if (state.bubbleText != null) {
            bubbleHideTask = scheduler.schedule(REMOTE_BUBBLE_DURATION_MS) {
                bubbleHideTask = null
                if (!closed && !localOverrideActive) {
                    uiSink.render(
                        VisibleState(
                            expression = state.expression,
                            bubbleText = null,
                            bubbleStyle = state.bubbleStyle,
                            revision = state.updatedAt,
                        ),
                    )
                }
            }
        }
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
