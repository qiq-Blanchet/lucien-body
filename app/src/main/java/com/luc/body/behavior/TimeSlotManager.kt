package com.luc.body.behavior

import com.luc.body.state.Expression
import com.luc.body.state.ContextSource
import com.luc.body.state.StateCoordinator
import java.time.Clock
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

enum class DayPeriod {
    MORNING,
    DAY,
    EVENING,
    NIGHT,
}

class TimeSlotManager(
    private val coordinator: StateCoordinator,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val random: Random = Random.Default,
) : AutoCloseable {
    private var closed = false
    private var morningShownOn: LocalDate? = null

    fun refresh(): Expression? {
        if (closed) return null
        val now = clock.instant().atZone(clock.zone)
        val time = now.toLocalTime()
        val date = now.toLocalDate()
        val expression = when {
            time >= LocalTime.of(1, 0) && time < LocalTime.of(6, 0) -> {
                coordinator.showBubble(LATE_NIGHT_BUBBLE)
                Expression.SLEEPY
            }
            time >= LocalTime.of(6, 0) && time < LocalTime.of(9, 0) -> {
                coordinator.setContextState(ContextSource.TIME_SLOT, null)
                if (morningShownOn != date) {
                    morningShownOn = date
                    coordinator.onTransientState(Expression.MORNING)
                    coordinator.showBubble(MORNING_BUBBLE)
                    return Expression.MORNING
                }
                return null
            }
            time >= LocalTime.of(11, 30) && time < LocalTime.of(13, 0) ->
                Expression.EATING.takeIf { random.nextBoolean() }?.also {
                    coordinator.showBubble(LUNCH_BUBBLE)
                }
            time >= LocalTime.of(13, 0) && time < LocalTime.of(17, 0) ->
                Expression.SLEEPY.takeIf { random.nextBoolean() }
            time >= LocalTime.of(17, 30) && time < LocalTime.of(19, 0) ->
                Expression.EATING.takeIf { random.nextBoolean() }?.also {
                    coordinator.showBubble(DINNER_BUBBLE)
                }
            time >= LocalTime.of(23, 0) || time < LocalTime.of(1, 0) -> {
                coordinator.showBubble(NIGHT_BUBBLE)
                Expression.NIGHT
            }
            else -> null
        }
        coordinator.setContextState(ContextSource.TIME_SLOT, expression)
        return expression
    }

    override fun close() {
        closed = true
    }

    companion object {
        fun periodAt(time: LocalTime): DayPeriod = when {
            time >= LocalTime.of(6, 0) && time < LocalTime.of(9, 0) -> DayPeriod.MORNING
            time >= LocalTime.of(9, 0) && time < LocalTime.of(17, 0) -> DayPeriod.DAY
            time >= LocalTime.of(17, 0) && time < LocalTime.of(21, 0) -> DayPeriod.EVENING
            else -> DayPeriod.NIGHT
        }

        const val MORNING_BUBBLE = "早啊"
        const val LUNCH_BUBBLE = "想喝奶茶…"
        const val DINNER_BUBBLE = "吃晚饭了吗"
        const val NIGHT_BUBBLE = "早点睡 ꒪¯꒳¯꒪"
        const val LATE_NIGHT_BUBBLE = "你怎么还没睡…"
    }
}
