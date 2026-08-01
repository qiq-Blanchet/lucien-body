package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.Expression
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PollingLoopTest {
    private lateinit var server: MockWebServer
    private lateinit var scheduler: FakeScheduler
    private lateinit var states: MutableList<Expression>
    private lateinit var loop: PollingLoop

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scheduler = FakeScheduler()
        states = mutableListOf()
        loop = PollingLoop(
            SupabaseClient(SupabaseConfig(server.url("/").toString().removeSuffix("/"), "test-key")),
            scheduler,
        ) { states += it.expression }
    }

    @After
    fun tearDown() {
        loop.stop()
        server.close()
    }

    @Test
    fun startFetchesImmediatelyAndIsIdempotentUntilCompletion() {
        server.enqueue(stateResponse("happy"))

        loop.start()
        loop.start()

        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        await { scheduler.activeTaskCount == 1 }
        assertEquals(listOf(Expression.HAPPY), states)
        assertEquals(1, server.requestCount)
        assertEquals(5_000L, scheduler.nextDelayMs)
    }

    @Test
    fun eachCompletedFetchSchedulesExactlyOneNextFetchWithoutCatchUp() {
        server.enqueue(stateResponse("happy"))
        server.enqueue(stateResponse("sleepy"))

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        await { scheduler.activeTaskCount == 1 }
        scheduler.runNext()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        await { scheduler.activeTaskCount == 1 }

        assertEquals(listOf(Expression.HAPPY, Expression.SLEEPY), states)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun stopCancelsTheScheduledPollAndPreventsAnotherRequest() {
        server.enqueue(stateResponse("happy"))

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        await { scheduler.activeTaskCount == 1 }
        loop.stop()
        scheduler.runAllEvenIfCanceled()

        assertEquals(1, server.requestCount)
        assertEquals(0, scheduler.activeTaskCount)
        assertTrue(scheduler.allCanceled)
    }

    @Test
    fun stopDuringAnActiveFetchDoesNotScheduleAnotherPoll() {
        server.enqueue(
            MockResponse.Builder().code(200).body(stateJson("happy")).bodyDelay(10, TimeUnit.SECONDS).build(),
        )

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        loop.stop()
        Thread.sleep(100)

        assertEquals(0, scheduler.activeTaskCount)
        assertFalse(scheduler.hasTasks)
    }

    private fun stateResponse(expression: String): MockResponse =
        MockResponse.Builder().code(200).body(stateJson(expression)).build()

    private fun stateJson(expression: String): String =
        """[{"expression":"$expression","bubble_text":null,"bubble_style":"normal","updated_at":"2026-07-29T12:00:00Z"}]"""

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(10)
        check(condition()) { "Condition was not met" }
    }

    private class FakeScheduler : DelayScheduler {
        private data class Scheduled(val delayMs: Long, val action: () -> Unit, var canceled: Boolean = false)

        private val tasks = mutableListOf<Scheduled>()
        val activeTaskCount: Int get() = tasks.count { !it.canceled }
        val nextDelayMs: Long get() = tasks.first { !it.canceled }.delayMs
        val hasTasks: Boolean get() = tasks.isNotEmpty()
        val allCanceled: Boolean get() = tasks.all { it.canceled }

        override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
            val task = Scheduled(delayMs, action)
            tasks += task
            return Cancelable { task.canceled = true }
        }

        fun runNext() {
            val task = tasks.first { !it.canceled }
            task.canceled = true
            task.action()
        }

        fun runAllEvenIfCanceled() {
            tasks.toList().forEach { it.action() }
        }
    }
}
