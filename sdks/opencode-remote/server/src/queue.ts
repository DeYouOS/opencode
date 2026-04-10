import type { WsEnvelope, WsPayload } from "../../shared/protocol"

const MAX = 1000

/** 有界事件队列，超出容量丢弃最旧消息 */
export function createQueue() {
  const buf: WsEnvelope[] = []
  let counter = 0

  return {
    push(payload: WsPayload): WsEnvelope {
      counter++
      const env: WsEnvelope = { seq: counter, ts: Date.now(), payload }
      buf.push(env)
      if (buf.length > MAX) buf.shift()
      return env
    },

    since(seq: number): WsEnvelope[] {
      return buf.filter((e) => e.seq > seq)
    },

    latest(): number {
      return counter
    },

    flush(): WsEnvelope[] {
      return [...buf]
    },

    clear() {
      buf.length = 0
    },
  }
}

export type Queue = ReturnType<typeof createQueue>
