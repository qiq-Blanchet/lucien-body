package com.luc.body.web

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClawdHtmlSecurityTest {
    @Test
    fun `sprites render in two script-disabled iframe documents`() {
        val html = File("src/main/assets/clawd.html").readText()

        assertEquals(2, Regex("<iframe\\b").findAll(html).count())
        assertEquals(2, Regex("sandbox=\"\"").findAll(html).count())
        assertTrue(html.contains("incoming.onload"))
        assertTrue(html.contains("currentRevision !== revision"))
        assertFalse(html.contains("innerHTML"))
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
