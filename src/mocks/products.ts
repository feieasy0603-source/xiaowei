import type { ProductCategory, ProductConfig } from '@/types/product'

const productMeta: Record<
  string,
  {
    category: ProductCategory
    pinned?: boolean
    defaultWordCount?: number
    compactLayout?: boolean
  }
> = {
  graduation: {
    category: 'writing',
    pinned: true,
    defaultWordCount: 12000,
    compactLayout: true,
  },
  journal: { category: 'writing', pinned: true, defaultWordCount: 10000 },
  revise: { category: 'check' },
  review: { category: 'writing' },
  aigc: { category: 'check', pinned: true },
  proposal: { category: 'writing' },
  course: { category: 'writing', defaultWordCount: 6000 },
  task: { category: 'writing' },
  paraphrase: { category: 'check', pinned: true },
  'aigc-reduce': { category: 'check' },
  ppt: { category: 'defense', pinned: true },
  imitate: { category: 'writing' },
  'ppt-master': { category: 'defense' },
  'lit-ppt': { category: 'defense' },
  'sci-en': { category: 'sci' },
  'sci-premium': { category: 'sci' },
  report: { category: 'writing' },
  translate: { category: 'tools' },
  intern: { category: 'writing' },
  data: { category: 'tools' },
  survey: { category: 'writing' },
  questionnaire: { category: 'tools' },
  experiment: { category: 'writing' },
  book: { category: 'writing' },
  ideology: { category: 'writing' },
}

function withMeta(p: ProductConfig): ProductConfig {
  const meta = productMeta[p.id] ?? { category: 'tools' as ProductCategory }
  const merged = { ...p, ...meta }
  const compactLayout =
    meta.compactLayout ??
    (!merged.centerTitle &&
      (meta.category === 'writing' || meta.category === 'defense'))
  return { ...merged, compactLayout }
}

const defaultAgreement =
  '我已阅读并同意：生成的论文范文仅用于参考，不作为毕业、发表等用途'

const rawProducts: ProductConfig[] = [
  {
    id: 'graduation',
    label: '毕业论文',
    icon: '🎓',
    banner: '你只负责输入标题，写论文的这100小时，小微来帮你节省',
    processVariant: 'standard',
    formVariant: 'graduation',
    titleFieldLabel: '提交论文标题',
    titlePlaceholder:
      '输入5-50字论文标题，如果不知道写什么标题，可以试试推荐哦~',
    proLinkText: '切换至专业版，设字数、选提纲',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'journal',
    label: '期刊论文',
    icon: '📰',
    processVariant: 'journal',
    formVariant: 'journal',
    titleFieldLabel: '提交论文标题',
    titlePlaceholder:
      '输入5-50字论文标题，如果不知道写什么标题，可以试试推荐哦~',
    proLinkText: '切换至专业版，可选提纲摘要',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'revise',
    label: 'AI无限改稿',
    icon: '✨',
    processVariant: 'revise',
    formVariant: 'revise',
    titleFieldLabel: '请上传文档',
    titlePlaceholder: '',
    submitLabel: '立即改稿',
    agreementText:
      '我已阅读并同意：改稿结果仅用于参考，请自行核对学术规范',
    showFaq: true,
    centerTitle: true,
  },
  {
    id: 'review',
    label: '文献综述',
    icon: '📚',
    processVariant: 'review',
    formVariant: 'review',
    titleFieldLabel: '提交文献综述标题',
    titlePlaceholder:
      '输入5-50字论文标题，如果不知道写什么标题，可以试试推荐哦~',
    proLinkText: '切换至专业版，定制更多参数',
    submitLabel: '立即生成',
    agreementText:
      '我已阅读并同意：生成的范文仅用于参考，不作为毕业、发表使用',
  },
  {
    id: 'aigc',
    label: 'AIGC 检测说明',
    icon: '🔍',
    processVariant: 'aigc',
    formVariant: 'aigc',
    titleFieldLabel: '上传论文或报告',
    titlePlaceholder: '',
    submitLabel: '提交检测说明',
    showFaq: true,
    centerTitle: true,
  },
  {
    id: 'proposal',
    label: '开题报告',
    icon: '📝',
    processVariant: 'proposal',
    formVariant: 'proposal',
    titleFieldLabel: '提交开题报告标题',
    titlePlaceholder:
      '输入5-50字论文标题，如果不知道写什么标题，可以试试推荐哦~',
    proLinkText: '切换至专业版，定制更多参数',
    submitLabel: '立即生成',
    agreementText:
      '我已阅读并同意：生成的范文仅用于参考，不作为毕业、发表使用',
  },
  {
    id: 'course',
    label: '课程论文',
    icon: '📖',
    processVariant: 'course',
    formVariant: 'course',
    titleFieldLabel: '提交论文标题',
    titlePlaceholder:
      '输入5-50字论文标题，如果不知道写什么标题，可以试试推荐哦~',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'task',
    label: '任务书',
    icon: '📋',
    processVariant: 'task',
    formVariant: 'task',
    titleFieldLabel: '提交任务书标题',
    titlePlaceholder:
      '输入完整的任务书标题，可获得更好的生成效果（5-50字或20词以内）',
    submitLabel: '立即生成',
    agreementText:
      '我已阅读并同意：生成的范文仅用于参考，不作为毕业、发表使用',
    showFaq: true,
  },
  {
    id: 'paraphrase',
    label: '论文降重',
    icon: '↓',
    badge: '热',
    processVariant: 'paraphrase',
    formVariant: 'paraphrase',
    titleFieldLabel: '上传文件',
    titlePlaceholder: '',
    submitLabel: '立即降重',
    agreementText:
      '我已阅读并同意：生成的范文仅用于参考，不作为毕业、发表使用',
    showFaq: true,
    centerTitle: true,
  },
  {
    id: 'aigc-reduce',
    label: 'AIGC降重',
    icon: '🤖',
    processVariant: 'paraphrase',
    formVariant: 'paraphrase',
    titleFieldLabel: '上传文件',
    titlePlaceholder: '',
    submitLabel: '立即降重',
    agreementText:
      '我已阅读并同意：降重结果仅用于参考，请自行核对',
    centerTitle: true,
  },
  {
    id: 'ppt',
    label: '答辩PPT',
    icon: '📊',
    processVariant: 'ppt',
    formVariant: 'ppt',
    titleFieldLabel: '提交答辩主题',
    titlePlaceholder: '输入答辩PPT主题或论文标题',
    submitLabel: '立即生成PPT',
    agreementText: defaultAgreement,
  },
  {
    id: 'imitate',
    label: '论文仿写',
    icon: '✍️',
    processVariant: 'standard',
    formVariant: 'generic',
    titleFieldLabel: '提交仿写标题',
    titlePlaceholder: '输入需要仿写的论文标题或主题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'ppt-master',
    label: 'PPT高定大师',
    icon: '🎨',
    processVariant: 'ppt',
    formVariant: 'ppt',
    titleFieldLabel: '提交PPT主题',
    titlePlaceholder: '输入PPT主题与风格要求',
    submitLabel: '立即定制',
    agreementText: defaultAgreement,
  },
  {
    id: 'lit-ppt',
    label: '文献汇报PPT',
    icon: '📑',
    processVariant: 'ppt',
    formVariant: 'ppt',
    titleFieldLabel: '提交汇报主题',
    titlePlaceholder: '输入文献汇报主题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'sci-en',
    label: 'SCI论文 英文',
    icon: '🌐',
    processVariant: 'journal',
    formVariant: 'generic',
    titleFieldLabel: '提交论文标题',
    titlePlaceholder: 'Enter paper title (English)',
    submitLabel: 'Generate Now',
    agreementText: defaultAgreement,
  },
  {
    id: 'sci-premium',
    label: 'SCI论文 高端',
    icon: '💎',
    processVariant: 'journal',
    formVariant: 'generic',
    titleFieldLabel: '提交论文标题',
    titlePlaceholder: '输入SCI论文标题（英文）',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'report',
    label: '调研报告',
    icon: '📈',
    processVariant: 'standard',
    formVariant: 'generic',
    titleFieldLabel: '提交调研主题',
    titlePlaceholder: '输入调研报告主题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'translate',
    label: '文献翻译',
    icon: '🔤',
    processVariant: 'upload',
    formVariant: 'upload',
    titleFieldLabel: '上传文献',
    titlePlaceholder: '',
    submitLabel: '立即翻译',
    centerTitle: true,
  },
  {
    id: 'intern',
    label: '实习报告',
    icon: '💼',
    processVariant: 'course',
    formVariant: 'generic',
    titleFieldLabel: '提交实习报告标题',
    titlePlaceholder: '输入实习报告标题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'data',
    label: '数据分析',
    icon: '📉',
    processVariant: 'upload',
    formVariant: 'upload',
    titleFieldLabel: '上传数据文件',
    titlePlaceholder: '',
    submitLabel: '开始分析',
    centerTitle: true,
  },
  {
    id: 'survey',
    label: '调查报告',
    icon: '📋',
    processVariant: 'standard',
    formVariant: 'generic',
    titleFieldLabel: '提交调查主题',
    titlePlaceholder: '输入调查报告主题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'questionnaire',
    label: '问卷设计',
    icon: '❓',
    processVariant: 'standard',
    formVariant: 'generic',
    titleFieldLabel: '提交问卷主题',
    titlePlaceholder: '输入问卷研究主题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'experiment',
    label: '实验报告',
    icon: '🧪',
    processVariant: 'course',
    formVariant: 'generic',
    titleFieldLabel: '提交实验报告标题',
    titlePlaceholder: '输入实验报告标题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'book',
    label: '读后感',
    icon: '📕',
    processVariant: 'standard',
    formVariant: 'generic',
    titleFieldLabel: '提交书名/主题',
    titlePlaceholder: '输入书籍名称或读后感主题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
  {
    id: 'ideology',
    label: '思想汇报',
    icon: '⭐',
    processVariant: 'standard',
    formVariant: 'generic',
    titleFieldLabel: '提交汇报主题',
    titlePlaceholder: '输入思想汇报主题',
    submitLabel: '立即生成',
    agreementText: defaultAgreement,
  },
]

export const products: ProductConfig[] = rawProducts.map(withMeta)

export const sidebarMenu = products.map((p) => ({
  id: p.id,
  label: p.label,
  icon: p.icon,
  badge: p.badge,
}))

export function getProduct(id: string): ProductConfig {
  return products.find((p) => p.id === id) ?? products[0]!
}
