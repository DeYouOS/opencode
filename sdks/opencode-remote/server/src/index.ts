import { resolveToken } from "./auth"
import { createQueue } from "./queue"
import { createWsHandler } from "./ws"
import { createRoutes } from "./routes"

const port = parseInt(process.env.PORT ?? "3100", 10)
const token = resolveToken()

const pluginQueue = createQueue()
const phoneQueue = createQueue()
const handler = createWsHandler(token, pluginQueue, phoneQueue)
const app = createRoutes(token, handler, phoneQueue, pluginQueue)

const server = Bun.serve({
  port,
  fetch(req, server) {
    const url = new URL(req.url)
    if (url.pathname === "/ws") {
      const ok = server.upgrade(req, { data: { alive: true, authenticated: false } })
      if (ok) return undefined
      return new Response("WebSocket upgrade failed", { status: 400 })
    }
    return app.fetch(req)
  },
  websocket: {
    open: handler.open,
    message: handler.message,
    close: handler.close,
  },
})

console.log(`[relay] 服务器启动于 http://localhost:${server.port}`)
console.log(`[relay] WebSocket 端点: ws://localhost:${server.port}/ws`)
console.log(`[relay] Token: ${token}`)
