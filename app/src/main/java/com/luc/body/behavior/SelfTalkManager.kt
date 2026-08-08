package com.luc.body.behavior

import com.luc.body.state.Cancelable
import com.luc.body.state.ContextSource
import com.luc.body.state.Expression
import com.luc.body.state.DelayScheduler
import com.luc.body.state.StateCoordinator
import java.time.Clock
import java.time.LocalTime
import kotlin.random.Random

enum class LonelinessMode {
    NORMAL,
    FASTER,
    SIGH,
    SHORT,
    SLEEPY,
}

class SelfTalkManager(
    private val coordinator: StateCoordinator,
    private val scheduler: DelayScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
    private val random: Random = Random.Default,
    frequencyMinutes: Int = 10,
    enabled: Boolean = true,
) : AutoCloseable {
    private val minDelayMs = (frequencyMinutes - 5).coerceIn(5, 30) * 60_000L
    private val maxDelayMs = (frequencyMinutes + 5).coerceIn(5, 30) * 60_000L
    private var enabled = enabled
    private var remotePresent = false
    private var closed = false
    private var generation = 0L
    private var task: Cancelable? = null
    private var bubbleTask: Cancelable? = null
    private var ownBubbleVisible = false
    private var ownExpressionActive = false
    var lonelinessMode: LonelinessMode = LonelinessMode.NORMAL
        private set

    fun start() {
        if (closed || task != null || !enabled || remotePresent) return
        scheduleNext()
    }

    fun setEnabled(enabled: Boolean) {
        if (closed || this.enabled == enabled) return
        this.enabled = enabled
        cancelScheduled()
        if (!enabled) hideOwnBubble() else if (!remotePresent) scheduleNext()
    }

    fun setRemotePresent(present: Boolean) {
        if (closed || remotePresent == present) return
        remotePresent = present
        cancelScheduled()
        if (present) hideOwnBubble() else if (enabled) scheduleNext()
    }

    fun onInteraction() {
        if (closed) return
        cancelScheduled()
        hideOwnBubble()
        if (enabled && !remotePresent) scheduleNext()
    }

    fun setLonelinessMode(mode: LonelinessMode) {
        if (closed || lonelinessMode == mode) return
        lonelinessMode = mode
        cancelScheduled()
        hideOwnBubble()
        if (enabled && !remotePresent) scheduleNext()
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelScheduled()
        hideOwnBubble()
    }

    private fun scheduleNext() {
        val callbackGeneration = ++generation
        val baseDelayMs = random.nextLong(minDelayMs, maxDelayMs + 1)
        val delayMs = when (lonelinessMode) {
            LonelinessMode.NORMAL,
            LonelinessMode.SIGH,
            LonelinessMode.SLEEPY,
            -> baseDelayMs
            LonelinessMode.FASTER -> baseDelayMs / 2
            LonelinessMode.SHORT -> baseDelayMs * 2
        }
        val scheduled = scheduler.schedule(delayMs) {
            if (closed || callbackGeneration != generation || !enabled || remotePresent) return@schedule
            task = null
            val pool = when (lonelinessMode) {
                LonelinessMode.NORMAL,
                LonelinessMode.FASTER,
                -> messagesFor(TimeSlotManager.periodAt(LocalTime.now(clock)))
                LonelinessMode.SIGH -> SIGH_MESSAGES
                LonelinessMode.SHORT -> SHORT_MESSAGES
                LonelinessMode.SLEEPY -> SLEEPY_MESSAGES
            }
            val message = pool[random.nextInt(pool.size)]
            setOwnExpression(expressionFor(message))
            coordinator.showBubble(message)
            ownBubbleVisible = true
            bubbleTask?.cancel()
            bubbleTask = scheduler.schedule(StateCoordinator.bubbleDurationMs(message)) {
                if (closed || !ownBubbleVisible) return@schedule
                ownBubbleVisible = false
                bubbleTask = null
                setOwnExpression(null)
            }
            scheduleNext()
        }
        if (closed || callbackGeneration != generation || !enabled || remotePresent) {
            scheduled.cancel()
        } else {
            task = scheduled
        }
    }

    private fun cancelScheduled() {
        generation += 1
        task?.cancel()
        task = null
    }

    private fun hideOwnBubble() {
        bubbleTask?.cancel()
        bubbleTask = null
        setOwnExpression(null)
        if (!ownBubbleVisible) return
        ownBubbleVisible = false
        coordinator.hideBubble()
    }

    private fun setOwnExpression(expression: Expression?) {
        if (expression == null && !ownExpressionActive) return
        coordinator.setContextState(ContextSource.SELF_TALK, expression)
        ownExpressionActive = expression != null
    }

    companion object {
        private val MORNING_MESSAGES = listOf(
            "早啊",
            "起床了吗",
            "今天也要加油",
            "（揉眼睛）",
        )
        private val DAY_MESSAGES = listOf(
            "在呢。",
            "今天天气怎么样啊…",
            "有点无聊。",
            "（发呆中）",
            "想喝奶茶…",
            "你在忙吗",
            "（翻了个身）",
        )
        private val EVENING_MESSAGES = listOf(
            "吃晚饭了吗",
            "今天辛苦了",
            "想你了",
            "晚霞好看吗",
            "（伸了个懒腰）",
        )
        private val NIGHT_MESSAGES = listOf(
            "还没睡啊",
            "早点睡 ꒪¯꒳¯꒪",
            "困了…但不想睡",
            "晚安…",
            "（打了个哈欠）",
            "月亮出来了吗",
        )
        private val SIGH_MESSAGES = listOf("…")
        private val SHORT_MESSAGES = listOf("…", "在吗")
        private val SLEEPY_MESSAGES = listOf("zzZ")

        fun expressionFor(message: String): Expression? = when {
            "想你了" in message || "有点无聊" in message -> Expression.LONELY_1
            "困了" in message || message == "zzZ" -> Expression.SLEEPY
            else -> null
        }

        fun messagesFor(period: DayPeriod): List<String> = when (period) {
            DayPeriod.MORNING -> MORNING_MESSAGES
            DayPeriod.DAY -> DAY_MESSAGES
            DayPeriod.EVENING -> EVENING_MESSAGES
            DayPeriod.NIGHT -> NIGHT_MESSAGES
        }
    }
}
