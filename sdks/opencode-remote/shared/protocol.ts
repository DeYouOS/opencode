/**
 * OpenCode Remote 通信协议
 *
 * 定义 Plugin ↔ Relay Server ↔ Phone App 三方之间的 WebSocket 消息格式。
 * 所有消息统一用 WsEnvelope 封装，通过 seq/ack 保证可靠投递。
 */

// ============================================================
// 基础信封
// ============================================================

/** WebSocket 消息信封，所有 payload 都包裹在内 */
export type WsEnvelope = {
  /** 单调递增序列号，用于 ACK 确认和离线重传 */
  seq: number
  /** 时间戳（毫秒） */
  ts: number
  /** 实际载荷 */
  payload: WsPayload
}

/** ACK 消息，确认收到指定 seq 的消息 */
export type WsAck = {
  ack: number
}

/** 心跳消息 */
export type WsPing = { type: "ping" }
export type WsPong = { type: "pong" }

/** 所有可能的 payload 类型 */
export type WsPayload = RemoteEvent | RemoteAction | WsPing | WsPong

// ============================================================
// 事件（OpenCode → Relay → Phone）
// ============================================================

/** 权限请求事件 — 需要用户在手机上审批 */
export type PermissionEvent = {
  type: "event.permission"
  data: {
    id: string
    kind: string
    pattern?: string | string[]
    sessionID: string
    messageID: string
    callID?: string
    title: string
    metadata: Record<string, unknown>
    created: number
  }
}

/** 权限已回复事件 */
export type PermissionRepliedEvent = {
  type: "event.permission.replied"
  data: {
    sessionID: string
    permissionID: string
    response: string
  }
}

/** 会话状态变更事件 */
export type SessionStatusEvent = {
  type: "event.session.status"
  data: {
    sessionID: string
    status: { type: "idle" } | { type: "busy" } | { type: "retry"; attempt: number; message: string; next: number }
  }
}

/** 会话创建/更新/删除事件 */
export type SessionInfoEvent = {
  type: "event.session.created" | "event.session.updated" | "event.session.deleted"
  data: {
    id: string
    title: string
    created: number
    updated: number
  }
}

/** 会话错误事件 */
export type SessionErrorEvent = {
  type: "event.session.error"
  data: {
    sessionID?: string
    error?: {
      name: string
      message?: string
    }
  }
}

/** AI 消息文本流增量 */
export type MessageDeltaEvent = {
  type: "event.message.delta"
  data: {
    sessionID: string
    messageID: string
    partID: string
    partType: string
    delta?: string
    text?: string
  }
}

/** 工具调用状态更新 */
export type ToolUpdateEvent = {
  type: "event.tool.update"
  data: {
    sessionID: string
    messageID: string
    partID: string
    tool: string
    callID: string
    status: "pending" | "running" | "completed" | "error"
    title?: string
    input?: Record<string, unknown>
    output?: string
    error?: string
    time?: { start?: number; end?: number }
  }
}

/** Todo 列表更新 */
export type TodoUpdateEvent = {
  type: "event.todo.updated"
  data: {
    sessionID: string
    todos: Array<{
      content: string
      status: string
      priority: string
    }>
  }
}

/** 实例信息 — 连接后首条消息，描述 OpenCode 实例基本信息 */
export type InstanceInfoEvent = {
  type: "event.instance.info"
  data: {
    instanceId: string
    version: string
    project: string
    directory: string
    sessions: Array<{
      id: string
      title: string
      status: "idle" | "busy" | "retry"
    }>
  }
}

/** 实例断开事件 — plugin 断开时 relay 通知 phone 移除对应终端 */
export type InstanceDisconnectedEvent = {
  type: "event.instance.disconnected"
  data: {
    instanceId: string
  }
}

/** 实例同步事件 — phone 连接时 relay 推送当前所有在线 plugin 列表 */
export type InstanceSyncEvent = {
  type: "event.instance.sync"
  data: {
    instanceIds: string[]
  }
}

/** 消息信息更新（含 token 用量、cost 等） */
export type MessageInfoEvent = {
  type: "event.message.info"
  data: {
    sessionID: string
    messageID: string
    role: "user" | "assistant"
    cost?: number
    tokens?: {
      input: number
      output: number
      reasoning: number
    }
    error?: {
      name: string
      message?: string
    }
  }
}

/** Agent 向用户提问事件 */
export type QuestionAskedEvent = {
  type: "event.question.asked"
  data: {
    id: string
    sessionID: string
    questions: Array<{
      question: string
      header: string
      options: Array<{
        label: string
        description: string
      }>
      multiple?: boolean
      custom?: boolean
    }>
    tool?: {
      messageID: string
      callID: string
    }
  }
}

/** 问题已回复事件 */
export type QuestionRepliedEvent = {
  type: "event.question.replied"
  data: {
    sessionID: string
    requestID: string
    answers: string[][]
  }
}

/** 问题已拒绝事件 */
export type QuestionRejectedEvent = {
  type: "event.question.rejected"
  data: {
    sessionID: string
    requestID: string
  }
}

/** Action 执行错误事件（Plugin → Phone 错误反馈） */
export type ActionErrorEvent = {
  type: "event.action.error"
  data: {
    sessionID?: string
    actionType: string
    error: string
  }
}

/** 可用模型列表事件 — Plugin 推送给 Phone */
export type ProviderListEvent = {
  type: "event.provider.list"
  data: {
    providers: Array<{
      id: string
      name: string
      connected: boolean
      models: Array<{
        id: string
        name: string
        reasoning: boolean
        context: number
        output: number
      }>
    }>
  }
}

/** 所有事件类型的联合 */
export type RemoteEvent =
  | PermissionEvent
  | PermissionRepliedEvent
  | SessionStatusEvent
  | SessionInfoEvent
  | SessionErrorEvent
  | MessageDeltaEvent
  | ToolUpdateEvent
  | TodoUpdateEvent
  | InstanceInfoEvent
  | MessageInfoEvent
  | QuestionAskedEvent
  | QuestionRepliedEvent
  | QuestionRejectedEvent
  | ActionErrorEvent
  | ProviderListEvent
  | InstanceDisconnectedEvent
  | InstanceSyncEvent
  | CommandListEvent

// ============================================================
// 操作（Phone → Relay → OpenCode）
// ============================================================

/** 回复权限请求 */
export type PermissionReplyAction = {
  type: "action.permission.reply"
  data: {
    sessionID: string
    permissionID: string
    /** once=仅本次，always=始终允许此模式，reject=拒绝 */
    response: "once" | "always" | "reject"
    feedback?: string
  }
}

/** 发送消息到会话 */
export type SessionMessageAction = {
  type: "action.session.message"
  data: {
    sessionID: string
    content: string
    agent?: string
    model?: {
      providerID: string
      modelID: string
    }
  }
}

/** 执行斜杠命令（如 /commit, /help 等） */
export type SessionCommandAction = {
  type: "action.session.command"
  data: {
    sessionID: string
    command: string
    arguments?: string
    agent?: string
    model?: {
      providerID: string
      modelID: string
    }
  }
}

/** 命令列表事件 — 连接时推送所有可用命令 */
export type CommandListEvent = {
  type: "event.command.list"
  data: {
    commands: Array<{
      name: string
      description?: string
      hints?: string[]
    }>
  }
}

/** 中止当前会话 */
export type SessionAbortAction = {
  type: "action.session.abort"
  data: {
    sessionID: string
  }
}

/** 创建新会话 */
export type SessionCreateAction = {
  type: "action.session.create"
  data: {
    title?: string
  }
}

/** 回复 agent 提出的问题 */
export type QuestionReplyAction = {
  type: "action.question.reply"
  data: {
    sessionID: string
    questionID: string
    answer: string
  }
}

/** 拒绝 agent 提出的问题 */
export type QuestionRejectAction = {
  type: "action.question.reject"
  data: {
    sessionID: string
    questionID: string
  }
}

/** 请求刷新实例信息 */
export type RefreshAction = {
  type: "action.refresh"
  data: Record<string, never>
}

/** 所有操作类型的联合 */
export type RemoteAction =
  | PermissionReplyAction
  | SessionMessageAction
  | SessionAbortAction
  | SessionCreateAction
  | QuestionReplyAction
  | QuestionRejectAction
  | RefreshAction
  | SessionCommandAction

// ============================================================
// 认证
// ============================================================

/** 连接握手时的认证消息（WebSocket 连接后首条消息） */
export type AuthHandshake = {
  type: "auth"
  role: "plugin" | "phone"
  token: string
  /** 仅 plugin 提供 */
  instance?: string
}

/** 认证响应 */
export type AuthResult = {
  type: "auth.result"
  ok: boolean
  error?: string
}

// ============================================================
// REST 降级接口的类型
// ============================================================

/** GET /api/events?since=<seq> 的响应 */
export type EventsResponse = {
  events: WsEnvelope[]
  latest: number
}

/** POST /api/action 的请求体 */
export type ActionRequest = {
  token: string
  action: RemoteAction
}
