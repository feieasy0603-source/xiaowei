const REF_KEY = 'xiaowei_invite_ref'

export function getStoredInviteCode(): string {
  return localStorage.getItem(REF_KEY)?.trim() ?? ''
}

export function setStoredInviteCode(code: string) {
  const trimmed = code.trim().toUpperCase()
  if (trimmed) localStorage.setItem(REF_KEY, trimmed)
  else localStorage.removeItem(REF_KEY)
}

/** 从 URL query 读取 ref / invite 并持久化 */
export function captureInviteFromQuery(query: Record<string, unknown>) {
  const raw = query.ref ?? query.invite
  if (typeof raw === 'string' && raw.trim()) {
    setStoredInviteCode(raw)
  }
}

export function consumeInviteCode(): string | undefined {
  const code = getStoredInviteCode()
  if (!code) return undefined
  localStorage.removeItem(REF_KEY)
  return code
}
