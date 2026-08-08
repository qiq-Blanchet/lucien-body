package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventBatcherTest {
    private lateinit var server: MockWebServer
    private lateinit var scheduler: FakeScheduler
    private lateinit var batcher: EventBatcher

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        scheduler = FakeScheduler()
        batcher = EventBatcher(
            SupabaseClient(SupabaseConfig(server.url("/").toString().removeSuffix("/"), "test-key")),
            scheduler,
        )
    }

    @After
    fun tearDown() {
        batcher.close()
        server.shutdown()
    }

    @Test
    fun thirdEventFlushesOneJsonArrayImmediately() {
        server.enqueue(MockResponse().setResponseCode(201))

        batcher.enqueue(ClawdEvent("tap"))
        batcher.enqueue(ClawdEvent("app_foreground", mapOf("package" to "com.example.music")))
        assertNull(server.takeRequest(100, TimeUnit.MILLISECONDS))
        batcher.enqueue(ClawdEvent("double_tap"))

        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val rows = JSONArray(request.body.readUtf8())
        assertEquals(3, rows.length())
        assertEquals("tap", rows.getJSONObject(0).getString("event_type"))
        assertEquals(
            "com.example.music",
            rows.getJSONObject(1).getJSONObject("payload").getString("package"),
        )
        assertEquals("test-key", request.headers["apikey"])
        assertEquals("Bearer test-key", request.headers["Authorization"])
        assertTrue(scheduler.tasks.all { it.canceled })
    }

    @Test
    fun pendingEventsFlushAfterTenSeconds() {
        server.enqueue(MockResponse().setResponseCode(201))
        batcher.enqueue(ClawdEvent("tap"))

        assertEquals(10_000L, scheduler.nextActive().delayMs)
        scheduler.runNext()

        val rows = JSONArray(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8())
        assertEquals(1, rows.length())
    }

    @Test
    fun failedBatchIsRetainedForTheNextTimedFlush() {
        server.enqueue(MockResponse().setResponseCode(503))
        batcher.enqueue(ClawdEvent("tap"))
        scheduler.runNext()

        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        waitUntil { scheduler.activeCount == 1 }
        server.enqueue(MockResponse().setResponseCode(201))
        scheduler.runNext()

        val retried = JSONArray(requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).body.readUtf8())
        assertEquals("tap", retried.getJSONObject(0).getString("event_type"))
    }

    @Test
    fun completionBeforePostReturnsDoesNotLeaveAStaleActiveCall() {
        val executor = DirectExecutorService()
        val httpClient = OkHttpClient.Builder().dispatcher(Dispatcher(executor)).build()
        val synchronousBatcher = EventBatcher(
            SupabaseClient(
                SupabaseConfig(server.url("/").toString().removeSuffix("/"), "test-key"),
                httpClient,
            ),
            FakeScheduler(),
        )
        try {
            server.enqueue(MockResponse().setResponseCode(201))
            server.enqueue(MockResponse().setResponseCode(201))

            repeat(6) { synchronousBatcher.enqueue(ClawdEvent("tap")) }

            requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            assertEquals(2, server.requestCount)
        } finally {
            synchronousBatcher.close()
            httpClient.connectionPool.evictAll()
            executor.shutdownNow()
        }
    }

    @Test
    fun closeCancelsTheTimerAndDiscardsItsStaleCallback() {
        batcher.enqueue(ClawdEvent("tap"))
        batcher.close()

        scheduler.runAllEvenIfCanceled()

        assertNull(server.takeRequest(200, TimeUnit.MILLISECONDS))
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!condition() && System.nanoTime() < deadline) Thread.yield()
        check(condition()) { "Condition was not met before timeout" }
    }

    private class FakeScheduler : DelayScheduler {
        data class Task(val delayMs: Long, val action: () -> Unit, var canceled: Boolean = false)

        val tasks = mutableListOf<Task>()
        val activeCount: Int get() = synchronized(tasks) { tasks.count { !it.canceled } }

        override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
            val task = Task(delayMs, action)
            synchronized(tasks) { tasks += task }
            return Cancelable { synchronized(tasks) { task.canceled = true } }
        }

        fun nextActive(): Task = synchronized(tasks) { tasks.first { !it.canceled } }

        fun runNext() {
            val task = nextActive()
            task.canceled = true
            task.action()
        }

        fun runAllEvenIfCanceled() {
            synchronized(tasks) { tasks.toList() }.forEach { it.action() }
        }
    }

    private class DirectExecutorService : AbstractExecutorService() {
        @Volatile
        private var shutdown = false

        override fun execute(command: Runnable) = command.run()
        override fun shutdown() { shutdown = true }
        override fun shutdownNow(): MutableList<Runnable> = mutableListOf<Runnable>().also { shutdown = true }
        override fun isShutdown(): Boolean = shutdown
        override fun isTerminated(): Boolean = shutdown
        override fun awaitTermination(timeout: Long, unit: TimeUnit): Boolean = shutdown
    }
}
