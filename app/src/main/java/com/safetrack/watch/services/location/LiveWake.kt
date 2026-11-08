package com.safetrack.watch.services.location

import android.util.Log
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * Starts the live map at once when a guardian opens it (2026-11-08).
 *
 * The guardian's app sends a "wake" on the public Realtime topic
 * live-wake:<child id> (no location in it). The watch keeps one small
 * WebSocket open to that topic and, on a wake, asks SafeTrack whether a
 * guardian is watching (push_watch_live), which switches it to 1-second
 * fixes. Before this the watch only asked every 20 s, so the live map could
 * take up to 20 s to start. A fake wake costs one extra request and nothing
 * else: the database still decides whether anyone is watching.
 *
 * Phoenix protocol (Supabase Realtime): join the topic, a heartbeat every
 * [HEARTBEAT_MS], reconnect with back-off when the connection drops.
 */
class LiveWake(
    baseUrl: String,
    private val publishableKey: String,
    private val scope: CoroutineScope,
    private val onWake: () -> Unit,
) {
    private val socketUrl = baseUrl.trimEnd('/')
        .replaceFirst("https://", "wss://")
        .plus("/realtime/v1/websocket?apikey=$publishableKey&vsn=1.0.0")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }

    private var childId: String? = null
    private var socket: WebSocket? = null
    private var heartbeat: Job? = null
    private var reconnect: Job? = null
    private var ref = 0
    private var backoffMs = MIN_BACKOFF_MS

    /** Listens for the given child (null stops). */
    fun watch(child: String?) {
        if (child == childId) return
        close()
        childId = child
        if (child != null && publishableKey.isNotBlank()) connect()
    }

    private fun connect() {
        val child = childId ?: return
        val topic = "realtime:live-wake:$child"
        socket = client.newWebSocket(Request.Builder().url(socketUrl).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                backoffMs = MIN_BACKOFF_MS
                webSocket.send(
                    """{"topic":"$topic","event":"phx_join","ref":"${++ref}","join_ref":"$ref",""" +
                        """"payload":{"config":{"broadcast":{"self":false},"presence":{"key":""},"private":false}}}""",
                )
                heartbeat?.cancel()
                heartbeat = scope.launch {
                    while (isActive) {
                        delay(HEARTBEAT_MS)
                        webSocket.send("""{"topic":"phoenix","event":"heartbeat","payload":{},"ref":"${++ref}"}""")
                    }
                }
                Log.i(TAG, "Listening for live-map requests")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
                if (message["event"]?.jsonPrimitive?.content != "broadcast") return
                val inner = message["payload"] as? JsonObject ?: return
                if (inner["event"]?.jsonPrimitive?.content != "wake") return
                Log.i(TAG, "Guardian opened the live map")
                scope.launch { onWake() }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = retry(webSocket)

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) = retry(webSocket)
        })
    }

    private fun retry(closed: WebSocket) {
        if (closed !== socket) return
        heartbeat?.cancel()
        socket = null
        reconnect?.cancel()
        reconnect = scope.launch {
            delay(backoffMs)
            backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            if (socket == null) connect()
        }
    }

    private fun close() {
        reconnect?.cancel()
        heartbeat?.cancel()
        socket?.let { it.close(1000, null) }
        socket = null
    }

    private companion object {
        const val TAG = "LiveWake"
        const val HEARTBEAT_MS = 25_000L
        const val MIN_BACKOFF_MS = 2_000L
        const val MAX_BACKOFF_MS = 60_000L
    }
}
