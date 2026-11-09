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
    fun topMovementCanOverhangByTheSvgCanvasPadding() {
        val geometry = OverlayGeometry(density = 1f)
        val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)

        val placement = geometry.movePet(500, -100, bounds)

        assertEquals(-60, geometry.minimumPetY(bounds))
        assertEquals(-60, placement.petY)
        assertEquals(30, placement.bubbleY)
        assertTrue(placement.bubbleBelowPet)
    }

    @Test
    fun bubbleInBoundsTooSmallAboveAndBelowClampsToTheTopMostSafeCoordinate() {
        val placement = geometry.movePet(0, 0, SafeBoundsPx(100, 200, 200, 250))

        assertEquals(100, placement.petX)
        assertEquals(80, placement.petY)
        assertEquals(100, placement.bubbleX)
        assertEquals(200, placement.bubbleY)
        assertTrue(placement.bubbleBelowPet)
    }

    @Test
    fun `snap includes exact four dp outer-edge gap on both screen sides`() {
        val bounds = SafeBoundsPx(50, 100, 1_050, 2_000)

        val left = geometry.snapPet(58, 500, bounds)
        val right = geometry.snapPet(862, 500, bounds)

        assertEquals(SnapEdge.LEFT, left.edge)
        assertEquals(-10, left.placement.petX)
        assertEquals(SnapEdge.RIGHT, right.edge)
        assertEquals(930, right.placement.petX)
        assertEquals(null, geometry.snapPet(500, 100, bounds).edge)
        assertEquals(null, geometry.snapPet(500, 1_820, bounds).edge)
    }

    @Test
    fun `snap excludes an outer-edge gap beyond four dp and chooses nearest edge`() {
        val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)

        assertEquals(null, geometry.snapPet(9, 500, bounds).edge)
        val nearest = geometry.snapPet(8, 10, bounds)
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

    @Test
    fun miniMenuSitsBelowTheVisibleCrabWhenThereIsRoom() {
        val bounds = SafeBoundsPx(0, 40, 1080, 2200)
        val menu = geometry.miniMenuPlacement(500, 600, 224, 376, bounds)

        assertFalse(menu.abovePet)
        assertEquals(478, menu.x)
        assertEquals(744, menu.y)
    }

    @Test
    fun miniMenuFlipsAboveTheVisibleHeadNearBottom() {
        val bounds = SafeBoundsPx(0, 40, 1080, 2200)
        val menu = geometry.miniMenuPlacement(500, 2000, 224, 376, bounds)

        assertTrue(menu.abovePet)
        assertEquals(478, menu.x)
        assertEquals(1698, menu.y)
        // Visible head begins at about petY + 53dp; resulting visual gap is 16dp.
        assertEquals(32, 2000 + 106 - (menu.y + 376))
    }

    @Test
    fun miniMenuClampsInsideSafeSidesWhenPetIsSnapped() {
        val bounds = SafeBoundsPx(50, 100, 1050, 2000)

        val left = geometry.miniMenuPlacement(-10, 500, 224, 376, bounds)
        val right = geometry.miniMenuPlacement(1030, 500, 224, 376, bounds)

        assertEquals(50, left.x)
        assertEquals(826, right.x)
    }

}
