package com.luc.body.network

import com.luc.body.state.BubbleStyle
import com.luc.body.state.Expression
import com.luc.body.state.RemoteState
import java.io.IOException
import java.util.UUID
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

class SupabaseClient(
    private val config: SupabaseConfig,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val lifecycleLock = Any()
    private var generation = 0L

    fun cancelAll() {
        synchronized(lifecycleLock) {
            generation += 1
        }
        httpClient.dispatcher.cancelAll()
    }

    fun fetchLatest(callback: (Result<RemoteState?>) -> Unit): Call {
        val url = config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("rest/v1/clawd_state")
            .addQueryParameter(
                "select",
                "expression,bubble_text,bubble_style,valence,arousal,heat,updated_at",
            )
            .addQueryParameter("order", "updated_at.desc")
            .addQueryParameter("limit", "1")
            .build()
        return synchronized(lifecycleLock) {
            val requestGeneration = generation
            val call = httpClient.newCall(request(url.toString()).build())
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (isCurrent(requestGeneration)) callback(Result.failure(e))
                }

                override fun onResponse(call: Call, response: Response) {
                    val result = runCatching {
                        response.use {
                            check(it.isSuccessful) { "HTTP ${it.code}" }
                            parseStateResponse(checkNotNull(it.body).string())
                        }
                    }
                    if (isCurrent(requestGeneration)) callback(result)
                }
            })
            call
        }
    }

    fun postTap(eventId: UUID, callback: (Result<Unit>) -> Unit): Call {
        @Suppress("UNUSED_VARIABLE")
        val ignoredEventId = eventId
        return postEvents(listOf(ClawdEvent("tap")), callback)
    }

    fun postEvents(events: List<ClawdEvent>, callback: (Result<Unit>) -> Unit): Call {
        require(events.isNotEmpty()) { "At least one event is required" }
        val body = JSONArray().apply {
            events.forEach { event ->
                put(
                    JSONObject()
                        .put("event_type", event.eventType)
                        .put("payload", JSONObject(event.payload)),
                )
            }
        }.toString()
        return synchronized(lifecycleLock) {
            enqueueEventsLocked(body, callback, generation)
        }
    }

    private fun enqueueEventsLocked(
        body: String,
        callback: (Result<Unit>) -> Unit,
        requestGeneration: Long,
    ): Call {
        val call = httpClient.newCall(
            request("${config.baseUrl}/rest/v1/clawd_events")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .build(),
        )
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (isCurrent(requestGeneration)) callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val successful = response.use { it.isSuccessful }
                if (successful) {
                    if (isCurrent(requestGeneration)) callback(Result.success(Unit))
                } else {
                    if (isCurrent(requestGeneration)) callback(Result.failure(IOException("HTTP response was unsuccessful")))
                }
            }
        })
        return call
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lifecycleLock) {
        generation == requestGeneration
    }

    private fun request(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", config.publishableKey)
        .header("Authorization", "Bearer ${config.publishableKey}")
        .header("Accept", "application/json")

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}

data class ClawdEvent(
    val eventType: String,
    val payload: Map<String, Any?> = emptyMap(),
) {
    init {
        require(eventType.isNotBlank()) { "Event type must not be blank" }
    }
}

internal fun parseStateResponse(body: String): RemoteState? {
    val rows = JSONArray(body)
    return if (rows.length() == 0) null else parseRemoteState(rows.getJSONObject(0))
}

internal fun parseRemoteState(row: JSONObject): RemoteState = RemoteState(
    expression = Expression.fromRemote(row.optNullableString("expression")),
    bubbleText = row.optNullableString("bubble_text"),
    bubbleStyle = BubbleStyle.fromRemote(row.optNullableString("bubble_style")),
    updatedAt = row.getString("updated_at"),
    valence = row.optNullableNumber("valence")?.toDouble(),
    arousal = row.optNullableNumber("arousal")?.toDouble(),
    heat = row.optNullableNumber("heat")?.toInt(),
)

private fun JSONObject.optNullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else getString(name)

private fun JSONObject.optNullableNumber(name: String): Number? =
    if (!has(name) || isNull(name)) null else get(name) as? Number
