package com.luc.body.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class LegacySafeBoundsTest {
    @Test
    fun mandatoryGestureInsetWinsWhenItIsLargerThanTheNavigationBarInset() {
        val insets = EdgeInsetsPx(left = 0, top = 72, right = 0, bottom = 24)
            .maxPerEdge(EdgeInsetsPx(left = 0, top = 0, right = 0, bottom = 96))

        assertEquals(EdgeInsetsPx(left = 0, top = 72, right = 0, bottom = 96), insets)
    }

    @Test
    fun movementBoundsReachTopAndSidesWhileKeepingTheLargestBottomInset() {
        val bounds = LegacySafeBounds.fromRealDisplay(
            width = 1_080,
            height = 2_400,
            systemInsets = EdgeInsetsPx(left = 0, top = 72, right = 0, bottom = 126),
            cutoutInsets = EdgeInsetsPx(left = 0, top = 100, right = 0, bottom = 0),
        )

        assertEquals(SafeBoundsPx(0, 0, 1_080, 2_274), bounds)
    }

    @Test
    fun modernDisplayBoundsUseTheSameExpandedMovementPolicy() {
        val bounds = OverlayMovementBounds.fromDisplay(
            displayBounds = SafeBoundsPx(10, 20, 1_090, 2_420),
            insets = EdgeInsetsPx(left = 60, top = 100, right = 70, bottom = 126),
        )

        assertEquals(SafeBoundsPx(10, 20, 1_090, 2_294), bounds)
    }
}
