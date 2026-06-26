export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const TOKEN_KEY = 'xiaowei_token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

const API_TIMEOUT_MS = 8000

export type ApiFetchOptions = RequestInit & { timeoutMs?: number }

/** 业务错误（code 来自 ApiResponse，如 409 草稿冲突） */
export class ApiError extends Error {
  code: number
  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }
}

export async function apiFetch<T>(
  path: string,
  init?: ApiFetchOptions,
): Promise<T> {
  const { timeoutMs = API_TIMEOUT_MS, ...requestInit } = init ?? {}
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    ...(init?.headers as Record<string, string>),
  }
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const res = await fetch(`${base}${path}`, {
      ...requestInit,
      headers,
      signal: controller.signal,
    })
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    let json: ApiResponse<T>
    try {
      json = (await res.json()) as ApiResponse<T>
    } catch {
      throw new Error(`HTTP ${res.status}`)
    }
    if (json.code === 401) {
      setToken(null)
      void import('@/stores/app').then(({ useAppStore }) => useAppStore().logout())
      throw new Error(json.message || '请先登录')
    }
    if (json.code !== 0) {
      throw new ApiError(json.code, json.message || '请求失败')
    }
    return json.data
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new Error('请求超时，请确认后端服务已启动')
    }
    throw e
  } finally {
    window.clearTimeout(timer)
  }
}

export async function apiUpload<T>(
  path: string,
  file: File,
  timeoutMs = API_TIMEOUT_MS,
): Promise<T> {
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  const fd = new FormData()
  fd.append('file', file)
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const controller = new AbortController()
  const timer = window.setTimeout(() => controller.abort(), timeoutMs)

  try {
    const res = await fetch(`${base}${path}`, {
      method: 'POST',
      body: fd,
      headers,
      signal: controller.signal,
    })
    if (!res.ok) {
      throw new Error(`HTTP ${res.status}`)
    }
    let json: ApiResponse<T>
    try {
      json = (await res.json()) as ApiResponse<T>
    } catch {
      throw new Error(`HTTP ${res.status}`)
    }
    if (json.code === 401) {
      setToken(null)
      void import('@/stores/app').then(({ useAppStore }) => useAppStore().logout())
      throw new Error(json.message || '请先登录')
    }
    if (json.code !== 0) {
      throw new ApiError(json.code, json.message || '上传失败')
    }
    return json.data
  } catch (e) {
    if (e instanceof DOMException && e.name === 'AbortError') {
      throw new Error('上传超时，请确认后端服务已启动')
    }
    throw e
  } finally {
    window.clearTimeout(timer)
  }
}

/**
 * 是否走后端 API。
 * - VITE_USE_API=true/false 显式指定时优先
 * - 未配置时默认 true（避免生产构建误发布为离线演示）
 * - 纯静态演示请显式设置 VITE_USE_API=false
 */
export function useApiEnabled(): boolean {
  const flag = import.meta.env.VITE_USE_API
  if (flag === 'false') return false
  if (flag === 'true') return true
  return true
}

export function apiModeLabel(): 'api' | 'offline' {
  return useApiEnabled() ? 'api' : 'offline'
}
