import type { PluginInput } from "@opencode-ai/plugin"
import type { AuthHandshake, AuthResult, RemoteEvent, WsEnvelope } from "../../shared/protocol"
import fs from "fs"
import path from "path"
import os from "os"

type ActionHandler = (env: WsEnvelope) => void
type ConnectHandler = () => void

const LOCK = path.join(os.tmpdir(), "opencode-remote-plugin.lock")
const PROBE = 15_000

/**
 * 尝试获取文件锁。锁文件中写入当前 PID，
 * 如果已有锁且持有者进程存活则获取失败。
 */
function acquire(): boolean {
  try {
    if (fs.existsSync(LOCK)) {
      const pid = parseInt(fs.readFileSync(LOCK, "utf8").trim())
      if (!isNaN(pid) && pid !== process.pid) {
        try {
          process.kill(pid, 0)
          return false
        } catch {
          // 持有者已退出，锁过期
        }
      }
    }
    fs.writeFileSync(LOCK, String(process.pid))
    return true
  } catch {
    return false
  }
}

function release() {
  try {
    if (!fs.existsSync(LOCK)) return
    const pid = parseInt(fs.readFileSync(LOCK, "utf8").trim())
    if (pid === process.pid) fs.unlinkSync(LOCK)
  } catch {}
}

/**
 * 检查当前进程是否持有锁，或锁是否可获取。
 * 用于周期性探测：如果 leader 进程退出了，其他实例可以接管。
 */
function probe(): boolean {
  try {
    if (!fs.existsSync(LOCK)) return acquire()
    const pid = parseInt(fs.readFileSync(LOCK, "utf8").trim())
    if (pid === process.pid) return true
    if (!isNaN(pid)) {
      try {
        process.kill(pid, 0)
        return false
      } catch {
        return acquire()
      }
    }
    return acquire()
  } catch {
    return false
  }
}

export function connect(url: string, token: string, _input: PluginInput) {
  if (!acquire()) {
    console.log("[remote] 其他实例已持有锁，进入待命")
    const noop = {
      send(_event: RemoteEvent) {},
      onAction(_fn: ActionHandler) {},
      onConnected(_fn: ConnectHandler) {},
      close() {
        clearInterval(timer)
      },
    }
    let handler: ActionHandler | null = null
    let connectedFn: ConnectHandler | null = null
    let promoted = false

    // 周期性探测：leader 退出后自动接管
    const timer = setInterval(() => {
      if (promoted) return
      if (probe()) {
        console.log("[remote] leader 已退出，接管连接")
        promoted = true
        clearInterval(timer)
        const real = doConnect(url, token)
        noop.send = (e) => real.send(e)
        real.onAction((env) => handler?.(env))
        real.onConnected(() => connectedFn?.())
        noop.close = () => real.close()
      }
    }, PROBE)

    noop.onAction = (fn) => {
      handler = fn
    }
    noop.onConnected = (fn) => {
      connectedFn = fn
    }
    return noop
  }

  return doConnect(url, token)
}

function doConnect(url: string, token: string) {
  let ws: WebSocket | null = null
  let handler: ActionHandler | null = null
  let connectedFn: ConnectHandler | null = null
  let retry = 0
  let stopped = false
  const maxDelay = 30_000

  function open() {
    if (stopped) return
    const endpoint = url.replace(/\/$/, "").replace(/\/ws$/, "") + "/ws"
    console.log(`[remote] 连接 relay: ${endpoint}`)
    ws = new WebSocket(endpoint)

    ws.onopen = () => {
      retry = 0
      const auth: AuthHandshake = { type: "auth", role: "plugin", token }
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
      if (ev.code === 4003) {
        console.log("[remote] 被新实例替换，释放锁并停止")
        stopped = true
        release()
        return
      }
      if (stopped) return
      console.log("[remote] 连接断开，准备重连...")
      const delay = Math.min(1000 * Math.pow(2, retry), maxDelay)
      retry++
      setTimeout(open, delay)
    }

    ws.onerror = (err) => {
      console.error("[remote] WebSocket 错误:", err)
    }
  }

  // 进程退出时释放锁
  const cleanup = () => {
    release()
  }
  process.on("exit", cleanup)
  process.on("SIGINT", cleanup)
  process.on("SIGTERM", cleanup)

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
      release()
      ws?.close()
    },
  }
}
