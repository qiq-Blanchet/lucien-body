package com.luc.body.web

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BubbleLayoutContractTest {
    @Test
    fun belowPetPlacementUsesTheTopEdgeWithAFourPixelGap() {
        val html = File("src/main/assets/bubble.html").readText()
        val css = File("src/main/assets/css/bubble.css").readText().replace("\r\n", "\n")

        assertTrue(html.contains("classList.toggle(\"bubble-below\", Boolean(belowPet))"))
        assertTrue(css.contains("body.bubble-below {\n  align-items: flex-start;"))
        assertTrue(css.contains("body.bubble-below .bubble {\n  margin-top: 4px;"))
    }

    @Test
    fun overlayForwardsTheGeometryDirectionToTheRenderer() {
        val source = File("src/main/java/com/luc/body/overlay/OverlayController.kt").readText()

        assertTrue(source.contains("renderer?.setBubbleBelowPet(placement.bubbleBelowPet)"))
    }

    @Test
    fun bubbleVisibilityIsOwnedByTheNativeTenSecondTimerWithoutACssExpiry() {
        val html = File("src/main/assets/bubble.html").readText()
        val css = File("src/main/assets/css/bubble.css").readText()

        assertTrue(css.contains("animation: bubble-enter-soft .3s ease-out 1 both"))
        assertTrue(css.contains("@keyframes bubble-enter-soft"))
        assertTrue(css.contains("@keyframes bubble-enter-opaque"))
        assertFalse(css.contains("bubble-cycle"))
        assertFalse(css.contains("animation: bubble-enter-soft 5s"))
        assertTrue(html.contains("bubble.dataset.revision === normalizedRevision"))
        assertTrue(html.contains("bubble.dataset.style === safeStyle"))
        assertTrue(html.contains("bubble.classList.contains(\"bubble--visible\")"))
    }
}
