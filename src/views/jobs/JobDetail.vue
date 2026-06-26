<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import JobProgressPanel from '@/components/iw/common/JobProgressPanel.vue'
import { followJob } from '@/composables/useJobFollow'
import { clampProgress } from '@/composables/jobProgressDisplay'
import { cancelJob, getJob, retryJob, type JobDto } from '@/api/modules/jobs'
import { fetchPaper } from '@/api/modules/papers'
import {
  downloadJobDelivery,
  fetchJobDeliveries,
  type DeliveryFile,
} from '@/api/modules/files'
import { useApiEnabled } from '@/api/http'
import {
  parseJobResult,
  resultDisplayText,
  TASK_TYPE_LABELS,
  type JobResultPayload,
} from '@/utils/jobResult'
import type { PaperPreview } from '@/types/paper'

const route = useRoute()
const router = useRouter()
const apiMode = computed(() => useApiEnabled())

const jobId = computed(() => Number(route.params.id))
const job = ref<JobDto | null>(null)
const result = ref<JobResultPayload | null>(null)
const preview = ref<PaperPreview | null>(null)
const status = ref<'loading' | 'generating' | 'done' | 'failed'>('loading')
const progress = ref(0)
const errorMsg = ref('')
const deliveries = ref<DeliveryFile[]>([])
const retrying = ref(false)
const cancelling = ref(false)
let stopFollow: (() => void) | null = null

const taskLabel = computed(
  () => TASK_TYPE_LABELS[job.value?.taskType ?? ''] ?? '任务结果',
)
const displayText = computed(() => {
  if (preview.value) {
    const p = preview.value
    const parts: string[] = []
    if (p.abstractZh) parts.push(`【摘要】\n${p.abstractZh}`)
    for (const s of p.sections ?? []) {
      parts.push(`\n${s.title}\n${s.content}`)
    }
    return parts.join('\n\n')
  }
  return resultDisplayText(result.value)
})

async function loadDeliveries() {
  if (!apiMode.value || !jobId.value) return
  try {
    deliveries.value = await fetchJobDeliveries(jobId.value)
  } catch {
    deliveries.value = []
  }
}

async function loadPreviewFromPaper(paperId?: string) {
  if (!paperId || !apiMode.value) return
  try {
    const remote = await fetchPaper(paperId)
    if (remote.preview) preview.value = remote.preview
  } catch {
    /* ignore */
  }
}

function applyJob(j: JobDto) {
  job.value = j
  result.value = parseJobResult(j.resultJson)
  if (j.status === 'success') {
    status.value = 'done'
    progress.value = 100
    if (result.value?.hasPreview && result.value.paperId) {
      void loadPreviewFromPaper(result.value.paperId)
    }
    void loadDeliveries()
  } else if (j.status === 'failed') {
    status.value = 'failed'
    errorMsg.value = j.errorMsg || '任务失败'
  } else if (j.status === 'running' || j.status === 'pending') {
    status.value = 'generating'
    progress.value = clampProgress(j.progress ?? 0)
  }
}

function startFollow(id: number) {
  stopFollow?.()
  status.value = 'generating'
  stopFollow = followJob(id, {
    onProgress: (p) => {
      progress.value = clampProgress(p)
    },
    onDone: async (j) => {
      applyJob(j)
      status.value = 'done'
      ElMessage.success('任务已完成')
    },
    onError: (msg) => {
      status.value = 'failed'
      errorMsg.value = msg
      ElMessage.error(msg)
    },
  })
}

async function init() {
  if (!apiMode.value) {
    status.value = 'failed'
    errorMsg.value = '请连接后端并登录后查看任务'
    return
  }
  if (!Number.isFinite(jobId.value)) {
    status.value = 'failed'
    errorMsg.value = '无效的任务 ID'
    return
  }
  try {
    const j = await getJob(jobId.value)
    applyJob(j)
    if (j.status === 'running' || j.status === 'pending') {
      startFollow(jobId.value)
    }
  } catch (e) {
    status.value = 'failed'
    errorMsg.value = (e as Error).message
  }
}

async function onCancel() {
  if (!jobId.value) return
  cancelling.value = true
  try {
    stopFollow?.()
    const j = await cancelJob(jobId.value)
    applyJob(j)
    status.value = 'failed'
    errorMsg.value = j.errorMsg || '任务已取消'
    ElMessage.success('任务已取消')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    cancelling.value = false
  }
}

async function onRetry() {
  if (!jobId.value) return
  retrying.value = true
  try {
    stopFollow?.()
    const j = await retryJob(jobId.value)
    applyJob(j)
    status.value = 'generating'
    progress.value = 0
    errorMsg.value = ''
    ElMessage.success('已重新提交任务')
    startFollow(jobId.value)
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    retrying.value = false
  }
}

function copyText() {
  const text = displayText.value
  if (!text.trim()) {
    ElMessage.warning('暂无可复制内容')
    return
  }
  navigator.clipboard.writeText(text).then(() => ElMessage.success('已复制'))
}

function downloadTxt() {
  const text = displayText.value
  if (!text.trim()) {
    ElMessage.warning('暂无可下载内容')
    return
  }
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const a = document.createElement('a')
  a.href = URL.createObjectURL(blob)
  a.download = `${taskLabel.value}.txt`
  a.click()
  URL.revokeObjectURL(a.href)
}

onMounted(() => void init())
onUnmounted(() => stopFollow?.())
</script>

<template>
  <div class="job-detail xw-card">
    <header class="head">
      <el-button text @click="router.back()">← 返回</el-button>
      <h1>{{ taskLabel }}</h1>
      <p v-if="job" class="meta">
        任务号 {{ job.jobNo }} · {{ job.taskType }}
        <span v-if="job.status === 'success'" class="ok">已完成</span>
        <span v-else-if="job.status === 'failed'" class="err">失败</span>
        <span v-else class="run">处理中</span>
      </p>
    </header>

    <JobProgressPanel
      v-if="status === 'generating'"
      :percentage="progress"
      active
      status="generating"
    />
    <div v-if="status === 'generating'" class="retry-row">
      <el-button :loading="cancelling" @click="onCancel">取消任务</el-button>
    </div>

    <el-alert v-if="status === 'failed'" type="error" :title="errorMsg" show-icon />
    <div v-if="status === 'failed' && job?.status === 'failed'" class="retry-row">
      <el-button type="primary" :loading="retrying" @click="onRetry">重新生成</el-button>
      <el-button @click="router.push({ name: 'orders' })">查看订单</el-button>
    </div>

    <section v-if="status === 'done' && preview" class="preview-block">
      <h3>摘要</h3>
      <p class="body">{{ preview.abstractZh }}</p>
      <div v-for="(sec, i) in preview.sections" :key="i" class="section">
        <h4>{{ sec.title }}</h4>
        <p class="body">{{ sec.content }}</p>
      </div>
    </section>

    <section v-else-if="status === 'done' && displayText" class="result-block">
      <pre class="body">{{ displayText }}</pre>
    </section>

    <section v-else-if="status === 'done'" class="empty">
      <el-empty description="任务已完成，暂无文本结果" />
    </section>

    <div v-if="status === 'done'" class="actions">
      <el-button @click="copyText">复制全文</el-button>
      <el-button type="primary" plain @click="downloadTxt">下载 TXT</el-button>
      <el-button
        v-for="f in deliveries"
        :key="f.id"
        type="primary"
        @click="downloadJobDelivery(f.id, f.fileName)"
      >
        下载 {{ f.fileType.toUpperCase() }}
      </el-button>
    </div>
  </div>
</template>

<style scoped>
.job-detail {
  max-width: 900px;
  margin: 24px auto;
  padding: 24px;
}
.retry-row {
  display: flex;
  gap: 8px;
  margin: 12px 0;
}

.head h1 {
  font-size: 20px;
  margin: 8px 0;
}

.meta {
  font-size: 13px;
  color: var(--xw-muted);
}

.ok { color: #16a34a; }
.err { color: #dc2626; }
.run { color: var(--xw-primary); }

.body {
  white-space: pre-wrap;
  line-height: 1.75;
  font-size: 14px;
}

.result-block pre {
  background: #f8fafc;
  padding: 16px;
  border-radius: 8px;
  max-height: 480px;
  overflow: auto;
}

.section {
  margin-top: 20px;
}

.section h4 {
  font-size: 15px;
  margin-bottom: 8px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 20px;
}

.empty {
  margin: 24px 0;
}
</style>
