package com.luc.body.behavior

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.StateCoordinator
import com.luc.body.state.UiSink
import com.luc.body.state.VisibleState
import com.luc.body.state.Expression
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

internal class MutableClock(
    private var current: Instant = Instant.parse("2026-08-04T00:00:00Z"),
    private val currentZone: ZoneId = ZoneOffset.UTC,
) : Clock() {
    override fun instant(): Instant = current

    override fun getZone(): ZoneId = currentZone

    override fun withZone(zone: ZoneId): Clock = MutableClock(current, zone)

    fun advanceBy(delayMs: Long) {
        current = current.plusMillis(delayMs)
    }

    fun moveTo(instant: Instant) {
        current = instant
    }
}

internal class BehaviorScheduler(
    private val clock: MutableClock,
) : DelayScheduler {
    private data class Scheduled(
        val dueAt: Instant,
        val action: () -> Unit,
        var canceled: Boolean = false,
    )

    private val tasks = mutableListOf<Scheduled>()

    val activeTaskCount: Int
        get() = tasks.count { !it.canceled }

    val nextDelayMs: Long?
        get() = tasks.filterNot { it.canceled }
            .minByOrNull { it.dueAt }
            ?.let { Duration.between(clock.instant(), it.dueAt).toMillis() }

    override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
        val scheduled = Scheduled(clock.instant().plusMillis(delayMs), action)
        tasks += scheduled
        return Cancelable { scheduled.canceled = true }
    }

    fun advanceBy(delayMs: Long) {
        val target = clock.instant().plusMillis(delayMs)
        while (true) {
            val next = tasks
                .filter { !it.canceled && !it.dueAt.isAfter(target) }
                .minByOrNull { it.dueAt }
                ?: break
            tasks.remove(next)
            clock.moveTo(next.dueAt)
            next.action()
        }
        clock.moveTo(target)
    }

    fun runAllEvenIfCanceled() {
        tasks.toList().forEach { it.action() }
    }
}

internal class BehaviorSink : UiSink {
    val states = mutableListOf<VisibleState>()

    override fun render(state: VisibleState) {
        states += state
    }
}

internal fun coordinator(sink: UiSink, scheduler: DelayScheduler): StateCoordinator =
    StateCoordinator(sink, scheduler) { Expression.HAPPY }
