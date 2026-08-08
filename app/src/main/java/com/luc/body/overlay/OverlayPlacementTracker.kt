package com.luc.body.overlay

import kotlin.math.roundToInt

class OverlayPlacementTracker(
    private val geometry: OverlayGeometry,
    private val boundsProvider: () -> SafeBoundsPx,
) {
    private var authoritativePetX: Float? = null
    private var authoritativePetY: Float? = null
    var isStuck: Boolean = false
        private set
    var currentPlacement: OverlayPlacementPx? = null
        private set

    fun initialPlacement(savedPosition: Pair<Int, Int>? = null): OverlayPlacementPx = update(
        savedPosition?.let { (x, y) -> geometry.movePet(x, y, boundsProvider()) }
            ?: geometry.initialPlacement(boundsProvider()),
    )

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

    fun reclampToCurrentBounds(): OverlayPlacementPx {
        val requestedPetX = checkNotNull(authoritativePetX) { "initial placement is required" }
        val requestedPetY = checkNotNull(authoritativePetY) { "initial placement is required" }
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

    fun finishDrag(allowSnap: Boolean = true): SnapResult {
        val petX = checkNotNull(authoritativePetX) { "initial placement is required" }.roundToInt()
        val petY = checkNotNull(authoritativePetY) { "initial placement is required" }.roundToInt()
        val result = if (allowSnap) {
            geometry.snapPet(petX, petY, boundsProvider())
        } else {
            SnapResult(geometry.movePet(petX, petY, boundsProvider()), edge = null)
        }
        isStuck = result.edge != null
        update(result.placement)
        return result
    }

    private fun update(
        newPlacement: OverlayPlacementPx,
        requestedPetX: Float = newPlacement.petX.toFloat(),
        requestedPetY: Float = newPlacement.petY.toFloat(),
    ): OverlayPlacementPx {
        currentPlacement = newPlacement
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
