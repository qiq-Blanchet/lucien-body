package com.luc.body.overlay

import com.luc.body.state.BubbleStyle
import com.luc.body.state.EmotionMetrics
import com.luc.body.state.Expression
import com.luc.body.state.VisibleState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayRenderDecisionTest {
    @Test
    fun onlyMetricsChangesDoNotRestartThePetOrBubbleRenderer() {
        val base = state()

        assertTrue(
            differsOnlyInEmotionMetrics(
                base,
                base.copy(emotionMetrics = EmotionMetrics(0.78, 0.42, 7)),
            ),
        )
        assertFalse(differsOnlyInEmotionMetrics(null, base))
        assertFalse(differsOnlyInEmotionMetrics(base, base))
        assertFalse(differsOnlyInEmotionMetrics(base, base.copy(bubbleText = "new")))
        assertFalse(differsOnlyInEmotionMetrics(base, base.copy(expression = Expression.LOVE)))
    }

    @Test
    fun metricsVisibilityChangesReapplyPlacementOnlyWhenThePanelAppearsOrDisappears() {
        val hidden = state()
        val visible = hidden.copy(emotionMetrics = EmotionMetrics(0.78, 0.42, 7))

        assertTrue(emotionMetricsVisibilityChanged(hidden, visible))
        assertTrue(emotionMetricsVisibilityChanged(visible, hidden))
        assertFalse(emotionMetricsVisibilityChanged(null, hidden))
        assertFalse(
            emotionMetricsVisibilityChanged(
                visible,
                visible.copy(emotionMetrics = EmotionMetrics(0.79, 0.43, 8)),
            ),
        )
    }

    private fun state() = VisibleState(
        expression = Expression.HAPPY,
        bubbleText = "hello",
        bubbleStyle = BubbleStyle.NORMAL,
        revision = "revision",
    )
}
