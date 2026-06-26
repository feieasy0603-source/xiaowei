import { apiFetch, getToken } from '@/api/http'

export interface JobDto {
  id: number
  jobNo: string
  productId: string
  paperId?: string
  taskType: string
  status: string
  progress: number
  resultJson?: string
  errorMsg?: string
}

export async function createJob(
  productId: string,
  paperId: string | undefined,
  payload: Record<string, unknown>,
): Promise<JobDto> {
  return apiFetch<JobDto>(
    '/jobs',
    {
      method: 'POST',
      body: JSON.stringify({ productId, paperId, payload }),
      timeoutMs: 30_000,
    },
  )
}

export async function getJob(id: number): Promise<JobDto> {
  return apiFetch<JobDto>(`/jobs/${id}`, { timeoutMs: 15_000 })
}

export async function retryJob(id: number): Promise<JobDto> {
  return apiFetch<JobDto>(`/jobs/${id}/retry`, { method: 'POST' })
}

export async function cancelJob(id: number): Promise<JobDto> {
  return apiFetch<JobDto>(`/jobs/${id}/cancel`, { method: 'POST' })
}

/** 该草稿最近一次免费预览任务（无 orderId） */
export async function fetchPreviewJobByPaper(paperId: string): Promise<JobDto | null> {
  const raw = await apiFetch<Record<string, unknown>>(`/jobs/by-paper/${encodeURIComponent(paperId)}`)
  if (!raw?.id) return null
  return {
    id: Number(raw.id),
    jobNo: String(raw.jobNo ?? ''),
    productId: String(raw.productId ?? ''),
    paperId: raw.paperId as string | undefined,
    taskType: String(raw.taskType ?? ''),
    status: String(raw.status ?? ''),
    progress: Number(raw.progress ?? 0),
    resultJson: raw.resultJson as string | undefined,
    errorMsg: raw.errorMsg as string | undefined,
  }
}

interface SseEvent {
  event: string
  data: string
}

function parseSseBuffer(buffer: string): { events: SseEvent[]; rest: string } {
  const events: SseEvent[] = []
  const parts = buffer.split('\n\n')
  const rest = parts.pop() ?? ''
  for (const part of parts) {
    if (!part.trim()) continue
    let event = 'message'
    let data = ''
    for (const line of part.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) data += line.slice(5).trim()
    }
    events.push({ event, data })
  }
  return { events, rest }
}

/** 使用 fetch + Authorization 订阅任务 SSE，避免 JWT 出现在 URL */
export function subscribeJobProgress(
  jobId: number,
  onProgress: (p: number) => void,
  onDone: (result: string) => void,
  onError: (msg: string) => void,
): () => void {
  const controller = new AbortController()
  const base = import.meta.env.VITE_API_BASE ?? '/api'
  const token = getToken()

  void (async () => {
    try {
      const headers: Record<string, string> = { Accept: 'text/event-stream' }
      if (token) headers.Authorization = `Bearer ${token}`

      const res = await fetch(`${base}/jobs/${jobId}/stream`, {
        headers,
        signal: controller.signal,
      })
      if (!res.ok || !res.body) {
        onError(`任务流连接失败 (${res.status})`)
        return
      }

      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      let buffer = ''

      while (true) {
        const { done, value } = await reader.read()
        if (done) break
        buffer += decoder.decode(value, { stream: true })
        const parsed = parseSseBuffer(buffer)
        buffer = parsed.rest
        for (const ev of parsed.events) {
          if (ev.event === 'progress') onProgress(Number(ev.data))
          else if (ev.event === 'done') {
            onDone(ev.data)
            controller.abort()
            return
          } else if (ev.event === 'error' && ev.data) {
            onError(ev.data)
            controller.abort()
            return
          }
        }
      }
    } catch (e) {
      if (controller.signal.aborted) return
      onError(e instanceof Error ? e.message : '任务流连接中断')
    }
  })()

  return () => controller.abort()
}
