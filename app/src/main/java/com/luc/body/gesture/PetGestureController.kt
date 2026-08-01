package com.luc.body.gesture

import android.view.MotionEvent
import android.view.View

class PetGestureController(
    private val classifier: GestureClassifier,
    private val onMove: (Float, Float) -> Unit,
    private val onTap: () -> Unit,
) : View.OnTouchListener {
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        onPointerEvent(event.actionMasked, event.rawX, event.rawY, event.eventTime)
        return true
    }

    internal fun onPointerEvent(
        actionMasked: Int,
        rawX: Float,
        rawY: Float,
        eventTimeMs: Long,
    ): GestureResult {
        val result = when (actionMasked) {
            MotionEvent.ACTION_DOWN -> classifier.onDown(rawX, rawY, eventTimeMs)
            MotionEvent.ACTION_MOVE -> classifier.onMove(rawX, rawY, eventTimeMs)
            MotionEvent.ACTION_UP -> classifier.onUp(rawX, rawY, eventTimeMs)
            MotionEvent.ACTION_CANCEL -> classifier.onCancel()
            else -> GestureResult.None
        }
        when (result) {
            is GestureResult.Move -> onMove(result.deltaX, result.deltaY)
            GestureResult.Tap -> onTap()
            GestureResult.None, GestureResult.DragEnd -> Unit
        }
        return result
    }
}
