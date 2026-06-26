import { apiFetch } from '@/api/http'
import { getProduct } from '@/mocks/products'
import type { ProductConfig } from '@/types/product'

export interface ProductQuote {
  productId: string
  degree?: string
  wordCount?: number
  modelType?: string
  price: number
}

export async function fetchProducts(): Promise<ProductConfig[]> {
  const list = await apiFetch<Record<string, unknown>[]>('/products')
  return list.map(mapProduct)
}

export async function fetchProduct(id: string): Promise<ProductConfig> {
  const p = await apiFetch<Record<string, unknown>>(`/products/${id}`)
  return mapProduct(p)
}

export async function quoteProductPrice(params: {
  productId: string
  degree?: string
  wordCount?: number
  modelType?: string
}): Promise<ProductQuote> {
  const q = new URLSearchParams()
  if (params.degree) q.set('degree', params.degree)
  if (params.wordCount != null) q.set('wordCount', String(params.wordCount))
  if (params.modelType) q.set('modelType', params.modelType)
  const raw = await apiFetch<Record<string, unknown>>(
    `/products/${params.productId}/quote?${q}`,
  )
  return {
    productId: String(raw.productId),
    degree: raw.degree as string | undefined,
    wordCount: raw.wordCount as number | undefined,
    modelType: raw.modelType as string | undefined,
    price: Number(raw.price),
  }
}

function mapProduct(p: Record<string, unknown>): ProductConfig {
  const id = String(p.id)
  const mock = getProduct(id)
  return {
    id,
    label: String(p.label),
    icon: String(p.icon ?? mock.icon),
    badge: (p.badge as string | undefined) ?? mock.badge,
    category: (p.category as ProductConfig['category']) ?? mock.category,
    pinned: (p.pinned as boolean | undefined) ?? mock.pinned,
    defaultWordCount:
      (p.defaultWordCount as number | undefined) ?? mock.defaultWordCount,
    compactLayout: (p.compactLayout as boolean | undefined) ?? mock.compactLayout,
    banner: (p.banner as string | undefined) ?? mock.banner,
    processVariant:
      (p.processVariant as ProductConfig['processVariant']) ?? mock.processVariant,
    formVariant: (p.formVariant as ProductConfig['formVariant']) ?? mock.formVariant,
    titleFieldLabel: String(p.titleFieldLabel ?? mock.titleFieldLabel),
    titlePlaceholder: String(p.titlePlaceholder ?? mock.titlePlaceholder),
    proLinkText: (p.proLinkText as string | undefined) ?? mock.proLinkText,
    submitLabel: String(p.submitLabel ?? mock.submitLabel),
    agreementText: (p.agreementText as string | undefined) ?? mock.agreementText,
    showFaq: p.showFaq !== false && mock.showFaq !== false,
    centerTitle: Boolean(p.centerTitle ?? mock.centerTitle),
    taskType: (p.taskType as string | undefined) ?? mock.taskType,
    flowType: (p.flowType as string | undefined) ?? mock.flowType,
  }
}
