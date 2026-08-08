package com.luc.body.gesture

import com.luc.body.state.Expression
import kotlin.math.abs
import kotlin.math.hypot

enum class FlingDirection(val expression: Expression) {
    UP(Expression.DIZZY),
    DOWN(Expression.DIZZY),
    LEFT(Expression.DIZZY),
    RIGHT(Expression.DIZZY),
}

data class FlingGesture(
    val direction: FlingDirection,
    val velocityX: Float,
    val velocityY: Float,
)

sealed interface GestureResult {
    data object None : GestureResult

    data class DragStart(
        val deltaX: Float,
        val deltaY: Float,
        val fromStuck: Boolean,
    ) : GestureResult

    data class Move(
        val deltaX: Float,
        val deltaY: Float,
    ) : GestureResult

    data class Tap(val fromStuck: Boolean) : GestureResult

    data object DoubleTap : GestureResult

    data object LongPress : GestureResult

    data class DragEnd(
        val fling: FlingGesture?,
        val cancelled: Boolean = false,
    ) : GestureResult
}

class GestureClassifier(
    private val touchSlopPx: Float,
    private val doubleTapWindowMs: Long,
    private val longPressMs: Long,
    private val flingVelocityThresholdPxPerSecond: Float,
) {
    private var active: ActiveGesture? = null
    private var pendingTap: PendingTap? = null

    init {
        require(touchSlopPx > 0f) { "touchSlopPx must be positive" }
        require(doubleTapWindowMs > 0) { "doubleTapWindowMs must be positive" }
        require(longPressMs > 0) { "longPressMs must be positive" }
        require(flingVelocityThresholdPxPerSecond > 0f) {
            "flingVelocityThresholdPxPerSecond must be positive"
        }
    }

    fun onDown(
        rawX: Float,
        rawY: Float,
        eventTimeMs: Long,
        pointerId: Int = 0,
        fromStuck: Boolean = false,
    ): GestureResult {
        if (active != null) return GestureResult.None
        val previousTap = pendingTap
        val isSecondTap = previousTap != null &&
            eventTimeMs >= previousTap.upTimeMs &&
            eventTimeMs <= safeAdd(previousTap.upTimeMs, doubleTapWindowMs)
        if (isSecondTap) pendingTap = null
        active = ActiveGesture(
            pointerId = pointerId,
            downX = rawX,
            downY = rawY,
            lastX = rawX,
            lastY = rawY,
            downTimeMs = eventTimeMs,
            fromStuck = fromStuck,
            secondTap = isSecondTap,
        )
        return GestureResult.None
    }

    fun onMove(
        rawX: Float,
        rawY: Float,
        eventTimeMs: Long,
        pointerId: Int = 0,
    ): GestureResult {
        val current = active?.takeIf { it.pointerId == pointerId } ?: return GestureResult.None
        if (current.longPressed) return GestureResult.None
        val sampled = sampleVelocity(current, rawX, rawY, eventTimeMs)
        if (!current.dragStarted && distanceFromDown(current, rawX, rawY) < touchSlopPx) {
            active = sampled
            return GestureResult.None
        }
        if (!current.dragStarted) {
            pendingTap = null
            active = sampled.copy(dragStarted = true)
            return GestureResult.DragStart(
                deltaX = rawX - current.downX,
                deltaY = rawY - current.downY,
                fromStuck = current.fromStuck,
            )
        }
        active = sampled
        return GestureResult.Move(rawX - current.lastX, rawY - current.lastY)
    }

    fun onUp(
        rawX: Float,
        rawY: Float,
        eventTimeMs: Long,
        pointerId: Int = 0,
    ): GestureResult {
        val current = active?.takeIf { it.pointerId == pointerId } ?: return GestureResult.None
        active = null
        if (eventTimeMs < current.downTimeMs) return GestureResult.None
        val sampled = sampleVelocity(current, rawX, rawY, eventTimeMs)
        if (current.dragStarted) {
            val direction = classifyFling(sampled.velocityX, sampled.velocityY)
            return GestureResult.DragEnd(
                fling = direction?.let {
                    FlingGesture(it, sampled.velocityX, sampled.velocityY)
                },
            )
        }
        if (current.longPressed || distanceFromDown(current, rawX, rawY) >= touchSlopPx) {
            pendingTap = null
            return GestureResult.None
        }
        if (current.secondTap) return GestureResult.DoubleTap
        pendingTap = PendingTap(eventTimeMs, current.fromStuck)
        return GestureResult.None
    }

    fun onCancel(pointerId: Int? = null): GestureResult {
        val current = active ?: return GestureResult.None
        if (pointerId != null && current.pointerId != pointerId) return GestureResult.None
        active = null
        if (current.secondTap) pendingTap = null
        return if (current.dragStarted) {
            GestureResult.DragEnd(fling = null, cancelled = true)
        } else {
            GestureResult.None
        }
    }

    fun onTimeout(eventTimeMs: Long): GestureResult {
        val current = active
        if (
            current != null &&
            !current.dragStarted &&
            !current.longPressed &&
            eventTimeMs >= safeAdd(current.downTimeMs, longPressMs)
        ) {
            pendingTap = null
            active = current.copy(longPressed = true)
            return GestureResult.LongPress
        }
        val tap = pendingTap
        if (tap != null && eventTimeMs > safeAdd(tap.upTimeMs, doubleTapWindowMs)) {
            pendingTap = null
            return GestureResult.Tap(tap.fromStuck)
        }
        return GestureResult.None
    }

    fun nextDeadlineMs(): Long? {
        val deadlines = buildList {
            active?.takeIf { !it.dragStarted && !it.longPressed }
                ?.let { add(safeAdd(it.downTimeMs, longPressMs)) }
            pendingTap?.let { add(safeAdd(safeAdd(it.upTimeMs, doubleTapWindowMs), 1L)) }
        }
        return deadlines.minOrNull()
    }

    fun classifyFling(velocityX: Float, velocityY: Float): FlingDirection? {
        if (maxOf(abs(velocityX), abs(velocityY)) < flingVelocityThresholdPxPerSecond) return null
        return if (abs(velocityY) >= abs(velocityX)) {
            if (velocityY < 0f) FlingDirection.UP else FlingDirection.DOWN
        } else {
            if (velocityX < 0f) FlingDirection.LEFT else FlingDirection.RIGHT
        }
    }

    private fun sampleVelocity(
        gesture: ActiveGesture,
        rawX: Float,
        rawY: Float,
        eventTimeMs: Long,
    ): ActiveGesture {
        val elapsedMs = eventTimeMs - gesture.downTimeMs
        return gesture.copy(
            lastX = rawX,
            lastY = rawY,
            velocityX = if (elapsedMs > 0) (rawX - gesture.downX) * 1_000f / elapsedMs else 0f,
            velocityY = if (elapsedMs > 0) (rawY - gesture.downY) * 1_000f / elapsedMs else 0f,
        )
    }

    private fun distanceFromDown(gesture: ActiveGesture, rawX: Float, rawY: Float): Float =
        hypot((rawX - gesture.downX).toDouble(), (rawY - gesture.downY).toDouble()).toFloat()

    private fun safeAdd(value: Long, delta: Long): Long =
        if (value > Long.MAX_VALUE - delta) Long.MAX_VALUE else value + delta

    private data class ActiveGesture(
        val pointerId: Int,
        val downX: Float,
        val downY: Float,
        val lastX: Float,
        val lastY: Float,
        val downTimeMs: Long,
        val fromStuck: Boolean,
        val secondTap: Boolean,
        val dragStarted: Boolean = false,
        val longPressed: Boolean = false,
        val velocityX: Float = 0f,
        val velocityY: Float = 0f,
    )

    private data class PendingTap(
        val upTimeMs: Long,
        val fromStuck: Boolean,
    )

    companion object {
        private const val TOUCH_SLOP_DP = 10f
        private const val DOUBLE_TAP_WINDOW_MS = 300L
        private const val LONG_PRESS_MS = 500L
        private const val DEFAULT_MIN_FLING_VELOCITY_DP_PER_SECOND = 50f

        fun fromDensity(
            density: Float,
            flingVelocityThresholdPxPerSecond: Float = DEFAULT_MIN_FLING_VELOCITY_DP_PER_SECOND * density,
        ): GestureClassifier {
            require(density > 0f) { "density must be positive" }
            return GestureClassifier(
                touchSlopPx = TOUCH_SLOP_DP * density,
                doubleTapWindowMs = DOUBLE_TAP_WINDOW_MS,
                longPressMs = LONG_PRESS_MS,
                flingVelocityThresholdPxPerSecond = flingVelocityThresholdPxPerSecond,
            )
        }
    }
}
