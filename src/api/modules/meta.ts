import { apiFetch } from '@/api/http'

export interface FormOptions {
  categories: string[]
  degrees: string[]
  paperTypes: string[]
}

export async function fetchFormOptions(): Promise<FormOptions> {
  return apiFetch<FormOptions>('/meta/form-options')
}

export interface SupportInfo {
  enabled: boolean
  title: string
  workHours: string
  phone?: string | null
  email?: string | null
  wechatId?: string | null
  qq?: string | null
  externalUrl?: string | null
  note?: string | null
  hasChannel?: boolean
}

const OFFLINE_SUPPORT: SupportInfo = {
  enabled: true,
  title: '在线客服',
  workHours: '工作日 9:00–18:00',
  phone: '400-000-0000',
  wechatId: 'xiaowei-service',
  note: '连接后端后可在管理后台配置真实客服信息。',
  hasChannel: true,
}

export async function fetchSupportInfo(): Promise<SupportInfo> {
  try {
    return await apiFetch<SupportInfo>('/meta/support')
  } catch {
    return OFFLINE_SUPPORT
  }
}

export interface SiteBranding {
  siteTitle: string
  slogan: string
  documentTitle: string
  logoText: string
  logoUrl?: string | null
  faviconUrl?: string | null
  logoStorageKey?: string | null
  faviconStorageKey?: string | null
}

export async function fetchSiteBranding(): Promise<SiteBranding> {
  return apiFetch<SiteBranding>('/meta/branding')
}

export function offlineSupportInfo(): SupportInfo {
  return { ...OFFLINE_SUPPORT }
}
