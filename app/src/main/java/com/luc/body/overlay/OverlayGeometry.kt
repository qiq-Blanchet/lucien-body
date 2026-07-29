package com.luc.body.overlay

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

class OverlayGeometry(
    density: Float,
) {
    private val petSizePx = dpToPx(PET_SIZE_DP, density)
    private val bubbleWidthPx = dpToPx(BUBBLE_WIDTH_DP, density)
    private val bubbleHeightPx = dpToPx(BUBBLE_HEIGHT_DP, density)
    private val edgeMarginPx = dpToPx(EDGE_MARGIN_DP, density)

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

        val desiredBubbleX = clampedPetX + (petSizePx - bubbleWidthPx) / 2
        val bubbleX = desiredBubbleX.coerceIn(
            bounds.left,
            maxCoordinate(bounds.left, bounds.right, bubbleWidthPx),
        )
        val bubbleBelowPet = clampedPetY - bubbleHeightPx < bounds.top
        val desiredBubbleY = if (bubbleBelowPet) {
            clampedPetY + petSizePx
        } else {
            clampedPetY - bubbleHeightPx
        }
        val bubbleY = desiredBubbleY.coerceIn(
            bounds.top,
            maxCoordinate(bounds.top, bounds.bottom, bubbleHeightPx),
        )

        return OverlayPlacementPx(
            petX = clampedPetX,
            petY = clampedPetY,
            bubbleX = bubbleX,
            bubbleY = bubbleY,
            bubbleBelowPet = bubbleBelowPet,
        )
    }

    private fun dpToPx(dp: Int, density: Float): Int = (dp * density).roundToInt()

    private fun maxCoordinate(minimum: Int, maximumExclusive: Int, size: Int): Int =
        (maximumExclusive - size).coerceAtLeast(minimum)

    private companion object {
        const val PET_SIZE_DP = 120
        const val BUBBLE_WIDTH_DP = 240
        const val BUBBLE_HEIGHT_DP = 160
        const val EDGE_MARGIN_DP = 16
    }
}
