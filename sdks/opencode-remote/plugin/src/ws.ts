import type { PluginInput } from "@opencode-ai/plugin"
import type { AuthHandshake, AuthResult, RemoteEvent, WsEnvelope } from "../../shared/protocol"

type ActionHandler = (env: WsEnvelope) => void
type ConnectHandler = () => void

// 全局锁：同一进程内只允许一个 plugin 实例连接 relay
let active = false

export function connect(url: string, token: string, input: PluginInput) {
  if (active) {
    console.log("[remote] 已有活跃连接，跳过重复连接")
    return {
      send(_event: RemoteEvent) {},
      onAction(_fn: ActionHandler) {},
      onConnected(_fn: ConnectHandler) {},
      close() {},
    }
  }
  active = true

  let ws: WebSocket | null = null
  let handler: ActionHandler | null = null
  let connectedFn: ConnectHandler | null = null
  let retry = 0
  // 被 relay 踢掉时不重连（code 4003 = replaced by newer plugin）
  let replaced = false
  const maxRetry = 30_000

  function open() {
    if (replaced) return
    const endpoint = url.replace(/\/$/, "") + "/ws"
    console.log(`[remote] 连接 relay: ${endpoint}`)
    ws = new WebSocket(endpoint)

    ws.onopen = () => {
      retry = 0
      const auth: AuthHandshake = {
        type: "auth",
        role: "plugin",
        token,
        instance: input.project.id,
      }
      ws!.send(JSON.stringify(auth))
    }

    ws.onmessage = (ev) => {
      const data = JSON.parse(String(ev.data))
      if (data.type === "auth.result") {
        const result = data as AuthResult
        if (!result.ok) {
          console.error(`[remote] 认证失败: ${result.error}`)
          return
        }
        console.log("[remote] 已连接到 relay server")
        connectedFn?.()
        return
      }
      if (data.type === "ping") {
        ws?.send(JSON.stringify({ type: "pong" }))
        return
      }
      if (data.seq && data.payload) {
        const env = data as WsEnvelope
        ws?.send(JSON.stringify({ ack: env.seq }))
        handler?.(env)
      }
    }

    ws.onclose = (ev) => {
      ws = null
      // 被新实例替换（relay 发 4003），放弃重连
      if (ev.code === 4003) {
        console.log("[remote] 被新 plugin 实例替换，停止重连")
        replaced = true
        active = false
        return
      }
      console.log("[remote] 连接断开，准备重连...")
      const delay = Math.min(1000 * Math.pow(2, retry), maxRetry)
      retry++
      setTimeout(open, delay)
    }

    ws.onerror = (err) => {
      console.error("[remote] WebSocket 错误:", err)
    }
  }

  open()

  return {
    send(event: RemoteEvent) {
      if (ws?.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify(event))
      }
    },

    onAction(fn: ActionHandler) {
      handler = fn
    },

    onConnected(fn: ConnectHandler) {
      connectedFn = fn
    },

    close() {
      replaced = true
      active = false
      ws?.close()
    },
  }
}
