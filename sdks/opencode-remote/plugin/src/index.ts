import type { PluginModule, PluginInput, Hooks } from "@opencode-ai/plugin"
import type { Event } from "@opencode-ai/sdk"
import { connect } from "./ws"
import { mapEvent } from "./mapper"
import { dispatch } from "./dispatcher"
import type { RemoteAction, WsEnvelope } from "../../shared/protocol"

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

    // 收到 phone 端操作 → 调用本地 OpenCode API 执行
    ws.onAction((env: WsEnvelope) => {
      const action = env.payload as RemoteAction
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
