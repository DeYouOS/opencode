package com.opencode.remote.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ── 基础信封 ──

@Serializable
data class WsEnvelope(
    val seq: Int,
    val ts: Long,
    val payload: JsonObject
)

@Serializable
data class WsAck(val ack: Int)

@Serializable
data class AuthHandshake(
    val type: String = "auth",
    val role: String = "phone",
    val token: String
)

@Serializable
data class AuthResult(
    val type: String,
    val ok: Boolean,
    val error: String? = null
)

// ── 事件数据 ──

@Serializable
data class PermissionData(
    val id: String,
    val kind: String,
    val sessionID: String,
    val messageID: String,
    val callID: String? = null,
    val title: String,
    val metadata: JsonObject = JsonObject(emptyMap()),
    val created: Long
)

@Serializable
data class PermissionRepliedData(
    val sessionID: String,
    val permissionID: String,
    val response: String
)

@Serializable
data class SessionStatusData(
    val sessionID: String,
    val status: SessionStatus
)

@Serializable
data class SessionStatus(
    val type: String,
    val attempt: Int? = null,
    val message: String? = null,
    val next: Long? = null
)

@Serializable
data class SessionInfoData(
    val id: String,
    val title: String,
    val created: Long,
    val updated: Long
)

@Serializable
data class SessionErrorData(
    val sessionID: String? = null,
    val error: ErrorInfo? = null
)

@Serializable
data class ErrorInfo(
    val name: String,
    val message: String? = null
)

@Serializable
data class MessageDeltaData(
    val sessionID: String,
    val messageID: String,
    val partID: String,
    val partType: String,
    val delta: String? = null,
    val text: String? = null
)

@Serializable
data class ToolUpdateData(
    val sessionID: String,
    val messageID: String,
    val partID: String,
    val tool: String,
    val callID: String,
    val status: String,
    val title: String? = null,
    val input: JsonObject? = null,
    val output: String? = null,
    val error: String? = null
)

@Serializable
data class TodoItem(
    val id: String,
    val content: String,
    val status: String,
    val priority: String
)

@Serializable
data class TodoUpdateData(
    val sessionID: String,
    val todos: List<TodoItem>
)

@Serializable
data class InstanceInfoSession(
    val id: String,
    val title: String,
    val status: String
)

@Serializable
data class InstanceInfoData(
    val version: String,
    val project: String,
    val directory: String,
    val sessions: List<InstanceInfoSession>
)

@Serializable
data class MessageInfoData(
    val sessionID: String,
    val messageID: String,
    val role: String,
    val cost: Double? = null,
    val tokens: TokenInfo? = null,
    val error: ErrorInfo? = null
)

@Serializable
data class TokenInfo(
    val input: Int,
    val output: Int,
    val reasoning: Int
)

// ── 操作（Phone → Relay → OpenCode） ──

@Serializable
data class PermissionReplyAction(
    val type: String = "action.permission.reply",
    val data: PermissionReplyData
)

@Serializable
data class PermissionReplyData(
    val sessionID: String,
    val permissionID: String,
    val response: String,
    val feedback: String? = null
)

@Serializable
data class SessionMessageAction(
    val type: String = "action.session.message",
    val data: SessionMessageData
)

@Serializable
data class SessionMessageData(
    val sessionID: String,
    val content: String,
    val agent: String? = null
)

@Serializable
data class SessionAbortAction(
    val type: String = "action.session.abort",
    val data: SessionAbortData
)

@Serializable
data class SessionAbortData(val sessionID: String)

@Serializable
data class SessionCreateAction(
    val type: String = "action.session.create",
    val data: SessionCreateData
)

@Serializable
data class SessionCreateData(val title: String? = null)

@Serializable
data class RefreshAction(
    val type: String = "action.refresh",
    val data: JsonObject = JsonObject(emptyMap())
)
