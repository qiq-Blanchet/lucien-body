package com.luc.body.network

import com.luc.body.state.Expression
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.json.JSONObject
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
        server.close()
    }

    @Test
    fun fetchUsesDualHeadersAndParsesLatestState() {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """[{"expression":"happy","bubble_text":"在呢","bubble_style":"love","updated_at":"2026-07-29T12:00:00Z"}]""",
            ).build(),
        )

        val result = awaitResult { client.fetchLatest(it) }
        val request = server.takeRequest(1, TimeUnit.SECONDS)

        requireNotNull(request)
        assertEquals("/rest/v1/clawd_state", request.url.encodedPath)
        assertEquals("expression,bubble_text,bubble_style,updated_at", request.url.queryParameter("select"))
        assertEquals("updated_at.desc", request.url.queryParameter("order"))
        assertEquals("1", request.url.queryParameter("limit"))
        assertEquals("test-key", request.headers["apikey"])
        assertEquals("Bearer test-key", request.headers["Authorization"])
        assertEquals("application/json", request.headers["Accept"])
        assertEquals(Expression.HAPPY, result.getOrThrow()?.expression)
        assertEquals("在呢", result.getOrThrow()?.bubbleText)
        assertEquals("2026-07-29T12:00:00Z", result.getOrThrow()?.updatedAt)
    }

    @Test
    fun fetchReturnsNullForAnEmptyArray() {
        server.enqueue(MockResponse.Builder().code(200).body("[]").build())

        assertNull(awaitResult { client.fetchLatest(it) }.getOrThrow())
    }

    @Test
    fun fetchReturnsFailureForInvalidJson() {
        server.enqueue(MockResponse.Builder().code(200).body("not-json").build())

        assertTrue(awaitResult { client.fetchLatest(it) }.isFailure)
    }

    @Test
    fun fetchMapsUnknownExpressionToIdle() {
        server.enqueue(
            MockResponse.Builder().code(200).body(
                """[{"expression":"excited","bubble_text":null,"bubble_style":"normal","updated_at":"2026-07-29T12:00:00Z"}]""",
            ).build(),
        )

        assertEquals(Expression.IDLE, awaitResult { client.fetchLatest(it) }.getOrThrow()?.expression)
    }

    @Test
    fun postUsesTheProvidedUuidAndRequiredHeaders() {
        val eventId = UUID.randomUUID()
        server.enqueue(MockResponse.Builder().code(201).build())

        assertTrue(awaitResult { client.postTap(eventId, it) }.isSuccess)
        val request = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val body = JSONObject(requireNotNull(request.body).utf8())

        assertEquals("POST", request.method)
        assertEquals("/rest/v1/clawd_events", request.url.encodedPath)
        assertEquals(eventId.toString(), body.getString("id"))
        assertEquals("tap", body.getString("event_type"))
        assertEquals(0, body.getJSONObject("payload").length())
        assertEquals("test-key", request.headers["apikey"])
        assertEquals("Bearer test-key", request.headers["Authorization"])
        assertTrue(request.headers["Content-Type"].orEmpty().startsWith("application/json"))
        assertEquals("return=minimal", request.headers["Prefer"])
    }

    @Test
    fun postRetriesOne503WithTheSameUuidAndBody() {
        val eventId = UUID.randomUUID()
        server.enqueue(MockResponse.Builder().code(503).build())
        server.enqueue(MockResponse.Builder().code(201).build())

        assertTrue(awaitResult { client.postTap(eventId, it) }.isSuccess)
        val first = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))
        val second = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS))

        val firstBody = requireNotNull(first.body).utf8()
        val secondBody = requireNotNull(second.body).utf8()
        assertEquals(firstBody, secondBody)
        assertEquals(eventId.toString(), JSONObject(secondBody).getString("id"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun getFailureDoesNotRetryImmediately() {
        server.enqueue(MockResponse.Builder().code(503).build())

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
