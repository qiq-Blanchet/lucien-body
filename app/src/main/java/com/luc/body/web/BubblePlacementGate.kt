package com.luc.body.web

class BubblePlacementGate(
    private val dispatch: (belowPet: Boolean) -> Unit,
) {
    private var pageReady = false
    private var belowPet = false
    private var lastDispatched: Boolean? = null

    fun setBelowPet(value: Boolean) {
        belowPet = value
        dispatchIfNeeded()
    }

    fun onPageFinished() {
        pageReady = true
        dispatchIfNeeded()
    }

    private fun dispatchIfNeeded() {
        if (!pageReady || lastDispatched == belowPet) return
        lastDispatched = belowPet
        dispatch(belowPet)
    }
}
