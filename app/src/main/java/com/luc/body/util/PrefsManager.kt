package com.luc.body.util

import android.content.Context
import com.luc.body.BuildConfig

class PrefsManager(
    context: Context,
    private val defaultSupabaseUrl: String = BuildConfig.SUPABASE_URL,
    private val defaultSupabaseKey: String = BuildConfig.SUPABASE_PUBLISHABLE_KEY,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var supabaseUrl: String
        get() = prefs.getString(KEY_SUPABASE_URL, defaultSupabaseUrl) ?: defaultSupabaseUrl
        set(value) = prefs.edit().putString(KEY_SUPABASE_URL, value.trim()).apply()

    var supabaseKey: String
        get() = prefs.getString(KEY_SUPABASE_KEY, defaultSupabaseKey) ?: defaultSupabaseKey
        set(value) = prefs.edit().putString(KEY_SUPABASE_KEY, value.trim()).apply()

    var bootEnabled: Boolean
        get() = prefs.getBoolean(KEY_BOOT_ENABLED, DEFAULT_BOOT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_BOOT_ENABLED, value).apply()

    var selfTalkEnabled: Boolean
        get() = prefs.getBoolean(KEY_SELF_TALK_ENABLED, DEFAULT_SELF_TALK_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_SELF_TALK_ENABLED, value).apply()

    var selfTalkFrequencyMinutes: Int
        get() = normalizeSelfTalkFrequency(
            prefs.getInt(KEY_SELF_TALK_FREQUENCY_MINUTES, DEFAULT_SELF_TALK_FREQUENCY_MINUTES),
        )
        set(value) = prefs.edit().putInt(KEY_SELF_TALK_FREQUENCY_MINUTES, normalizeSelfTalkFrequency(value)).apply()

    var lonelinessEnabled: Boolean
        get() = prefs.getBoolean(KEY_LONELINESS_ENABLED, DEFAULT_LONELINESS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_LONELINESS_ENABLED, value).apply()

    var appAwarenessEnabled: Boolean
        get() = prefs.getBoolean(KEY_APP_AWARENESS_ENABLED, DEFAULT_APP_AWARENESS_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_APP_AWARENESS_ENABLED, value).apply()

    var petSizeDp: Int
        get() = normalizePetSize(prefs.getInt(KEY_PET_SIZE_DP, DEFAULT_PET_SIZE_DP))
        set(value) = prefs.edit().putInt(KEY_PET_SIZE_DP, normalizePetSize(value)).apply()

    var bubbleDurationSeconds: Int
        get() = normalizeBubbleDuration(
            prefs.getInt(KEY_BUBBLE_DURATION_SECONDS, DEFAULT_BUBBLE_DURATION_SECONDS),
        )
        set(value) = prefs.edit().putInt(KEY_BUBBLE_DURATION_SECONDS, normalizeBubbleDuration(value)).apply()

    fun savedPosition(): Pair<Int, Int>? = if (
        prefs.contains(KEY_POSITION_X) && prefs.contains(KEY_POSITION_Y)
    ) {
        prefs.getInt(KEY_POSITION_X, 0) to prefs.getInt(KEY_POSITION_Y, 0)
    } else {
        null
    }

    fun savePosition(x: Int, y: Int) {
        prefs.edit().putInt(KEY_POSITION_X, x).putInt(KEY_POSITION_Y, y).apply()
    }

    fun resetPosition() {
        prefs.edit().remove(KEY_POSITION_X).remove(KEY_POSITION_Y).apply()
    }

    companion object {
        const val DEFAULT_BOOT_ENABLED = true
        const val DEFAULT_SELF_TALK_ENABLED = true
        const val DEFAULT_SELF_TALK_FREQUENCY_MINUTES = 10
        const val MIN_SELF_TALK_FREQUENCY_MINUTES = 5
        const val MAX_SELF_TALK_FREQUENCY_MINUTES = 30
        const val DEFAULT_LONELINESS_ENABLED = true
        const val DEFAULT_APP_AWARENESS_ENABLED = true
        const val DEFAULT_PET_SIZE_DP = 90
        const val MIN_PET_SIZE_DP = 60
        const val MAX_PET_SIZE_DP = 120
        const val DEFAULT_BUBBLE_DURATION_SECONDS = 4
        const val MIN_BUBBLE_DURATION_SECONDS = 2
        const val MAX_BUBBLE_DURATION_SECONDS = 10

        internal const val PREFS_NAME = "luc_settings"
        internal const val KEY_SUPABASE_URL = "supabase_url"
        internal const val KEY_SUPABASE_KEY = "supabase_key"
        internal const val KEY_BOOT_ENABLED = "boot_enabled"
        internal const val KEY_SELF_TALK_ENABLED = "self_talk_enabled"
        internal const val KEY_SELF_TALK_FREQUENCY_MINUTES = "self_talk_frequency_minutes"
        internal const val KEY_LONELINESS_ENABLED = "loneliness_enabled"
        internal const val KEY_APP_AWARENESS_ENABLED = "app_awareness_enabled"
        internal const val KEY_PET_SIZE_DP = "pet_size_dp"
        internal const val KEY_BUBBLE_DURATION_SECONDS = "bubble_duration_seconds"
        internal const val KEY_RESET_POSITION = "reset_position"
        internal const val KEY_POSITION_X = "position_x"
        internal const val KEY_POSITION_Y = "position_y"

        internal val SETTINGS_ITEMS = listOf(
            KEY_SUPABASE_URL,
            KEY_SUPABASE_KEY,
            KEY_BOOT_ENABLED,
            KEY_SELF_TALK_ENABLED,
            KEY_SELF_TALK_FREQUENCY_MINUTES,
            KEY_LONELINESS_ENABLED,
            KEY_APP_AWARENESS_ENABLED,
            KEY_PET_SIZE_DP,
            KEY_BUBBLE_DURATION_SECONDS,
            KEY_RESET_POSITION,
        )

        fun normalizeSelfTalkFrequency(value: Int): Int =
            value.coerceIn(MIN_SELF_TALK_FREQUENCY_MINUTES, MAX_SELF_TALK_FREQUENCY_MINUTES)

        fun normalizePetSize(value: Int): Int = value.coerceIn(MIN_PET_SIZE_DP, MAX_PET_SIZE_DP)

        fun normalizeBubbleDuration(value: Int): Int =
            value.coerceIn(MIN_BUBBLE_DURATION_SECONDS, MAX_BUBBLE_DURATION_SECONDS)
    }
}
