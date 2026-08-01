package com.luc.body.web

import com.luc.body.state.VisibleState

internal class WebRenderGate(
    private val dispatch: (VisibleState) -> Unit,
) {
    private var latestState: VisibleState? = null
    private var petReady = false
    private var bubbleReady = false

    fun render(state: VisibleState) {
        latestState = state
        dispatchIfReady()
    }

    fun onPetPageFinished() {
        if (petReady) return
        petReady = true
        dispatchIfReady()
    }

    fun onBubblePageFinished() {
        if (bubbleReady) return
        bubbleReady = true
        dispatchIfReady()
    }

    private fun dispatchIfReady() {
        if (petReady && bubbleReady) {
            latestState?.let(dispatch)
        }
    }
}
