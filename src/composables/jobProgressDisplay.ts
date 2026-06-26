/** 根据任务进度与章节进度生成展示文案 */
export function jobProgressPhase(
  percent: number,
  sectionDone?: number,
  sectionTotal?: number,
): string {
  const p = Math.min(100, Math.max(0, Math.round(percent)))
  if (p >= 100) return '生成完成，正在保存结果…'
  if (p >= 88) return '正在生成摘要与英文摘要…'
  if (sectionTotal != null && sectionTotal > 0 && sectionDone != null) {
    if (sectionDone >= sectionTotal) return '章节已全部生成，正在收尾…'
    return `正在撰写第 ${sectionDone + 1} / ${sectionTotal} 节`
  }
  if (p < 12) return '正在解析提纲与任务参数…'
  if (p < 28) return '正在准备章节结构…'
  return 'AI 正在连续撰写正文…'
}

export function clampProgress(n: number): number {
  return Math.min(100, Math.max(0, Math.round(n)))
}

export function sectionProgressPercent(done: number, total: number): number {
  if (total <= 0) return 0
  return clampProgress((done / total) * 100)
}
