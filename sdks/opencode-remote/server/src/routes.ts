import { Hono } from "hono"
import { verify } from "./auth"
import type { WsHandler } from "./ws"
import type { Queue } from "./queue"
import type { ActionRequest, EventsResponse, RemoteAction } from "../../shared/protocol"

export function createRoutes(token: string, handler: WsHandler, phoneQueue: Queue, pluginQueue: Queue) {
  const app = new Hono()

  app.get("/api/health", (c) => {
    return c.json({ ok: true, ...handler.status() })
  })

  // REST 降级：手机端轮询获取事件
  app.get("/api/events", (c) => {
    const t = c.req.query("token")
    if (!t || !verify(t, token)) return c.json({ error: "unauthorized" }, 401)
    const since = parseInt(c.req.query("since") ?? "0", 10)
    const resp: EventsResponse = {
      events: phoneQueue.since(since),
      latest: phoneQueue.latest(),
    }
    return c.json(resp)
  })

  // REST 降级：手机端通过 HTTP 发送操作
  app.post("/api/action", async (c) => {
    const body = (await c.req.json()) as ActionRequest
    if (!verify(body.token, token)) return c.json({ error: "unauthorized" }, 401)
    const env = pluginQueue.push(body.action)
    const ws = handler.conn.plugin
    if (ws?.data.authenticated) {
      ws.send(JSON.stringify(env))
    }
    return c.json({ ok: true, seq: env.seq })
  })

  return app
}
