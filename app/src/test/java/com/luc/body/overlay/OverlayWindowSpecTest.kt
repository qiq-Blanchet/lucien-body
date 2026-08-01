package com.luc.body.overlay

import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayWindowSpecTest {
    @Test
    fun petWindowIsExactlyOneHundredTwentyDpAndTouchable() {
        val pet = OverlayWindowSpec.pet()

        assertEquals(120, pet.widthDp)
        assertEquals(120, pet.heightDp)
        assertTrue(pet.touchable)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            pet.flags,
        )
    }

    @Test
    fun bubbleWindowIsNeverTouchable() {
        val bubble = OverlayWindowSpec.bubble()

        assertEquals(240, bubble.widthDp)
        assertEquals(160, bubble.heightDp)
        assertFalse(bubble.touchable)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            bubble.flags,
        )
    }

    @Test
    fun bothWindowsUseTheApprovedOverlayWindowConfiguration() {
        listOf(OverlayWindowSpec.pet(), OverlayWindowSpec.bubble()).forEach { spec ->
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, spec.type)
            assertEquals(PixelFormat.TRANSLUCENT, spec.pixelFormat)
            assertEquals(Gravity.TOP or Gravity.START, spec.gravity)
        }
    }

    @Test
    fun sizesConvertFromDpToExactPixelsForWindowLayout() {
        assertEquals(OverlaySizePx(width = 150, height = 150), OverlayWindowSpec.pet().sizePx(1.25f))
        assertEquals(OverlaySizePx(width = 300, height = 200), OverlayWindowSpec.bubble().sizePx(1.25f))
    }
}
