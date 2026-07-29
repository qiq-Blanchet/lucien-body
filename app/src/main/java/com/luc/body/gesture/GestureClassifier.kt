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
        val movedBeyondSlop = activeGesture.movedBeyondSlop ||
            distanceFromDown(activeGesture, rawX, rawY) >= touchSlopPx
        val result = GestureResult.Move(
            deltaX = rawX - activeGesture.lastX,
            deltaY = rawY - activeGesture.lastY,
        )
        gesture = activeGesture.copy(
            lastX = rawX,
            lastY = rawY,
            movedBeyondSlop = movedBeyondSlop,
        )
        return result
    }

    fun onUp(rawX: Float, rawY: Float, eventTimeMs: Long): GestureResult {
        val activeGesture = gesture ?: return GestureResult.None
        gesture = null

        val distance = distanceFromDown(activeGesture, rawX, rawY)
        val durationMs = eventTimeMs - activeGesture.downTimeMs
        return if (
            !activeGesture.movedBeyondSlop &&
            distance < touchSlopPx &&
            durationMs < tapTimeoutMs
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

    private data class ActiveGesture(
        val downX: Float,
        val downY: Float,
        val lastX: Float,
        val lastY: Float,
        val downTimeMs: Long,
        val movedBeyondSlop: Boolean = false,
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
