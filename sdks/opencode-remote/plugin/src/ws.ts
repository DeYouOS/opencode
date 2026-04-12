import type { AuthHandshake, AuthResult, RemoteEvent, WsEnvelope } from "../../shared/protocol"

type ActionHandler = (env: WsEnvelope) => void
type ConnectHandler = () => void

export function connect(url: string, token: string, instanceId: string) {
  let ws: WebSocket | null = null
  let handler: ActionHandler | null = null
  let connectedFn: ConnectHandler | null = null
  let retry = 0
  let stopped = false
  const maxDelay = 30_000

  function open() {
    if (stopped) return
    const endpoint = url.replace(/\/$/, "").replace(/\/ws$/, "") + "/ws"
    ws = new WebSocket(endpoint)

    ws.onopen = () => {
      retry = 0
      const auth: AuthHandshake = { type: "auth", role: "plugin", token, instance: instanceId }
      ws!.send(JSON.stringify(auth))
    }

    ws.onmessage = (ev) => {
      const data = JSON.parse(String(ev.data))
      if (data.type === "auth.result") {
        if (!(data as AuthResult).ok) return
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

    ws.onclose = () => {
      ws = null
      if (stopped) return
      const delay = Math.min(1000 * Math.pow(2, retry), maxDelay)
      retry++
      setTimeout(open, delay)
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
      stopped = true
      ws?.close()
    },
  }
}
