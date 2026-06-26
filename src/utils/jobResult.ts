export interface JobResultPayload {
  taskType?: string
  message?: string
  revisedText?: string
  paraphrasedText?: string
  translatedText?: string
  report?: string
  slidesOutline?: string
  reportSummary?: string
  hasPreview?: boolean
  paperId?: string
}

export function parseJobResult(resultJson?: string): JobResultPayload | null {
  if (!resultJson) return null
  try {
    return JSON.parse(resultJson) as JobResultPayload
  } catch {
    return null
  }
}

export function resultDisplayText(r: JobResultPayload | null): string {
  if (!r) return ''
  return (
    r.revisedText ??
    r.paraphrasedText ??
    r.translatedText ??
    r.report ??
    r.slidesOutline ??
    r.reportSummary ??
    r.message ??
    ''
  )
}

export const TASK_TYPE_LABELS: Record<string, string> = {
  revise: '改稿结果',
  paraphrase: '降重结果',
  aigc_check: 'AIGC 检测报告',
  ppt_generate: 'PPT 大纲',
  file_translate: '翻译结果',
  data_analysis: '数据分析报告',
  paper_generate: '论文生成',
}
