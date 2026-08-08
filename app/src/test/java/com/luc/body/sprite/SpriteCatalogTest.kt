package com.luc.body.sprite

import org.junit.Assert.assertEquals
import org.junit.Test

class SpriteCatalogTest {
    @Test
    fun `groups exact and suffixed names using the longest complete state id`() {
        val catalog = SpriteCatalog(
            assets = listOf(
                SpriteAsset("stuck_1.svg", "asset-stuck"),
                SpriteAsset("stuck_tap_1.svg", "asset-tap"),
                SpriteAsset("idle_1.svg", "asset-idle"),
            ),
            stateIds = setOf("idle", "stuck", "stuck_tap"),
            randomIndex = { 0 },
        )

        assertEquals("asset-tap", catalog.choose("stuck_tap").url)
        assertEquals("asset-stuck", catalog.choose("stuck").url)
    }

    @Test
    fun `falls back to idle and avoids an immediate repeat when variants exist`() {
        val catalog = SpriteCatalog(
            assets = listOf(
                SpriteAsset("idle_1.svg", "idle-1"),
                SpriteAsset("idle_2.svg", "idle-2"),
                SpriteAsset("happy.svg", "happy"),
            ),
            stateIds = setOf("idle", "happy"),
            randomIndex = { 0 },
        )

        assertEquals("idle-1", catalog.choose("missing").url)
        assertEquals("idle-2", catalog.choose("missing").url)
        assertEquals("happy", catalog.choose("happy").url)
    }

    @Test
    fun `lonely numbered variants match the complete lonely state id`() {
        val catalog = SpriteCatalog(
            assets = listOf(
                SpriteAsset("idle_1.svg", "idle"),
                SpriteAsset("lonely_1_1.svg", "lonely-one"),
                SpriteAsset("lonely_2.svg", "lonely-two"),
            ),
            stateIds = setOf("idle", "lonely_1", "lonely_2"),
            randomIndex = { 0 },
        )

        assertEquals("lonely-one", catalog.choose("lonely_1").url)
        assertEquals("lonely-two", catalog.choose("lonely_2").url)
    }

    @Test
    fun `external sprites replace same-named bundled sprites and empty external is assets only`() {
        val bundled = listOf(
            SpriteAsset("idle_1.svg", "bundled-idle"),
            SpriteAsset("happy.svg", "bundled-happy"),
        )
        val external = listOf(
            SpriteAsset("idle_1.svg", "external-idle"),
            SpriteAsset("idle_2.svg", "external-extra"),
        )

        assertEquals(
            listOf("external-idle", "external-extra", "bundled-happy"),
            SpriteCatalogLoader.merge(external, bundled).map(SpriteAsset::url),
        )
        assertEquals(bundled, SpriteCatalogLoader.merge(emptyList(), bundled))
    }
}
