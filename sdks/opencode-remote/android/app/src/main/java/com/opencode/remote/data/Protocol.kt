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
    val instanceId: String = "",
    val version: String,
    val project: String,
    val directory: String,
    val sessions: List<InstanceInfoSession>
)

@Serializable
data class InstanceDisconnectedData(
    val instanceId: String
)

@Serializable
data class InstanceSyncData(
    val instanceIds: List<String>
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

// ── Question 事件数据 ──

@Serializable
data class QuestionOption(
    val label: String,
    val description: String
)

@Serializable
data class QuestionInfo(
    val question: String,
    val header: String,
    val options: List<QuestionOption>,
    val multiple: Boolean? = null,
    val custom: Boolean? = null
)

@Serializable
data class QuestionAskedData(
    val id: String,
    val sessionID: String,
    val questions: List<QuestionInfo>,
    val tool: QuestionToolRef? = null
)

@Serializable
data class QuestionToolRef(
    val messageID: String,
    val callID: String
)

@Serializable
data class QuestionRepliedData(
    val sessionID: String,
    val requestID: String,
    val answers: List<List<String>>
)

@Serializable
data class QuestionRejectedData(
    val sessionID: String,
    val requestID: String
)

// ── Action 错误反馈 ──

@Serializable
data class ActionErrorData(
    val sessionID: String? = null,
    val actionType: String,
    val error: String
)

// ── Provider/Model 列表 ──

@Serializable
data class ModelItem(
    val id: String,
    val name: String,
    val reasoning: Boolean = false,
    val context: Int = 0,
    val output: Int = 0
)

@Serializable
data class ProviderInfo(
    val id: String,
    val name: String,
    val connected: Boolean = false,
    val models: List<ModelItem> = emptyList()
)

@Serializable
data class ModelRef(
    val providerID: String,
    val modelID: String
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
    val agent: String? = null,
    val model: ModelRef? = null
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

@Serializable
data class QuestionReplyAction(
    val type: String = "action.question.reply",
    val data: QuestionReplyData
)

@Serializable
data class QuestionReplyData(
    val sessionID: String,
    val questionID: String,
    val answer: String
)

@Serializable
data class QuestionRejectAction(
    val type: String = "action.question.reject",
    val data: QuestionRejectData
)

@Serializable
data class QuestionRejectData(
    val sessionID: String,
    val questionID: String
)

// ── 命令相关 ──

@Serializable
data class CommandInfo(
    val name: String,
    val description: String? = null,
    val hints: List<String>? = null
)

@Serializable
data class SessionCommandAction(
    val type: String = "action.session.command",
    val data: SessionCommandData
)

@Serializable
data class SessionCommandData(
    val sessionID: String,
    val command: String,
    val arguments: String? = null,
    val agent: String? = null,
    val model: ModelRef? = null
)
