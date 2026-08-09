package com.luc.body.web

import org.junit.Assert.assertEquals
import org.junit.Test

class BubblePlacementGateTest {
    @Test
    fun placementSetBeforePageLoadIsAppliedWhenTheBubblePageFinishes() {
        val dispatched = mutableListOf<Boolean>()
        val gate = BubblePlacementGate { dispatched += it }

        gate.setBelowPet(true)
        assertEquals(emptyList<Boolean>(), dispatched)
        gate.onPageFinished()

        assertEquals(listOf(true), dispatched)
    }

    @Test
    fun readyPageReceivesDirectionChangesButNotDuplicates() {
        val dispatched = mutableListOf<Boolean>()
        val gate = BubblePlacementGate { dispatched += it }
        gate.onPageFinished()

        gate.setBelowPet(false)
        gate.setBelowPet(true)
        gate.setBelowPet(true)
        gate.setBelowPet(false)

        assertEquals(listOf(false, true, false), dispatched)
    }
}
