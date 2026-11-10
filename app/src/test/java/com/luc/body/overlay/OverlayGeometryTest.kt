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
    fun initialPlacementUsesTheHorizontalCenterAndUpperQuarterOfNonZeroSafeBounds() {
        val placement = geometry.initialPlacement(SafeBoundsPx(50, 100, 1050, 2000))

        assertEquals(460, placement.petX)
        assertEquals(530, placement.petY)
        assertEquals(370, placement.bubbleX)
        assertEquals(290, placement.bubbleY)
        assertFalse(placement.bubbleBelowPet)
    }

    @Test
    fun `mini menu sits under the pet feet and remains centered in open space`() {
        val geometry = OverlayGeometry(density = 1f)

        val menu = geometry.miniMenuPlacement(500, 400, SafeBoundsPx(0, 0, 1_000, 1_000), null)

        assertEquals(88, geometry.miniMenuWidthPx)
        assertEquals(112, geometry.miniMenuHeightPx)
        assertEquals(MiniMenuPlacementPx(x = 501, y = 486, belowPet = true), menu)
    }

    @Test
    fun `mini menu moves above a pet near the bottom`() {
        val geometry = OverlayGeometry(density = 1f)

        val menu = geometry.miniMenuPlacement(500, 910, SafeBoundsPx(0, 0, 1_000, 1_000), null)

        assertEquals(MiniMenuPlacementPx(x = 501, y = 835, belowPet = false), menu)
    }

    @Test
    fun `mini menu hugs the matching safe side for snapped pets`() {
        val geometry = OverlayGeometry(density = 1f)
        val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)

        assertEquals(0, geometry.miniMenuPlacement(-30, 400, bounds, SnapEdge.LEFT).x)
        assertEquals(912, geometry.miniMenuPlacement(940, 400, bounds, SnapEdge.RIGHT).x)
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

        assertEquals(MetricsPlacementPx(x = 424, y = 401), metrics)
    }

    @Test
    fun metricsPanelMovesToTheRightWithoutMovingThePetWhenLeftSpaceIsInsufficient() {
        val geometry = OverlayGeometry(density = 1f)
        val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)
        val pet = geometry.movePet(0, 400, bounds)

        val metrics = geometry.metricsPlacement(pet.petX, pet.petY, bounds)

        assertEquals(0, pet.petX)
        assertEquals(MetricsPlacementPx(x = 78, y = 401), metrics)
    }

    @Test
    fun metricsPanelClampsVerticallyWithoutChangingPetOrBubbleCoordinates() {
        val geometry = OverlayGeometry(density = 1f)
        val bounds = SafeBoundsPx(0, 100, 1_000, 400)
        val pet = geometry.movePet(500, 100, bounds)

        assertEquals(MetricsPlacementPx(x = 424, y = 101), geometry.metricsPlacement(pet.petX, pet.petY, bounds))
        assertEquals(500, pet.petX)
        assertEquals(100, pet.petY)
        assertEquals(455, pet.bubbleX)
        assertEquals(190, pet.bubbleY)
    }

    @Test
    fun `visible metrics top clamp moves the bubble by the same offset and restores at default position`() {
        val geometry = OverlayGeometry(density = 1f)
        val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)

        val topPet = geometry.movePet(500, -60, bounds)
        assertEquals(30, topPet.bubbleY)
        assertEquals(89, geometry.bubbleYFollowingMetricsTopClamp(topPet, bounds))

        val restoredPet = geometry.movePet(500, 0, bounds)
        assertEquals(90, restoredPet.bubbleY)
        assertEquals(90, geometry.bubbleYFollowingMetricsTopClamp(restoredPet, bounds))
    }
}
