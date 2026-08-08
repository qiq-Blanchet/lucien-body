package com.luc.body.network

import com.luc.body.state.Cancelable
import com.luc.body.state.DelayScheduler
import com.luc.body.state.RemoteState
import java.util.concurrent.Executor
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/** Supabase Realtime v1 client with HTTP polling fallback. */
class SupabaseRealtimeClient(
    private val config: SupabaseConfig,
    private val httpClient: OkHttpClient,
    private val pollingLoop: PollingLoop,
    private val scheduler: DelayScheduler,
    private val ownerExecutor: Executor,
    private val onState: (RemoteState) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private var started = false
    private var generation = 0L
    private var ref = 0L
    private var reconnectIndex = 0
    private var nextAttemptId = 0L
    private var activeAttemptId: Long? = null
    private var joined = false
    private var socket: WebSocket? = null
    private var joinRef: String? = null
    private var pendingHeartbeatRef: String? = null
    private var joinTimeoutTask: Cancelable? = null
    private var heartbeatTask: Cancelable? = null
    private var reconnectTask: Cancelable? = null

    fun start() {
        val runGeneration = synchronized(lock) {
            if (started) return
            started = true
            ++generation
        }
        connect(runGeneration)
    }

    override fun close() {
        val oldSocket = synchronized(lock) {
            if (!started) return
            started = false
            generation += 1
            activeAttemptId = null
            joined = false
            joinRef = null
            pendingHeartbeatRef = null
            joinTimeoutTask?.cancel()
            heartbeatTask?.cancel()
            reconnectTask?.cancel()
            joinTimeoutTask = null
            heartbeatTask = null
            reconnectTask = null
            socket.also { socket = null }
        }
        oldSocket?.close(NORMAL_CLOSURE_CODE, null)
        pollingLoop.stop()
    }

    private fun connect(runGeneration: Long) {
        val attemptId = synchronized(lock) {
            if (!isCurrentLocked(runGeneration) || activeAttemptId != null) return
            (++nextAttemptId).also { activeAttemptId = it }
        }
        val request = Request.Builder()
            .url(realtimeUrl())
            .header("apikey", config.publishableKey)
            .header("Authorization", "Bearer ${config.publishableKey}")
            .build()
        val createdSocket = httpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    dispatch { handleOpen(runGeneration, attemptId, webSocket) }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    dispatch { handleMessage(runGeneration, attemptId, webSocket, text) }
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(code, null)
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    dispatch { handleDisconnected(runGeneration, attemptId) }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    response?.close()
                    dispatch { handleDisconnected(runGeneration, attemptId) }
                }
            },
        )
        val cancel = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId)) {
                true
            } else {
                socket = createdSocket
                false
            }
        }
        if (cancel) createdSocket.cancel()
    }

    private fun handleOpen(runGeneration: Long, attemptId: Long, webSocket: WebSocket) {
        val message = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId)) return
            socket = webSocket
            val currentJoinRef = (++ref).toString()
            joinRef = currentJoinRef
            joinMessage(currentJoinRef)
        }
        if (!webSocket.send(message)) {
            handleDisconnected(runGeneration, attemptId)
            webSocket.cancel()
        } else {
            scheduleJoinTimeout(runGeneration, attemptId, webSocket)
        }
    }

    private fun handleMessage(runGeneration: Long, attemptId: Long, webSocket: WebSocket, text: String) {
        if (!isAttemptCurrent(runGeneration, attemptId)) return
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        when (message.optString("event")) {
            "phx_reply" -> handleReply(runGeneration, attemptId, webSocket, message)
            "system" -> handleSystemMessage(runGeneration, attemptId, webSocket, message)
            "postgres_changes" -> handlePostgresChange(runGeneration, attemptId, message)
            "phx_error", "phx_close" -> {
                handleDisconnected(runGeneration, attemptId)
                webSocket.cancel()
            }
        }
    }

    private fun handleReply(
        runGeneration: Long,
        attemptId: Long,
        webSocket: WebSocket,
        message: JSONObject,
    ) {
        val isJoined = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId)) return
            joined
        }
        if (isJoined) {
            handleHeartbeatReply(runGeneration, attemptId, webSocket, message)
        } else {
            handleJoinReply(runGeneration, attemptId, webSocket, message)
        }
    }

    private fun handleJoinReply(
        runGeneration: Long,
        attemptId: Long,
        webSocket: WebSocket,
        message: JSONObject,
    ) {
        val connected = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId) || joined) return
            if (message.optString("ref") != joinRef) return
            val status = message.optJSONObject("payload")?.optString("status")
            if (status != "ok" || !hasExpectedPostgresSubscription(message)) return@synchronized false
            joined = true
            joinTimeoutTask?.cancel()
            joinTimeoutTask = null
            reconnectIndex = 0
            reconnectTask?.cancel()
            reconnectTask = null
            true
        }
        if (!connected) {
            handleDisconnected(runGeneration, attemptId)
            webSocket.cancel()
            return
        }
        pollingLoop.stop()
        scheduleHeartbeat(runGeneration, attemptId, webSocket)
    }

    private fun handleSystemMessage(
        runGeneration: Long,
        attemptId: Long,
        webSocket: WebSocket,
        message: JSONObject,
    ) {
        val payload = message.optJSONObject("payload") ?: return
        if (payload.optString("extension") != "postgres_changes" || payload.optString("status") == "ok") return
        handleDisconnected(runGeneration, attemptId)
        webSocket.cancel()
    }

    private fun handleHeartbeatReply(
        runGeneration: Long,
        attemptId: Long,
        webSocket: WebSocket,
        message: JSONObject,
    ) {
        val failed = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId) || !joined) return
            if (message.optString("ref") != pendingHeartbeatRef) return
            if (message.optJSONObject("payload")?.optString("status") == "ok") {
                pendingHeartbeatRef = null
                false
            } else {
                true
            }
        }
        if (failed) {
            handleDisconnected(runGeneration, attemptId)
            webSocket.cancel()
        }
    }

    private fun handlePostgresChange(
        runGeneration: Long,
        attemptId: Long,
        message: JSONObject,
    ) {
        if (!synchronized(lock) { isAttemptCurrentLocked(runGeneration, attemptId) && joined }) return
        val data = message.optJSONObject("payload")?.optJSONObject("data") ?: return
        if (!data.optString("type").equals("UPDATE", ignoreCase = true)) return
        val record = data.optJSONObject("record") ?: return
        runCatching { parseRemoteState(record) }.getOrNull()?.let(onState)
    }

    private fun handleDisconnected(runGeneration: Long, attemptId: Long) {
        val shouldFallback = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId)) return
            activeAttemptId = null
            socket = null
            joined = false
            joinRef = null
            pendingHeartbeatRef = null
            joinTimeoutTask?.cancel()
            heartbeatTask?.cancel()
            joinTimeoutTask = null
            heartbeatTask = null
            true
        }
        if (!shouldFallback) return
        pollingLoop.start()
        scheduleReconnect(runGeneration)
    }

    private fun scheduleJoinTimeout(runGeneration: Long, attemptId: Long, webSocket: WebSocket) {
        val task = scheduler.schedule(JOIN_TIMEOUT_MS) {
            dispatch {
                val timedOut = synchronized(lock) {
                    isAttemptCurrentLocked(runGeneration, attemptId) && !joined
                }
                if (timedOut) {
                    handleDisconnected(runGeneration, attemptId)
                    webSocket.cancel()
                }
            }
        }
        val cancel = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId) || joined || joinTimeoutTask != null) {
                true
            } else {
                joinTimeoutTask = task
                false
            }
        }
        if (cancel) task.cancel()
    }

    private fun scheduleHeartbeat(runGeneration: Long, attemptId: Long, webSocket: WebSocket) {
        val task = scheduler.schedule(HEARTBEAT_INTERVAL_MS) {
            dispatch {
                var timedOut = false
                val message = synchronized(lock) {
                    if (!isAttemptCurrentLocked(runGeneration, attemptId) || !joined) return@dispatch
                    heartbeatTask = null
                    if (pendingHeartbeatRef != null) {
                        timedOut = true
                        null
                    } else {
                        val heartbeatRef = (++ref).toString()
                        pendingHeartbeatRef = heartbeatRef
                        heartbeatMessage(heartbeatRef)
                    }
                }
                if (timedOut) {
                    handleDisconnected(runGeneration, attemptId)
                    webSocket.cancel()
                } else if (message != null && webSocket.send(message)) {
                    scheduleHeartbeat(runGeneration, attemptId, webSocket)
                } else if (message != null) {
                    handleDisconnected(runGeneration, attemptId)
                    webSocket.cancel()
                }
            }
        }
        val cancel = synchronized(lock) {
            if (!isAttemptCurrentLocked(runGeneration, attemptId) || !joined) {
                true
            } else {
                heartbeatTask?.cancel()
                heartbeatTask = task
                false
            }
        }
        if (cancel) task.cancel()
    }

    private fun scheduleReconnect(runGeneration: Long) {
        val delay = synchronized(lock) {
            if (!isCurrentLocked(runGeneration) || reconnectTask != null) return
            reconnectDelayMs(reconnectIndex).also {
                reconnectIndex = (reconnectIndex + 1).coerceAtMost(RECONNECT_DELAYS_MS.lastIndex)
            }
        }
        val task = scheduler.schedule(delay) {
            val reconnect = synchronized(lock) {
                if (!isCurrentLocked(runGeneration) || activeAttemptId != null) {
                    false
                } else {
                    reconnectTask = null
                    true
                }
            }
            if (reconnect) connect(runGeneration)
        }
        val cancel = synchronized(lock) {
            if (!isCurrentLocked(runGeneration) || reconnectTask != null) {
                true
            } else {
                reconnectTask = task
                false
            }
        }
        if (cancel) task.cancel()
    }

    private fun joinMessage(messageRef: String): String = JSONObject()
        .put("topic", TOPIC)
        .put("event", "phx_join")
        .put(
            "payload",
            JSONObject()
                .put(
                    "config",
                    JSONObject()
                        .put(
                            "postgres_changes",
                            JSONArray().put(
                                JSONObject()
                                    .put("event", "UPDATE")
                                    .put("schema", "public")
                                    .put("table", "clawd_state"),
                            ),
                        )
                        .put("private", false),
                ),
        )
        .put("join_ref", messageRef)
        .put("ref", messageRef)
        .toString()

    private fun hasExpectedPostgresSubscription(message: JSONObject): Boolean {
        val changes = message.optJSONObject("payload")
            ?.optJSONObject("response")
            ?.optJSONArray("postgres_changes")
            ?: return false
        return (0 until changes.length()).any { index ->
            val change = changes.optJSONObject(index) ?: return@any false
            change.optString("event") == "UPDATE" &&
                change.optString("schema") == "public" &&
                change.optString("table") == "clawd_state"
        }
    }

    private fun heartbeatMessage(messageRef: String): String = JSONObject()
        .put("topic", "phoenix")
        .put("event", "heartbeat")
        .put("payload", JSONObject())
        .put("ref", messageRef)
        .toString()

    private fun realtimeUrl(): String {
        val httpUrl = config.baseUrl.toHttpUrl()
        return httpUrl.newBuilder()
            .addPathSegments("realtime/v1/websocket")
            .addQueryParameter("apikey", config.publishableKey)
            .addQueryParameter("vsn", "1.0.0")
            .build()
            .toString()
    }

    private fun dispatch(action: () -> Unit) {
        ownerExecutor.execute(action)
    }

    private fun isAttemptCurrent(runGeneration: Long, attemptId: Long): Boolean = synchronized(lock) {
        isAttemptCurrentLocked(runGeneration, attemptId)
    }

    private fun isCurrentLocked(runGeneration: Long): Boolean = started && generation == runGeneration

    private fun isAttemptCurrentLocked(runGeneration: Long, attemptId: Long): Boolean =
        isCurrentLocked(runGeneration) && activeAttemptId == attemptId

    companion object {
        private const val TOPIC = "realtime:clawd_state"
        private const val JOIN_TIMEOUT_MS = 10_000L
        private const val HEARTBEAT_INTERVAL_MS = 25_000L
        private const val NORMAL_CLOSURE_CODE = 1000
        private val RECONNECT_DELAYS_MS = longArrayOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L, 30_000L)

        internal fun reconnectDelayMs(attempt: Int): Long =
            RECONNECT_DELAYS_MS[attempt.coerceIn(0, RECONNECT_DELAYS_MS.lastIndex)]
    }
}
