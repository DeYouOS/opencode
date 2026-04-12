package com.opencode.remote.data

import android.util.Log
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

private const val TAG = "RelayClient"

sealed class WsState {
    data object Disconnected : WsState()
    data object Connecting : WsState()
    data object Connected : WsState()
    data class Error(val msg: String) : WsState()
}

/** 带自动重连和日志的 WebSocket 客户端 */
class RelayClient {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private val http = OkHttpClient.Builder()
        .pingInterval(25, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
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
        // 先关闭旧连接，避免多个 WebSocket 互相替换
        ws?.close(1000, "reconnect")
        ws = null
        open()
    }

    fun disconnect() {
        Log.i(TAG, "用户主动断开")
        ws?.close(1000, "user disconnect")
        ws = null
        _state.value = WsState.Disconnected
    }

    fun send(payload: String) {
        val w = ws
        if (w == null) {
            Log.w(TAG, "发送失败: WebSocket 未连接")
            return
        }
        w.send(payload)
    }

    private fun open() {
        _state.value = WsState.Connecting
        Log.i(TAG, "正在连接 $url (第${retry + 1}次)")
        val req = Request.Builder().url(url).build()

        ws = http.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "WebSocket 已连接")
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
                        Log.i(TAG, "认证成功")
                        _state.value = WsState.Connected
                    } else {
                        Log.e(TAG, "认证失败: ${result.error}")
                        _state.value = WsState.Error(result.error ?: "认证失败")
                        webSocket.close(4001, "auth failed")
                    }
                    return
                }

                // 心跳响应
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
                Log.e(TAG, "连接失败: ${t.message}", t)
                _state.value = WsState.Error(t.message ?: "连接失败")
                scheduleReconnect()
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                // 1000 = 用户主动断开，不需要重连
                if (code == 1000) {
                    Log.i(TAG, "连接关闭（主动断开）code=$code reason=$reason")
                    return
                }
                // 4003 = 被新连接替换，说明有新的连接已建立，停止重连
                if (code == 4003) {
                    Log.i(TAG, "连接被替换（新连接已建立），停止重连")
                    _state.value = WsState.Disconnected
                    ws = null
                    return
                }
                Log.w(TAG, "连接即将关闭 code=$code reason=$reason")
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                if (code == 1000 || code == 4003) {
                    Log.i(TAG, "onClosed 跳过: code=$code reason=$reason")
                    return
                }
                Log.w(TAG, "连接断开 code=$code reason=$reason")
                _state.value = WsState.Error("断开: $code $reason")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        val delay = min(1000L * 2.0.pow(retry).toLong(), 60_000L)
        retry++
        Log.i(TAG, "${delay / 1000}秒后重连 (第${retry}次)")
        Thread {
            Thread.sleep(delay)
            // 用户主动断开或被替换后不再重连
            if (_state.value == WsState.Disconnected) {
                Log.i(TAG, "已手动断开，取消重连")
                return@Thread
            }
            open()
        }.start()
    }
}
