package com.luc.body.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SvgMarkupNormalizerTest {
    @Test
    fun lowercaseViewboxIsCorrectedOnlyInTheLoadedMarkup() {
        val source = "<svg viewbox=\"-15 -25 45 45\"><rect/></svg>"

        val normalized = SvgMarkupNormalizer.normalize(source)

        assertTrue(normalized.contains("viewBox=\"-15 -25 45 45\""))
        assertEquals(source, "<svg viewbox=\"-15 -25 45 45\"><rect/></svg>")
    }

    @Test
    fun correctlyCasedViewBoxIsLeftUnchanged() {
        val source = "<svg viewBox=\"0 0 10 10\"></svg>"

        assertEquals(source, SvgMarkupNormalizer.normalize(source))
    }
}
