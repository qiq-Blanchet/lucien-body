package com.luc.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayServiceActionsTest {
    @Test
    fun actionsAreStableAndPackageScoped() {
        val actions = listOf(
            OverlayServiceActions.ACTION_SHOW,
            OverlayServiceActions.ACTION_HIDE,
            OverlayServiceActions.ACTION_EXIT,
            OverlayServiceActions.ACTION_RESET_POSITION,
            OverlayServiceActions.ACTION_RELOAD,
        )
        assertEquals(5, actions.distinct().size)
        assertTrue(actions.all { it.startsWith("com.luc.body.action.") })
    }
}
