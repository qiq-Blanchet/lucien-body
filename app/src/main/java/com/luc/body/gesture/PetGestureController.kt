package com.luc.body.gesture

import android.view.MotionEvent
import android.view.View

class PetGestureController(
    private val classifier: GestureClassifier,
    private val onMove: (Float, Float) -> Unit,
    private val onTap: () -> Unit,
) : View.OnTouchListener {
    override fun onTouch(view: View, event: MotionEvent): Boolean {
        val result = when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> classifier.onDown(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_MOVE -> classifier.onMove(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_UP -> classifier.onUp(event.rawX, event.rawY, event.eventTime)
            MotionEvent.ACTION_CANCEL -> classifier.onCancel()
            else -> GestureResult.None
        }
        when (result) {
            is GestureResult.Move -> onMove(result.deltaX, result.deltaY)
            GestureResult.Tap -> onTap()
            GestureResult.None, GestureResult.DragEnd -> Unit
        }
        return true
    }
}
