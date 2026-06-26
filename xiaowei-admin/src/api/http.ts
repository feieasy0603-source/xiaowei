import type { Router } from 'vue-router'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const TOKEN_KEY = 'xiaowei_admin_token'

let adminRouter: Router | null = null

/** 在 main.ts 中绑定，401 时用路由跳转而非整页刷新 */
export function bindAdminRouter(router: Router) {
  adminRouter = router
}

export function getAdminToken() {
  return localStorage.getItem(TOKEN_KEY)
}

export function setAdminToken(t: string | null) {
  if (t) localStorage.setItem(TOKEN_KEY, t)
  else localStorage.removeItem(TOKEN_KEY)
}

function isOnLoginPage() {
  const base = import.meta.env.BASE_URL.replace(/\/$/, '') || '/admin'
  return window.location.pathname === `${base}/login` || window.location.pathname.endsWith('/login')
}

function loginRedirectPath() {
  const base = import.meta.env.BASE_URL.replace(/\/$/, '') || '/admin'
  let path = window.location.pathname + window.location.search
  if (path.startsWith(base)) path = path.slice(base.length) || '/'
  return path
}

function redirectToLogin() {
  if (isOnLoginPage()) return
  setAdminToken(null)
  const redirect = loginRedirectPath()
  if (adminRouter) {
    void adminRouter.replace({ name: 'login', query: { redirect } })
    return
  }
  const base = import.meta.env.BASE_URL.replace(/\/$/, '')
  window.location.assign(`${base}/login?redirect=${encodeURIComponent(redirect)}`)
}

export type AdminFetchOptions = RequestInit & { skipAuthRedirect?: boolean }

const API_BASE = (import.meta.env.VITE_API_BASE || '/api').replace(/\/$/, '')

export function adminApiUrl(path: string): string {
  const p = path.startsWith('/') ? path : `/${path}`
  return `${API_BASE}${p}`
}

export async function adminFetch<T>(path: string, init?: AdminFetchOptions): Promise<T> {
  const { skipAuthRedirect, ...requestInit } = init ?? {}
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(requestInit.headers as Record<string, string>),
  }
  const token = getAdminToken()
  if (token) headers.Authorization = `Bearer ${token}`

  let res: Response
  try {
    res = await fetch(adminApiUrl(path), { ...requestInit, headers })
  } catch {
    throw new Error('无法连接后端，请确认 xiaowei-server 已启动（端口 8080）')
  }

  let json: ApiResponse<T>
  try {
    json = (await res.json()) as ApiResponse<T>
  } catch {
    if (res.status === 401 || res.status === 403) {
      if (!skipAuthRedirect) redirectToLogin()
      throw new Error(res.status === 403 ? '无管理端权限，请使用管理员账号登录' : '请先登录')
    }
    throw new Error(res.ok ? '响应解析失败' : `HTTP ${res.status}`)
  }

  const unauthorized = json.code === 401 || res.status === 401
  const forbidden = json.code === 403 || res.status === 403
  if (unauthorized || forbidden) {
    if (!skipAuthRedirect) redirectToLogin()
    throw new Error(
      json.message || (forbidden ? '无管理端权限，请使用管理员账号登录' : '请先登录'),
    )
  }
  if (json.code !== 0) throw new Error(json.message || '请求失败')
  return json.data
}
