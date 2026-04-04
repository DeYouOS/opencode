/** 获取或生成 relay token */
export function resolveToken(): string {
  const env = process.env.RELAY_TOKEN
  if (env) return env
  const generated = crypto.randomUUID()
  console.log(`[relay] 未设置 RELAY_TOKEN，已自动生成: ${generated}`)
  return generated
}

/** 校验 token */
export function verify(input: string, expected: string): boolean {
  if (input.length !== expected.length) return false
  const a = new TextEncoder().encode(input)
  const b = new TextEncoder().encode(expected)
  let diff = 0
  for (let i = 0; i < a.length; i++) diff |= (a[i] ?? 0) ^ (b[i] ?? 0)
  return diff === 0
}
