import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { fetchSiteBranding, type SiteBranding } from '@/api/modules/meta'
import { useApiEnabled } from '@/api/http'

const DEFAULTS: SiteBranding = {
  siteTitle: '小微智能写作',
  slogan: '一站式论文辅助平台',
  documentTitle: '小微智能 AI 论文写作',
  logoText: 'AI',
  logoUrl: null,
  faviconUrl: null,
}

function resolveAssetUrl(url?: string | null): string | null {
  if (!url) return null
  const v = url.trim()
  if (!v) return null
  if (v.startsWith('http://') || v.startsWith('https://')) return v
  const apiBase = import.meta.env.VITE_API_BASE ?? '/api'
  if (v.startsWith('/api/')) return v
  if (v.startsWith('/files/')) return `${apiBase}${v}`
  if (v.startsWith('/')) return v
  return `${apiBase}/files/download/${v}`
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

export function applySiteBrandingToDocument(branding: SiteBranding) {
  document.title = branding.documentTitle || DEFAULTS.documentTitle

  const favicon = resolveAssetUrl(branding.faviconUrl) ?? '/favicon.svg'
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

export const useSiteBrandingStore = defineStore('siteBranding', () => {
  const branding = ref<SiteBranding>({ ...DEFAULTS })
  const loaded = ref(false)

  const siteTitle = computed(() => branding.value.siteTitle || DEFAULTS.siteTitle)
  const slogan = computed(() => branding.value.slogan || DEFAULTS.slogan)
  const logoText = computed(() => branding.value.logoText || DEFAULTS.logoText)
  const logoImageUrl = computed(() => resolveAssetUrl(branding.value.logoUrl))
  const faviconImageUrl = computed(() => resolveAssetUrl(branding.value.faviconUrl))

  async function load() {
    if (!useApiEnabled()) {
      applySiteBrandingToDocument(DEFAULTS)
      loaded.value = true
      return
    }
    try {
      const remote = await fetchSiteBranding()
      branding.value = { ...DEFAULTS, ...remote }
    } catch {
      branding.value = { ...DEFAULTS }
    }
    applySiteBrandingToDocument(branding.value)
    loaded.value = true
  }

  function applyLocal(partial: Partial<SiteBranding>) {
    branding.value = { ...branding.value, ...partial }
    applySiteBrandingToDocument(branding.value)
  }

  return {
    branding,
    loaded,
    siteTitle,
    slogan,
    logoText,
    logoImageUrl,
    faviconImageUrl,
    load,
    applyLocal,
    resolveAssetUrl,
  }
})
