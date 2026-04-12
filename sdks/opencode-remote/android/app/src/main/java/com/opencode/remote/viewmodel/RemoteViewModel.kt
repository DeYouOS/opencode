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
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.builtins.ListSerializer
import java.util.concurrent.atomic.AtomicLong

// 终端实例信息
data class TerminalInfo(
    val instanceIds: MutableSet<String> = mutableSetOf(),
    val project: String,
    val directory: String,
    val sessions: List<SessionInfo>
)

// 会话基本信息
data class SessionInfo(
    val id: String,
    val title: String,
    val status: String = "idle",
    val instanceId: String = ""
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
    class Question(seq: Long, val data: QuestionAskedData) : TimelineItem(seq)
    class ActionErr(seq: Long, val data: ActionErrorData) : TimelineItem(seq)
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

    private val _messages = MutableStateFlow<Map<String, List<MessagePart>>>(emptyMap())
    val messages = _messages.asStateFlow()

    private val _tools = MutableStateFlow<Map<String, List<ToolInfo>>>(emptyMap())
    val tools = _tools.asStateFlow()

    private val _todos = MutableStateFlow<Map<String, List<TodoItem>>>(emptyMap())
    val todos = _todos.asStateFlow()

    private val _terminals = MutableStateFlow<Map<String, TerminalInfo>>(emptyMap())
    val terminals = _terminals.asStateFlow()

    // 统一时间线：按 sessionID 分组，内部按 seq 排序
    private val _timeline = MutableStateFlow<Map<String, List<TimelineItem>>>(emptyMap())
    val timeline = _timeline.asStateFlow()

    // 最新消息开销信息（token/cost），按 sessionID 分组
    private val _msgInfo = MutableStateFlow<Map<String, MessageInfoData>>(emptyMap())
    val msgInfo = _msgInfo.asStateFlow()

    // 待回答的问题列表
    private val _questions = MutableStateFlow<List<QuestionAskedData>>(emptyList())
    val questions = _questions.asStateFlow()

    // 可用 provider/model 列表
    private val _providers = MutableStateFlow<List<ProviderInfo>>(emptyList())
    val providers = _providers.asStateFlow()

    // 可用命令列表（/command）
    private val _commands = MutableStateFlow<List<CommandInfo>>(emptyList())
    val commands = _commands.asStateFlow()

    // 当前选中的模型
    private val _selectedModel = MutableStateFlow<ModelRef?>(null)
    val selectedModel = _selectedModel.asStateFlow()

    init {
        viewModelScope.launch {
            client.events.collect { (type, payload) ->
                handleEvent(type, payload)
            }
        }
        viewModelScope.launch {
            client.state.collect { state ->
                if (state is WsState.Connected) {
                    // 不清空 terminals，等 sync 事件来清理已断开的终端
                    // 清空 sessions 避免残留
                    _sessions.value = emptyList()
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
        removeTimeline(sessionID, "__perm_$permissionID")
    }

    fun sendMessage(sessionID: String, content: String) {
        // 输入以 / 开头时走命令通道
        if (content.startsWith("/")) {
            val parts = content.trimStart('/').split(Regex("\\s+"), 2)
            val cmd = parts[0]
            val args = if (parts.size > 1) parts[1] else null
            val action = SessionCommandAction(data = SessionCommandData(sessionID, cmd, args, model = _selectedModel.value))
            client.send(json.encodeToString(SessionCommandAction.serializer(), action))
            return
        }
        val action = SessionMessageAction(
            data = SessionMessageData(sessionID, content, model = _selectedModel.value)
        )
        client.send(json.encodeToString(SessionMessageAction.serializer(), action))
    }

    fun selectModel(model: ModelRef?) {
        _selectedModel.value = model
    }

    fun abortSession(sessionID: String) {
        val action = SessionAbortAction(data = SessionAbortData(sessionID))
        client.send(json.encodeToString(SessionAbortAction.serializer(), action))
    }

    fun createSession() {
        val action = SessionCreateAction(data = SessionCreateData())
        client.send(json.encodeToString(SessionCreateAction.serializer(), action))
    }

    fun replyQuestion(sessionID: String, questionID: String, answer: String) {
        val action = QuestionReplyAction(
            data = QuestionReplyData(sessionID, questionID, answer)
        )
        client.send(json.encodeToString(QuestionReplyAction.serializer(), action))
        _questions.value = _questions.value.filter { it.id != questionID }
        removeTimeline(sessionID, "__question_$questionID")
    }

    fun rejectQuestion(sessionID: String, questionID: String) {
        val action = QuestionRejectAction(
            data = QuestionRejectData(sessionID, questionID)
        )
        client.send(json.encodeToString(QuestionRejectAction.serializer(), action))
        _questions.value = _questions.value.filter { it.id != questionID }
        removeTimeline(sessionID, "__question_$questionID")
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
        is TimelineItem.Question -> "__question_${item.data.id}"
        is TimelineItem.ActionErr -> "__actionerr_${item.data.actionType}_${item.seq}"
    }

    private fun findTerminalBySession(sid: String): String =
        _terminals.value.entries.find { (_, t) -> t.sessions.any { it.id == sid } }?.key ?: ""

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
                for ((iid, t) in _terminals.value) {
                    val idx = t.sessions.indexOfFirst { it.id == s.sessionID }
                    if (idx >= 0) {
                        val updated = t.sessions.toMutableList()
                        updated[idx] = updated[idx].copy(status = s.status.type)
                        _terminals.value = _terminals.value + (iid to t.copy(sessions = updated))
                        break
                    }
                }
            }

            "event.session.created" -> {
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                val iid = payload["data"]?.let { it.jsonObject["instanceId"]?.jsonPrimitive?.content } ?: findTerminalBySession(s.id)
                _sessions.value = _sessions.value + SessionInfo(s.id, s.title, instanceId = iid)
            }

            "event.session.updated" -> {
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                _sessions.value = _sessions.value.map {
                    if (it.id == s.id) it.copy(title = s.title) else it
                }
                for ((iid, t) in _terminals.value) {
                    val idx = t.sessions.indexOfFirst { it.id == s.id }
                    if (idx >= 0) {
                        val updated = t.sessions.toMutableList()
                        updated[idx] = updated[idx].copy(title = s.title)
                        _terminals.value = _terminals.value + (iid to t.copy(sessions = updated))
                        break
                    }
                }
            }

            "event.session.deleted" -> {
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                _sessions.value = _sessions.value.filter { it.id != s.id }
                for ((iid, t) in _terminals.value) {
                    if (t.sessions.any { it.id == s.id }) {
                        _terminals.value = _terminals.value + (iid to t.copy(sessions = t.sessions.filter { it.id != s.id }))
                        break
                    }
                }
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
                val info = json.decodeFromJsonElement(MessageInfoData.serializer(), data)
                _msgInfo.value = _msgInfo.value + (info.sessionID to info)
                appendTimeline(info.sessionID, TimelineItem.Info(_seq.incrementAndGet(), info))
            }

            "event.question.asked" -> {
                val q = json.decodeFromJsonElement(QuestionAskedData.serializer(), data)
                _questions.value = _questions.value + q
                upsertTimeline(q.sessionID, "__question_${q.id}", TimelineItem.Question(_seq.incrementAndGet(), q))
            }

            "event.question.replied" -> {
                val q = json.decodeFromJsonElement(QuestionRepliedData.serializer(), data)
                _questions.value = _questions.value.filter { it.id != q.requestID }
                removeTimeline(q.sessionID, "__question_${q.requestID}")
            }

            "event.question.rejected" -> {
                val q = json.decodeFromJsonElement(QuestionRejectedData.serializer(), data)
                _questions.value = _questions.value.filter { it.id != q.requestID }
                removeTimeline(q.sessionID, "__question_${q.requestID}")
            }

            "event.action.error" -> {
                val err = json.decodeFromJsonElement(ActionErrorData.serializer(), data)
                if (err.sessionID != null) {
                    appendTimeline(err.sessionID, TimelineItem.ActionErr(_seq.incrementAndGet(), err))
                }
            }

            "event.provider.list" -> {
                val elem = payload["providers"] as? kotlinx.serialization.json.JsonElement
                if (elem != null) {
                    _providers.value = json.decodeFromJsonElement(ListSerializer(ProviderInfo.serializer()), elem)
                }
            }

            "event.instance.info" -> {
                val info = json.decodeFromJsonElement(InstanceInfoData.serializer(), data)
                val iid = info.instanceId.ifBlank { "default" }
                // 按 instanceId 分组，每个终端独立管理
                _terminals.value = _terminals.value + (iid to TerminalInfo(
                    mutableSetOf(iid), info.project.ifBlank { info.directory.substringAfterLast("/") }, info.directory,
                    info.sessions.map { SessionInfo(it.id, it.title, it.status, iid) }
                ))
                _sessions.value = _terminals.value.values.flatMap { it.sessions }.distinctBy { it.id }
            }

            "event.instance.disconnected" -> {
                val info = json.decodeFromJsonElement(InstanceDisconnectedData.serializer(), data)
                val iid = info.instanceId
                // 直接移除该实例的终端卡片
                if (iid in _terminals.value) {
                    _terminals.value = _terminals.value - iid
                    _sessions.value = _terminals.value.values.flatMap { it.sessions }.distinctBy { it.id }
                }
            }

            "event.instance.sync" -> {
                val info = json.decodeFromJsonElement(InstanceSyncData.serializer(), data)
                val online = info.instanceIds.toSet()
                // 只保留在线的终端，清理已断开的
                _terminals.value = _terminals.value.filterKeys { it in online }
                _sessions.value = _terminals.value.values.flatMap { it.sessions }.distinctBy { it.id }
            }

            "event.command.list" -> {
                val obj = data as? kotlinx.serialization.json.JsonObject ?: return
                val cmds = obj["commands"] as? kotlinx.serialization.json.JsonArray ?: return
                val list = json.decodeFromJsonElement(
                    kotlinx.serialization.builtins.ListSerializer(CommandInfo.serializer()), cmds
                )
                _commands.value = list
            }
        }
    }
}
