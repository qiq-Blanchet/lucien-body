package com.luc.body.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import kotlin.math.roundToInt

data class OverlaySizePx(
    val width: Int,
    val height: Int,
)

data class OverlayWindowSpec(
    val widthDp: Int,
    val heightDp: Int,
    val touchable: Boolean,
    val flags: Int,
    val type: Int,
    val pixelFormat: Int,
    val gravity: Int,
) {
    fun sizePx(density: Float): OverlaySizePx {
        require(density > 0f) { "density must be positive" }
        return OverlaySizePx(
            width = (widthDp * density).roundToInt(),
            height = (heightDp * density).roundToInt(),
        )
    }

    companion object {
        fun pet(sizeDp: Int = 90): OverlayWindowSpec = OverlayWindowSpec(
            widthDp = sizeDp,
            heightDp = sizeDp,
            touchable = true,
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            pixelFormat = PixelFormat.TRANSLUCENT,
            gravity = Gravity.TOP or Gravity.START,
        )

        fun bubble(): OverlayWindowSpec = OverlayWindowSpec(
            widthDp = 180,
            heightDp = 120,
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
