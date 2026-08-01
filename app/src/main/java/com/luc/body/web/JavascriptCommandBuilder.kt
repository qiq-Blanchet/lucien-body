package com.luc.body.web

import org.json.JSONObject

object JavascriptCommandBuilder {
    private val supportedExpressions = setOf("idle", "happy", "angry", "sleepy")
    private val supportedBubbleStyles = setOf("normal", "whisper", "shout", "love")

    fun setExpression(expression: String): String {
        val safeExpression = expression.lowercase().takeIf(supportedExpressions::contains) ?: "idle"
        return "window.LucPet.setExpression(${JSONObject.quote(safeExpression)})"
    }

    fun showBubble(text: String, style: String, revision: String): String {
        val safeStyle = style.lowercase().takeIf(supportedBubbleStyles::contains) ?: "normal"
        return "window.LucBubble.show(${JSONObject.quote(text)},${JSONObject.quote(safeStyle)},${JSONObject.quote(revision)})"
    }

    fun hideBubble(): String = "window.LucBubble.hide()"
}
