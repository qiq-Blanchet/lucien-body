package com.luc.body.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayGeometryTest {
    private val geometry = OverlayGeometry(density = 2f)

    @Test
    fun bubbleMovesBelowPetWhenTopSpaceIsInsufficient() {
        val bounds = SafeBoundsPx(0, 40, 1080, 2200)

        val placement = geometry.movePet(500, 60, bounds)

        assertTrue(placement.bubbleBelowPet)
        assertEquals(60 + 240, placement.bubbleY)
    }

    @Test
    fun initialPlacementUsesSixteenDpBottomRightMarginInsideNonZeroSafeBounds() {
        val placement = geometry.initialPlacement(SafeBoundsPx(50, 100, 1050, 2000))

        assertEquals(778, placement.petX)
        assertEquals(1_728, placement.petY)
        assertEquals(658, placement.bubbleX)
        assertEquals(1_408, placement.bubbleY)
        assertFalse(placement.bubbleBelowPet)
    }

    @Test
    fun bubbleXClampsIndependentlyWhilePetReachesTheLeftEdge() {
        val placement = geometry.movePet(0, 500, SafeBoundsPx(50, 40, 1050, 2000))

        assertEquals(50, placement.petX)
        assertEquals(50, placement.bubbleX)
        assertEquals(180, placement.bubbleY)
    }

    @Test
    fun movingPetProducesSynchronizedPetAndBubbleCoordinates() {
        val placement = geometry.movePet(200, 500, SafeBoundsPx(0, 40, 1080, 2200))

        assertEquals(200, placement.petX)
        assertEquals(500, placement.petY)
        assertEquals(80, placement.bubbleX)
        assertEquals(180, placement.bubbleY)
        assertFalse(placement.bubbleBelowPet)
    }

    @Test
    fun tinySafeBoundsNeverCreateANegativeClampRange() {
        val placement = geometry.movePet(0, 0, SafeBoundsPx(100, 200, 200, 250))

        assertEquals(100, placement.petX)
        assertEquals(200, placement.petY)
        assertEquals(100, placement.bubbleX)
        assertEquals(200, placement.bubbleY)
    }
}
