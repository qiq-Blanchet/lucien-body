package com.luc.body.web

import org.junit.Assert.assertEquals
import org.junit.Test

class JavascriptCommandBuilderTest {
    @Test
    fun bubbleCommandQuotesInjectionPayload() {
        assertEquals(
            "window.LucBubble.show(\"x\\\");alert(1);//\",\"love\",\"2026-07-29T00:00:00Z\")",
            JavascriptCommandBuilder.showBubble(
                text = "x\");alert(1);//",
                style = "love",
                revision = "2026-07-29T00:00:00Z",
            ),
        )
    }

    @Test
    fun bubbleCommandQuotesNewlinesUnicodeAndBackslashes() {
        assertEquals(
            "window.LucBubble.show(\"line1\\n雪\\\\path\",\"normal\",\"rev\\n2\")",
            JavascriptCommandBuilder.showBubble(
                text = "line1\n雪\\path",
                style = "normal",
                revision = "rev\n2",
            ),
        )
    }

    @Test
    fun expressionFallsBackToIdleWhenValueIsNotAllowlisted() {
        assertEquals(
            "window.LucPet.setExpression(\"idle\")",
            JavascriptCommandBuilder.setExpression("x\");alert(1);//"),
        )
    }

    @Test
    fun hideBubbleUsesNoArguments() {
        assertEquals("window.LucBubble.hide()", JavascriptCommandBuilder.hideBubble())
    }
}
