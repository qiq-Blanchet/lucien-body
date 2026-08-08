package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.Expression
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PollingLoopTest {
    private lateinit var server: MockWebServer
    private lateinit var scheduler: FakeScheduler
    private lateinit var ownerExecutor: QueuedExecutor
    private lateinit var states: MutableList<Expression>
    private lateinit var loop: PollingLoop
    private lateinit var httpClient: OkHttpClient
    private lateinit var supabaseClient: SupabaseClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scheduler = FakeScheduler()
        ownerExecutor = QueuedExecutor()
        states = CopyOnWriteArrayList()
        httpClient = OkHttpClient()
        supabaseClient = SupabaseClient(
            SupabaseConfig(server.url("/").toString().removeSuffix("/"), "test-key"),
            httpClient,
        )
        loop = PollingLoop(
            supabaseClient,
            scheduler,
            ownerExecutor,
        ) { states += it.expression }
    }

    @After
    fun tearDown() {
        loop.stop()
        supabaseClient.cancelAll()
        httpClient.dispatcher.executorService.shutdownNow()
        httpClient.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun startFetchesImmediatelyAndIsIdempotentUntilCompletion() {
        server.enqueue(stateResponse("happy"))

        loop.start()
        loop.start()

        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        ownerExecutor.runNext()
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
        ownerExecutor.runNext()
        scheduler.runNext()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        ownerExecutor.runNext()

        assertEquals(listOf(Expression.HAPPY, Expression.SLEEPY), states)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun stopCancelsTheScheduledPollAndPreventsAnotherRequest() {
        server.enqueue(stateResponse("happy"))

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        ownerExecutor.runNext()
        loop.stop()
        scheduler.runAllEvenIfCanceled()

        assertEquals(1, server.requestCount)
        assertEquals(0, scheduler.activeTaskCount)
        assertTrue(scheduler.allCanceled)
    }

    @Test
    fun stopDuringAnActiveFetchDoesNotScheduleAnotherPoll() {
        server.enqueue(
            MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE),
        )

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        loop.stop()
        ownerExecutor.runNext()

        assertEquals(0, scheduler.activeTaskCount)
        assertFalse(scheduler.hasTasks)
    }

    @Test
    fun completionWaitsForTheOwnerExecutorBeforeDeliveringStateOrScheduling() {
        val ownerExecutor = QueuedExecutor()
        loop = PollingLoop(
            supabaseClient,
            scheduler,
            ownerExecutor,
        ) { states += it.expression }
        server.enqueue(stateResponse("happy"))

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        assertEquals(emptyList<Expression>(), states)
        assertEquals(0, scheduler.activeTaskCount)
        ownerExecutor.runNext()
        assertEquals(listOf(Expression.HAPPY), states)
        assertEquals(1, scheduler.activeTaskCount)
    }

    @Test
    fun staleCompletionAfterStopAndRestartCannotPolluteTheNewRun() {
        val ownerExecutor = QueuedExecutor()
        loop = PollingLoop(
            supabaseClient,
            scheduler,
            ownerExecutor,
        ) { states += it.expression }
        server.enqueue(stateResponse("happy"))
        server.enqueue(stateResponse("sleepy"))

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val oldCompletion = ownerExecutor.takeNext()
        loop.stop()
        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val currentCompletion = ownerExecutor.takeNext()

        oldCompletion.run()
        assertEquals(emptyList<Expression>(), states)
        assertEquals(0, scheduler.activeTaskCount)
        currentCompletion.run()

        assertEquals(listOf(Expression.SLEEPY), states)
        assertEquals(1, scheduler.activeTaskCount)
    }

    @Test
    fun directExecutorCompletionCanStopAndRestartWithoutDeadlockOrOldRunOutput() {
        val restartFinished = CountDownLatch(1)
        val currentRunDelivered = CountDownLatch(1)
        loop = PollingLoop(
            supabaseClient,
            scheduler,
            DIRECT_EXECUTOR,
        ) { state ->
            if (state.expression == Expression.HAPPY) {
                val restart = Thread {
                    loop.stop()
                    loop.start()
                    restartFinished.countDown()
                }
                restart.start()
                check(restartFinished.await(1, TimeUnit.SECONDS)) { "Restart deadlocked behind completion" }
                restart.join()
            } else {
                states += state.expression
                currentRunDelivered.countDown()
            }
        }
        server.enqueue(stateResponse("happy"))
        server.enqueue(stateResponse("sleepy"))

        loop.start()
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        check(currentRunDelivered.await(1, TimeUnit.SECONDS)) { "Current run did not complete" }

        assertEquals(listOf(Expression.SLEEPY), states)
    }

    private fun stateResponse(expression: String): MockResponse =
        MockResponse().setResponseCode(200).setBody(stateJson(expression))

    private fun stateJson(expression: String): String =
        """[{"expression":"$expression","bubble_text":null,"bubble_style":"normal","updated_at":"2026-07-29T12:00:00Z"}]"""

    private class FakeScheduler : DelayScheduler {
        private data class Scheduled(val delayMs: Long, val action: () -> Unit, var canceled: Boolean = false)

        private val lock = Any()
        private val tasks = mutableListOf<Scheduled>()
        val activeTaskCount: Int get() = synchronized(lock) { tasks.count { !it.canceled } }
        val nextDelayMs: Long get() = synchronized(lock) { tasks.first { !it.canceled }.delayMs }
        val hasTasks: Boolean get() = synchronized(lock) { tasks.isNotEmpty() }
        val allCanceled: Boolean get() = synchronized(lock) { tasks.all { it.canceled } }

        override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
            val task = Scheduled(delayMs, action)
            synchronized(lock) { tasks += task }
            return Cancelable {
                synchronized(lock) { task.canceled = true }
            }
        }

        fun runNext() {
            val action = synchronized(lock) {
                val task = tasks.first { !it.canceled }
                task.canceled = true
                task.action
            }
            action()
        }

        fun runAllEvenIfCanceled() {
            val actions = synchronized(lock) { tasks.map { it.action } }
            actions.forEach { it() }
        }
    }

    private class QueuedExecutor : Executor {
        private val tasks = LinkedBlockingQueue<Runnable>()

        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runNext() {
            takeNext().run()
        }

        fun takeNext(): Runnable = checkNotNull(tasks.poll(3, TimeUnit.SECONDS)) { "Completion was not queued" }
    }

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
