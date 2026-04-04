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

data class SessionInfo(
    val id: String,
    val title: String,
    val status: String = "idle"
)

data class MessagePart(
    val id: String,
    val type: String,
    val text: String = ""
)

data class ToolInfo(
    val id: String,
    val tool: String,
    val callID: String,
    val status: String,
    val title: String? = null,
    val output: String? = null,
    val error: String? = null
)

class RemoteViewModel(app: Application) : AndroidViewModel(app) {
    private val json = Json { ignoreUnknownKeys = true }
    val client = RelayClient()

    private val _sessions = MutableStateFlow<List<SessionInfo>>(emptyList())
    val sessions = _sessions.asStateFlow()

    private val _permissions = MutableStateFlow<List<PermissionData>>(emptyList())
    val permissions = _permissions.asStateFlow()

    // sessionID → 累积文本
    private val _messages = MutableStateFlow<Map<String, List<MessagePart>>>(emptyMap())
    val messages = _messages.asStateFlow()

    private val _tools = MutableStateFlow<Map<String, List<ToolInfo>>>(emptyMap())
    val tools = _tools.asStateFlow()

    private val _todos = MutableStateFlow<Map<String, List<TodoItem>>>(emptyMap())
    val todos = _todos.asStateFlow()

    private val _instance = MutableStateFlow<InstanceInfoData?>(null)
    val instance = _instance.asStateFlow()

    init {
        viewModelScope.launch {
            client.events.collect { (type, payload) ->
                handleEvent(type, payload)
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

    private fun handleEvent(type: String, payload: JsonObject) {
        val data = payload["data"] ?: return

        when (type) {
            "event.permission" -> {
                val p = json.decodeFromJsonElement(PermissionData.serializer(), data)
                _permissions.value = _permissions.value + p
            }

            "event.permission.replied" -> {
                val p = json.decodeFromJsonElement(PermissionRepliedData.serializer(), data)
                _permissions.value = _permissions.value.filter { it.id != p.permissionID }
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
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                _sessions.value = _sessions.value.map {
                    if (it.id == s.id) it.copy(title = s.title) else it
                }
            }

            "event.session.deleted" -> {
                val s = json.decodeFromJsonElement(SessionInfoData.serializer(), data)
                _sessions.value = _sessions.value.filter { it.id != s.id }
            }

            "event.message.delta" -> {
                val d = json.decodeFromJsonElement(MessageDeltaData.serializer(), data)
                val sid = d.sessionID
                val cur = _messages.value[sid].orEmpty().toMutableList()
                val idx = cur.indexOfFirst { it.id == d.partID }
                if (idx >= 0) {
                    val old = cur[idx]
                    val text = if (d.delta != null) old.text + d.delta else d.text ?: old.text
                    cur[idx] = old.copy(text = text)
                } else {
                    cur.add(MessagePart(d.partID, d.partType, d.text ?: d.delta ?: ""))
                }
                _messages.value = _messages.value + (sid to cur)
            }

            "event.tool.update" -> {
                val t = json.decodeFromJsonElement(ToolUpdateData.serializer(), data)
                val sid = t.sessionID
                val cur = _tools.value[sid].orEmpty().toMutableList()
                val info = ToolInfo(t.partID, t.tool, t.callID, t.status, t.title, t.output, t.error)
                val idx = cur.indexOfFirst { it.id == t.partID }
                if (idx >= 0) cur[idx] = info else cur.add(info)
                _tools.value = _tools.value + (sid to cur)
            }

            "event.todo.updated" -> {
                val t = json.decodeFromJsonElement(TodoUpdateData.serializer(), data)
                _todos.value = _todos.value + (t.sessionID to t.todos)
            }

            "event.instance.info" -> {
                val info = json.decodeFromJsonElement(InstanceInfoData.serializer(), data)
                _instance.value = info
                _sessions.value = info.sessions.map { SessionInfo(it.id, it.title, it.status) }
            }
        }
    }
}
