package com.luc.body.sprite

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpritePlaybackGateTest {
    @Test
    fun parserUsesTheLongestAnimationDurationWithoutMistakingDelayForDuration() {
        val svg = """
            <svg><style>
              .a { animation: bob .8s infinite, blink 2s infinite 9s; }
              .b { animation-duration: 1500ms; }
            </style></svg>
        """.trimIndent()

        assertEquals(2_000L, SpriteCycleParser.longestDurationMs(svg))
    }

    @Test
    fun parserIgnoresAnimationDeclarationsInsideComments() {
        val svg = """
            <svg>
              <!-- animation: obsolete 99s infinite; -->
              <style>/* animation: old 50s; */ .pet { animation: real 2s infinite; }</style>
            </svg>
        """.trimIndent()

        assertEquals(2_000L, SpriteCycleParser.longestDurationMs(svg))
    }

    @Test
    fun everyBundledSpriteHasAParsedCycleAndIdleCyclesMatchTheirSources() {
        val directory = java.io.File("src/main/assets/clawd_sprites")
        val cycles = directory.listFiles { file -> file.extension == "svg" }
            .orEmpty()
            .associate { it.name to SpriteCycleParser.longestDurationMs(it.readText()) }

        assertEquals(37, cycles.size)
        assertTrue(cycles.values.all { it > 0L })
        assertEquals(4_500L, cycles["idle_1.svg"])
        assertEquals(4_000L, cycles["idle_2.svg"])
        assertEquals(16_000L, cycles["idle_3.svg"])
        assertEquals(4_000L, cycles["idle_4.svg"])
    }

    @Test
    fun sameStateKeepsItsSpriteForAFullCycleButDifferentStateSwitchesImmediately() {
        var nowMs = 0L
        var choice = 0
        val catalog = SpriteCatalog(
            assets = listOf(
                SpriteAsset("idle_1.svg", "idle-1", 4_000L),
                SpriteAsset("idle_2.svg", "idle-2", 4_000L),
                SpriteAsset("happy.svg", "happy", 2_000L),
            ),
            stateIds = setOf("idle", "happy"),
            randomIndex = { size -> (choice++ % size) },
        )
        val gate = SpritePlaybackGate(catalog) { nowMs }

        assertEquals("idle-1", gate.select("idle").url)
        nowMs = 3_999L
        assertEquals("idle-1", gate.select("idle").url)
        assertEquals("happy", gate.select("happy").url)
        assertEquals("idle-2", gate.select("idle").url)
        nowMs = 9_000L
        assertEquals("idle-1", gate.select("idle").url)
    }
}
