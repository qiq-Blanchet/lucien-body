package com.luc.body.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClawdHtmlSecurityTest {
    @Test
    fun `sprites render as isolated images instead of transparent sandbox frames`() {
        val html = File("src/main/assets/clawd.html").readText()

        assertEquals(2, Regex("<img\\b").findAll(html).count())
        assertEquals(0, Regex("<iframe\\b").findAll(html).count())
        assertTrue(html.contains("incoming.onload"))
        assertTrue(html.contains("currentRevision !== revision"))
        assertTrue(html.contains("outgoing.removeAttribute(\"src\")"))
        assertFalse(html.contains("innerHTML"))
    }

    @Test
    fun `outer layer has no sprite animation while state classes and hearts remain`() {
        val html = File("src/main/assets/clawd.html").readText()
        val css = File("src/main/assets/css/clawd.css").readText()

        assertFalse(css.contains("transition: opacity"))
        assertFalse(html.contains("pet--fading"))
        listOf("idle", "happy", "angry", "sleepy", "dizzy", "dancing").forEach { state ->
            assertFalse(css.contains(".pet--$state"))
        }
        listOf("idle-float", "idle-blink", "happy-sway", "angry-shake", "sleepy-breathe", "dizzy-spin", "dancing-bounce")
            .forEach { keyframe -> assertFalse(css.contains(keyframe)) }
        assertTrue(html.contains("pet--\${state}"))
        assertTrue(html.contains("\"dizzy\""))
        assertTrue(css.contains("animation: heart-rise"))
        assertTrue(css.contains("@keyframes heart-rise"))
    }

    @Test
    fun `bubble supports sleepy style without a border or tail`() {
        val html = File("src/main/assets/bubble.html").readText()
        val css = File("src/main/assets/css/bubble.css").readText()

        assertTrue(html.contains("\"sleepy\""))
        assertTrue(css.contains(".bubble--sleepy"))
        assertFalse(css.contains("border:"))
        assertFalse(css.contains("font-weight:"))
        assertFalse(css.contains("border-bottom"))
    }
}
