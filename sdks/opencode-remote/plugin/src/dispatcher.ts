import type { PluginInput } from "@opencode-ai/plugin"
import type {
  RemoteAction,
  RemoteEvent,
  ActionErrorEvent,
  ProviderListEvent,
  CommandListEvent,
} from "../../shared/protocol"

type SendFn = (event: RemoteEvent) => void

export async function fetchProviderList(input: PluginInput): Promise<ProviderListEvent["data"]> {
  const result = await input.client.provider.list()
  const all = (result.data as any).all ?? []
  const connected: string[] = (result.data as any).connected ?? []
  const connectedSet = new Set(connected)
  const providers = all
    .filter((p: any) => connectedSet.has(p.id))
    .map((p: any) => ({
      id: p.id,
      name: p.name,
      connected: true,
      models: Object.values(p.models as Record<string, any>).map((m: any) => ({
        id: m.id,
        name: m.name,
        reasoning: m.reasoning ?? false,
        context: m.limit?.context ?? 0,
        output: m.limit?.output ?? 0,
      })),
    }))
  return { providers }
}

// 获取后端注册的所有命令列表
export async function fetchCommandList(input: PluginInput): Promise<CommandListEvent["data"]> {
  const res = await input.client.command.list()
  const list = res.data as any as Array<{
    name: string
    description?: string
    hints?: string[]
    subtask?: boolean
  }>
  return {
    commands: list
      .filter((c) => !c.subtask)
      .map((c) => ({
        name: c.name,
        description: c.description,
        hints: c.hints,
      })),
  }
}

export async function dispatch(action: RemoteAction, input: PluginInput, send?: SendFn) {
  const client = input.client
  const base = input.serverUrl.toString().replace(/\/$/, "")
  const fs = await import("fs")
  const log = (msg: string) =>
    fs.appendFileSync("/tmp/opencode-remote-dispatch.log", `${new Date().toISOString()} ${msg}\n`)

  const sendError = (actionType: string, err: unknown, sessionID?: string) => {
    const msg = err instanceof Error ? err.message : String(err)
    log(`${actionType} error: ${msg}`)
    send?.({
      type: "event.action.error",
      data: { sessionID, actionType, error: msg },
    } satisfies ActionErrorEvent)
  }

  switch (action.type) {
    case "action.permission.reply":
      await client.postSessionIdPermissionsPermissionId({
        path: { id: action.data.sessionID, permissionID: action.data.permissionID },
        body: { response: action.data.response },
      })
      break

    case "action.session.message":
      log(`promptAsync sessionID=${action.data.sessionID} content="${action.data.content}"`)
      try {
        const res = await client.session.promptAsync({
          path: { id: action.data.sessionID },
          body: {
            parts: [{ type: "text" as const, text: action.data.content }],
            agent: action.data.agent || "build",
            model: action.data.model ?? undefined,
          },
        })
        log(`promptAsync result: ${JSON.stringify(res)}`)
      } catch (e) {
        sendError("action.session.message", e, action.data.sessionID)
      }
      break

    case "action.session.command": {
      const d = action.data
      log(`command sessionID=${d.sessionID} command=${d.command} args=${d.arguments ?? ""}`)
      try {
        const base = input.serverUrl.toString().replace(/\/$/, "")
        await fetch(`${base}/session/${d.sessionID}/command`, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            command: d.command,
            arguments: d.arguments ?? "",
            agent: d.agent,
            model: d.model ? `${d.model.providerID}/${d.model.modelID}` : undefined,
          }),
        })
      } catch (e) {
        sendError("action.session.command", e, d.sessionID)
      }
      break
    }

    case "action.session.abort":
      await client.session.abort({
        path: { id: action.data.sessionID },
      })
      break

    case "action.session.create":
      await client.session.create({
        body: { title: action.data.title },
      })
      break

    case "action.question.reply":
      await fetch(`${base}/question/${action.data.questionID}/reply`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ answers: [action.data.answer] }),
      })
      break

    case "action.question.reject":
      await fetch(`${base}/question/${action.data.questionID}/reject`, {
        method: "POST",
      })
      break

    case "action.refresh":
      break
  }
}
