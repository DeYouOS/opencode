import type { ServerWebSocket } from "bun"
import type { AuthHandshake, AuthResult, WsEnvelope, RemoteEvent, RemoteAction, WsAck } from "../../shared/protocol"
import { verify } from "./auth"
import type { Queue } from "./queue"

type Role = "plugin" | "phone"

type WsData = {
  role?: Role
  alive: boolean
  authenticated: boolean
}

type Connections = {
  plugin: ServerWebSocket<WsData> | null
  phone: ServerWebSocket<WsData> | null
}

export function createWsHandler(token: string, pluginQueue: Queue, phoneQueue: Queue) {
  const conn: Connections = { plugin: null, phone: null }

  function send(ws: ServerWebSocket<WsData>, data: unknown) {
    ws.send(JSON.stringify(data))
  }

  function sendEnvelope(ws: ServerWebSocket<WsData>, env: WsEnvelope) {
    ws.send(JSON.stringify(env))
  }

  // 连接后未认证的消息先走认证流程
  function handleAuth(ws: ServerWebSocket<WsData>, msg: AuthHandshake) {
    if (!verify(msg.token, token)) {
      send(ws, { type: "auth.result", ok: false, error: "invalid token" } satisfies AuthResult)
      ws.close(4001, "auth failed")
      return
    }
    const role = msg.role
    if (role !== "plugin" && role !== "phone") {
      send(ws, { type: "auth.result", ok: false, error: "invalid role" } satisfies AuthResult)
      ws.close(4002, "invalid role")
      return
    }

    // 踢掉旧连接
    const old = conn[role]
    if (old && old !== ws) {
      old.close(4003, "replaced")
    }

    ws.data.role = role
    ws.data.authenticated = true
    conn[role] = ws
    send(ws, { type: "auth.result", ok: true } satisfies AuthResult)
    console.log(`[relay] ${role} 已连接`)

    // 投递离线消息
    const q = role === "plugin" ? pluginQueue : phoneQueue
    for (const env of q.flush()) {
      sendEnvelope(ws, env)
    }
    q.clear()
  }

  // 已认证后的消息路由
  function handleMessage(ws: ServerWebSocket<WsData>, raw: string) {
    const parsed = JSON.parse(raw) as Record<string, unknown>

    if ("ack" in parsed) return

    // pong 心跳回应
    if (parsed.type === "pong") {
      ws.data.alive = true
      return
    }

    const role = ws.data.role
    if (role === "plugin") {
      // plugin 发事件 → 转给 phone
      const event = parsed as RemoteEvent
      const env = phoneQueue.push(event)
      if (conn.phone?.data.authenticated) {
        sendEnvelope(conn.phone, env)
      }
    } else if (role === "phone") {
      // phone 发操作 → 转给 plugin
      const action = parsed as RemoteAction
      const env = pluginQueue.push(action)
      if (conn.plugin?.data.authenticated) {
        sendEnvelope(conn.plugin, env)
      }
    }
  }

  // 心跳定时器：30 秒发 ping，10 秒等 pong
  const heartbeat = setInterval(() => {
    for (const role of ["plugin", "phone"] as const) {
      const ws = conn[role]
      if (!ws) continue
      if (!ws.data.alive) {
        console.log(`[relay] ${role} 心跳超时，断开`)
        ws.close(4004, "heartbeat timeout")
        conn[role] = null
        continue
      }
      ws.data.alive = false
      send(ws, { type: "ping" })
    }
  }, 30_000)

  return {
    conn,
    heartbeat,

    open(ws: ServerWebSocket<WsData>) {
      ws.data.alive = true
      ws.data.authenticated = false
    },

    message(ws: ServerWebSocket<WsData>, raw: string | Buffer) {
      const str = typeof raw === "string" ? raw : raw.toString()
      try {
        if (!ws.data.authenticated) {
          const msg = JSON.parse(str) as AuthHandshake
          if (msg.type !== "auth") {
            ws.close(4005, "auth required")
            return
          }
          handleAuth(ws, msg)
          return
        }
        handleMessage(ws, str)
      } catch (e) {
        console.error("[relay] 消息解析失败:", e)
      }
    },

    close(ws: ServerWebSocket<WsData>) {
      const role = ws.data.role
      if (role && conn[role] === ws) {
        conn[role] = null
        console.log(`[relay] ${role} 已断开`)
      }
    },

    status() {
      return {
        plugin: conn.plugin?.data.authenticated ?? false,
        phone: conn.phone?.data.authenticated ?? false,
      }
    },
  }
}

export type WsHandler = ReturnType<typeof createWsHandler>
