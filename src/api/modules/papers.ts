import { apiFetch } from '@/api/http'
import type { PaperDraft } from '@/types/paper'

export async function fetchPaper(id: string): Promise<PaperDraft> {
  return apiFetch<PaperDraft>(`/papers/${id}`)
}

export async function createPaper(productId: string): Promise<PaperDraft> {
  return apiFetch<PaperDraft>('/papers', {
    method: 'POST',
    body: JSON.stringify({ productId }),
  })
}

export interface PaperSummary {
  id: string
  title: string
  productId?: string
  maxVisitedStep?: number
  updatedAt?: string
  version?: number
}

export async function fetchMyPapers(limit = 30): Promise<PaperSummary[]> {
  return apiFetch<PaperSummary[]>(`/papers/mine?limit=${limit}`)
}

export async function savePaper(id: string, draft: PaperDraft): Promise<PaperDraft> {
  return apiFetch<PaperDraft>(`/papers/${id}`, {
    method: 'PUT',
    body: JSON.stringify(draft),
  })
}
