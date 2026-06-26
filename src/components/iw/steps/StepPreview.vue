<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import PayDialog from '@/components/iw/common/PayDialog.vue'
import JobProgressPanel from '@/components/iw/common/JobProgressPanel.vue'
import NavFooter from '@/components/iw/common/NavFooter.vue'
import { clampProgress } from '@/composables/jobProgressDisplay'
import { formatOutlineLabel } from '@/composables/useOutlineNumbering'
import { useMockLoading } from '@/composables/useMockLoading'
import { followJob } from '@/composables/useJobFollow'
import { requireLogin } from '@/composables/useRequireLogin'
import { getToken, useApiEnabled } from '@/api/http'
import { usePaperRoute } from '@/composables/usePaperRoute'
import { cancelJob, createJob, fetchPreviewJobByPaper, getJob, retryJob } from '@/api/modules/jobs'
import { downloadDelivery, fetchPaperDeliveries, type DeliveryFile } from '@/api/modules/files'
import { fetchPaper } from '@/api/modules/papers'
import { fetchOrder, quoteOrder, type OrderPriceQuote } from '@/api/modules/orders'
import { useAppStore } from '@/stores/app'
import { usePaperStore } from '@/stores/paper'
import type { PaperPreview } from '@/types/paper'

const emit = defineEmits<{ prev: [] }>()

const route = useRoute()
const router = useRouter()
const paperStore = usePaperStore()
const appStore = useAppStore()
const { goToStep } = usePaperRoute()
const { loading: genLoading, run: runGenerate } = useMockLoading()
const payVisible = ref(false)
const status = ref<'idle' | 'generating' | 'done'>('idle')
const progress = ref(0)
const payAmount = ref(0)
const priceQuote = ref<OrderPriceQuote | null>(null)
const deliveryFiles = ref<DeliveryFile[]>([])
const deliveriesLoading = ref(false)
let stopFollow: (() => void) | null = null
let deliveryPollTimer: ReturnType<typeof setTimeout> | null = null
const activeJobId = ref<number | null>(null)
const cancelling = ref(false)

const draft = computed(() => paperStore.draft)
const preview = computed(() => draft.value?.preview)
const sectionProgress = computed(() => preview.value?.generationProgress)
const apiMode = computed(() => useApiEnabled())
const canFreePreview = computed(
  () =>
    apiMode.value &&
    appStore.isLoggedIn &&
    priceQuote.value?.willUseFreeQuota === true,
)
const payLabel = computed(() => {
  const q = priceQuote.value
  if (!apiMode.value) return '支付下载完整版'
  if (!q) return `支付下载完整版（¥${payAmount.value.toFixed(2)}）`
  if (q.willUseFreeQuota || q.finalAmount === 0) return '使用 VIP 免费额度生成'
  return `支付下载（¥${Number(q.finalAmount).toFixed(2)}，原价 ¥${Number(q.originalAmount).toFixed(2)}）`
})
const outlineLabels = computed(() => {
  const o = draft.value?.outline ?? []
  return o.map((_, i) => formatOutlineLabel(o, i))
})

const previewWordCount = computed(() => {
  const p = preview.value
  if (!p) return 0
  if (p.approxWords && p.approxWords > 0) return p.approxWords
  let n = (p.abstractZh?.length ?? 0) + Math.round((p.abstractEn?.length ?? 0) * 0.5)
  for (const s of p.sections ?? []) {
    n += s.content?.length ?? 0
  }
  return n
})

const targetWordCount = computed(() =>
  preview.value?.targetWordCount ?? draft.value?.meta.wordCount ?? 8000,
)

function buildLocalPreview(): PaperPreview {
  const d = draft.value!
  const title = d.title
  return {
    abstractZh: `本文围绕「${title}」展开研究，分析了相关理论基础与实践路径，并给出结论与展望。`,
    abstractEn: `This paper studies "${title}" and presents analysis and conclusions.`,
    sections: d.outline.map((n, i) => ({
      title: formatOutlineLabel(d.outline, i),
      content: `【${n.title}】本节正文（离线预览）。`,
    })),
  }
}

function normalizePreview(raw: unknown): PaperPreview | null {
  if (!raw || typeof raw !== 'object') return null
  const p = raw as PaperPreview
  const sections = Array.isArray(p.sections)
    ? p.sections.filter((s) => s?.title && String(s.content ?? '').trim().length >= 15)
    : []
  const abstractZh = p.abstractZh?.trim() ?? ''
  if (sections.length === 0 && abstractZh.length < 40) return null
  return {
    abstractZh:
      abstractZh ||
      `本文围绕相关主题展开研究，共 ${sections.length} 个章节，详见正文。`,
    abstractEn:
      p.abstractEn?.trim() ||
      'This paper presents research analysis and practical recommendations.',
    sections,
    approxWords: p.approxWords,
    targetWordCount: p.targetWordCount,
    plannedSections: p.plannedSections,
    generatedSections: p.generatedSections,
    generationProgress: p.generationProgress,
  }
}

function parsePreviewFromResult(resultJson?: string): PaperPreview | null {
  if (!resultJson) return null
  try {
    const result = JSON.parse(resultJson) as { preview?: unknown }
    return normalizePreview(result.preview)
  } catch {
    return null
  }
}

async function refreshPreview() {
  if (!paperStore.currentId || !apiMode.value) return
  try {
    const remote = await fetchPaper(paperStore.currentId)
    const normalized = normalizePreview(remote.preview)
    if (normalized) paperStore.setPreview(normalized)
  } catch {
    /* ignore */
  }
}

async function loadDeliveries() {
  if (!apiMode.value || !paperStore.currentId) return
  deliveriesLoading.value = true
  try {
    deliveryFiles.value = await fetchPaperDeliveries(paperStore.currentId)
  } catch {
    deliveryFiles.value = []
  } finally {
    deliveriesLoading.value = false
  }
}

function stopDeliveryPoll() {
  if (deliveryPollTimer) {
    clearTimeout(deliveryPollTimer)
    deliveryPollTimer = null
  }
}

/** 任务成功后轮询交付文件，避免 DOCX 归档延迟需手动刷新 */
async function pollDeliveriesUntilReady(maxMs = 60_000) {
  stopDeliveryPoll()
  const start = Date.now()
  await loadDeliveries()
  if (deliveryFiles.value.length) return
  const tick = async () => {
    if (Date.now() - start > maxMs) {
      stopDeliveryPoll()
      return
    }
    await loadDeliveries()
    if (deliveryFiles.value.length) {
      stopDeliveryPoll()
      return
    }
    deliveryPollTimer = setTimeout(() => void tick(), 3000)
  }
  deliveryPollTimer = setTimeout(() => void tick(), 3000)
}

function applySectionProgressBlend() {
  const sp = sectionProgress.value
  if (status.value !== 'generating' || !sp?.total || sp.total <= 0) return
  const blended = clampProgress(12 + Math.round(((sp.done ?? 0) / sp.total) * 76))
  if (blended > progress.value) progress.value = blended
}

function beginFollow(jobId: number, successMsg: string) {
  stopFollow?.()
  activeJobId.value = jobId
  status.value = 'generating'
  progress.value = 0
  genLoading.value = true
  let lastDraftPull = 0

  stopFollow = followJob(jobId, {
    onProgress: (p) => {
      progress.value = clampProgress(p)
      applySectionProgressBlend()
      const now = Date.now()
      if (apiMode.value && now - lastDraftPull > 2500) {
        lastDraftPull = now
        void refreshPreview().then(() => applySectionProgressBlend())
      }
    },
    onDone: async (_job, resultJson) => {
      activeJobId.value = null
      const fromJob = parsePreviewFromResult(resultJson)
      if (fromJob) paperStore.setPreview(fromJob)
      else await refreshPreview()
      progress.value = 100
      status.value = 'done'
      genLoading.value = false
      await pollDeliveriesUntilReady()
      void loadQuote()
      ElMessage.success(successMsg)
    },
    onError: async (msg) => {
      activeJobId.value = null
      if (msg === '任务已取消') {
        status.value = preview.value ? 'done' : 'idle'
        genLoading.value = false
        return
      }
      try {
        const latest = await getJob(jobId)
        if (latest.status === 'success') {
          const fromJob = parsePreviewFromResult(latest.resultJson)
          if (fromJob) paperStore.setPreview(fromJob)
          else await refreshPreview()
          progress.value = 100
          status.value = 'done'
          genLoading.value = false
          await pollDeliveriesUntilReady()
          ElMessage.success(successMsg)
          return
        }
      } catch {
        /* ignore */
      }
      ElMessage.error(msg || '生成失败')
      status.value = preview.value ? 'done' : 'idle'
      genLoading.value = false
    },
  })
}

function validateBeforeGenerate(): boolean {
  const title = draft.value?.title?.trim() ?? ''
  if (title.length < 5) {
    ElMessage.warning('请先在标题步骤填写 5 字以上的论文标题')
    return false
  }
  const outline = paperStore.effectiveOutlineText().trim()
  if (outline.length < 20) {
    ElMessage.warning('请先在提纲步骤完善大纲（至少 20 字）')
    goToStep(2)
    return false
  }
  return true
}

async function loadQuote() {
  const d = draft.value
  if (!d || !apiMode.value || !getToken()) return
  try {
    const q = await quoteOrder({
      productId: appStore.activeMenuId,
      degree: d.meta.degree,
      wordCount: d.meta.wordCount,
      modelType: d.model ?? 'standard',
    })
    priceQuote.value = q
    payAmount.value = Number(q.finalAmount ?? q.price)
  } catch (e) {
    priceQuote.value = null
    payAmount.value = 0
    ElMessage.warning((e as Error).message || '无法加载报价，请稍后重试')
  }
}

async function startGenerate() {
  if (!draft.value) return
  if (apiMode.value && !(await requireLogin())) return
  if (!validateBeforeGenerate()) return

  stopFollow?.()
  if (paperStore.effectiveOutlineText()) {
    paperStore.setOutlineText(paperStore.effectiveOutlineText())
  }
  if (apiMode.value && !(await paperStore.persistDraft())) return

  if (!apiMode.value) {
    status.value = 'generating'
    progress.value = 0
    const tick = setInterval(() => {
      if (progress.value < 95) progress.value += 5
    }, 120)
    await runGenerate(async () => {})
    clearInterval(tick)
    progress.value = 100
    paperStore.setPreview(buildLocalPreview())
    status.value = 'done'
    ElMessage.success('范文已生成（离线预览）')
    return
  }

  try {
    const outlineText =
      paperStore.effectiveOutlineText() || draft.value.title

    const job = await createJob(appStore.activeMenuId, paperStore.currentId ?? undefined, {
      title: draft.value.title,
      outlineText,
      paperId: paperStore.currentId,
      wordCount: draft.value.meta.wordCount,
      degree: draft.value.meta.degree,
    })
    beginFollow(job.id, '范文生成完成')
  } catch (e) {
    ElMessage.warning((e as Error).message)
    status.value = 'idle'
    genLoading.value = false
  }
}

async function onCancelGenerate() {
  const jobId = activeJobId.value
  if (!jobId || !apiMode.value) return
  try {
    await ElMessageBox.confirm('确定取消当前生成任务？已生成的章节预览可能保留在草稿中。', '取消生成', {
      type: 'warning',
      confirmButtonText: '取消任务',
      cancelButtonText: '继续生成',
    })
  } catch {
    return
  }
  cancelling.value = true
  try {
    stopFollow?.()
    stopFollow = null
    await cancelJob(jobId)
    activeJobId.value = null
    status.value = preview.value ? 'done' : 'idle'
    genLoading.value = false
    progress.value = preview.value ? 100 : 0
    ElMessage.success('已取消生成')
  } catch (e) {
    ElMessage.error((e as Error).message || '取消失败')
    if (status.value === 'generating') {
      beginFollow(jobId, '范文生成完成')
    }
  } finally {
    cancelling.value = false
  }
}

async function onRegenerate() {
  if (apiMode.value && appStore.isLoggedIn && !canFreePreview.value) {
    ElMessage.warning('今日免费预览次数已用完，请支付后生成')
    void openPay()
    return
  }
  if (apiMode.value && paperStore.currentId) {
    try {
      const j = await fetchPreviewJobByPaper(paperStore.currentId)
      if (j && (j.status === 'failed' || j.status === 'cancelled')) {
        const retried = await retryJob(j.id)
        beginFollow(retried.id, '范文生成完成')
        return
      }
    } catch {
      /* 无历史任务则新建 */
    }
  }
  void startGenerate()
}

async function openPay() {
  if (apiMode.value && !(await requireLogin())) return
  if (!validateBeforeGenerate()) return
  if (paperStore.effectiveOutlineText()) {
    paperStore.setOutlineText(paperStore.effectiveOutlineText())
  }
  if (apiMode.value && !(await paperStore.persistDraft())) return
  payVisible.value = true
}

function onOrderPaid(payload: { orderId: number; jobId?: number }) {
  if (payload.jobId) {
    beginFollow(payload.jobId, '支付成功，正在生成交付文件')
    return
  }
  void waitForFulfillmentJob(payload.orderId)
}

async function waitForFulfillmentJob(orderId: number) {
  for (let i = 0; i < 30; i++) {
    try {
      const o = await fetchOrder(orderId)
      if (o.jobId) {
        beginFollow(o.jobId, '支付成功，正在生成交付文件')
        return
      }
    } catch {
      /* 履约任务创建中，继续轮询 */
    }
    await new Promise((r) => setTimeout(r, 1000))
  }
  void refreshPreview()
  void loadDeliveries()
  ElMessage.info('支付成功，交付任务创建中，请稍候或刷新页面')
}

async function downloadFile(file: DeliveryFile) {
  if (apiMode.value && !(await requireLogin())) return
  try {
    await downloadDelivery(file.id, file.fileName)
    ElMessage.success('已开始下载')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function downloadDocx() {
  if (apiMode.value) {
    if (!(await requireLogin())) return
    if (!paperStore.currentId) {
      ElMessage.warning('草稿未保存')
      return
    }
    await loadDeliveries()
    const f = deliveryFiles.value.find((x) => x.fileType === 'docx')
    if (f) {
      await downloadFile(f)
      return
    }
    if (status.value !== 'done') {
      ElMessage.warning('请先生成范文，或完成支付后等待任务结束')
      return
    }
    ElMessage.warning('Word 文件生成中，请稍后刷新再试')
    return
  }
  ElMessage.info('离线模式请使用 TXT 下载')
}

async function downloadTxt() {
  if (apiMode.value) {
    if (!(await requireLogin())) return
    if (!paperStore.currentId) {
      ElMessage.warning('草稿未保存')
      return
    }
    await loadDeliveries()
    if (deliveryFiles.value.length) {
      const f = deliveryFiles.value.find((x) => x.fileType === 'txt') ?? deliveryFiles.value[0]
      await downloadFile(f)
      return
    }
    if (status.value !== 'done') {
      ElMessage.warning('请先生成范文，或完成支付后等待任务结束')
      return
    }
    ElMessage.warning('交付文件生成中，请稍后刷新再试')
    return
  }

  const p = preview.value
  if (!p) {
    ElMessage.warning('请先生成范文')
    return
  }
  const d = draft.value
  if (!d) return
  const lines = [
    d.title,
    '',
    '摘要',
    p.abstractZh,
    '',
    'ABSTRACT',
    p.abstractEn,
    '',
    ...p.sections.flatMap((s) => [`\n${s.title}`, s.content]),
  ]
  const blob = new Blob([lines.join('\n')], { type: 'text/plain;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${d.title || '论文'}.txt`
  a.click()
  URL.revokeObjectURL(a.href)
}

function formatSize(bytes?: number) {
  if (!bytes) return '—'
  if (bytes < 1024) return `${bytes} B`
  return `${Math.round(bytes / 1024)} KB`
}

watch(
  () => appStore.isLoggedIn,
  (loggedIn) => {
    if (loggedIn && apiMode.value) {
      void loadQuote()
      void loadDeliveries()
      void paperStore.persistDraft()
    }
  },
)

watch(
  () => getToken(),
  (token) => {
    if (token && apiMode.value) void loadQuote()
  },
)

watch(sectionProgress, () => {
  applySectionProgressBlend()
})

async function resumePreviewJob() {
  if (!apiMode.value || !appStore.isLoggedIn || !paperStore.currentId) return
  try {
    const j = await fetchPreviewJobByPaper(paperStore.currentId)
    if (!j?.id) return
    if (j.status === 'pending' || j.status === 'running') {
      beginFollow(j.id, '范文生成完成')
      return
    }
    if (j.status === 'success' && status.value === 'idle') {
      const fromJob = parsePreviewFromResult(j.resultJson)
      if (fromJob) paperStore.setPreview(fromJob)
      else await refreshPreview()
      if (preview.value) {
        status.value = 'done'
        progress.value = 100
      }
      await loadDeliveries()
      return
    }
    if (j.status === 'failed' && status.value === 'idle') {
      ElMessage.warning(j.errorMsg || '上次生成失败，可点击「免费预览生成」重试')
    }
  } catch {
    /* ignore */
  }
}

function clearAutogenQuery() {
  if (route.query.autogen !== '1') return
  const q = { ...route.query }
  delete q.autogen
  void router.replace({ name: 'intelligentWriting', params: route.params, query: q })
}

async function tryAutogenFromQuery() {
  if (route.query.autogen !== '1' || status.value !== 'idle') return
  clearAutogenQuery()
  if (apiMode.value && appStore.isLoggedIn && !canFreePreview.value) {
    ElMessage.warning('今日免费预览次数已用完，请支付后生成')
    payVisible.value = true
    return
  }
  await startGenerate()
}

onMounted(() => {
  const existing = normalizePreview(draft.value?.preview)
  if (existing) {
    paperStore.setPreview(existing)
    status.value = 'done'
    progress.value = 100
  }
  void loadQuote()
  if (apiMode.value && getToken()) {
    void loadDeliveries()
    if (status.value === 'idle') {
      void resumePreviewJob().then(() => {
        if (status.value === 'idle') void tryAutogenFromQuery()
      })
    }
  }
})

watch(
  () => route.query.autogen,
  (v) => {
    if (v === '1' && status.value === 'idle') void tryAutogenFromQuery()
  },
)

onUnmounted(() => {
  stopFollow?.()
  stopDeliveryPoll()
})
</script>

<template>
  <div v-if="!draft" class="step-preview">
    <el-skeleton :rows="6" animated />
  </div>
  <div v-else class="step-preview">
    <el-alert
      v-if="apiMode && !appStore.isLoggedIn"
      type="warning"
      :closable="false"
      show-icon
      title="预览与下载需先登录"
      description="登录后可将草稿同步云端，并生成可下载的交付文件。"
      class="login-alert"
    />

    <section class="xw-card">
      <h2 class="xw-section-title">预览 / 下载</h2>

      <div class="benefits">
        <el-tag type="success">AI 辅助范文生成</el-tag>
        <el-tag type="warning">AIGC 说明报告（非第三方检测）</el-tag>
        <el-tag v-if="appStore.vipLevel > 0" type="warning">VIP{{ appStore.vipLevel }}</el-tag>
        <el-tag>按学校标注格式（持续扩充）</el-tag>
      </div>

      <el-alert
        v-if="apiMode && appStore.isLoggedIn && priceQuote"
        :type="canFreePreview ? 'success' : 'info'"
        :closable="false"
        show-icon
        class="quota-alert"
        :title="
          canFreePreview
            ? `今日可免费预览 ${priceQuote.freeRemaining} 次（已用 ${priceQuote.usedToday}/${priceQuote.dailyFree}）`
            : `今日免费预览已用完，支付享 ${priceQuote.discountPercent}% 折扣（¥${Number(priceQuote.finalAmount).toFixed(2)}）`
        "
      />

      <div v-if="status === 'idle'" class="actions-top">
        <el-button
          v-if="!apiMode || !appStore.isLoggedIn || canFreePreview"
          type="primary"
          size="large"
          :loading="genLoading"
          @click="startGenerate"
        >
          {{
            !apiMode
              ? '开始生成范文（离线）'
              : !appStore.isLoggedIn
                ? '登录后免费预览'
                : canFreePreview
                  ? '免费预览生成'
                  : '开始生成范文'
          }}
        </el-button>
        <el-button
          v-else
          type="primary"
          size="large"
          @click="openPay"
        >
          今日免费次数已用完，支付后生成
        </el-button>
        <el-button
          v-if="apiMode && (appStore.isLoggedIn || getToken()) && canFreePreview"
          size="large"
          type="success"
          plain
          @click="openPay"
        >
          {{ payLabel }}
        </el-button>
      </div>

      <div v-else-if="status === 'generating'" class="generating">
        <JobProgressPanel
          :percentage="progress"
          :section-done="sectionProgress?.done"
          :section-total="sectionProgress?.total ?? preview?.plannedSections"
          active
          status="generating"
        />
        <p v-if="preview?.sections?.length" class="partial-hint top-hint">
          已生成 {{ preview.sections.length }} 节，可下拉查看实时内容
        </p>
        <el-button
          v-if="apiMode && activeJobId"
          class="cancel-gen-btn"
          :loading="cancelling"
          @click="onCancelGenerate"
        >
          取消生成
        </el-button>
        <div
          v-if="preview?.sections?.length"
          class="preview-block partial-preview"
        >
          <p class="partial-hint">章节预览</p>
          <div v-for="(s, i) in preview.sections" :key="i" class="section">
            <h4>{{ s.title }}</h4>
            <p>{{ s.content.slice(0, 200) }}{{ s.content.length > 200 ? '…' : '' }}</p>
          </div>
        </div>
      </div>

      <template v-else>
        <h3>{{ draft.title }}</h3>
        <p v-if="preview" class="word-count-hint">
          当前范文约 <strong>{{ previewWordCount }}</strong> 字
          <template v-if="preview.plannedSections">
            ，共 <strong>{{ preview.sections?.length ?? 0 }}</strong> / {{ preview.plannedSections }} 节
          </template>
          <template v-if="targetWordCount">（目标 {{ targetWordCount }} 字，可在标题步骤调整字数）</template>
        </p>
        <div class="preview-block">
          <h4>摘要</h4>
          <p>{{ preview?.abstractZh }}</p>
          <h4>ABSTRACT</h4>
          <p>{{ preview?.abstractEn }}</p>
          <h4>大纲</h4>
          <ul>
            <li v-for="(l, i) in outlineLabels" :key="i">{{ l }}</li>
          </ul>
          <h4>正文节选</h4>
          <div v-for="(s, i) in preview?.sections" :key="i" class="section">
            <strong>{{ s.title }}</strong>
            <p>{{ s.content }}</p>
          </div>
        </div>

        <div v-if="apiMode && deliveryFiles.length" class="delivery-list">
          <h4>交付文件</h4>
          <el-table v-loading="deliveriesLoading" :data="deliveryFiles" size="small" stripe>
            <el-table-column prop="fileName" label="文件名" min-width="160" />
            <el-table-column prop="fileType" label="类型" width="72" />
            <el-table-column label="大小" width="88">
              <template #default="{ row }">{{ formatSize(row.sizeBytes) }}</template>
            </el-table-column>
            <el-table-column label="操作" width="88">
              <template #default="{ row }">
                <el-button link type="primary" @click="downloadFile(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
        </div>
        <p v-else-if="apiMode && status === 'done'" class="delivery-hint">
          交付文件归档中，请稍后点击「刷新文件列表」
        </p>

        <div class="actions-top">
          <el-button type="primary" @click="downloadTxt">下载 TXT</el-button>
          <el-button v-if="apiMode" type="success" plain @click="downloadDocx">下载 Word</el-button>
          <el-button v-if="apiMode" @click="loadDeliveries">刷新文件列表</el-button>
          <el-button @click="openPay">{{ payLabel }}</el-button>
          <el-button @click="onRegenerate">重新生成</el-button>
        </div>
      </template>
    </section>

    <NavFooter show-prev :show-next="false" @prev="emit('prev')" />

    <PayDialog
      v-model:visible="payVisible"
      :amount="payAmount"
      :product-id="appStore.activeMenuId"
      :paper-id="paperStore.currentId ?? undefined"
      :degree="draft.meta.degree"
      :word-count="draft.meta.wordCount"
      :model-type="draft.model ?? 'standard'"
      @paid="onOrderPaid"
    />
  </div>
</template>

<style scoped>
.login-alert,
.quota-alert {
  margin-bottom: 16px;
}

.benefits {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 20px;
}

.actions-top {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 20px;
}

.generating {
  padding: 24px 0;
}

.generating p {
  margin-bottom: 12px;
  color: var(--xw-primary);
  font-weight: 500;
}

.cancel-gen-btn {
  margin-top: 12px;
}

.word-count-hint {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--xw-muted);
}

.word-count-hint strong {
  color: var(--xw-primary);
  font-size: 15px;
}

.preview-block h4 {
  margin: 16px 0 8px;
  font-size: 15px;
}

.preview-block p,
.preview-block li {
  font-size: 14px;
  color: #374151;
  line-height: 1.7;
}

.partial-preview {
  margin-top: 16px;
  max-height: 360px;
  overflow-y: auto;
  border: 1px dashed #cbd5e1;
  padding: 12px;
  border-radius: 8px;
}
.partial-hint {
  font-size: 13px;
  color: #64748b;
  margin: 0 0 8px;
}

.top-hint {
  margin-top: 12px;
  text-align: center;
}
.preview-block ul {
  padding-left: 20px;
}

.section {
  margin-top: 12px;
  padding: 12px;
  background: #f9fafb;
  border-radius: 8px;
}

.delivery-list {
  margin: 20px 0;
}

.delivery-list h4 {
  margin: 0 0 12px;
  font-size: 15px;
}

.delivery-hint {
  font-size: 13px;
  color: var(--xw-muted);
  margin: 0 0 12px;
}
</style>
