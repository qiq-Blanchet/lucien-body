package com.luc.body.overlay

import kotlin.math.abs
import kotlin.math.roundToInt

data class SafeBoundsPx(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
)

data class OverlayPlacementPx(
    val petX: Int,
    val petY: Int,
    val bubbleX: Int,
    val bubbleY: Int,
    /**
     * The preferred direction selected before independent bubble Y clamping.
     * It does not guarantee that an undersized safe bound can avoid overlap.
     */
    val bubbleBelowPet: Boolean,
)

enum class SnapEdge { LEFT, TOP, RIGHT, BOTTOM }

data class SnapResult(
    val placement: OverlayPlacementPx,
    val edge: SnapEdge?,
)

class OverlayGeometry(
    density: Float,
    petSizeDp: Int = DEFAULT_PET_SIZE_DP,
) {
    val petSizePx = dpToPx(petSizeDp, density)
    private val bubbleWidthPx = dpToPx(BUBBLE_WIDTH_DP, density)
    private val bubbleHeightPx = dpToPx(BUBBLE_HEIGHT_DP, density)
    private val edgeMarginPx = dpToPx(EDGE_MARGIN_DP, density)
    private val snapThresholdPx = SNAP_THRESHOLD_DP * density
    // The source sprites use a 45-unit viewBox with roughly 15 units of side padding.
    private val horizontalSnapOverhangPx = petSizePx / 3

    init {
        require(density > 0f) { "density must be positive" }
    }

    fun initialPlacement(bounds: SafeBoundsPx): OverlayPlacementPx = movePet(
        petX = bounds.right - petSizePx - edgeMarginPx,
        petY = bounds.bottom - petSizePx - edgeMarginPx,
        bounds = bounds,
    )

    fun movePet(petX: Int, petY: Int, bounds: SafeBoundsPx): OverlayPlacementPx {
        val clampedPetX = petX.coerceIn(bounds.left, maxCoordinate(bounds.left, bounds.right, petSizePx))
        val clampedPetY = petY.coerceIn(bounds.top, maxCoordinate(bounds.top, bounds.bottom, petSizePx))

        return placePet(clampedPetX, clampedPetY, bounds)
    }

    fun moveSnappedPet(petX: Int, petY: Int, bounds: SafeBoundsPx): OverlayPlacementPx {
        val minimumPetX = bounds.left - horizontalSnapOverhangPx
        val maximumPetX =
            maxCoordinate(bounds.left, bounds.right, petSizePx) + horizontalSnapOverhangPx
        return placePet(
            petX = petX.coerceIn(minimumPetX, maximumPetX),
            petY = petY.coerceIn(bounds.top, maxCoordinate(bounds.top, bounds.bottom, petSizePx)),
            bounds = bounds,
        )
    }

    private fun placePet(petX: Int, petY: Int, bounds: SafeBoundsPx): OverlayPlacementPx {
        val desiredBubbleX = petX + (petSizePx - bubbleWidthPx) / 2
        val bubbleX = desiredBubbleX.coerceIn(
            bounds.left,
            maxCoordinate(bounds.left, bounds.right, bubbleWidthPx),
        )
        val bubbleBelowPet = petY - bubbleHeightPx < bounds.top
        val desiredBubbleY = if (bubbleBelowPet) {
            petY + petSizePx
        } else {
            petY - bubbleHeightPx
        }
        val bubbleY = desiredBubbleY.coerceIn(
            bounds.top,
            maxCoordinate(bounds.top, bounds.bottom, bubbleHeightPx),
        )

        return OverlayPlacementPx(
            petX = petX,
            petY = petY,
            bubbleX = bubbleX,
            bubbleY = bubbleY,
            bubbleBelowPet = bubbleBelowPet,
        )
    }

    /**
     * Snap detection uses the window edge. The snapped window then overhangs
     * horizontally by the SVG canvas padding so the visible crab reaches the edge.
     */
    fun snapPet(petX: Int, petY: Int, bounds: SafeBoundsPx): SnapResult {
        val placement = movePet(petX, petY, bounds)
        val maxPetX = maxCoordinate(bounds.left, bounds.right, petSizePx)
        val maxPetY = maxCoordinate(bounds.top, bounds.bottom, petSizePx)
        val edge = listOf(
            SnapEdge.LEFT to abs(placement.petX - bounds.left).toFloat(),
            SnapEdge.TOP to abs(placement.petY - bounds.top).toFloat(),
            SnapEdge.RIGHT to abs(maxPetX - placement.petX).toFloat(),
            SnapEdge.BOTTOM to abs(maxPetY - placement.petY).toFloat(),
        ).minByOrNull { it.second }
            ?.takeIf { it.second <= snapThresholdPx }
            ?.first
        val snapped = when (edge) {
            SnapEdge.LEFT -> placePet(
                bounds.left - horizontalSnapOverhangPx,
                placement.petY,
                bounds,
            )
            SnapEdge.TOP -> movePet(placement.petX, bounds.top, bounds)
            SnapEdge.RIGHT -> placePet(
                maxPetX + horizontalSnapOverhangPx,
                placement.petY,
                bounds,
            )
            SnapEdge.BOTTOM -> movePet(placement.petX, maxPetY, bounds)
            null -> placement
        }
        return SnapResult(snapped, edge)
    }

    private fun dpToPx(dp: Int, density: Float): Int = (dp * density).roundToInt()

    private fun maxCoordinate(minimum: Int, maximumExclusive: Int, size: Int): Int =
        (maximumExclusive - size).coerceAtLeast(minimum)

    private companion object {
        const val DEFAULT_PET_SIZE_DP = 90
        const val BUBBLE_WIDTH_DP = 180
        const val BUBBLE_HEIGHT_DP = 120
        const val EDGE_MARGIN_DP = 16
        const val SNAP_THRESHOLD_DP = 15f
    }
}
