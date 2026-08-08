package com.luc.body.gesture

import android.os.SystemClock
import android.view.MotionEvent
import android.view.View

class PetGestureController(
    private val classifier: GestureClassifier,
    private val isStuck: () -> Boolean,
    private val onDragStart: (fromStuck: Boolean) -> Unit,
    private val onMove: (Float, Float) -> Unit,
    private val onDragEnd: (cancelled: Boolean, fling: FlingGesture?) -> Unit,
    private val onTap: (fromStuck: Boolean) -> Unit,
    private val onDoubleTap: () -> Unit,
    private val onLongPress: () -> Unit,
    private val onFling: (FlingGesture) -> Unit,
) : View.OnTouchListener {
    constructor(
        classifier: GestureClassifier,
        onMove: (Float, Float) -> Unit,
        onTap: () -> Unit,
    ) : this(
        classifier = classifier,
        isStuck = { false },
        onDragStart = {},
        onMove = onMove,
        onDragEnd = { _, _ -> },
        onTap = { onTap() },
        onDoubleTap = onTap,
        onLongPress = {},
        onFling = {},
    )

    private var activePointerId = INVALID_POINTER_ID
    private var timeoutRunnable: Runnable? = null

    override fun onTouch(view: View, event: MotionEvent): Boolean {
        dispatch(classifier.onTimeout(event.eventTime))
        val result = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                activePointerId = event.getPointerId(event.actionIndex)
                classifier.onDown(
                    rawX = event.rawXAt(event.actionIndex),
                    rawY = event.rawYAt(event.actionIndex),
                    eventTimeMs = event.eventTime,
                    pointerId = activePointerId,
                    fromStuck = isStuck(),
                )
            }
            MotionEvent.ACTION_POINTER_DOWN -> GestureResult.None
            MotionEvent.ACTION_MOVE -> {
                val index = event.findPointerIndex(activePointerId)
                if (index < 0) GestureResult.None else classifier.onMove(
                    rawX = event.rawXAt(index),
                    rawY = event.rawYAt(index),
                    eventTimeMs = event.eventTime,
                    pointerId = activePointerId,
                )
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                if (pointerId != activePointerId) {
                    GestureResult.None
                } else {
                    activePointerId = INVALID_POINTER_ID
                    classifier.onCancel(pointerId)
                }
            }
            MotionEvent.ACTION_UP -> {
                val pointerId = event.getPointerId(event.actionIndex)
                activePointerId = INVALID_POINTER_ID
                classifier.onUp(
                    rawX = event.rawXAt(event.actionIndex),
                    rawY = event.rawYAt(event.actionIndex),
                    eventTimeMs = event.eventTime,
                    pointerId = pointerId,
                )
            }
            MotionEvent.ACTION_CANCEL -> {
                val pointerId = activePointerId.takeIf { it != INVALID_POINTER_ID }
                activePointerId = INVALID_POINTER_ID
                classifier.onCancel(pointerId)
            }
            else -> GestureResult.None
        }
        dispatch(result)
        scheduleNextTimeout(view)
        return true
    }

    internal fun onPointerEvent(
        actionMasked: Int,
        rawX: Float,
        rawY: Float,
        eventTimeMs: Long,
        pointerId: Int = 0,
    ): GestureResult {
        dispatch(classifier.onTimeout(eventTimeMs))
        val result = when (actionMasked) {
            MotionEvent.ACTION_DOWN -> classifier.onDown(
                rawX,
                rawY,
                eventTimeMs,
                pointerId,
                fromStuck = isStuck(),
            )
            MotionEvent.ACTION_MOVE -> classifier.onMove(rawX, rawY, eventTimeMs, pointerId)
            MotionEvent.ACTION_UP -> classifier.onUp(rawX, rawY, eventTimeMs, pointerId)
            MotionEvent.ACTION_CANCEL -> classifier.onCancel(pointerId)
            else -> GestureResult.None
        }
        dispatch(result)
        return result
    }

    internal fun onTimeout(eventTimeMs: Long): GestureResult =
        classifier.onTimeout(eventTimeMs).also(::dispatch)

    private fun dispatch(result: GestureResult) {
        when (result) {
            is GestureResult.DragStart -> {
                onDragStart(result.fromStuck)
                onMove(result.deltaX, result.deltaY)
            }
            is GestureResult.Move -> onMove(result.deltaX, result.deltaY)
            is GestureResult.Tap -> onTap(result.fromStuck)
            GestureResult.DoubleTap -> onDoubleTap()
            GestureResult.LongPress -> onLongPress()
            is GestureResult.DragEnd -> {
                onDragEnd(result.cancelled, result.fling)
                result.fling?.let(onFling)
            }
            GestureResult.None -> Unit
        }
    }

    private fun scheduleNextTimeout(view: View) {
        timeoutRunnable?.let(view::removeCallbacks)
        val deadline = classifier.nextDeadlineMs() ?: run {
            timeoutRunnable = null
            return
        }
        val runnable = Runnable {
            dispatch(classifier.onTimeout(SystemClock.uptimeMillis()))
            scheduleNextTimeout(view)
        }
        timeoutRunnable = runnable
        view.postDelayed(runnable, (deadline - SystemClock.uptimeMillis()).coerceAtLeast(0L))
    }

    private fun MotionEvent.rawXAt(index: Int): Float =
        getX(index) + rawX - x

    private fun MotionEvent.rawYAt(index: Int): Float =
        getY(index) + rawY - y

    private companion object {
        const val INVALID_POINTER_ID = -1
    }
}
