import { apiFetch, apiUpload, getToken } from '@/api/http'

export async function uploadFile(file: File): Promise<Record<string, unknown>> {
  return apiUpload<Record<string, unknown>>('/files/upload', file)
}

export interface DeliveryFile {
  id: number
  paperId?: string
  jobId?: number
  fileType: string
  fileName: string
  sizeBytes?: number
  downloadUrl: string
}

export async function fetchPaperDeliveries(paperId: string): Promise<DeliveryFile[]> {
  return apiFetch<DeliveryFile[]>(`/files/papers/${paperId}/deliveries`)
}

export async function fetchJobDeliveries(jobId: number): Promise<DeliveryFile[]> {
  return apiFetch<DeliveryFile[]>(`/files/jobs/${jobId}/deliveries`)
}

export function jobDeliveryDownloadUrl(fileId: number): string {
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  return `${base}/files/job-delivery/${fileId}/download`
}

export async function downloadJobDelivery(fileId: number, fileName: string) {
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`${base}/files/job-delivery/${fileId}/download`, { headers })
  if (res.status === 401) throw new Error('请先登录')
  if (!res.ok) {
    let msg = '下载失败'
    try {
      const json = await res.json()
      if (json.message) msg = json.message
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }
  const blob = await res.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = fileName
  a.click()
  URL.revokeObjectURL(a.href)
}

export function deliveryDownloadUrl(fileId: number): string {
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  return `${base}/files/delivery/${fileId}/download`
}

/** 游客凭订单号或任务号下载交付文件 */
export async function downloadPublicDelivery(
  ref: { orderNo?: string; jobNo?: string },
  fileId: number,
  source: 'paper' | 'job',
  fileName: string,
) {
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  const q = new URLSearchParams({
    fileId: String(fileId),
    source,
  })
  if (ref.orderNo) q.set('orderNo', ref.orderNo)
  else if (ref.jobNo) q.set('jobNo', ref.jobNo)
  const res = await fetch(`${base}/files/public/download?${q}`)
  if (!res.ok) {
    let msg = '下载失败'
    try {
      const json = await res.json()
      if (json.message) msg = json.message
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }
  const blob = await res.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = fileName
  a.click()
  URL.revokeObjectURL(a.href)
}

export async function downloadDelivery(fileId: number, fileName: string) {
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  const headers: Record<string, string> = {}
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`${base}/files/delivery/${fileId}/download`, { headers })
  if (res.status === 401) throw new Error('请先登录')
  if (!res.ok) {
    let msg = '下载失败'
    try {
      const json = await res.json()
      if (json.message) msg = json.message
    } catch {
      /* ignore */
    }
    throw new Error(msg)
  }
  const blob = await res.blob()
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = fileName
  a.click()
  URL.revokeObjectURL(a.href)
}
