package com.luc.body.gesture

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
    fun euclideanDistanceFromTheOriginalDownPointDeterminesTap() {
        val classifier = GestureClassifier(touchSlopPx = 15f, tapTimeoutMs = 200)

        classifier.onDown(100f, 100f, 1_000)

        assertEquals(GestureResult.DragEnd, classifier.onUp(110f, 110f, 1_100))
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

        assertEquals(GestureResult.Move(8f, -3f), classifier.onMove(108f, 97f, 1_010))
        assertEquals(GestureResult.Move(4f, 7f), classifier.onMove(112f, 104f, 1_020))
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
