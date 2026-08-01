package com.luc.body.web

import com.luc.body.state.BubbleStyle
import com.luc.body.state.Expression
import com.luc.body.state.VisibleState
import org.junit.Assert.assertEquals
import org.junit.Test

class WebRenderGateTest {
    @Test
    fun renderBeforePagesAreReadyDoesNotDispatch() {
        val dispatched = mutableListOf<VisibleState>()
        val gate = WebRenderGate(dispatched::add)

        gate.render(state("first"))

        assertEquals(emptyList<VisibleState>(), dispatched)
    }

    @Test
    fun pendingRendersKeepOnlyTheLatestState() {
        val dispatched = mutableListOf<VisibleState>()
        val gate = WebRenderGate(dispatched::add)
        val latest = state("latest")

        gate.render(state("older"))
        gate.render(latest)
        gate.onPetPageFinished()
        gate.onBubblePageFinished()

        assertEquals(listOf(latest), dispatched)
    }

    @Test
    fun onlyPetPageFinishedDoesNotDispatch() {
        val dispatched = mutableListOf<VisibleState>()
        val gate = WebRenderGate(dispatched::add)

        gate.render(state("pending"))
        gate.onPetPageFinished()

        assertEquals(emptyList<VisibleState>(), dispatched)
    }

    @Test
    fun onlyBubblePageFinishedDoesNotDispatch() {
        val dispatched = mutableListOf<VisibleState>()
        val gate = WebRenderGate(dispatched::add)

        gate.render(state("pending"))
        gate.onBubblePageFinished()

        assertEquals(emptyList<VisibleState>(), dispatched)
    }

    @Test
    fun pagesFinishedDispatchesLatestPendingState() {
        val dispatched = mutableListOf<VisibleState>()
        val gate = WebRenderGate(dispatched::add)
        val pending = state("pending")

        gate.render(pending)
        gate.onBubblePageFinished()
        gate.onPetPageFinished()

        assertEquals(listOf(pending), dispatched)
    }

    @Test
    fun renderAfterBothPagesAreReadyDispatchesImmediately() {
        val dispatched = mutableListOf<VisibleState>()
        val gate = WebRenderGate(dispatched::add)
        val rendered = state("ready")

        gate.onPetPageFinished()
        gate.onBubblePageFinished()
        gate.render(rendered)

        assertEquals(listOf(rendered), dispatched)
    }

    @Test
    fun duplicatePageFinishedDoesNotReplayState() {
        val dispatched = mutableListOf<VisibleState>()
        val gate = WebRenderGate(dispatched::add)
        val pending = state("pending")

        gate.render(pending)
        gate.onPetPageFinished()
        gate.onBubblePageFinished()
        gate.onPetPageFinished()
        gate.onBubblePageFinished()

        assertEquals(listOf(pending), dispatched)
    }

    private fun state(revision: String) = VisibleState(
        expression = Expression.HAPPY,
        bubbleText = revision,
        bubbleStyle = BubbleStyle.LOVE,
        revision = revision,
    )
}
