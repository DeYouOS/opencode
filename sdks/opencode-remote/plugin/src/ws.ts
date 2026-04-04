import type { PluginInput } from "@opencode-ai/plugin"
import type { AuthHandshake, AuthResult, RemoteEvent, WsEnvelope } from "../../shared/protocol"

type ActionHandler = (env: WsEnvelope) => void

export function connect(url: string, token: string, input: PluginInput) {
  let ws: WebSocket | null = null
  let handler: ActionHandler | null = null
  let retry = 0
  const maxRetry = 30_000

  function open() {
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
        return
      }
      if (data.type === "ping") {
        ws?.send(JSON.stringify({ type: "pong" }))
        return
      }
      // relay 转发来的操作消息
      if (data.seq && data.payload) {
        const env = data as WsEnvelope
        ws?.send(JSON.stringify({ ack: env.seq }))
        handler?.(env)
      }
    }

    ws.onclose = () => {
      console.log("[remote] 连接断开，准备重连...")
      ws = null
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

    close() {
      ws?.close()
    },
  }
}
