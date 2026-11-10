package com.luc.body.overlay

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmotionMetricsPanelContractTest {
    @Test
    fun panelAndAllDrawnContentUseTheSameEightyEightDpScale() {
        val source = File("src/main/java/com/luc/body/overlay/EmotionMetricsPanelView.kt").readText()

        assertEquals(88, EMOTION_METRICS_WIDTH_DP)
        assertEquals(88, EMOTION_METRICS_HEIGHT_DP)
        assertEquals(88f / 112f, EMOTION_METRICS_SCALE)
        assertEquals(20, EMOTION_METRICS_GAP_DP)
        assertTrue(source.contains("private fun scaledDp(value: Float): Float = dp(value * EMOTION_METRICS_SCALE)"))
        assertTrue(source.contains("private fun scaledSp(value: Float): Float = sp(value * EMOTION_METRICS_SCALE)"))
    }
}
