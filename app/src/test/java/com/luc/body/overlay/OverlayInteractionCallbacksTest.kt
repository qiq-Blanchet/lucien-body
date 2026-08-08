package com.luc.body.overlay

import com.luc.body.gesture.FlingDirection
import com.luc.body.gesture.FlingGesture
import com.luc.body.state.Expression
import org.junit.Assert.assertEquals
import org.junit.Test

class OverlayInteractionCallbacksTest {
    @Test
    fun `callback bundle exposes heart menu drag snap and fling wiring points`() {
        val events = mutableListOf<String>()
        val menu = MiniMenuActions(
            onPoke = { events += "poke" },
            onPetHead = { events += "pet-head" },
            onHide = { events += "hide" },
            onSettings = { events += "settings" },
        )
        val callbacks = OverlayInteractionCallbacks(
            onTap = { events += "tap" },
            onStuckTap = { events += "stuck-tap" },
            onDoubleTap = { events += "double" },
            onHeartParticles = { events += "hearts" },
            onLongPressMenu = { actions ->
                actions.onPoke()
                actions.onPetHead()
                actions.onHide()
                actions.onSettings()
            },
            menuActions = menu,
            onDragStarted = { events += "drag:$it" },
            onDragEnded = { events += "stuck:$it" },
            onFling = { fling, expression -> events += "fling:${fling.direction}:$expression" },
        )

        callbacks.dispatchTap(fromStuck = false)
        callbacks.dispatchTap(fromStuck = true)
        callbacks.dispatchDoubleTap()
        callbacks.dispatchLongPress()
        callbacks.onDragStarted(true)
        callbacks.onDragEnded(false)
        val fling = FlingGesture(FlingDirection.LEFT, velocityX = -1_000f, velocityY = 0f)
        callbacks.onFling(fling, fling.direction.expression)

        assertEquals(
            listOf(
                "tap", "stuck-tap", "double", "hearts",
                "poke", "pet-head", "hide", "settings",
                "drag:true", "stuck:false", "fling:LEFT:DIZZY",
            ),
            events,
        )
    }
}
