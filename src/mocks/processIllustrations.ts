import type { ProcessVariant } from '@/types/product'

const CDN_BASE = 'https://thesis-down.checkmore.net/oem/static'

/** 本地 public 优先，CDN 回退 */
const ILLUSTRATION_SOURCES = {
  aiPaperRewrite: {
    local: '/process/aiPaperRewrite.png',
    cdn: `${CDN_BASE}/aiPaperRewrite.png`,
  },
  assignment: {
    local: '/process/assignment.png',
    cdn: `${CDN_BASE}/assignment.png`,
  },
  ppt: {
    local: '/process/ppt.png',
    cdn: `${CDN_BASE}/ppt.png`,
  },
  sci: {
    local: '/process/sci.png',
    cdn: `${CDN_BASE}/sci.png`,
  },
} as const

type IllustrationKey = keyof typeof ILLUSTRATION_SOURCES

const byVariant: Partial<Record<ProcessVariant, IllustrationKey>> = {
  revise: 'aiPaperRewrite',
  task: 'assignment',
  ppt: 'ppt',
}

const byProductId: Record<string, IllustrationKey> = {
  'sci-en': 'sci',
  'sci-premium': 'sci',
  'ppt-master': 'ppt',
  'lit-ppt': 'ppt',
}

/** 预加载配图，切换菜单时更快显示 */
const preloaded = new Set<string>()

export function preloadIllustration(key: IllustrationKey) {
  const src = ILLUSTRATION_SOURCES[key]
  if (preloaded.has(src.local)) return
  preloaded.add(src.local)
  const img = new Image()
  img.src = src.local
}

export function preloadAllIllustrations() {
  for (const key of Object.keys(ILLUSTRATION_SOURCES) as IllustrationKey[]) {
    preloadIllustration(key)
  }
}

export function resolveIllustrationUrls(
  variant: ProcessVariant,
  productId?: string,
): { primary: string; fallback: string } | undefined {
  const key = (productId && byProductId[productId]) || byVariant[variant]
  if (!key) return undefined
  const src = ILLUSTRATION_SOURCES[key]
  return { primary: src.local, fallback: src.cdn }
}

export function illustrationKeyFor(
  variant: ProcessVariant,
  productId?: string,
): IllustrationKey | undefined {
  return (productId && byProductId[productId]) || byVariant[variant]
}
