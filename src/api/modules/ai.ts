import { apiFetch, apiUpload } from '@/api/http'

const LLM_TIMEOUT_MS = 90_000

export async function polishTitle(title: string): Promise<string> {
  const res = await apiFetch<{ title: string }>('/ai/polish-title', {
    method: 'POST',
    body: JSON.stringify({ title }),
    timeoutMs: LLM_TIMEOUT_MS,
  })
  return res.title
}

export async function searchOutline(title: string, degree: string) {
  return apiFetch<Record<string, unknown>[]>('/ai/outline/search', {
    method: 'POST',
    body: JSON.stringify({ title, degree }),
    timeoutMs: LLM_TIMEOUT_MS,
  })
}

export async function generateOutline(title: string, depth: 2 | 3): Promise<string> {
  const res = await apiFetch<{ outlineText: string }>('/ai/outline/generate', {
    method: 'POST',
    body: JSON.stringify({ title, depth }),
    timeoutMs: LLM_TIMEOUT_MS,
  })
  return res.outlineText
}

export async function recommendTitles(
  keyword: string,
  productId?: string,
): Promise<string[]> {
  const res = await apiFetch<{ titles: string[] }>('/ai/recommend-titles', {
    method: 'POST',
    body: JSON.stringify({ keyword, productId }),
    timeoutMs: LLM_TIMEOUT_MS,
  })
  return res.titles ?? []
}

export async function searchLiterature(keyword: string) {
  return apiFetch<Record<string, unknown>[]>(
    `/ai/literature/search?keyword=${encodeURIComponent(keyword)}`,
  )
}

export async function parseProposal(file: File): Promise<{ summary: string; textLength?: number }> {
  return apiUpload<{ summary: string; textLength?: number }>('/ai/parse-proposal', file, 120_000)
}
