export type PaperLanguage = 'zh' | 'en' | 'ja'

export type PaperModel = 'standard' | 'academia'

export interface PaperMeta {
  unit: string
  position: string
  paperType: string
  trainingStart: string
  trainingEnd: string
  wordCount: number
  language: PaperLanguage
  degree: string
  category: string
  schoolId: string
  chartFormula: string
  formatNote: string
}

export interface ProposalFileMeta {
  name: string
  size: number
}

export interface LiteratureItem {
  id: string
  title: string
  authors: string
  source: string
  year: number
  lang: 'zh' | 'en'
  gbtCitation: string
}

export interface OutlineNode {
  id: string
  level: 1 | 2 | 3
  title: string
  wordCount: number
  zhRefs: number
  enRefs: number
}

export interface PaperPreview {
  abstractZh: string
  abstractEn: string
  sections: { title: string; content: string }[]
  /** 后端统计的节选总字数（字符数） */
  approxWords?: number
  targetWordCount?: number
  plannedSections?: number
  generatedSections?: number
  generationProgress?: { done: number; total: number }
}

export interface PaperDraft {
  id: string
  title: string
  /** 关联产品（同步订单/任务） */
  productId?: string
  /** 服务端最后更新时间（ISO） */
  updatedAt?: string
  /** 草稿乐观锁版本（与服务端 papers.version 对应） */
  version?: number
  /** 本地编辑时间戳，仅客户端使用 */
  _localUpdatedAt?: number
  /** 冲突时强制覆盖云端（仅保存请求，不入库） */
  _forceOverwrite?: boolean
  model?: PaperModel
  meta: PaperMeta
  proposalFile?: ProposalFileMeta
  proposalParsed?: string
  researchNotes: string
  literature: LiteratureItem[]
  outline: OutlineNode[]
  /** 专业版提纲编辑区原文，与 outline 同步 */
  outlineText?: string
  preview?: PaperPreview
  maxVisitedStep: number
}

export const defaultMeta = (): PaperMeta => ({
  unit: '',
  position: '',
  paperType: '毕业论文',
  trainingStart: '',
  trainingEnd: '',
  wordCount: 12000,
  language: 'zh',
  degree: '本科',
  category: '教育经管',
  schoolId: '',
  chartFormula: '无',
  formatNote: '',
})

export const createEmptyDraft = (id: string): PaperDraft => ({
  id,
  title: '',
  meta: defaultMeta(),
  researchNotes: '',
  literature: [],
  outline: [],
  outlineText: '',
  maxVisitedStep: 0,
})
