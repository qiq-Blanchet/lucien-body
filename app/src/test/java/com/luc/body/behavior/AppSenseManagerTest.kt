package com.luc.body.behavior

import com.luc.body.state.Expression
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSenseManagerTest {
    @Test
    fun peekingExpiresAfterOneAnimationCycleAndFallsBackToIdle() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val manager = AppSenseManager(
            coordinator(sink, scheduler),
            scheduler,
            ForegroundPackageSource { "music.app" },
            AppSceneClassifier(AppPackageCategories(setOf("launcher"))),
            AppEventSink { _, _ -> },
        )

        manager.start()
        scheduler.advanceBy(AppSenseManager.POLL_INTERVAL_MS)
        assertEquals(Expression.PEEKING, sink.states.last().expression)

        scheduler.advanceBy(AppSenseManager.PEEKING_DURATION_MS - 1L)
        assertEquals(Expression.PEEKING, sink.states.last().expression)
        scheduler.advanceBy(1L)
        assertEquals(Expression.IDLE, sink.states.last().expression)
    }

    @Test
    fun peekingKeepsTheExistingStuckPriority() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        val coordinator = coordinator(sink, scheduler)
        coordinator.setStuck(true)
        val manager = AppSenseManager(
            coordinator,
            scheduler,
            ForegroundPackageSource { "music.app" },
            AppSceneClassifier(AppPackageCategories(setOf("launcher"))),
            AppEventSink { _, _ -> },
        )

        manager.start()
        scheduler.advanceBy(AppSenseManager.POLL_INTERVAL_MS)

        assertEquals(Expression.STUCK, sink.states.last().expression)
    }

    @Test
    fun onlyDesktopOtherAndReturnToDesktopMappingsAreApplied() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        var foreground = "launcher"
        val events = mutableListOf<Pair<String, Map<String, String>>>()
        val manager = AppSenseManager(
            coordinator(sink, scheduler),
            scheduler,
            ForegroundPackageSource { foreground },
            AppSceneClassifier(AppPackageCategories(setOf("launcher"))),
            AppEventSink { type, payload -> events += type to payload },
        )

        manager.start()
        scheduler.advanceBy(AppSenseManager.POLL_INTERVAL_MS)
        assertEquals(Expression.IDLE, sink.states.last().expression)

        foreground = "music.app"
        scheduler.advanceBy(AppSenseManager.POLL_INTERVAL_MS)
        assertEquals(Expression.PEEKING, sink.states.last().expression)

        foreground = "game.app"
        scheduler.advanceBy(AppSenseManager.POLL_INTERVAL_MS)
        assertEquals(Expression.PEEKING, sink.states.last().expression)

        foreground = "launcher"
        scheduler.advanceBy(AppSenseManager.POLL_INTERVAL_MS)
        assertEquals(Expression.WAVING, sink.states.last().expression)
        assertEquals(
            listOf("launcher", "music.app", "game.app", "launcher"),
            events.map { it.second.getValue(AppSenseManager.PACKAGE_PAYLOAD_KEY) },
        )
        assertEquals(List(4) { AppSenseManager.APP_FOREGROUND_EVENT }, events.map { it.first })
    }

    @Test
    fun unchangedPackageIsNotReportedAgainAndCloseCancelsPolling() {
        val clock = MutableClock()
        val scheduler = BehaviorScheduler(clock)
        val sink = BehaviorSink()
        var reports = 0
        val manager = AppSenseManager(
            coordinator(sink, scheduler),
            scheduler,
            ForegroundPackageSource { "launcher" },
            AppSceneClassifier(AppPackageCategories(setOf("launcher"))),
            AppEventSink { _, _ -> reports += 1 },
        )

        manager.start()
        scheduler.advanceBy(2 * AppSenseManager.POLL_INTERVAL_MS)
        assertEquals(1, reports)
        manager.close()
        scheduler.runAllEvenIfCanceled()
        assertEquals(1, reports)
        assertEquals(0, scheduler.activeTaskCount)
    }
}
