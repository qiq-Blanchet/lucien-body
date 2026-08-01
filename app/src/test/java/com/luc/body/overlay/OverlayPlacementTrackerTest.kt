package com.luc.body.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPlacementTrackerTest {
    private val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)

    @Test
    fun consecutiveMovesAccumulateFromTheLatestAuthoritativePetPosition() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }

        tracker.initialPlacement()
        assertEquals(OverlayPlacementPx(854, 870, 760, 710, false), tracker.moveBy(-10f, 6f))
        assertEquals(OverlayPlacementPx(858, 862, 760, 702, false), tracker.moveBy(4f, -8f))
    }

    @Test
    fun fractionalDeltasAccumulateBeforeTheyAreProjectedToPixels() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }

        tracker.initialPlacement()
        assertEquals(864, tracker.moveBy(0.4f, 0f).petX)
        assertEquals(865, tracker.moveBy(0.4f, 0f).petX)
    }

    @Test
    fun moveReclampsTheAuthoritativePositionAgainstCurrentBounds() {
        var currentBounds = bounds
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { currentBounds }

        tracker.initialPlacement()
        currentBounds = SafeBoundsPx(100, 100, 500, 500)

        assertEquals(OverlayPlacementPx(380, 380, 260, 220, false), tracker.moveBy(0f, 0f))
    }

    @Test
    fun reclampRepositionsWithoutRequiringAnotherTouchEvent() {
        var currentBounds = bounds
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { currentBounds }

        tracker.initialPlacement()
        currentBounds = SafeBoundsPx(100, 100, 500, 500)

        assertEquals(OverlayPlacementPx(380, 380, 260, 220, false), tracker.reclampToCurrentBounds())
    }
}
