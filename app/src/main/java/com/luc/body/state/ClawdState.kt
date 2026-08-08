package com.luc.body.state

import java.util.Locale

enum class Expression(val priority: Int) {
    DIZZY(10),
    GRABBED(9),
    STUCK_GRAB(9),
    LOVE(8),
    DANCING(8),
    THINKING(7),
    TALKING(7),
    ANGRY(6),
    SHOCKED(6),
    HAPPY(5),
    SMUG(5),
    CONFUSED(5),
    SHY(5),
    PROUD(5),
    SULKY(5),
    STUCK_TAP(5),
    WAVING(4),
    CLINGY(4),
    MORNING(3),
    NIGHT(3),
    SLEEPY(3),
    LONELY_1(2),
    LONELY_2(2),
    LONELY_3(2),
    EATING(2),
    PEEKING(1),
    STUCK(1),
    IDLE(0),
    ;

    val id: String
        get() = name.lowercase(Locale.ROOT)

    companion object {
        private val byId = entries.associateBy(Expression::id)

        fun fromRemote(value: String?): Expression =
            byId[value?.trim()?.lowercase(Locale.ROOT)] ?: IDLE
    }
}

enum class BubbleStyle {
    NORMAL,
    WHISPER,
    SHOUT,
    LOVE,
    SLEEPY,
    ;

    companion object {
        private val byId = entries.associateBy { it.name.lowercase(Locale.ROOT) }

        fun fromRemote(value: String?): BubbleStyle =
            byId[value?.trim()?.lowercase(Locale.ROOT)] ?: NORMAL
    }
}

/**
 * A remote Supabase state row. [updatedAt] must be an ISO-8601 instant from
 * Supabase's `updated_at` column; malformed values are ignored by [StateCoordinator].
 * Emotion fields remain nullable for rows produced before v2.
 */
data class RemoteState(
    val expression: Expression,
    val bubbleText: String?,
    val bubbleStyle: BubbleStyle,
    val updatedAt: String,
    val valence: Double? = null,
    val arousal: Double? = null,
    val heat: Int? = null,
)

data class VisibleState(
    val expression: Expression,
    val bubbleText: String?,
    val bubbleStyle: BubbleStyle,
    val revision: String,
)

enum class ContextSource {
    DEFAULT,
    APP,
    LONELINESS,
    TIME_SLOT,
    SELF_TALK,
}

fun interface UiSink {
    fun render(state: VisibleState)
}
