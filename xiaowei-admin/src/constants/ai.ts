/** 与前端用户端、后端 MockAiProvider 对齐的 AI 任务类型 */
export const TASK_TYPES = [
  { value: 'paper_generate', label: '论文生成', color: '#2563eb' },
  { value: 'revise', label: 'AI 改稿', color: '#7c3aed' },
  { value: 'paraphrase', label: '降重', color: '#0891b2' },
  { value: 'aigc_check', label: 'AIGC 查重', color: '#ea580c' },
  { value: 'ppt_generate', label: 'PPT 生成', color: '#db2777' },
  { value: 'file_translate', label: '文献翻译', color: '#059669' },
  { value: 'data_analysis', label: '数据分析', color: '#4f46e5' },
] as const

/** 与后端 withLlmLimit(taskType) 对齐，用于模型池任务路由 */
export const LLM_ROUTE_TASKS = [
  ...TASK_TYPES,
  { value: 'polish', label: '标题润色', group: 'wizard' },
  { value: 'recommend_titles', label: '标题推荐', group: 'wizard' },
  { value: 'outline_search', label: '提纲搜索', group: 'wizard' },
  { value: 'outline_generate', label: '提纲生成', group: 'wizard' },
  { value: 'parse_proposal', label: '开题解析', group: 'wizard' },
  { value: 'connectivity_test', label: '连通测试', group: 'system' },
] as const

export const FLOW_TYPES = [
  { value: 'both', label: '标准+专业版', desc: '毕业论文等，可切专业版' },
  { value: 'quick', label: '一键提交', desc: '改稿/降重/PPT 等快捷任务' },
  { value: 'wizard', label: '向导流程', desc: '标题→文献→提纲→预览' },
] as const

export const JOB_STATUS = [
  { value: 'pending', label: '排队中', type: 'info' as const },
  { value: 'running', label: '生成中', type: 'warning' as const },
  { value: 'success', label: '已完成', type: 'success' as const },
  { value: 'failed', label: '失败', type: 'danger' as const },
  { value: 'cancelled', label: '已取消', type: 'info' as const },
] as const

export const PROCESS_VARIANTS = [
  { value: 'standard', label: '标准流程' },
  { value: 'pro', label: '专业版' },
] as const

export const FORM_VARIANTS = [
  { value: 'graduation', label: '毕业论文表单' },
  { value: 'generic', label: '通用表单' },
  { value: 'minimal', label: '极简表单' },
] as const

export function taskTypeLabel(v: string) {
  return TASK_TYPES.find((t) => t.value === v)?.label ?? v
}

export function taskTypeColor(v: string) {
  return TASK_TYPES.find((t) => t.value === v)?.color ?? '#64748b'
}

export function flowTypeLabel(v: string) {
  return FLOW_TYPES.find((f) => f.value === v)?.label ?? v
}

export function jobStatusLabel(v: string) {
  return JOB_STATUS.find((s) => s.value === v)?.label ?? v
}

export function jobStatusType(v: string) {
  return JOB_STATUS.find((s) => s.value === v)?.type ?? 'info'
}
