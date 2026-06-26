import { getJob, subscribeJobProgress } from '@/api/modules/jobs'
import type { JobDto } from '@/api/modules/jobs'
import { clampProgress } from '@/composables/jobProgressDisplay'

export interface JobFollowCallbacks {
  onProgress?: (progress: number) => void
  onDone?: (job: JobDto, resultJson?: string) => void
  onError?: (message: string) => void
}

/** SSE（Authorization 头）+ 高频轮询兜底；SSE 失败不中断轮询 */
export function followJob(jobId: number, callbacks: JobFollowCallbacks): () => void {
  let stopped = false
  let lastProgress = 0
  let sseClosed = false

  const emitProgress = (p: number) => {
    const next = clampProgress(p)
    if (next >= lastProgress) {
      lastProgress = next
      callbacks.onProgress?.(next)
    }
  }

  const stopPoll = (() => {
    let timer: ReturnType<typeof setTimeout> | null = null
    const tick = async () => {
      if (stopped) return
      try {
        const job = await getJob(jobId)
        emitProgress(job.progress ?? 0)
        if (job.status === 'success') {
          stopped = true
          if (timer) clearTimeout(timer)
          callbacks.onDone?.(job, job.resultJson)
        } else if (job.status === 'failed') {
          stopped = true
          if (timer) clearTimeout(timer)
          callbacks.onError?.(job.errorMsg || '生成失败')
        } else if (job.status === 'cancelled') {
          stopped = true
          if (timer) clearTimeout(timer)
          callbacks.onError?.('任务已取消')
        }
      } catch {
        /* 轮询失败时继续等待 */
      } finally {
        if (!stopped) {
          timer = setTimeout(() => void tick(), 1200)
        }
      }
    }
    void tick()
    return () => {
      if (timer) clearTimeout(timer)
    }
  })()

  let unsubSse = subscribeJobProgress(
    jobId,
    (p) => {
      if (!stopped) emitProgress(p)
    },
    () => {
      if (stopped) return
      void getJob(jobId).then((job) => {
        if (job.status === 'success') {
          stopped = true
          stopPoll()
          callbacks.onDone?.(job, job.resultJson)
        } else if (job.status === 'failed') {
          stopped = true
          stopPoll()
          callbacks.onError?.(job.errorMsg || '生成失败')
        } else if (job.status === 'cancelled') {
          stopped = true
          stopPoll()
          callbacks.onError?.('任务已取消')
        }
      })
    },
    () => {
      // SSE 连接异常时仅关闭流，轮询继续兜底
      if (stopped || sseClosed) return
      sseClosed = true
      unsubSse()
    },
  )

  return () => {
    stopped = true
    unsubSse()
    stopPoll()
  }
}
