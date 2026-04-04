import type { PluginInput } from "@opencode-ai/plugin"
import type { RemoteAction } from "../../shared/protocol"

/**
 * 将手机端操作转发到本地 OpenCode API
 *
 * SDK 中尚未包含 question 相关方法，对这些端点使用 HTTP 直接调用
 */
export async function dispatch(action: RemoteAction, input: PluginInput) {
  const client = input.client
  const base = input.serverUrl.toString().replace(/\/$/, "")
  const fs = await import("fs")
  const log = (msg: string) =>
    fs.appendFileSync("/tmp/opencode-remote-dispatch.log", `${new Date().toISOString()} ${msg}\n`)

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
            agent: action.data.agent ?? undefined,
          },
        })
        log(`promptAsync result: ${JSON.stringify(res)}`)
      } catch (e) {
        log(`promptAsync error: ${e}`)
      }
      break

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
