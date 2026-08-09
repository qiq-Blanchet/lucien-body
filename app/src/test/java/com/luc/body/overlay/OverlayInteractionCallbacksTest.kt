package com.luc.body.overlay

import com.luc.body.gesture.FlingDirection
import com.luc.body.gesture.FlingGesture
import com.luc.body.state.Expression
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayInteractionCallbacksTest {
    @Test
    fun `service defers fling settlement callback until coordinator is initialized`() {
        val source = File("src/main/java/com/luc/body/OverlayService.kt").readText()

        assertFalse(source.contains("onFlingSettled = coordinator::"))
        assertTrue(source.contains("onFlingSettled = { isStuck -> coordinator.setStuck(isStuck) }"))
    }

    @Test
    fun `long press keeps the existing menu and also reopens the latest emotion metrics`() {
        val source = File("src/main/java/com/luc/body/OverlayService.kt").readText()

        assertTrue(source.contains("controller.showMiniMenu(actions)"))
        assertTrue(source.contains("coordinator.showLatestEmotionMetrics()"))
    }

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
            onTap = { events += "tap:$it" },
            onStuckTap = { events += "stuck-tap:$it" },
            onDoubleTap = { first, second -> events += "double:$first:$second" },
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
            onFlingSettled = { events += "fling-stuck:$it" },
        )

        callbacks.dispatchTap(fromStuck = false, eventTimeMs = 100L)
        callbacks.dispatchTap(fromStuck = true, eventTimeMs = 200L)
        callbacks.dispatchDoubleTap(firstEventTimeMs = 300L, secondEventTimeMs = 500L)
        callbacks.dispatchLongPress()
        callbacks.onDragStarted(true)
        callbacks.onDragEnded(false)
        val fling = FlingGesture(FlingDirection.LEFT, velocityX = -1_000f, velocityY = 0f)
        callbacks.onFling(fling, fling.direction.expression)
        callbacks.onFlingSettled(true)

        assertEquals(
            listOf(
                "tap:100", "stuck-tap:200", "double:300:500", "hearts",
                "poke", "pet-head", "hide", "settings",
                "drag:true", "stuck:false", "fling:LEFT:DIZZY",
                "fling-stuck:true",
            ),
            events,
        )
    }
}
