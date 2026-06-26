import { computed, reactive } from 'vue'
import { adminApiUrl, adminFetch } from '@/api/http'

export interface AdminSiteBranding {
  siteTitle: string
  slogan: string
  documentTitle: string
  logoText: string
  logoUrl?: string | null
  faviconUrl?: string | null
}

const DEFAULTS: AdminSiteBranding = {
  siteTitle: '小微智能写作',
  slogan: '一站式论文辅助平台',
  documentTitle: '小微智能 AI 论文写作',
  logoText: 'AI',
  logoUrl: null,
  faviconUrl: null,
}

const state = reactive({
  branding: { ...DEFAULTS } as AdminSiteBranding,
  loaded: false,
  routeTitle: '',
})

function resolveAssetUrl(url?: string | null): string | null {
  if (!url) return null
  const v = url.trim()
  if (!v) return null
  if (v.startsWith('http://') || v.startsWith('https://')) return v
  if (v.startsWith('/api/')) return v
  if (v.startsWith('/files/')) return adminApiUrl(v)
  if (v.startsWith('/')) return v
  return adminApiUrl(`/files/download/${v}`)
}

function faviconMime(url: string): string | undefined {
  const lower = url.toLowerCase()
  if (lower.endsWith('.svg')) return 'image/svg+xml'
  if (lower.endsWith('.png')) return 'image/png'
  if (lower.endsWith('.ico')) return 'image/x-icon'
  if (lower.endsWith('.webp')) return 'image/webp'
  if (lower.endsWith('.jpg') || lower.endsWith('.jpeg')) return 'image/jpeg'
  return undefined
}

export function updateAdminDocumentBranding(routeTitle?: string) {
  if (routeTitle !== undefined) {
    state.routeTitle = routeTitle
  }
  const baseTitle = state.branding.documentTitle || state.branding.siteTitle || DEFAULTS.documentTitle
  document.title = state.routeTitle ? `${state.routeTitle} - ${baseTitle}` : `${baseTitle} 管理端`

  const favicon = resolveAssetUrl(state.branding.faviconUrl) ?? '/favicon.svg'
  let link = document.querySelector<HTMLLinkElement>('link[rel="icon"]')
  if (!link) {
    link = document.createElement('link')
    link.rel = 'icon'
    document.head.appendChild(link)
  }
  link.href = favicon
  const mime = faviconMime(favicon)
  if (mime) link.type = mime
  else link.removeAttribute('type')
}

export function applyAdminBranding(partial: Partial<AdminSiteBranding>) {
  state.branding = { ...state.branding, ...partial }
  updateAdminDocumentBranding()
}

export async function loadAdminBranding() {
  try {
    const remote = await adminFetch<AdminSiteBranding>('/meta/branding', {
      skipAuthRedirect: true,
    })
    state.branding = { ...DEFAULTS, ...remote }
  } catch {
    state.branding = { ...DEFAULTS }
  } finally {
    state.loaded = true
    updateAdminDocumentBranding()
  }
}

export function useAdminBranding() {
  return {
    state,
    branding: computed(() => state.branding),
    siteTitle: computed(() => state.branding.siteTitle || DEFAULTS.siteTitle),
    slogan: computed(() => state.branding.slogan || DEFAULTS.slogan),
    logoText: computed(() => state.branding.logoText || DEFAULTS.logoText),
    logoImageUrl: computed(() => resolveAssetUrl(state.branding.logoUrl)),
    faviconImageUrl: computed(() => resolveAssetUrl(state.branding.faviconUrl)),
    resolveAssetUrl,
  }
}
