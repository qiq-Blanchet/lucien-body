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

data class MetricsPlacementPx(
    val x: Int,
    val y: Int,
)

data class MiniMenuPlacementPx(
    val x: Int,
    val y: Int,
    val abovePet: Boolean,
)

enum class SnapEdge { LEFT, RIGHT }

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
    private val metricsWidthPx = dpToPx(METRICS_WINDOW_WIDTH_DP, density)
    private val metricsHeightPx = dpToPx(METRICS_WINDOW_HEIGHT_DP, density)
    private val metricsPanelInsetPx = dpToPx(METRICS_PANEL_INSET_DP, density)
    private val metricsGapPx = dpToPx(METRICS_GAP_DP, density)
    private val edgeMarginPx = dpToPx(EDGE_MARGIN_DP, density)
    private val snapThresholdPx = SNAP_THRESHOLD_DP * density
    // The source sprites use a 45-unit viewBox with roughly 15 units of side padding.
    private val horizontalSnapOverhangPx = petSizePx / 3
    // Visible bodies start around 30 units down in the same 45-unit source viewBox.
    private val topMovementOverhangPx = petSizePx * 2 / 3

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
        val clampedPetY = petY.coerceIn(minimumPetY(bounds), maxCoordinate(bounds.top, bounds.bottom, petSizePx))

        return placePet(clampedPetX, clampedPetY, bounds)
    }

    fun moveSnappedPet(petX: Int, petY: Int, bounds: SafeBoundsPx): OverlayPlacementPx {
        val minimumPetX = bounds.left - horizontalSnapOverhangPx
        val maximumPetX =
            maxCoordinate(bounds.left, bounds.right, petSizePx) + horizontalSnapOverhangPx
        return placePet(
            petX = petX.coerceIn(minimumPetX, maximumPetX),
            petY = petY.coerceIn(minimumPetY(bounds), maxCoordinate(bounds.top, bounds.bottom, petSizePx)),
            bounds = bounds,
        )
    }

    fun minimumPetY(bounds: SafeBoundsPx): Int = bounds.top - topMovementOverhangPx

    fun metricsPlacement(petX: Int, petY: Int, bounds: SafeBoundsPx): MetricsPlacementPx {
        val visibleLeft = petX + petSizePx / 3
        val visibleRight = petX + petSizePx * 2 / 3
        val leftX = visibleLeft - metricsGapPx - metricsWidthPx + metricsPanelInsetPx
        val rightX = visibleRight + metricsGapPx - metricsPanelInsetPx
        val maximumX = maxCoordinate(bounds.left, bounds.right, metricsWidthPx)
        val desiredX = when {
            leftX >= bounds.left -> leftX
            rightX <= maximumX -> rightX
            visibleLeft - bounds.left >= bounds.right - visibleRight -> leftX
            else -> rightX
        }
        val desiredY = petY + (petSizePx - metricsHeightPx) / 2

        return MetricsPlacementPx(
            x = desiredX.coerceIn(bounds.left, maximumX),
            y = desiredY.coerceIn(
                bounds.top,
                maxCoordinate(bounds.top, bounds.bottom, metricsHeightPx),
            ),
        )
    }

    /**
     * The crab occupies only the lower part of its transparent SVG WebView.
     * Below: the menu starts just beneath the visible feet.
     * Above: the menu ends roughly 16dp before the visible head at 90dp size.
     */
    fun miniMenuPlacement(
        petX: Int,
        petY: Int,
        menuWidthPx: Int,
        menuHeightPx: Int,
        bounds: SafeBoundsPx,
    ): MiniMenuPlacementPx {
        require(menuWidthPx > 0 && menuHeightPx > 0)
        val centeredX = petX + (petSizePx - menuWidthPx) / 2
        val desiredBelowY = petY + petSizePx * 4 / 5
        val abovePet = desiredBelowY + menuHeightPx > bounds.bottom
        val desiredY = if (abovePet) {
            petY + (petSizePx * 37 / 90) - menuHeightPx
        } else {
            desiredBelowY
        }
        return MiniMenuPlacementPx(
            x = centeredX.coerceIn(bounds.left, maxCoordinate(bounds.left, bounds.right, menuWidthPx)),
            y = desiredY.coerceIn(bounds.top, maxCoordinate(bounds.top, bounds.bottom, menuHeightPx)),
            abovePet = abovePet,
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
     * Only the vertical screen sides are sticky. The snapped window overhangs
     * horizontally by the SVG canvas padding so the visible crab reaches the edge.
     */
    fun snapPet(petX: Int, petY: Int, bounds: SafeBoundsPx): SnapResult {
        val placement = movePet(petX, petY, bounds)
        val maxPetX = maxCoordinate(bounds.left, bounds.right, petSizePx)
        val edge = listOf(
            SnapEdge.LEFT to abs(placement.petX - bounds.left).toFloat(),
            SnapEdge.RIGHT to abs(maxPetX - placement.petX).toFloat(),
        ).minByOrNull { it.second }
            ?.takeIf { it.second <= snapThresholdPx }
            ?.first
        val snapped = when (edge) {
            SnapEdge.LEFT -> placePet(
                bounds.left - horizontalSnapOverhangPx,
                placement.petY,
                bounds,
            )
            SnapEdge.RIGHT -> placePet(
                maxPetX + horizontalSnapOverhangPx,
                placement.petY,
                bounds,
            )
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
        const val METRICS_WINDOW_WIDTH_DP = 112
        const val METRICS_WINDOW_HEIGHT_DP = 106
        const val METRICS_PANEL_INSET_DP = 4
        const val METRICS_GAP_DP = 20
        const val EDGE_MARGIN_DP = 16
        const val SNAP_THRESHOLD_DP = 4f
    }
}
