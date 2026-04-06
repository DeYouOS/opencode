package com.opencode.remote.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.opencode.remote.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.atomic.AtomicLong

// 会话基本信息
data class SessionInfo(
    val id: String,
    val title: String,
    val status: String = "idle"
)

// 消息片段（文本/推理）
data class MessagePart(
    val id: String,
    val type: String,
    val text: String = ""
)

// 工具调用信息，包含输入输出详情
data class ToolInfo(
    val id: String,
    val tool: String,
    val callID: String,
    val status: String,
    val title: String? = null,
    val input: JsonObject? = null,
    val output: String? = null,
    val error: String? = null
)

// 统一时间线项，用于按顺序交织展示消息、工具、任务、开销信息
sealed class TimelineItem(val seq: Long) {
    class Msg(seq: Long, val part: MessagePart) : TimelineItem(seq)
    class Tool(seq: Long, val info: ToolInfo) : TimelineItem(seq)
    class Todo(seq: Long, val items: List<TodoItem>) : TimelineItem(seq)
    class Info(seq: Long, val info: MessageInfoData) : TimelineItem(seq)
    class Perm(seq: Long, val data: PermissionData) : TimelineItem(seq)
}

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; explicitNulls = false }
    val client = RelayClient()

    // 单调递增序列号，用于时间线排序
    private val _seq = AtomicLong(0)

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionData>>(emptyList())
    val permissions = _permissions.asStateFlow()

    // 按 sessionID 分组的消息片段（保留兼容）
    private val _messages = MutableStateFlow<Map<String, List<MessagePart>>>(emptyMap())
    val messages = _messages.asStateFlow()

    // 按 sessionID 分组的工具调用（保留兼容）
    private val _tools = MutableStateFlow<Map<String, List<ToolInfo>>>(emptyMap())
    val tools = _tools.asStateFlow()

    private val _todos = MutableStateFlow<Map<String, List<TodoItem>>>(emptyMap())
    val todos = _todos.asStateFlow()

    private val _instance = MutableStateFlow<InstanceInfoData?>(null)
    val instance = _instance.asStateFlow()

    // 统一时间线：按 sessionID 分组，内部按 seq 排序
    private val _timeline = MutableStateFlow<Map<String, List<TimelineItem>>>(emptyMap())
    val timeline = _timeline.asStateFlow()

    // 最新消息开销信息（token/cost），按 sessionID 分组
    private val _msgInfo = MutableStateFlow<Map<String, MessageInfoData>>(emptyMap())
    val msgInfo = _msgInfo.asStateFlow()

    init {
        viewModelScope.launch {
            client.events.collect { (type, payload) ->
                handleEvent(type, payload)
            }
        }
        viewModelScope.launch {
            client.state.collect { state ->
                if (state is WsState.Connected) {
                    val action = RefreshAction()
                    client.send(json.encodeToString(RefreshAction.serializer(), action))
                }
            }
        }
    }

    fun connect(url: String, token: String) {
        viewModelScope.launch {
            getApplication<Application>().saveConfig(url, token)
            client.connect(url, token)
        }
    }

    fun disconnect() {
        client.disconnect()
    }

    fun replyPermission(sessionID: String, permissionID: String, response: String) {
        val action = PermissionReplyAction(
            data = PermissionReplyData(sessionID, permissionID, response)
        )
        client.send(json.encodeToString(PermissionReplyAction.serializer(), action))
        _permissions.value = _permissions.value.filter { it.id != permissionID }
    }

    fun sendMessage(sessionID: String, content: String) {
        val action = SessionMessageAction(
            data = SessionMessageData(sessionID, content)
        )
        client.send(json.encodeToString(SessionMessageAction.serializer(), action))
    }

    fun abortSession(sessionID: String) {
        val action = SessionAbortAction(data = SessionAbortData(sessionID))
        client.send(json.encodeToString(SessionAbortAction.serializer(), action))
    }

    fun createSession() {
        val action = SessionCreateAction(data = SessionCreateData())
        client.send(json.encodeToString(SessionCreateAction.serializer(), action))
    }

    // 向时间线中插入或更新一个项目，按 partID 去重
    private fun upsertTimeline(sid: String, id: String, item: TimelineItem) {
        val cur = _timeline.value[sid].orEmpty().toMutableList()
        val idx = cur.indexOfFirst { timelineId(it) == id }
        if (idx >= 0) cur[idx] = item else cur.add(item)
        _timeline.value = _timeline.value + (sid to cur)
    }

    // 向时间线追加一个项目（不去重）
    private fun appendTimeline(sid: String, item: TimelineItem) {
        val cur = _timeline.value[sid].orEmpty()
        _timeline.value = _timeline.value + (sid to (cur + item))
    }

    // 从时间线中按 ID 移除指定项
    private fun removeTimeline(sid: String, id: String) {
        val cur = _timeline.value[sid] ?: return
        _timeline.value = _timeline.value + (sid to cur.filter { timelineId(it) != id })
    }

    // 替换时间线中指定类型的唯一项（如 Todo 列表每个 session 只保留一份）
    private fun replaceTimeline(sid: String, item: TimelineItem, cls: Class<*>) {
        val cur = _timeline.value[sid].orEmpty().toMutableList()
        val idx = cur.indexOfFirst { cls.isInstance(it) }
        if (idx >= 0) cur[idx] = item else cur.add(item)
        _timeline.value = _timeline.value + (sid to cur)
    }

    // 获取时间线项的唯一标识
    private fun timelineId(item: TimelineItem): String = when (item) {
        is TimelineItem.Msg -> item.part.id
        is TimelineItem.Tool -> item.info.id
        is TimelineItem.Todo -> "__todo__"
        is TimelineItem.Info -> "__info_${item.info.messageID}"
        is TimelineItem.Perm -> "__perm_${item.data.id}"
    }

    private fun handleEvent(type: String, payload: JsonObject) {
        val data = payload["data"] ?: return

        when (type) {
            "event.permission" -> {
                val p = json.decodeFromJsonElement(PermissionData.serializer(), data)
                _permissions.value = _permissions.value + p
                // 同时插入时间线，让权限卡片出现在会话对话流中
                upsertTimeline(p.sessionID, "__perm_${p.id}", TimelineItem.Perm(_seq.incrementAndGet(), p))
            }

            "event.permission.replied" -> {
                val p = json.decodeFromJsonElement(PermissionRepliedData.serializer(), data)
                _permissions.value = _permissions.value.filter { it.id != p.permissionID }
                // 从时间线中移除已回复的权限卡片
                removeTimeline(p.sessionID, "__perm_${p.permissionID}")
            }

            "event.session.status" -> {
                val s = json.decodeFromJsonElement(SessionStatusData.serializer(), data)
                _sessions.value = _sessions.value.map {
                    if (it.id == s.sessionID) it.copy(status = s.status.type) else it
                }
            }

            "event.session.created" -> {
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                _sessions.value = _sessions.value + SessionInfo(s.id, s.title)
            }

            "event.session.updated" -> {
                // upsert：已有则更新，没有则添加
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                val exists = _sessions.value.any { it.id == s.id }
                _sessions.value = if (exists) {
                    _sessions.value.map { if (it.id == s.id) it.copy(title = s.title) else it }
                } else {
                    _sessions.value + SessionInfo(s.id, s.title)
                }
            }

            "event.session.deleted" -> {
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                _sessions.value = _sessions.value.filter { it.id != s.id }
            }

            "event.message.delta" -> {
                val d = json.decodeFromJsonElement(MessageDeltaData.serializer(), data)
                val sid = d.sessionID
                // 更新消息列表
                val cur = _messages.value[sid].orEmpty().toMutableList()
                val idx = cur.indexOfFirst { it.id == d.partID }
                val part: MessagePart
                if (idx >= 0) {
                    val old = cur[idx]
                    val text = if (d.delta != null) old.text + d.delta else d.text ?: old.text
                    part = old.copy(text = text)
                    cur[idx] = part
                } else {
                    part = MessagePart(d.partID, d.partType, d.text ?: d.delta ?: "")
                    cur.add(part)
                }
                _messages.value = _messages.value + (sid to cur)
                // 同步更新时间线（每次 delta 更新 seq 保证最新消息在底部）
                upsertTimeline(sid, d.partID, TimelineItem.Msg(_seq.incrementAndGet(), part))
            }

            "event.tool.update" -> {
                val t = json.decodeFromJsonElement(ToolUpdateData.serializer(), data)
                val sid = t.sessionID
                val info = ToolInfo(t.partID, t.tool, t.callID, t.status, t.title, t.input, t.output, t.error)
                // 更新工具列表
                val cur = _tools.value[sid].orEmpty().toMutableList()
                val idx = cur.indexOfFirst { it.id == t.partID }
                if (idx >= 0) cur[idx] = info else cur.add(info)
                _tools.value = _tools.value + (sid to cur)
                // 同步更新时间线
                upsertTimeline(sid, t.partID, TimelineItem.Tool(_seq.incrementAndGet(), info))
            }

            "event.todo.updated" -> {
                val t = json.decodeFromJsonElement(TodoUpdateData.serializer(), data)
                _todos.value = _todos.value + (t.sessionID to t.todos)
                // 时间线中 Todo 只保留一份最新的
                replaceTimeline(t.sessionID, TimelineItem.Todo(_seq.incrementAndGet(), t.todos), TimelineItem.Todo::class.java)
            }

            "event.message.info" -> {
                // 消息开销信息（token/cost）
                val info = json.decodeFromJsonElement(MessageInfoData.serializer(), data)
                _msgInfo.value = _msgInfo.value + (info.sessionID to info)
                // 追加到时间线
                appendTimeline(info.sessionID, TimelineItem.Info(_seq.incrementAndGet(), info))
            }

            "event.instance.info" -> {
                val info = json.decodeFromJsonElement(InstanceInfoData.serializer(), data)
                _instance.value = info
                _sessions.value = info.sessions.map { SessionInfo(it.id, it.title, it.status) }
            }
        }
    }
}
