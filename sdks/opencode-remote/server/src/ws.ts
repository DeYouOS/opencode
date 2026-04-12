import type { ServerWebSocket } from "bun"
import type {
  AuthHandshake,
  AuthResult,
  WsEnvelope,
  RemoteEvent,
  RemoteAction,
  InstanceDisconnectedEvent,
  InstanceSyncEvent,
} from "../../shared/protocol"
import { verify } from "./auth"
import type { Queue } from "./queue"

type Role = "plugin" | "phone"

type WsData = {
  role?: Role
  instanceId?: string
  alive: boolean
  authenticated: boolean
}

export function createWsHandler(token: string, pluginQueue: Queue, phoneQueue: Queue) {
  let phone: ServerWebSocket<WsData> | null = null
  const plugins = new Map<string, ServerWebSocket<WsData>>()

  function sendEnvelope(ws: ServerWebSocket<WsData>, env: WsEnvelope) {
    ws.send(JSON.stringify(env))
  }

  function broadcastToPhone(env: WsEnvelope) {
    if (phone?.data.authenticated) sendEnvelope(phone, env)
  }

  function broadcastToPlugins(env: WsEnvelope) {
    for (const ws of plugins.values()) {
      if (ws.data.authenticated) sendEnvelope(ws, env)
    }
  }

  function handleAuth(ws: ServerWebSocket<WsData>, msg: AuthHandshake) {
    if (!verify(msg.token, token)) {
      ws.send(JSON.stringify({ type: "auth.result", ok: false, error: "invalid token" } satisfies AuthResult))
      ws.close(4001, "auth failed")
      return
    }
    const role = msg.role
    if (role !== "plugin" && role !== "phone") {
      ws.send(JSON.stringify({ type: "auth.result", ok: false, error: "invalid role" } satisfies AuthResult))
      ws.close(4002, "invalid role")
      return
    }

    ws.data.role = role
    ws.data.authenticated = true

    if (role === "phone") {
      if (phone && phone !== ws) phone.close(4003, "replaced")
      phone = ws
    } else {
      const iid = msg.instance || `plugin-${Date.now()}`
      ws.data.instanceId = iid
      plugins.set(iid, ws)
    }

    ws.send(JSON.stringify({ type: "auth.result", ok: true } satisfies AuthResult))
    console.log(`[relay] ${role} 已连接${role === "plugin" ? ` (${ws.data.instanceId})` : ""}, plugins=${plugins.size}`)

    // phone 连接后推送当前所有在线 plugin 列表，让 App 同步终端状态
    if (role === "phone") {
      const sync: InstanceSyncEvent = {
        type: "event.instance.sync",
        data: { instanceIds: [...plugins.keys()] },
      }
      sendEnvelope(ws, { seq: 0, ts: Date.now(), payload: sync })
      // 通知所有 plugin 重新推送信息（instanceInfo、providerList、commandList）
      broadcastToPlugins({ seq: 0, ts: Date.now(), payload: { type: "action.refresh" } as RemoteAction })
    }

    const q = role === "plugin" ? pluginQueue : phoneQueue
    for (const env of q.flush()) {
      sendEnvelope(ws, env)
    }
    q.clear()
  }

  function handleMessage(ws: ServerWebSocket<WsData>, raw: string) {
    const parsed = JSON.parse(raw) as Record<string, unknown>
    if ("ack" in parsed) return
    if (parsed.type === "pong") {
      ws.data.alive = true
      return
    }

    const role = ws.data.role
    if (role === "plugin") {
      const env = phoneQueue.push(parsed as RemoteEvent)
      broadcastToPhone(env)
    } else if (role === "phone") {
      const env = pluginQueue.push(parsed as RemoteAction)
      broadcastToPlugins(env)
    }
  }

  const heartbeat = setInterval(() => {
    for (const [iid, ws] of plugins) {
      if (!ws.data.alive) {
        console.log(`[relay] plugin (${iid}) 心跳超时，断开`)
        ws.close(4004, "heartbeat timeout")
        plugins.delete(iid)
        // 通知 phone 移除该终端
        broadcastToPhone({
          seq: 0,
          ts: Date.now(),
          payload: {
            type: "event.instance.disconnected",
            data: { instanceId: iid },
          } satisfies InstanceDisconnectedEvent,
        })
        continue
      }
      ws.data.alive = false
      try {
        ws.send(JSON.stringify({ type: "ping" }))
      } catch {
        /* 发送失败说明连接已断 */
      }
    }
    if (phone) {
      if (!phone.data.alive) {
        console.log("[relay] phone 心跳超时，断开")
        phone.close(4004, "heartbeat timeout")
        phone = null
      } else {
        phone.data.alive = false
        try {
          phone.send(JSON.stringify({ type: "ping" }))
        } catch {}
      }
    }
  }, 15_000)

  return {
    plugins,
    phone,
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

    close(ws: ServerWebSocket<WsData>, code: number, reason: string) {
      const role = ws.data.role
      console.log(
        `[relay] close: role=${role} code=${code} reason=${reason || "无"} authenticated=${ws.data.authenticated}`,
      )
      if (role === "phone" && phone === ws) {
        phone = null
      } else if (role === "plugin" && ws.data.instanceId) {
        const iid = ws.data.instanceId
        plugins.delete(iid)
        console.log(`[relay] plugin (${iid}) 已断开, remaining=${plugins.size}`)
        // 通知 phone 移除该终端
        broadcastToPhone({
          seq: 0,
          ts: Date.now(),
          payload: {
            type: "event.instance.disconnected",
            data: { instanceId: iid },
          } satisfies InstanceDisconnectedEvent,
        })
      }
    },

    status() {
      return {
        plugins: plugins.size,
        phone: phone?.data.authenticated ?? false,
      }
    },
  }
}

export type WsHandler = ReturnType<typeof createWsHandler>
