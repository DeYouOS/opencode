import type { PluginModule, PluginInput, Hooks } from "@opencode-ai/plugin"
import type { Event } from "@opencode-ai/sdk"
import { connect } from "./ws"
import { mapEvent } from "./mapper"
import { dispatch } from "./dispatcher"
import type { RemoteAction, InstanceInfoEvent, WsEnvelope } from "../../shared/protocol"

async function pushInstanceInfo(input: PluginInput, send: (e: InstanceInfoEvent) => void) {
  try {
    const res = await input.client.session.list()
    const sessions = (res.data ?? []) as Array<{
      id: string
      title: string
      status?: { type?: string }
    }>
    send({
      type: "event.instance.info",
      data: {
        version: "",
        project: input.project.id ?? "",
        directory: input.directory ?? "",
        sessions: sessions.map((s) => ({
          id: s.id,
          title: s.title ?? "",
          status: (s.status?.type ?? "idle") as "idle" | "busy" | "retry",
        })),
      },
    })
  } catch (e) {
    console.error("[remote] pushInstanceInfo 失败:", e)
  }
}

const plugin: PluginModule = {
  id: "opencode-remote",

  async server(input: PluginInput, options): Promise<Hooks> {
    const url = (options?.relay_url as string) ?? process.env.OPENCODE_REMOTE_URL
    const token = (options?.relay_token as string) ?? process.env.OPENCODE_REMOTE_TOKEN
    if (!url || !token) {
      console.error("[remote] 缺少 relay_url 或 relay_token 配置")
      return {}
    }

    const ws = connect(url, token, input)

    ws.onConnected(() => {
      pushInstanceInfo(input, (e) => ws.send(e))
    })

    ws.onAction((env: WsEnvelope) => {
      const action = env.payload as RemoteAction
      if (action.type === "action.refresh") {
        pushInstanceInfo(input, (e) => ws.send(e))
        return
      }
      dispatch(action, input).catch((e) => console.error("[remote] dispatch 失败:", e))
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
