export type ProcessVariant =
  | 'standard'
  | 'journal'
  | 'revise'
  | 'review'
  | 'aigc'
  | 'proposal'
  | 'course'
  | 'task'
  | 'paraphrase'
  | 'ppt'
  | 'upload'

export type ProductCategory = 'writing' | 'check' | 'defense' | 'sci' | 'tools'

export const PRODUCT_CATEGORY_LABELS: Record<ProductCategory, string> = {
  writing: '论文写作',
  check: '查重降重',
  defense: '答辩汇报',
  sci: 'SCI / 国际',
  tools: '其他工具',
}

export type FormVariant =
  | 'graduation'
  | 'journal'
  | 'revise'
  | 'review'
  | 'aigc'
  | 'proposal'
  | 'course'
  | 'task'
  | 'paraphrase'
  | 'generic'
  | 'upload'
  | 'ppt'

export interface ProductConfig {
  id: string
  label: string
  icon: string
  badge?: string
  category?: ProductCategory
  pinned?: boolean
  defaultWordCount?: number
  /** 居中紧凑表单（参考原站毕业论文页） */
  compactLayout?: boolean
  banner?: string
  processVariant: ProcessVariant
  formVariant: FormVariant
  titleFieldLabel: string
  titlePlaceholder: string
  proLinkText?: string
  submitLabel: string
  agreementText?: string
  showFaq?: boolean
  centerTitle?: boolean
  taskType?: string
  flowType?: string
}
