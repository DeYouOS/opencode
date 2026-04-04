package com.opencode.remote.data

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.math.pow

sealed class WsState {
    data object Disconnected : WsState()
    data object Connecting : WsState()
    data object Connected : WsState()
    data class Error(val msg: String) : WsState()
}

/** 带自动重连的 WebSocket 客户端，负责认证握手和消息收发 */
class RelayClient {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = OkHttpClient.Builder()
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var ws: WebSocket? = null
    private var retry = 0
    private var url = ""
    private var token = ""

    private val _state = MutableStateFlow<WsState>(WsState.Disconnected)
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<Pair<String, JsonObject>>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun connect(serverUrl: String, serverToken: String) {
        url = serverUrl.trimEnd('/')
        token = serverToken
        retry = 0
        open()
    }

    fun disconnect() {
        ws?.close(1000, "user disconnect")
        ws = null
        _state.value = WsState.Disconnected
    }

    fun send(payload: String) {
        ws?.send(payload)
    }

    private fun open() {
        _state.value = WsState.Connecting
        val endpoint = "$url/ws"
        val req = Request.Builder().url(endpoint).build()

        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                retry = 0
                val auth = json.encodeToString(AuthHandshake.serializer(), AuthHandshake(token = token))
                webSocket.send(auth)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val obj = json.parseToJsonElement(text).jsonObject
                val type = obj["type"]?.jsonPrimitive?.content

                // 认证响应
                if (type == "auth.result") {
                    val result = json.decodeFromString(AuthResult.serializer(), text)
                    if (result.ok) {
                        _state.value = WsState.Connected
                    } else {
                        _state.value = WsState.Error(result.error ?: "认证失败")
                        webSocket.close(4001, "auth failed")
                    }
                    return
                }

                // 心跳
                if (type == "ping") {
                    webSocket.send("""{"type":"pong"}""")
                    return
                }

                // 带 seq 的信封消息 → 发 ACK 并提取事件
                val seq = obj["seq"]
                if (seq != null) {
                    webSocket.send("""{"ack":${seq.jsonPrimitive.content}}""")
                    val payload = obj["payload"]?.jsonObject ?: return
                    val eventType = payload["type"]?.jsonPrimitive?.content ?: return
                    _events.tryEmit(eventType to payload)
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                _state.value = WsState.Error(t.message ?: "连接失败")
                scheduleReconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (_state.value != WsState.Disconnected) {
                    _state.value = WsState.Error("连接断开: $reason")
                    scheduleReconnect()
                }
            }
        })
    }

    private fun scheduleReconnect() {
        val delay = min(1000L * 2.0.pow(retry).toLong(), 30_000L)
        retry++
        Thread {
            Thread.sleep(delay)
            if (_state.value != WsState.Disconnected) open()
        }.start()
    }
}
