package com.luc.body.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager

data class OverlayWindowSpec(
    val widthDp: Int,
    val heightDp: Int,
    val touchable: Boolean,
    val flags: Int,
    val type: Int,
    val pixelFormat: Int,
    val gravity: Int,
) {
    companion object {
        fun pet(): OverlayWindowSpec = OverlayWindowSpec(
            widthDp = 120,
            heightDp = 120,
            touchable = true,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            pixelFormat = PixelFormat.TRANSLUCENT,
            gravity = Gravity.TOP or Gravity.START,
        )

        fun bubble(): OverlayWindowSpec = OverlayWindowSpec(
            widthDp = 240,
            heightDp = 160,
            touchable = false,
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            pixelFormat = PixelFormat.TRANSLUCENT,
            gravity = Gravity.TOP or Gravity.START,
        )
    }
}
