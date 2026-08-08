package com.luc.body.network

import com.luc.body.state.Expression
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SupabaseClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: SupabaseClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = SupabaseClient(SupabaseConfig(server.url("/").toString().removeSuffix("/"), "test-key"))
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun fetchUsesDualHeadersAndParsesLatestState() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"expression":"happy","bubble_text":"在呢","bubble_style":"love","valence":0.8,"arousal":0.4,"heat":7,"updated_at":"2026-07-29T12:00:00Z"}]""",
            ),
        )

        val result = awaitResult { client.fetchLatest(it) }
        val request = server.takeRequest(1, TimeUnit.SECONDS)

        requireNotNull(request)
        assertEquals("/rest/v1/clawd_state", request.requestUrl?.encodedPath)
        assertEquals(
            "expression,bubble_text,bubble_style,valence,arousal,heat,updated_at",
            request.requestUrl?.queryParameter("select"),
        )
        assertEquals("updated_at.desc", request.requestUrl?.queryParameter("order"))
        assertEquals("1", request.requestUrl?.queryParameter("limit"))
        assertEquals("test-key", request.headers["apikey"])
        assertEquals("Bearer test-key", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals(Expression.HAPPY, result.getOrThrow()?.expression)
        assertEquals("在呢", result.getOrThrow()?.bubbleText)
        assertEquals("2026-07-29T12:00:00Z", result.getOrThrow()?.updatedAt)
        assertEquals(0.8, result.getOrThrow()?.valence ?: -1.0, 0.0)
        assertEquals(0.4, result.getOrThrow()?.arousal ?: -1.0, 0.0)
        assertEquals(7, result.getOrThrow()?.heat)
    }

    @Test
    fun fetchReturnsNullForAnEmptyArray() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        assertNull(awaitResult { client.fetchLatest(it) }.getOrThrow())
    }

    @Test
    fun fetchReturnsFailureForInvalidJson() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("not-json"))

        assertTrue(awaitResult { client.fetchLatest(it) }.isFailure)
    }

    @Test
    fun fetchMapsUnknownExpressionToIdle() {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """[{"expression":"excited","bubble_text":null,"bubble_style":"normal","updated_at":"2026-07-29T12:00:00Z"}]""",
            ),
        )

        assertEquals(Expression.IDLE, awaitResult { client.fetchLatest(it) }.getOrThrow()?.expression)
    }

    @Test
    fun postUsesArrayPayloadAndRequiredHeaders() {
        val eventId = UUID.randomUUID()
        server.enqueue(MockResponse().setResponseCode(201))

        assertTrue(awaitResult { client.postTap(eventId, it) }.isSuccess)
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val rows = JSONArray(request.body.readUtf8())
        val body = rows.getJSONObject(0)

        assertEquals("POST", request.method)
        assertEquals("/rest/v1/clawd_events", request.requestUrl?.encodedPath)
        assertEquals(1, rows.length())
        assertFalse(body.has("id"))
        assertEquals("tap", body.getString("event_type"))
        assertEquals(0, body.getJSONObject("payload").length())
        assertEquals("test-key", request.headers["apikey"])
        assertEquals("Bearer test-key", request.headers["Authorization"])
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        assertEquals("return=minimal", request.headers["Prefer"])
    }

    @Test
    fun failedPostDoesNotLaunchAnUntrackedRetry() {
        val eventId = UUID.randomUUID()
        server.enqueue(MockResponse().setResponseCode(503))

        assertTrue(awaitResult { client.postTap(eventId, it) }.isFailure)
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertNull(server.takeRequest(300, TimeUnit.MILLISECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun getFailureDoesNotRetryImmediately() {
        server.enqueue(MockResponse().setResponseCode(503))

        assertTrue(awaitResult { client.fetchLatest(it) }.isFailure)
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun configurationValidationRejectsNonSupabaseValuesWithoutEchoingThem() {
        val invalidUrl = "http://example.invalid"
        val invalidKey = "secret-value"

        val urlError = runCatching { SupabaseConfig(invalidUrl, "sb_publishable_test").requireValid() }.exceptionOrNull()
        val keyError = runCatching { SupabaseConfig("https://project.supabase.co", invalidKey).requireValid() }.exceptionOrNull()

        requireNotNull(urlError)
        requireNotNull(keyError)
        assertFalse(urlError.message.orEmpty().contains(invalidUrl))
        assertFalse(keyError.message.orEmpty().contains(invalidKey))
    }

    private fun <T> awaitResult(start: (((Result<T>) -> Unit) -> Unit)): Result<T> {
        val latch = CountDownLatch(1)
        var result: Result<T>? = null
        start {
            result = it
            latch.countDown()
        }
        check(latch.await(3, TimeUnit.SECONDS)) { "Timed out waiting for HTTP callback" }
        return requireNotNull(result)
    }
}
