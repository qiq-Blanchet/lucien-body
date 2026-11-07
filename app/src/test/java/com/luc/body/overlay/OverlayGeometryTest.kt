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
        assertEquals(60 + 180, placement.bubbleY)
    }

    @Test
    fun initialPlacementUsesSixteenDpBottomRightMarginInsideNonZeroSafeBounds() {
        val placement = geometry.initialPlacement(SafeBoundsPx(50, 100, 1050, 2000))

        assertEquals(838, placement.petX)
        assertEquals(1_788, placement.petY)
        assertEquals(690, placement.bubbleX)
        assertEquals(1_548, placement.bubbleY)
        assertFalse(placement.bubbleBelowPet)
    }

    @Test
    fun bubbleXClampsIndependentlyWhilePetReachesTheLeftEdge() {
        val placement = geometry.movePet(0, 500, SafeBoundsPx(50, 40, 1050, 2000))

        assertEquals(50, placement.petX)
        assertEquals(50, placement.bubbleX)
        assertEquals(260, placement.bubbleY)
    }

    @Test
    fun movingPetProducesSynchronizedPetAndBubbleCoordinates() {
        val placement = geometry.movePet(200, 500, SafeBoundsPx(0, 40, 1080, 2200))

        assertEquals(200, placement.petX)
        assertEquals(500, placement.petY)
        assertEquals(110, placement.bubbleX)
        assertEquals(260, placement.bubbleY)
        assertFalse(placement.bubbleBelowPet)
    }

    @Test
    fun bubbleInBoundsTooSmallAboveAndBelowClampsToTheTopMostSafeCoordinate() {
        val placement = geometry.movePet(0, 0, SafeBoundsPx(100, 200, 200, 250))

        assertEquals(100, placement.petX)
        assertEquals(200, placement.petY)
        assertEquals(100, placement.bubbleX)
        assertEquals(200, placement.bubbleY)
        assertTrue(placement.bubbleBelowPet)
    }

    @Test
    fun `snap includes exact fifteen dp outer-edge gap on both screen sides`() {
        val bounds = SafeBoundsPx(50, 100, 1_050, 2_000)

        val left = geometry.snapPet(80, 500, bounds)
        val right = geometry.snapPet(840, 500, bounds)

        assertEquals(SnapEdge.LEFT, left.edge)
        assertEquals(-10, left.placement.petX)
        assertEquals(SnapEdge.RIGHT, right.edge)
        assertEquals(930, right.placement.petX)
        assertEquals(null, geometry.snapPet(500, 100, bounds).edge)
        assertEquals(null, geometry.snapPet(500, 1_820, bounds).edge)
    }

    @Test
    fun `snap excludes an outer-edge gap beyond fifteen dp and chooses nearest edge`() {
        val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)

        assertEquals(null, geometry.snapPet(31, 500, bounds).edge)
        val nearest = geometry.snapPet(20, 10, bounds)
        assertEquals(SnapEdge.LEFT, nearest.edge)
        assertEquals(-60, nearest.placement.petX)
        assertEquals(10, nearest.placement.petY)
    }

    @Test
    fun metricsPanelUsesTwentyDpGapFromTheVisibleBodyOnThePreferredLeftSide() {
        val geometry = OverlayGeometry(density = 1f)

        val metrics = geometry.metricsPlacement(500, 400, SafeBoundsPx(0, 0, 1_000, 1_000))

        assertEquals(MetricsPlacementPx(x = 402, y = 392), metrics)
    }

    @Test
    fun metricsPanelMovesToTheRightWithoutMovingThePetWhenLeftSpaceIsInsufficient() {
        val geometry = OverlayGeometry(density = 1f)
        val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)
        val pet = geometry.movePet(0, 400, bounds)

        val metrics = geometry.metricsPlacement(pet.petX, pet.petY, bounds)

        assertEquals(0, pet.petX)
        assertEquals(MetricsPlacementPx(x = 76, y = 392), metrics)
    }

    @Test
    fun metricsPanelClampsVerticallyWithoutChangingPetOrBubbleCoordinates() {
        val geometry = OverlayGeometry(density = 1f)
        val bounds = SafeBoundsPx(0, 100, 1_000, 400)
        val pet = geometry.movePet(500, 100, bounds)

        assertEquals(MetricsPlacementPx(x = 402, y = 100), geometry.metricsPlacement(pet.petX, pet.petY, bounds))
        assertEquals(500, pet.petX)
        assertEquals(100, pet.petY)
        assertEquals(455, pet.bubbleX)
        assertEquals(190, pet.bubbleY)
    }
}
