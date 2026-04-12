package com.opencode.remote.service

import android.content.Context
import com.opencode.remote.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.Json

// 单例连接服务，统一管理 WebSocket 生命周期
// 职责：App 启动时自动连接、断线重连、存储连接配置、提供连接状态
object ConnectionService {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    private var relayClient: RelayClient? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var stateJob: Job? = null
    private var eventsJob: Job? = null

    private val _state = MutableStateFlow<WsState>(WsState.Disconnected)
    // 公开的连接状态，供 UI 观察
    val state: StateFlow<WsState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<Pair<String, kotlinx.serialization.json.JsonObject>>(extraBufferCapacity = 64)
    // 公开的事件流，供 ViewModel 收集处理
    val events = _events.asSharedFlow()

    // 初始化（App 启动时调用）
    suspend fun init(context: Context) {
        // 从 SharedPreferences 加载配置
        val cfg = context.loadConfig()
        // 如果有配置则自动连接
        if (cfg.url.isNotBlank() && cfg.token.isNotBlank()) {
            connect(cfg.url, cfg.token)
        }
    }

    // 发起连接（ConnectScreen 调用）
    fun connect(url: String, token: String) {
        // 取消之前的订阅任务
        stateJob?.cancel()
        eventsJob?.cancel()
        // 如果已有 client 先 disconnect
        relayClient?.disconnect()
        // 创建新的 RelayClient 并连接
        val client = RelayClient()
        relayClient = client
        // 订阅 client 的状态变化，转发到 _state
        stateJob = scope.launch {
            client.state.collect { s ->
                _state.value = s
            }
        }
        // 订阅 client 的事件流，转发到 _events
        eventsJob = scope.launch {
            client.events.collect { e ->
                _events.emit(e)
            }
        }
        // 发起连接
        client.connect(url, token)
    }

    // 断开连接（设置页面用）
    fun disconnect() {
        stateJob?.cancel()
        eventsJob?.cancel()
        relayClient?.disconnect()
        relayClient = null
        _state.value = WsState.Disconnected
    }

    // 获取 client 实例（供 ViewModel 发送消息用）
    fun client(): RelayClient {
        return relayClient ?: RelayClient().also {
            relayClient = it
            // 注意：此时未连接，需要手动调用 connect
        }
    }

    // 保存配置
    suspend fun saveConfig(context: Context, url: String, token: String) {
        context.saveConfig(url, token)
    }

    // 加载配置
    suspend fun loadConfig(context: Context): ServerConfig {
        return context.loadConfig()
    }
}