package com.luc.body.gesture

import android.view.MotionEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class GestureClassifierTest {
    @Test
    fun movementBelowTenDpWithinTwoHundredMsIsTap() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)

        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.Tap, classifier.onUp(119f, 100f, 1_199))
    }

    @Test
    fun movementAtThresholdIsNotTap() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)

        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.DragEnd, classifier.onUp(120f, 100f, 1_100))
    }

    @Test
    fun durationAtThresholdIsNotTap() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)

        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.DragEnd, classifier.onUp(100f, 100f, 1_200))
    }

    @Test
    fun eventTimeBeforeDownIsNotTap() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.DragEnd, classifier.onUp(100f, 100f, 999))
    }

    @Test
    fun longTimestampExtremesDoNotOverflowIntoATap() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
        classifier.onDown(100f, 100f, Long.MIN_VALUE)

        assertEquals(GestureResult.DragEnd, classifier.onUp(100f, 100f, Long.MAX_VALUE))
    }

    @Test
    fun maximumTimestampCanStillRegisterAnImmediateTap() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
        classifier.onDown(100f, 100f, Long.MAX_VALUE)

        assertEquals(GestureResult.Tap, classifier.onUp(100f, 100f, Long.MAX_VALUE))
    }

    @Test
    fun euclideanDistanceFromTheOriginalDownPointDeterminesTap() {
        val classifier = GestureClassifier(touchSlopPx = 15f, tapTimeoutMs = 200)

        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.DragEnd, classifier.onUp(111f, 111f, 1_100))
    }

    @Test
    fun returningToTheDownPointAfterExceedingSlopIsStillADrag() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
        classifier.onDown(100f, 100f, 1_000)
        classifier.onMove(121f, 100f, 1_010)

        assertEquals(GestureResult.DragEnd, classifier.onUp(100f, 100f, 1_100))
    }

    @Test
    fun movesEmitContinuousDeltasFromThePreviousRawCoordinate() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.None, classifier.onMove(108f, 97f, 1_010))
        assertEquals(GestureResult.Move(20f, 0f), classifier.onMove(120f, 100f, 1_020))
        assertEquals(GestureResult.Move(4f, 7f), classifier.onMove(124f, 107f, 1_030))
    }

    @Test
    fun controllerDoesNotMovePetForJitterBelowTheThresholdAndStillTaps() {
        val moves = mutableListOf<Pair<Float, Float>>()
        var tapCount = 0
        val controller = PetGestureController(
            classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200),
            onMove = { deltaX, deltaY -> moves += deltaX to deltaY },
            onTap = { tapCount += 1 },
        )

        controller.onPointerEvent(MotionEvent.ACTION_DOWN, 100f, 100f, 1_000)
        controller.onPointerEvent(MotionEvent.ACTION_MOVE, 119f, 100f, 1_010)
        val result = controller.onPointerEvent(MotionEvent.ACTION_UP, 119f, 100f, 1_100)

        assertEquals(emptyList<Pair<Float, Float>>(), moves)
        assertEquals(1, tapCount)
        assertEquals(GestureResult.Tap, result)
    }

    @Test
    fun controllerStartsOneDragAtTheThresholdFromTheOriginalDownPoint() {
        val moves = mutableListOf<Pair<Float, Float>>()
        var tapCount = 0
        val controller = PetGestureController(
            classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200),
            onMove = { deltaX, deltaY -> moves += deltaX to deltaY },
            onTap = { tapCount += 1 },
        )

        controller.onPointerEvent(MotionEvent.ACTION_DOWN, 100f, 100f, 1_000)
        controller.onPointerEvent(MotionEvent.ACTION_MOVE, 119f, 100f, 1_010)
        controller.onPointerEvent(MotionEvent.ACTION_MOVE, 120f, 100f, 1_020)
        val result = controller.onPointerEvent(MotionEvent.ACTION_UP, 120f, 100f, 1_100)

        assertEquals(listOf(20f to 0f), moves)
        assertEquals(0, tapCount)
        assertEquals(GestureResult.DragEnd, result)
    }

    @Test
    fun cancelEndsTheGestureWithoutEmittingATap() {
        val classifier = GestureClassifier(touchSlopPx = 20f, tapTimeoutMs = 200)
        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.DragEnd, classifier.onCancel())
        assertEquals(GestureResult.None, classifier.onUp(100f, 100f, 1_010))
    }

    @Test
    fun densityFactoryConvertsTenDpToPixels() {
        val classifier = GestureClassifier.fromDensity(density = 2f)
        classifier.onDown(0f, 0f, 1_000)

        assertEquals(GestureResult.DragEnd, classifier.onUp(20f, 0f, 1_100))
    }
}
