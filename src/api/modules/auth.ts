import { apiFetch, setToken } from '@/api/http'
export interface AuthResult {
  token: string
  userId?: number
  phone?: string
  nickname?: string
  balance?: number
  inviteApplied?: boolean
  inviteInvalid?: boolean
  inviteRewardEnabled?: boolean
  inviteeReward?: number
}

export interface UserWritingPreferences {
  degree?: string
  wordCount?: number
  model?: string
  schoolId?: string
  category?: string
  language?: string
}

export interface UserMe {
  id: number
  phone: string
  nickname: string
  balance: number
  vipLevel: number
  preferences?: UserWritingPreferences
}

export async function updateProfile(body: {
  nickname?: string
  preferences?: UserWritingPreferences
}): Promise<UserMe> {
  return apiFetch<UserMe>('/auth/profile', {
    method: 'PUT',
    body: JSON.stringify(body),
  })
}

export async function demoLogin(): Promise<AuthResult> {
  const data = await apiFetch<AuthResult>('/auth/demo', { method: 'POST' })
  setToken(data.token)
  return data
}

export async function login(phone: string, password: string): Promise<AuthResult> {
  const data = await apiFetch<AuthResult>('/auth/login', {
    method: 'POST',
    body: JSON.stringify({ phone, password }),
  })
  setToken(data.token)
  return data
}

export async function changePassword(
  oldPassword: string,
  newPassword: string,
  confirmPassword: string,
): Promise<void> {
  await apiFetch<void>('/auth/change-password', {
    method: 'POST',
    body: JSON.stringify({ oldPassword, newPassword, confirmPassword }),
  })
}

export interface ShareInfo {
  referralCode: string
  shareLink: string
  invitedCount: number
  inviterReward: number
  inviteeReward: number
  enabled?: boolean
  shareLinkRelative?: boolean
  rules: string
}

export async function fetchShareInfo(): Promise<ShareInfo> {
  return apiFetch<ShareInfo>('/auth/share-info')
}

export async function register(
  phone: string,
  password: string,
  confirmPassword: string,
  nickname?: string,
  inviteCode?: string,
): Promise<AuthResult> {
  const data = await apiFetch<AuthResult>('/auth/register', {
    method: 'POST',
    body: JSON.stringify({ phone, password, confirmPassword, nickname, inviteCode }),
  })
  setToken(data.token)
  return data
}

export async function fetchMe(): Promise<UserMe> {
  return apiFetch<UserMe>('/auth/me')
}

export function logout() {
  setToken(null)
}
