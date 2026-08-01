package com.luc.body.overlay

import kotlin.math.roundToInt

class OverlayPlacementTracker(
    private val geometry: OverlayGeometry,
    private val boundsProvider: () -> SafeBoundsPx,
) {
    private var authoritativePetX: Float? = null
    private var authoritativePetY: Float? = null

    fun initialPlacement(): OverlayPlacementPx = update(geometry.initialPlacement(boundsProvider()))

    fun moveBy(deltaX: Float, deltaY: Float): OverlayPlacementPx {
        val requestedPetX = checkNotNull(authoritativePetX) { "initial placement is required" } + deltaX
        val requestedPetY = checkNotNull(authoritativePetY) { "initial placement is required" } + deltaY
        return update(
            geometry.movePet(
                petX = requestedPetX.roundToInt(),
                petY = requestedPetY.roundToInt(),
                bounds = boundsProvider(),
            ),
            requestedPetX,
            requestedPetY,
        )
    }

    private fun update(
        newPlacement: OverlayPlacementPx,
        requestedPetX: Float = newPlacement.petX.toFloat(),
        requestedPetY: Float = newPlacement.petY.toFloat(),
    ): OverlayPlacementPx {
        authoritativePetX = if (requestedPetX.roundToInt() == newPlacement.petX) {
            requestedPetX
        } else {
            newPlacement.petX.toFloat()
        }
        authoritativePetY = if (requestedPetY.roundToInt() == newPlacement.petY) {
            requestedPetY
        } else {
            newPlacement.petY.toFloat()
        }
        return newPlacement
    }
}
