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
