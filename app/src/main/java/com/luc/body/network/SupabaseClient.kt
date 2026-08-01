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
    fun fetchLatest(callback: (Result<RemoteState?>) -> Unit): Call {
        val url = config.baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("rest/v1/clawd_state")
            .addQueryParameter("select", "expression,bubble_text,bubble_style,updated_at")
            .addQueryParameter("order", "updated_at.desc")
            .addQueryParameter("limit", "1")
            .build()
        val call = httpClient.newCall(request(url.toString()).build())
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                callback(Result.failure(e))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        check(it.isSuccessful) { "HTTP ${it.code}" }
                        parseState(it.body.string())
                    }
                }
                callback(result)
            }
        })
        return call
    }

    fun postTap(eventId: UUID, callback: (Result<Unit>) -> Unit): Call {
        val body = JSONObject()
            .put("id", eventId.toString())
            .put("event_type", "tap")
            .put("payload", JSONObject())
            .toString()
        return enqueueTap(body, callback, retry = false)
    }

    private fun enqueueTap(body: String, callback: (Result<Unit>) -> Unit, retry: Boolean): Call {
        val call = httpClient.newCall(
            request("${config.baseUrl}/rest/v1/clawd_events")
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .header("Content-Type", "application/json")
                .header("Prefer", "return=minimal")
                .build(),
        )
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!retry && !call.isCanceled()) {
                    enqueueTap(body, callback, retry = true)
                } else {
                    callback(Result.failure(e))
                }
            }

            override fun onResponse(call: Call, response: Response) {
                val successful = response.use { it.isSuccessful }
                if (successful) {
                    callback(Result.success(Unit))
                } else if (!retry) {
                    enqueueTap(body, callback, retry = true)
                } else {
                    callback(Result.failure(IOException("HTTP response was unsuccessful")))
                }
            }
        })
        return call
    }

    private fun request(url: String): Request.Builder = Request.Builder()
        .url(url)
        .header("apikey", config.publishableKey)
        .header("Authorization", "Bearer ${config.publishableKey}")
        .header("Accept", "application/json")

    private fun parseState(body: String): RemoteState? {
        val rows = JSONArray(body)
        if (rows.length() == 0) return null
        val row = rows.getJSONObject(0)
        return RemoteState(
            expression = Expression.fromRemote(row.optNullableString("expression")),
            bubbleText = row.optNullableString("bubble_text"),
            bubbleStyle = BubbleStyle.fromRemote(row.optNullableString("bubble_style")),
            updatedAt = row.getString("updated_at"),
        )
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else getString(name)

    private companion object {
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
