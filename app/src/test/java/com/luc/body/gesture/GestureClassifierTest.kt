package com.luc.body.gesture

import com.luc.body.state.Expression
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureClassifierTest {
    private fun classifier() = GestureClassifier(
        touchSlopPx = 10f,
        doubleTapWindowMs = 300,
        longPressMs = 500,
        flingVelocityThresholdPxPerSecond = 1_000f,
    )

    @Test
    fun `ten pixel threshold starts drag but a fraction below does not`() {
        val classifier = classifier()
        classifier.onDown(0f, 0f, 0)

        assertEquals(GestureResult.None, classifier.onMove(9.999f, 0f, 10))
        assertEquals(
            GestureResult.DragStart(10f, 0f, fromStuck = false),
            classifier.onMove(10f, 0f, 20),
        )
    }

    @Test
    fun `double tap window includes exactly three hundred milliseconds`() {
        val classifier = classifier()
        classifier.onDown(0f, 0f, 0)
        assertEquals(GestureResult.None, classifier.onUp(0f, 0f, 100))
        assertEquals(GestureResult.None, classifier.onTimeout(400))

        classifier.onDown(20f, 20f, 400)
        assertEquals(GestureResult.DoubleTap, classifier.onUp(20f, 20f, 450))
    }

    @Test
    fun `single tap is emitted only after double tap window expires`() {
        val classifier = classifier()
        classifier.onDown(0f, 0f, 0, fromStuck = true)
        classifier.onUp(0f, 0f, 100)

        assertEquals(GestureResult.None, classifier.onTimeout(400))
        assertEquals(GestureResult.Tap(fromStuck = true), classifier.onTimeout(401))
    }

    @Test
    fun `long press fires at exactly five hundred milliseconds and suppresses tap`() {
        val classifier = classifier()
        classifier.onDown(0f, 0f, 1_000)

        assertEquals(GestureResult.None, classifier.onTimeout(1_499))
        assertEquals(GestureResult.LongPress, classifier.onTimeout(1_500))
        assertEquals(GestureResult.None, classifier.onUp(0f, 0f, 1_510))
    }

    @Test
    fun `cancel releases an active drag and never emits tap`() {
        val classifier = classifier()
        classifier.onDown(0f, 0f, 0)
        classifier.onMove(10f, 0f, 10)

        assertEquals(
            GestureResult.DragEnd(fling = null, cancelled = true),
            classifier.onCancel(),
        )
        assertEquals(GestureResult.None, classifier.onTimeout(1_000))
    }

    @Test
    fun `secondary pointer cannot steal or finish the primary gesture`() {
        val classifier = classifier()
        classifier.onDown(0f, 0f, 0, pointerId = 4)

        assertEquals(GestureResult.None, classifier.onDown(50f, 50f, 1, pointerId = 8))
        assertEquals(GestureResult.None, classifier.onMove(60f, 50f, 2, pointerId = 8))
        assertEquals(GestureResult.None, classifier.onUp(60f, 50f, 3, pointerId = 8))
        assertEquals(GestureResult.None, classifier.onUp(0f, 0f, 100, pointerId = 4))
        assertEquals(GestureResult.Tap(false), classifier.onTimeout(401))
    }

    @Test
    fun `fling direction uses dominant velocity axis and inclusive threshold`() {
        val cases = listOf(
            0f to -1_000f to FlingDirection.UP,
            0f to 1_000f to FlingDirection.DOWN,
            -1_000f to 0f to FlingDirection.LEFT,
            1_000f to 0f to FlingDirection.RIGHT,
        )

        cases.forEach { (velocity, direction) ->
            assertEquals(direction, classifier().classifyFling(velocity.first, velocity.second))
            assertEquals(Expression.DIZZY, direction.expression)
        }
        assertEquals(null, classifier().classifyFling(999.999f, 0f))
    }

    @Test
    fun `holding after a drag lowers release velocity below fling threshold`() {
        val classifier = classifier()
        classifier.onDown(0f, 0f, 0)
        classifier.onMove(20f, 0f, 10)

        assertEquals(
            GestureResult.DragEnd(fling = null),
            classifier.onUp(20f, 0f, 1_000),
        )
    }

    @Test
    fun `density factory converts the ten dp drag threshold to pixels`() {
        val classifier = GestureClassifier.fromDensity(
            density = 2f,
            flingVelocityThresholdPxPerSecond = 1_000f,
        )
        classifier.onDown(0f, 0f, 0)

        assertEquals(GestureResult.None, classifier.onMove(19.999f, 0f, 10))
        assertEquals(
            GestureResult.DragStart(20f, 0f, fromStuck = false),
            classifier.onMove(20f, 0f, 20),
        )
    }

    @Test
    fun `controller orders drag lock movement end and fling callbacks`() {
        val events = mutableListOf<String>()
        val controller = PetGestureController(
            classifier = classifier(),
            isStuck = { true },
            onDragStart = { events += "start:$it" },
            onMove = { x, y -> events += "move:$x,$y" },
            onDragEnd = { cancelled, fling -> events += "end:$cancelled:${fling?.direction}" },
            onTap = { events += "tap:$it" },
            onDoubleTap = { events += "double" },
            onLongPress = { events += "long" },
            onFling = { fling ->
                events += "fling:${fling.direction}:${fling.velocityX.toInt()},${fling.velocityY.toInt()}"
            },
        )

        controller.onPointerEvent(ACTION_DOWN, 0f, 0f, 0)
        controller.onPointerEvent(ACTION_MOVE, 10f, 0f, 10)
        controller.onPointerEvent(ACTION_MOVE, 30f, 0f, 20)
        controller.onPointerEvent(ACTION_UP, 30f, 0f, 21)

        assertEquals(
            listOf(
                "start:true", "move:10.0,0.0", "move:20.0,0.0",
                "end:false:RIGHT", "fling:RIGHT:1428,0",
            ),
            events,
        )
    }

    private companion object {
        const val ACTION_DOWN = 0
        const val ACTION_UP = 1
        const val ACTION_MOVE = 2
    }
}
