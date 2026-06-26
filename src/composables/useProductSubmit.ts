import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { createJob } from '@/api/modules/jobs'
import { createOrder, quoteOrder } from '@/api/modules/orders'
import { useApiEnabled } from '@/api/http'
import { useMockLoading } from '@/composables/useMockLoading'
import { usePaperRoute } from '@/composables/usePaperRoute'
import { requireLogin } from '@/composables/useRequireLogin'
import { useRecentActivity } from '@/composables/useRecentActivity'
import { capturePrefsFromDraft } from '@/composables/useUserPreferences'
import { usePaperStore } from '@/stores/paper'
import { useAppStore } from '@/stores/app'
import { useTaskPayStore } from '@/stores/taskPay'
import type { ProductConfig } from '@/types/product'

const SKIP_PREVIEW: ProductConfig['formVariant'][] = [
  'revise',
  'aigc',
  'paraphrase',
  'upload',
  'ppt',
]

export function useProductSubmit(product: () => ProductConfig) {
  const paperStore = usePaperStore()
  const appStore = useAppStore()
  const taskPay = useTaskPayStore()
  const router = useRouter()
  const { goToStep, openWizard, openProEdition, ensureLunwen } = usePaperRoute()
  const { loading, run } = useMockLoading(1500)
  const agreed = ref(false)
  const { notice: recentNotice, isDemo: recentIsDemo, setFromSubmit, setFromJob } =
    useRecentActivity()

  function orderParams(cfg: ProductConfig) {
    const d = paperStore.draft
    return {
      productId: cfg.id,
      paperId: paperStore.currentId ?? undefined,
      dCode: appStore.dCode || undefined,
      degree: d?.meta.degree,
      wordCount: d?.meta.wordCount,
      modelType: d?.model ?? 'standard',
    }
  }

  /** 改稿/降重等快捷任务：先报价，免费则直接建任务，否则下单支付 */
  async function submitQuickTask(
    cfg: ProductConfig,
    options?: {
      successMsg?: string
      jobPayload?: Record<string, unknown>
    },
  ) {
    if (!(await requireLogin())) return
    if (paperStore.draft) {
      capturePrefsFromDraft(paperStore.draft.meta, paperStore.draft.model)
      if (!(await paperStore.persistDraft())) return
    }

    const params = orderParams(cfg)
    try {
      const quote = await quoteOrder({
        productId: params.productId,
        degree: params.degree,
        wordCount: params.wordCount,
        modelType: params.modelType,
      })

      if (quote.willUseFreeQuota) {
        const job = await createJob(cfg.id, params.paperId, {
          title: paperStore.draft?.title,
          taskType: cfg.taskType,
          dCode: params.dCode,
          ...options?.jobPayload,
        })
        setFromJob(job.jobNo)
        ElMessage.success(options?.successMsg ?? `${cfg.submitLabel}已提交`)
        void router.push({ name: 'jobDetail', params: { id: String(job.id) } })
        return
      }

      const order = await createOrder(params)
      if (order.channelInvalid) {
        ElMessage.warning('推广码无效，订单已创建但不计入渠道分成')
      }
      if (order.id == null) {
        ElMessage.error('订单创建失败，请重试')
        return
      }
      taskPay.open({
        orderId: order.id,
        productId: params.productId,
        paperId: params.paperId,
        degree: params.degree,
        wordCount: params.wordCount,
        modelType: params.modelType,
      })
      ElMessage.info('请完成支付后自动开始任务')
    } catch (e) {
      ElMessage.error((e as Error).message || '提交失败')
    }
  }

  async function submit(options?: {
    requireTitle?: boolean
    skipPreview?: boolean
    successMsg?: string
    forceWizard?: boolean
    jobPayload?: Record<string, unknown>
  }) {
    const cfg = product()
    if (!agreed.value) {
      ElMessage.warning('请先阅读并同意使用须知')
      return
    }
    const noTitleForms: typeof cfg.formVariant[] = [
      'revise',
      'aigc',
      'paraphrase',
      'upload',
    ]
    const requireTitle = options?.requireTitle ?? !noTitleForms.includes(cfg.formVariant)
    if (requireTitle) {
      const title = paperStore.draft?.title.trim() ?? ''
      if (title.length < 5) {
        ElMessage.warning('请输入 5-50 字的标题')
        return
      }
      if (title.length > 50) {
        ElMessage.warning('标题不超过 50 字')
        return
      }
    }

    const skipPreview = options?.skipPreview ?? SKIP_PREVIEW.includes(cfg.formVariant)
    const enterWizard = options?.forceWizard || cfg.flowType === 'wizard'
    const enterPro = cfg.flowType === 'both' && !!cfg.proLinkText && !enterWizard

    if (useApiEnabled()) {
      if (skipPreview) {
        await submitQuickTask(cfg, options)
        return
      }
      if (!enterWizard && !enterPro) {
        if (!(await requireLogin())) return
        ensureLunwen()
        if (!(await paperStore.persistDraft())) return
        if (paperStore.draft) {
          capturePrefsFromDraft(paperStore.draft.meta, paperStore.draft.model)
        }
        openWizard()
        goToStep(1)
        ElMessage.success('请继续补充文献与提纲，最后在预览页生成范文')
        return
      }
      if (enterWizard || enterPro) {
        if (!(await requireLogin())) return
        ensureLunwen()
        if (!(await paperStore.persistDraft())) return
        if (paperStore.draft) {
          capturePrefsFromDraft(paperStore.draft.meta, paperStore.draft.model)
        }
        ElMessage.success(
          enterWizard ? '已进入分步向导，请在预览页生成范文' : '已进入专业版，请在预览页生成范文',
        )
        if (enterWizard) {
          openWizard()
        } else {
          openProEdition()
        }
        return
      }
    }

    if (skipPreview) {
      ElMessage.warning('改稿/降重/AIGC 等任务需连接后端，请配置 VITE_USE_API=true 并登录后使用')
      return
    }
    await run(async () => {})
    const title = paperStore.draft?.title ?? cfg.label
    setFromSubmit(cfg.label, title)
    ElMessage.success(options?.successMsg ?? `${cfg.submitLabel}已提交（演示，未创建任务）`)
    await paperStore.persistDraft()
    ensureLunwen()
    if (options?.forceWizard || cfg.flowType === 'wizard') {
      openWizard()
      return
    }
    if (cfg.flowType === 'both' && cfg.proLinkText) {
      openProEdition()
      return
    }
    openWizard()
    goToStep(1)
  }

  return { loading, agreed, recentNotice, recentIsDemo, submit }
}
