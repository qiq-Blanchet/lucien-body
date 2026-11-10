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
    fun petWindowIsExactlyNinetyDpTouchableWithoutTakingWindowFocus() {
        val pet = OverlayWindowSpec.pet()

        assertEquals(90, pet.widthDp)
        assertEquals(90, pet.heightDp)
        assertTrue(pet.touchable)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            pet.flags,
        )
    }

    @Test
    fun bubbleWindowIsNeverTouchable() {
        val bubble = OverlayWindowSpec.bubble()

        assertEquals(180, bubble.widthDp)
        assertEquals(120, bubble.heightDp)
        assertFalse(bubble.touchable)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            bubble.flags,
        )
    }

    @Test
    fun emotionMetricsWindowMatchesTheApprovedPanelAndNeverAcceptsTouches() {
        val metrics = OverlayWindowSpec.metrics()

        assertEquals(88, metrics.widthDp)
        assertEquals(88, metrics.heightDp)
        assertFalse(metrics.touchable)
        assertEquals(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            metrics.flags,
        )
    }

    @Test
    fun menuWindowUsesGlobalOverlayCoordinatesAndAcceptsOnlyItsOwnTouches() {
        val menu = OverlayWindowSpec.menu()

        assertEquals(88, menu.widthDp)
        assertEquals(112, menu.heightDp)
        assertTrue(menu.touchable)
        assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, menu.type)
        assertEquals(Gravity.TOP or Gravity.START, menu.gravity)
        assertTrue(menu.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertTrue(menu.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL != 0)
        assertEquals(0, menu.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }

    @Test
    fun bothWindowsUseTheApprovedOverlayWindowConfiguration() {
        listOf(
            OverlayWindowSpec.pet(),
            OverlayWindowSpec.bubble(),
            OverlayWindowSpec.metrics(),
            OverlayWindowSpec.menu(),
        ).forEach { spec ->
            assertEquals(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, spec.type)
            assertEquals(PixelFormat.TRANSLUCENT, spec.pixelFormat)
            assertEquals(Gravity.TOP or Gravity.START, spec.gravity)
        }
    }

    @Test
    fun sizesConvertFromDpToExactPixelsForWindowLayout() {
        assertEquals(OverlaySizePx(width = 113, height = 113), OverlayWindowSpec.pet().sizePx(1.25f))
        assertEquals(OverlaySizePx(width = 225, height = 150), OverlayWindowSpec.bubble().sizePx(1.25f))
        assertEquals(OverlaySizePx(width = 110, height = 110), OverlayWindowSpec.metrics().sizePx(1.25f))
    }
}
