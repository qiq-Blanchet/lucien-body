package com.luc.body.state

enum class Expression {
    IDLE,
    HAPPY,
    ANGRY,
    SLEEPY,
    ;

    companion object {
        fun fromRemote(value: String?): Expression = when (value?.lowercase()) {
            "happy" -> HAPPY
            "angry" -> ANGRY
            "sleepy" -> SLEEPY
            else -> IDLE
        }
    }
}

enum class BubbleStyle {
    NORMAL,
    WHISPER,
    SHOUT,
    LOVE,
    ;

    companion object {
        fun fromRemote(value: String?): BubbleStyle = when (value?.lowercase()) {
            "whisper" -> WHISPER
            "shout" -> SHOUT
            "love" -> LOVE
            else -> NORMAL
        }
    }
}

/**
 * A remote Supabase state row. [updatedAt] must be an ISO-8601 instant from
 * Supabase's `updated_at` column; malformed values are ignored by [StateCoordinator].
 */
data class RemoteState(
    val expression: Expression,
    val bubbleText: String?,
    val bubbleStyle: BubbleStyle,
    val updatedAt: String,
)

data class VisibleState(
    val expression: Expression,
    val bubbleText: String?,
    val bubbleStyle: BubbleStyle,
    val revision: String,
)

fun interface UiSink {
    fun render(state: VisibleState)
}
