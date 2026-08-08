package com.luc.body.behavior

import com.luc.body.state.Cancelable
import com.luc.body.state.ContextSource
import com.luc.body.state.DelayScheduler
import com.luc.body.state.Expression
import com.luc.body.state.StateCoordinator
import java.time.Clock
import java.time.Duration
import java.time.Instant

class LonelinessTracker(
    private val coordinator: StateCoordinator,
    private val selfTalkManager: SelfTalkManager,
    private val scheduler: DelayScheduler,
    private val clock: Clock = Clock.systemDefaultZone(),
    enabled: Boolean = true,
) : AutoCloseable {
    private enum class Stage { NORMAL, LONELY_1, LONELY_2, LONELY_3, SLEEPY }

    private var enabled = enabled
    private var closed = false
    private var generation = 0L
    private var task: Cancelable? = null
    private var lastInteraction: Instant = clock.instant()
    private var stage = Stage.NORMAL

    fun start() {
        if (closed || task != null || !enabled) return
        refreshAndSchedule()
    }

    fun onInteraction() {
        if (closed) return
        lastInteraction = clock.instant()
        cancelScheduled()
        if (stage != Stage.NORMAL) coordinator.setContextState(ContextSource.LONELINESS, null)
        stage = Stage.NORMAL
        selfTalkManager.setLonelinessMode(LonelinessMode.NORMAL)
        if (enabled) scheduleAfter(THIRTY_MINUTES_MS)
    }

    fun setEnabled(enabled: Boolean) {
        if (closed || this.enabled == enabled) return
        this.enabled = enabled
        cancelScheduled()
        if (!enabled) {
            if (stage != Stage.NORMAL) coordinator.setContextState(ContextSource.LONELINESS, null)
            stage = Stage.NORMAL
            selfTalkManager.setLonelinessMode(LonelinessMode.NORMAL)
        } else {
            lastInteraction = clock.instant()
            scheduleAfter(THIRTY_MINUTES_MS)
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        cancelScheduled()
    }

    private fun refreshAndSchedule() {
        val elapsedMs = Duration.between(lastInteraction, clock.instant()).toMillis().coerceAtLeast(0L)
        val newStage = when {
            elapsedMs < THIRTY_MINUTES_MS -> Stage.NORMAL
            elapsedMs < SIXTY_MINUTES_MS -> Stage.LONELY_1
            elapsedMs < ONE_TWENTY_MINUTES_MS -> Stage.LONELY_2
            elapsedMs < ONE_EIGHTY_MINUTES_MS -> Stage.LONELY_3
            else -> Stage.SLEEPY
        }
        if (newStage != stage) {
            stage = newStage
            coordinator.setContextState(
                ContextSource.LONELINESS,
                when (newStage) {
                    Stage.NORMAL -> null
                    Stage.LONELY_1 -> Expression.LONELY_1
                    Stage.LONELY_2 -> Expression.LONELY_2
                    Stage.LONELY_3 -> Expression.LONELY_3
                    Stage.SLEEPY -> Expression.SLEEPY
                },
            )
            selfTalkManager.setLonelinessMode(
                when (newStage) {
                    Stage.NORMAL -> LonelinessMode.NORMAL
                    Stage.LONELY_1 -> LonelinessMode.FASTER
                    Stage.LONELY_2 -> LonelinessMode.SIGH
                    Stage.LONELY_3 -> LonelinessMode.SHORT
                    Stage.SLEEPY -> LonelinessMode.SLEEPY
                },
            )
        }
        val nextThreshold = listOf(
            THIRTY_MINUTES_MS,
            SIXTY_MINUTES_MS,
            ONE_TWENTY_MINUTES_MS,
            ONE_EIGHTY_MINUTES_MS,
        ).firstOrNull { it > elapsedMs }
        if (nextThreshold != null) scheduleAfter(nextThreshold - elapsedMs)
    }

    private fun scheduleAfter(delayMs: Long) {
        val callbackGeneration = ++generation
        val scheduled = scheduler.schedule(delayMs) {
            if (closed || !enabled || callbackGeneration != generation) return@schedule
            task = null
            refreshAndSchedule()
        }
        if (closed || !enabled || callbackGeneration != generation) {
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

    companion object {
        private const val THIRTY_MINUTES_MS = 30 * 60_000L
        private const val SIXTY_MINUTES_MS = 60 * 60_000L
        private const val ONE_TWENTY_MINUTES_MS = 120 * 60_000L
        private const val ONE_EIGHTY_MINUTES_MS = 180 * 60_000L
    }
}
