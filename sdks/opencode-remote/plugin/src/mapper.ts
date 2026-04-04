import type { Event } from "@opencode-ai/sdk"
import type {
  RemoteEvent,
  PermissionEvent,
  PermissionRepliedEvent,
  SessionStatusEvent,
  SessionInfoEvent,
  SessionErrorEvent,
  MessageDeltaEvent,
  ToolUpdateEvent,
  TodoUpdateEvent,
  MessageInfoEvent,
} from "../../shared/protocol"

export function mapEvent(event: Event): RemoteEvent | null {
  switch (event.type) {
    case "permission.updated":
      return {
        type: "event.permission",
        data: {
          id: event.properties.id,
          kind: event.properties.type,
          pattern: event.properties.pattern,
          sessionID: event.properties.sessionID,
          messageID: event.properties.messageID,
          callID: event.properties.callID,
          title: event.properties.title,
          metadata: event.properties.metadata,
          created: event.properties.time.created,
        },
      } satisfies PermissionEvent

    case "permission.replied":
      return {
        type: "event.permission.replied",
        data: {
          sessionID: event.properties.sessionID,
          permissionID: event.properties.permissionID,
          response: event.properties.response,
        },
      } satisfies PermissionRepliedEvent

    case "session.status":
      return {
        type: "event.session.status",
        data: {
          sessionID: event.properties.sessionID,
          status: event.properties.status,
        },
      } satisfies SessionStatusEvent

    case "session.created":
    case "session.updated":
    case "session.deleted": {
      const info = event.properties.info
      const mapped =
        event.type === "session.created"
          ? "event.session.created"
          : event.type === "session.updated"
            ? "event.session.updated"
            : "event.session.deleted"
      return {
        type: mapped,
        data: {
          id: info.id,
          title: info.title,
          created: info.time.created,
          updated: info.time.updated,
        },
      } satisfies SessionInfoEvent
    }

    case "session.error":
      return {
        type: "event.session.error",
        data: {
          sessionID: event.properties.sessionID,
          error: event.properties.error
            ? {
                name: event.properties.error.name,
                message:
                  "data" in event.properties.error
                    ? String((event.properties.error as Record<string, unknown>).data)
                    : undefined,
              }
            : undefined,
        },
      } satisfies SessionErrorEvent

    case "message.part.updated": {
      const part = event.properties.part
      if (part.type === "text" || part.type === "reasoning") {
        return {
          type: "event.message.delta",
          data: {
            sessionID: part.sessionID,
            messageID: part.messageID,
            partID: part.id,
            partType: part.type,
            delta: event.properties.delta,
            text: part.text,
          },
        } satisfies MessageDeltaEvent
      }
      if (part.type === "tool") {
        return {
          type: "event.tool.update",
          data: {
            sessionID: part.sessionID,
            messageID: part.messageID,
            partID: part.id,
            tool: part.tool,
            callID: part.callID,
            status: part.state.status,
            title: "title" in part.state ? (part.state.title as string) : undefined,
            input: part.state.input,
            output: "output" in part.state ? (part.state.output as string) : undefined,
            error: "error" in part.state ? (part.state.error as string) : undefined,
            time: "time" in part.state ? (part.state.time as { start?: number; end?: number }) : undefined,
          },
        } satisfies ToolUpdateEvent
      }
      return null
    }

    case "todo.updated":
      return {
        type: "event.todo.updated",
        data: {
          sessionID: event.properties.sessionID,
          todos: event.properties.todos.map((t) => ({
            id: t.id,
            content: t.content,
            status: t.status,
            priority: t.priority,
          })),
        },
      } satisfies TodoUpdateEvent

    case "message.updated": {
      const msg = event.properties.info
      if (msg.role === "assistant") {
        return {
          type: "event.message.info",
          data: {
            sessionID: msg.sessionID,
            messageID: msg.id,
            role: msg.role,
            cost: msg.cost,
            tokens: {
              input: msg.tokens.input,
              output: msg.tokens.output,
              reasoning: msg.tokens.reasoning,
            },
            error: msg.error
              ? {
                  name: msg.error.name,
                  message: "data" in msg.error ? String((msg.error as Record<string, unknown>).data) : undefined,
                }
              : undefined,
          },
        } satisfies MessageInfoEvent
      }
      return null
    }

    default:
      return null
  }
}
