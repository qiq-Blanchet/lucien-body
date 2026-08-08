package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.Expression
import com.luc.body.state.RemoteState
import java.util.concurrent.AbstractExecutorService
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseRealtimeClientTest {
    private lateinit var server: MockWebServer
    private lateinit var httpClient: OkHttpClient
    private lateinit var scheduler: FakeScheduler
    private lateinit var realtime: SupabaseRealtimeClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        httpClient = OkHttpClient()
        scheduler = FakeScheduler()
    }

    @After
    fun tearDown() {
        if (::realtime.isInitialized) realtime.close()
        httpClient.dispatcher.executorService.shutdownNow()
        httpClient.connectionPool.evictAll()
        server.shutdown()
    }

    @Test
    fun joinsWithV1ProtocolThenDeliversUpdateAndHeartbeats() {
        val states = CopyOnWriteArrayList<RemoteState>()
        val stateDelivered = CountDownLatch(1)
        val heartbeatReceived = CountDownLatch(1)
        val joinReceived = CountDownLatch(1)
        val messages = CopyOnWriteArrayList<JSONObject>()
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val message = JSONObject(text)
                        messages += message
                        when (message.getString("event")) {
                            "phx_join" -> {
                                joinReceived.countDown()
                                webSocket.send(joinReply(message.getString("ref")))
                                webSocket.send(stateChange())
                            }
                            "heartbeat" -> {
                                webSocket.send(heartbeatReply(message.getString("ref")))
                                heartbeatReceived.countDown()
                            }
                        }
                    }
                },
            ),
        )
        realtime = newRealtime {
            states += it
            stateDelivered.countDown()
        }

        realtime.start()

        assertTrue(joinReceived.await(2, TimeUnit.SECONDS))
        assertTrue(stateDelivered.await(2, TimeUnit.SECONDS))
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/realtime/v1/websocket", request.requestUrl?.encodedPath)
        assertEquals("test-key", request.requestUrl?.queryParameter("apikey"))
        assertEquals("1.0.0", request.requestUrl?.queryParameter("vsn"))
        assertEquals("test-key", request.headers["apikey"])
        assertEquals("Bearer test-key", request.headers["Authorization"])

        val join = messages.first()
        assertEquals("realtime:clawd_state", join.getString("topic"))
        assertEquals(join.getString("ref"), join.getString("join_ref"))
        assertFalse(join.getJSONObject("payload").has("access_token"))
        val change = join.getJSONObject("payload").getJSONObject("config")
            .getJSONArray("postgres_changes").getJSONObject(0)
        assertEquals("UPDATE", change.getString("event"))
        assertEquals("public", change.getString("schema"))
        assertEquals("clawd_state", change.getString("table"))

        assertEquals(Expression.LOVE, states.single().expression)
        assertEquals(0.9, states.single().valence ?: -1.0, 0.0)
        assertEquals(6, states.single().heat)
        assertEquals(25_000L, scheduler.activeTasks().single().delayMs)
        scheduler.runNext(25_000L)
        assertTrue(heartbeatReceived.await(2, TimeUnit.SECONDS))
        val heartbeat = messages.last()
        assertEquals("phoenix", heartbeat.getString("topic"))
        assertFalse(heartbeat.has("join_ref"))
    }

    @Test
    fun serverCloseMessageStartsHttpFallbackAndSchedulesReconnect() {
        val joined = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val message = JSONObject(text)
                        if (message.optString("event") == "phx_join") {
                            webSocket.send(joinReply(message.getString("ref")))
                            joined.countDown()
                            webSocket.send(
                                JSONObject()
                                    .put("topic", "realtime:clawd_state")
                                    .put("event", "phx_close")
                                    .put("payload", JSONObject())
                                    .put("ref", JSONObject.NULL)
                                    .toString(),
                            )
                        }
                    }
                },
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"expression":"happy","bubble_text":null,"bubble_style":"normal","updated_at":"2026-08-04T00:00:00Z"}]""",
            ),
        )
        val fallbackState = CountDownLatch(1)
        realtime = newRealtime { fallbackState.countDown() }

        realtime.start()

        assertTrue(joined.await(2, TimeUnit.SECONDS))
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val fallback = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/rest/v1/clawd_state", fallback.requestUrl?.encodedPath)
        assertTrue(fallbackState.await(2, TimeUnit.SECONDS))
        assertTrue(scheduler.activeTasks().any { it.delayMs == 1_000L })
    }

    @Test
    fun reconnectBackoffUsesTheSpecifiedSequenceAndCapsAtThirtySeconds() {
        assertEquals(
            listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L, 30_000L),
            (0..6).map { SupabaseRealtimeClient.reconnectDelayMs(it) },
        )
    }

    @Test
    fun missingJoinReplyStartsFallbackAfterTenSeconds() {
        val joinReceived = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        if (JSONObject(text).optString("event") == "phx_join") joinReceived.countDown()
                    }
                },
            ),
        )
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"expression":"happy","bubble_text":null,"bubble_style":"normal","updated_at":"2026-08-04T00:00:00Z"}]""",
            ),
        )
        val fallbackState = CountDownLatch(1)
        realtime = newRealtime { fallbackState.countDown() }

        realtime.start()

        assertTrue(joinReceived.await(2, TimeUnit.SECONDS))
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertTrue(scheduler.activeTasks().any { it.delayMs == 10_000L })
        scheduler.runNext(10_000L)
        val fallback = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/rest/v1/clawd_state", fallback.requestUrl?.encodedPath)
        assertTrue(fallbackState.await(2, TimeUnit.SECONDS))
        assertTrue(scheduler.activeTasks().any { it.delayMs == 1_000L })
    }

    @Test
    fun missingHeartbeatReplyStartsFallbackOnTheNextHeartbeatTick() {
        val joined = CountDownLatch(1)
        val heartbeatReceived = CountDownLatch(1)
        server.enqueue(
            MockResponse().withWebSocketUpgrade(
                object : WebSocketListener() {
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        val message = JSONObject(text)
                        when (message.optString("event")) {
                            "phx_join" -> {
                                webSocket.send(joinReply(message.getString("ref")))
                                joined.countDown()
                            }
                            "heartbeat" -> heartbeatReceived.countDown()
                        }
                    }
                },
            ),
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        realtime = newRealtime { }

        realtime.start()

        assertTrue(joined.await(2, TimeUnit.SECONDS))
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        waitUntil { scheduler.activeTasks().any { it.delayMs == 25_000L } }
        scheduler.runNext(25_000L)
        assertTrue(heartbeatReceived.await(2, TimeUnit.SECONDS))
        waitUntil { scheduler.activeTasks().any { it.delayMs == 25_000L } }
        scheduler.runNext(25_000L)

        val fallback = requireNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        assertEquals("/rest/v1/clawd_state", fallback.requestUrl?.encodedPath)
        assertTrue(scheduler.activeTasks().any { it.delayMs == 1_000L })
    }

    @Test
    fun failureBeforeNewWebSocketReturnsStillStartsFallbackAndReconnect() {
        val directExecutor = DirectExecutorService()
        httpClient = OkHttpClient.Builder().dispatcher(Dispatcher(directExecutor)).build()
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"expression":"happy","bubble_text":null,"bubble_style":"normal","updated_at":"2026-08-04T00:00:00Z"}]""",
            ),
        )
        val states = mutableListOf<Expression>()
        val config = SupabaseConfig(server.url("/").toString().removeSuffix("/"), "test-key")
        val supabase = SupabaseClient(config, httpClient)
        val polling = PollingLoop(supabase, scheduler, DIRECT_EXECUTOR) { states += it.expression }
        realtime = SupabaseRealtimeClient(config, httpClient, polling, scheduler, DIRECT_EXECUTOR) {
            states += it.expression
        }

        realtime.start()

        val websocketRequest = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val fallbackRequest = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals("/realtime/v1/websocket", websocketRequest.requestUrl?.encodedPath)
        assertEquals("/rest/v1/clawd_state", fallbackRequest.requestUrl?.encodedPath)
        assertEquals(listOf(Expression.HAPPY), states)
        assertTrue(scheduler.activeTasks().any { it.delayMs == 1_000L })
    }

    private fun newRealtime(onState: (RemoteState) -> Unit): SupabaseRealtimeClient {
        val config = SupabaseConfig(server.url("/").toString().removeSuffix("/"), "test-key")
        val supabase = SupabaseClient(config, httpClient)
        val polling = PollingLoop(supabase, scheduler, DIRECT_EXECUTOR, onState)
        return SupabaseRealtimeClient(config, httpClient, polling, scheduler, DIRECT_EXECUTOR, onState)
    }

    private fun joinReply(ref: String): String = JSONObject()
        .put("topic", "realtime:clawd_state")
        .put("event", "phx_reply")
        .put("payload", JSONObject().put("status", "ok").put("response", JSONObject()))
        .put("join_ref", ref)
        .put("ref", ref)
        .toString()

    private fun heartbeatReply(ref: String): String = JSONObject()
        .put("topic", "phoenix")
        .put("event", "phx_reply")
        .put("payload", JSONObject().put("status", "ok").put("response", JSONObject()))
        .put("ref", ref)
        .toString()

    private fun stateChange(): String = JSONObject()
        .put("topic", "realtime:clawd_state")
        .put("event", "postgres_changes")
        .put(
            "payload",
            JSONObject().put(
                "data",
                JSONObject()
                    .put("type", "UPDATE")
                    .put(
                        "record",
                        JSONObject()
                            .put("expression", "love")
                            .put("bubble_text", "在呢")
                            .put("bubble_style", "love")
                            .put("valence", 0.9)
                            .put("arousal", 0.7)
                            .put("heat", 6)
                            .put("updated_at", "2026-08-04T00:00:00Z"),
                    ),
            ),
        )
        .put("ref", JSONObject.NULL)
        .toString()

    private class FakeScheduler : DelayScheduler {
        data class Task(val delayMs: Long, val action: () -> Unit, var canceled: Boolean = false)

        private val tasks = mutableListOf<Task>()

        override fun schedule(delayMs: Long, action: () -> Unit): Cancelable {
            val task = Task(delayMs, action)
            synchronized(tasks) { tasks += task }
            return Cancelable { synchronized(tasks) { task.canceled = true } }
        }

        fun activeTasks(): List<Task> = synchronized(tasks) { tasks.filterNot(Task::canceled) }

        fun runNext(delayMs: Long) {
            val task = synchronized(tasks) { tasks.first { !it.canceled && it.delayMs == delayMs } }
            task.canceled = true
            task.action()
        }
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2)
        while (!condition() && System.nanoTime() < deadline) Thread.yield()
        check(condition()) { "Condition was not met before timeout" }
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

    private companion object {
        val DIRECT_EXECUTOR = Executor { it.run() }
    }
}
