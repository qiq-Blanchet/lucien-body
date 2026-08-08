package com.luc.body.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefsManagerTest {
    @Test
    fun defaultsAndRangesMatchV2Spec() {
        assertTrue(PrefsManager.DEFAULT_BOOT_ENABLED)
        assertTrue(PrefsManager.DEFAULT_SELF_TALK_ENABLED)
        assertEquals(10, PrefsManager.DEFAULT_SELF_TALK_FREQUENCY_MINUTES)
        assertEquals(5..30, PrefsManager.MIN_SELF_TALK_FREQUENCY_MINUTES..PrefsManager.MAX_SELF_TALK_FREQUENCY_MINUTES)
        assertTrue(PrefsManager.DEFAULT_LONELINESS_ENABLED)
        assertTrue(PrefsManager.DEFAULT_APP_AWARENESS_ENABLED)
        assertEquals(90, PrefsManager.DEFAULT_PET_SIZE_DP)
        assertEquals(60..120, PrefsManager.MIN_PET_SIZE_DP..PrefsManager.MAX_PET_SIZE_DP)
        assertEquals(4, PrefsManager.DEFAULT_BUBBLE_DURATION_SECONDS)
        assertEquals(2..10, PrefsManager.MIN_BUBBLE_DURATION_SECONDS..PrefsManager.MAX_BUBBLE_DURATION_SECONDS)
        assertEquals(10, PrefsManager.SETTINGS_ITEMS.size)
        assertEquals(10, PrefsManager.SETTINGS_ITEMS.distinct().size)
    }

    @Test
    fun numericSettingsAreClampedToSpecRanges() {
        assertEquals(5, PrefsManager.normalizeSelfTalkFrequency(0))
        assertEquals(30, PrefsManager.normalizeSelfTalkFrequency(31))
        assertEquals(60, PrefsManager.normalizePetSize(0))
        assertEquals(120, PrefsManager.normalizePetSize(121))
        assertEquals(2, PrefsManager.normalizeBubbleDuration(0))
        assertEquals(10, PrefsManager.normalizeBubbleDuration(11))
    }
}
