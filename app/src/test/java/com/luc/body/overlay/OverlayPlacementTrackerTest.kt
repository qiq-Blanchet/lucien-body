package com.luc.body.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayPlacementTrackerTest {
    private val bounds = SafeBoundsPx(0, 0, 1_000, 1_000)

    @Test
    fun consecutiveMovesAccumulateFromTheLatestAuthoritativePetPosition() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }

        tracker.initialPlacement()
        assertEquals(OverlayPlacementPx(884, 900, 820, 780, false), tracker.moveBy(-10f, 6f))
        assertEquals(OverlayPlacementPx(888, 892, 820, 772, false), tracker.moveBy(4f, -8f))
    }

    @Test
    fun fractionalDeltasAccumulateBeforeTheyAreProjectedToPixels() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }

        tracker.initialPlacement()
        assertEquals(894, tracker.moveBy(0.4f, 0f).petX)
        assertEquals(895, tracker.moveBy(0.4f, 0f).petX)
    }

    @Test
    fun moveReclampsTheAuthoritativePositionAgainstCurrentBounds() {
        var currentBounds = bounds
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { currentBounds }

        tracker.initialPlacement()
        currentBounds = SafeBoundsPx(100, 100, 500, 500)

        assertEquals(OverlayPlacementPx(410, 410, 320, 290, false), tracker.moveBy(0f, 0f))
    }

    @Test
    fun reclampRepositionsWithoutRequiringAnotherTouchEvent() {
        var currentBounds = bounds
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { currentBounds }

        tracker.initialPlacement()
        currentBounds = SafeBoundsPx(100, 100, 500, 500)

        assertEquals(OverlayPlacementPx(410, 410, 320, 290, false), tracker.reclampToCurrentBounds())
    }

    @Test
    fun `drag end snaps and reports stuck only within edge threshold`() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }
        tracker.initialPlacement()
        tracker.moveBy(-879f, -400f)

        val snapped = tracker.finishDrag()

        assertEquals(SnapEdge.LEFT, snapped.edge)
        assertEquals(-30, snapped.placement.petX)
        assertEquals(true, tracker.isStuck)
    }

    @Test
    fun `drag end away from edges remains free`() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }
        tracker.initialPlacement()
        tracker.moveBy(-400f, -400f)

        val result = tracker.finishDrag()

        assertEquals(null, result.edge)
        assertEquals(false, tracker.isStuck)
    }

    @Test
    fun `fling release already within threshold snaps immediately`() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }
        tracker.initialPlacement()
        tracker.moveBy(-879f, -400f)

        val result = tracker.finishDrag(allowSnap = true)

        assertEquals(SnapEdge.LEFT, result.edge)
        assertEquals(-30, result.placement.petX)
        assertEquals(true, tracker.isStuck)
    }

    @Test
    fun `fling final position is snapped after a non-edge release`() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }
        tracker.initialPlacement(200 to 500)
        val release = tracker.finishDrag(allowSnap = true)
        tracker.moveBy(-300f, 0f)

        val settled = tracker.finishDrag(allowSnap = true)

        assertEquals(null, release.edge)
        assertEquals(SnapEdge.LEFT, settled.edge)
        assertEquals(-30, settled.placement.petX)
        assertEquals(true, tracker.isStuck)
    }

    @Test
    fun `dragging a snapped pet inward does not jump across its transparent margin`() {
        val tracker = OverlayPlacementTracker(OverlayGeometry(density = 1f)) { bounds }
        tracker.initialPlacement()
        tracker.moveBy(-879f, -400f)
        tracker.finishDrag()

        assertEquals(-20, tracker.moveBy(10f, 0f).petX)
    }
}
