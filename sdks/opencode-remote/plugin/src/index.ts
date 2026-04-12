import { randomUUID } from "crypto"
import type { PluginModule, PluginInput, Hooks } from "@opencode-ai/plugin"
import type { Event } from "@opencode-ai/sdk"
import { connect } from "./ws"
import { mapEvent } from "./mapper"
import { dispatch, fetchProviderList } from "./dispatcher"
import type { RemoteAction, InstanceInfoEvent, ProviderListEvent, WsEnvelope } from "../../shared/protocol"

type SessionItem = {
  id: string
  title: string
  status?: { type?: string }
}

// 用 serverUrl 直接 fetch，绕过 v1 SDK 不支持 roots 参数的问题
async function listRootSessions(serverUrl: URL, directory: string): Promise<SessionItem[]> {
  const base = serverUrl.toString().replace(/\/$/, "")
  const dir = encodeURIComponent(directory)
  const res = await fetch(`${base}/session?directory=${dir}&roots=true`)
  return (await res.json()) as SessionItem[]
}

async function pushInstanceInfo(instanceId: string, input: PluginInput, send: (e: InstanceInfoEvent) => void) {
  try {
    let sessions: SessionItem[]
    try {
      sessions = await listRootSessions(input.serverUrl, input.directory ?? "")
    } catch {
      // fetch 失败时 fallback 到 SDK（不带 roots 过滤）
      const res = await input.client.session.list({ directory: input.directory })
      sessions = (res.data ?? []) as SessionItem[]
    }
    send({
      type: "event.instance.info",
      data: {
        instanceId,
        version: "",
        project: input.directory?.split("/").filter(Boolean).pop() ?? input.project.id ?? "",
        directory: input.directory ?? "",
        sessions: sessions.map((s) => ({
          id: s.id,
          title: s.title ?? "",
          status: (s.status?.type ?? "idle") as "idle" | "busy" | "retry",
        })),
      },
    })
  } catch {}
}

async function pushProviderList(input: PluginInput, send: (e: ProviderListEvent) => void) {
  try {
    const data = await fetchProviderList(input)
    send({ type: "event.provider.list", data })
  } catch {}
}

const plugin: PluginModule = {
  id: "opencode-remote",

  async server(input: PluginInput, options): Promise<Hooks> {
    const url = (options?.relay_url as string) ?? process.env.OPENCODE_REMOTE_URL
    const token = (options?.relay_token as string) ?? process.env.OPENCODE_REMOTE_TOKEN
    if (!url || !token) return {}

    const instanceId = `term-${process.pid}-${randomUUID().slice(0, 8)}`
    const ws = connect(url, token, instanceId)

    ws.onConnected(() => {
      pushInstanceInfo(instanceId, input, (e) => ws.send(e))
      pushProviderList(input, (e) => ws.send(e))
    })

    ws.onAction((env: WsEnvelope) => {
      const action = env.payload as RemoteAction
      if (action.type === "action.refresh") {
        pushInstanceInfo(instanceId, input, (e) => ws.send(e))
        pushProviderList(input, (e) => ws.send(e))
        return
      }
      dispatch(action, input, (e) => ws.send(e)).catch(() => {})
    })

    return {
      async event({ event }: { event: Event }) {
        const mapped = mapEvent(event)
        if (mapped) ws.send(mapped)
      },
    }
  },
}

export default plugin
