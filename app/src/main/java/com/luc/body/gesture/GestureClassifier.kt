package com.luc.body.gesture

import kotlin.math.hypot

sealed interface GestureResult {
    data object None : GestureResult

    data class Move(
        val deltaX: Float,
        val deltaY: Float,
    ) : GestureResult

    data object Tap : GestureResult

    data object DragEnd : GestureResult
}

class GestureClassifier(
    private val touchSlopPx: Float,
    private val tapTimeoutMs: Long,
) {
    private var gesture: ActiveGesture? = null

    init {
        require(touchSlopPx > 0f) { "touchSlopPx must be positive" }
        require(tapTimeoutMs > 0) { "tapTimeoutMs must be positive" }
    }

    fun onDown(rawX: Float, rawY: Float, eventTimeMs: Long): GestureResult {
        gesture = ActiveGesture(
            downX = rawX,
            downY = rawY,
            lastX = rawX,
            lastY = rawY,
            downTimeMs = eventTimeMs,
        )
        return GestureResult.None
    }

    fun onMove(rawX: Float, rawY: Float, eventTimeMs: Long): GestureResult {
        val activeGesture = gesture ?: return GestureResult.None
        if (!activeGesture.dragStarted) {
            if (distanceFromDown(activeGesture, rawX, rawY) < touchSlopPx) {
                return GestureResult.None
            }
            gesture = activeGesture.copy(
                lastX = rawX,
                lastY = rawY,
                dragStarted = true,
            )
            return GestureResult.Move(
                deltaX = rawX - activeGesture.downX,
                deltaY = rawY - activeGesture.downY,
            )
        }

        val result = GestureResult.Move(rawX - activeGesture.lastX, rawY - activeGesture.lastY)
        gesture = activeGesture.copy(
            lastX = rawX,
            lastY = rawY,
        )
        return result
    }

    fun onUp(rawX: Float, rawY: Float, eventTimeMs: Long): GestureResult {
        val activeGesture = gesture ?: return GestureResult.None
        gesture = null

        val distance = distanceFromDown(activeGesture, rawX, rawY)
        return if (
            !activeGesture.dragStarted &&
            distance < touchSlopPx &&
            isWithinTapTimeout(eventTimeMs, activeGesture.downTimeMs)
        ) {
            GestureResult.Tap
        } else {
            GestureResult.DragEnd
        }
    }

    fun onCancel(): GestureResult {
        if (gesture == null) return GestureResult.None
        gesture = null
        return GestureResult.DragEnd
    }

    private fun distanceFromDown(activeGesture: ActiveGesture, rawX: Float, rawY: Float): Float =
        hypot(
            (rawX - activeGesture.downX).toDouble(),
            (rawY - activeGesture.downY).toDouble(),
        ).toFloat()

    private fun isWithinTapTimeout(eventTimeMs: Long, downTimeMs: Long): Boolean {
        if (eventTimeMs < downTimeMs) return false

        val largestTapDurationMs = tapTimeoutMs - 1
        return if (downTimeMs > Long.MAX_VALUE - largestTapDurationMs) {
            true
        } else {
            eventTimeMs <= downTimeMs + largestTapDurationMs
        }
    }

    private data class ActiveGesture(
        val downX: Float,
        val downY: Float,
        val lastX: Float,
        val lastY: Float,
        val downTimeMs: Long,
        val dragStarted: Boolean = false,
    )

    companion object {
        private const val TOUCH_SLOP_DP = 10f
        private const val TAP_TIMEOUT_MS = 200L

        fun fromDensity(density: Float): GestureClassifier {
            require(density > 0f) { "density must be positive" }
            return GestureClassifier(
                touchSlopPx = TOUCH_SLOP_DP * density,
                tapTimeoutMs = TAP_TIMEOUT_MS,
            )
        }
    }
}
