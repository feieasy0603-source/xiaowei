export function clampProgress(n: number): number {
  return Math.min(100, Math.max(0, Math.round(Number(n) || 0)))
}

export function jobStatusProgressLabel(status: string, progress: number): string {
  const p = clampProgress(progress)
  if (status === 'pending') return p > 0 ? `排队中 ${p}%` : '排队中'
  if (status === 'running') return `生成中 ${p}%`
  if (status === 'success') return '已完成'
  if (status === 'failed') return '失败'
  if (status === 'cancelled') return '已取消'
  return `${p}%`
}

export function progressBarStatus(
  status: string,
): 'success' | 'exception' | 'warning' | undefined {
  if (status === 'success') return 'success'
  if (status === 'failed') return 'exception'
  if (status === 'cancelled') return 'warning'
  return undefined
}

export function isActiveJobStatus(status: string): boolean {
  return status === 'running' || status === 'pending'
}
