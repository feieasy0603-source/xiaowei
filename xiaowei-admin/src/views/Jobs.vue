<script setup lang="ts">
import { onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import JobProgressBar from '@/components/JobProgressBar.vue'
import TaskTypeTag from '@/components/TaskTypeTag.vue'
import { isActiveJobStatus } from '@/utils/jobProgress'
import { adminFetch } from '@/api/http'
import type { PageResult } from '@/api/types'
import { JOB_STATUS, TASK_TYPES, flowTypeLabel, jobStatusLabel, jobStatusType } from '@/constants/ai'

interface AdminJob {
  id: number
  jobNo: string
  userId?: number
  userPhone?: string
  productId: string
  productLabel?: string
  flowType?: string
  paperId?: string
  orderId?: number
  orderNo?: string
  taskType: string
  status: string
  progress: number
  payloadJson?: string
  resultJson?: string
  errorMsg?: string
  createdAt?: string
  finishedAt?: string
}

interface JobStats {
  days: number
  total: number
  success: number
  failed: number
  running: number
  cancelled: number
  successRate: number
  avgDurationSec: number
  topErrors: { reason: string; count: number }[]
}

const route = useRoute()
const router = useRouter()
const loading = ref(false)
const list = ref<AdminJob[]>([])
const total = ref(0)
const selected = ref<AdminJob[]>([])
const stats = ref<JobStats | null>(null)
const statsDays = ref(7)

const query = reactive({
  status: '',
  taskType: '',
  productId: '',
  jobNo: '',
  userPhone: '',
  userId: '' as string | number,
  dateRange: [] as string[],
  page: 1,
  size: 20,
})

const detailVisible = ref(false)
const detail = ref<AdminJob | null>(null)
const detailTab = ref('result')

function buildParams() {
  const params = new URLSearchParams({
    page: String(query.page),
    size: String(query.size),
  })
  if (query.status) params.set('status', query.status)
  if (query.taskType) params.set('taskType', query.taskType)
  if (query.productId) params.set('productId', query.productId)
  if (query.jobNo) params.set('jobNo', query.jobNo)
  if (query.userPhone) params.set('userPhone', query.userPhone)
  if (query.userId) params.set('userId', String(query.userId))
  if (query.dateRange?.length === 2) {
    params.set('createdFrom', new Date(query.dateRange[0]).toISOString())
    params.set('createdTo', new Date(query.dateRange[1] + 'T23:59:59').toISOString())
  }
  return params
}

async function loadStats() {
  try {
    stats.value = await adminFetch<JobStats>(`/admin/jobs/stats?days=${statsDays.value}`)
  } catch {
    stats.value = null
  }
}

let listPollTimer: ReturnType<typeof setTimeout> | null = null
let detailPollTimer: ReturnType<typeof setTimeout> | null = null

function syncListPoll() {
  if (listPollTimer) {
    clearTimeout(listPollTimer)
    listPollTimer = null
  }
  const hasActive = list.value.some((j) => isActiveJobStatus(j.status))
  if (!hasActive) return
  const tick = async () => {
    await load(true, false)
    if (list.value.some((j) => isActiveJobStatus(j.status))) {
      listPollTimer = setTimeout(() => void tick(), 5000)
    } else {
      listPollTimer = null
    }
  }
  listPollTimer = setTimeout(() => void tick(), 5000)
}

function syncDetailPoll() {
  if (detailPollTimer) {
    clearTimeout(detailPollTimer)
    detailPollTimer = null
  }
  if (!detailVisible.value || !detail.value?.id || !isActiveJobStatus(detail.value.status)) return
  const id = detail.value.id
  const tick = async () => {
    try {
      detail.value = await adminFetch<AdminJob>(`/admin/jobs/${id}`)
      if (
        detailVisible.value &&
        detail.value?.id === id &&
        isActiveJobStatus(detail.value.status)
      ) {
        detailPollTimer = setTimeout(() => void tick(), 3000)
      } else {
        detailPollTimer = null
      }
    } catch {
      detailPollTimer = setTimeout(() => void tick(), 3000)
    }
  }
  detailPollTimer = setTimeout(() => void tick(), 3000)
}

async function load(silent = false, updatePoll = true) {
  if (!silent) loading.value = true
  try {
    const res = await adminFetch<PageResult<AdminJob>>(`/admin/jobs?${buildParams()}`)
    list.value = res.items
    total.value = res.total
    if (updatePoll) syncListPoll()
  } catch (e) {
    if (!silent) ElMessage.error((e as Error).message)
  } finally {
    if (!silent) loading.value = false
  }
}

function onPageChange() {
  void load()
}

function onSearch() {
  query.page = 1
  void load()
  void loadStats()
}

async function openDetail(row: AdminJob) {
  try {
    detail.value = await adminFetch<AdminJob>(`/admin/jobs/${row.id}`)
    detailTab.value = detail.value.status === 'failed' ? 'error' : 'result'
    detailVisible.value = true
    syncDetailPoll()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function retry(row: AdminJob) {
  try {
    await ElMessageBox.confirm(`重新执行任务 ${row.jobNo}？`, '重试')
    await adminFetch(`/admin/jobs/${row.id}/retry`, { method: 'POST' })
    ElMessage.success('已重新提交')
    await load()
    await loadStats()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

async function cancel(row: AdminJob) {
  try {
    await ElMessageBox.confirm(`取消运行中任务 ${row.jobNo}？`, '取消任务')
    await adminFetch(`/admin/jobs/${row.id}/cancel`, { method: 'POST' })
    ElMessage.success('已取消')
    await load()
    await loadStats()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

async function batchRetry() {
  const failed = selected.value.filter((r) => r.status === 'failed' || r.status === 'cancelled')
  if (!failed.length) {
    ElMessage.warning('请勾选失败或已取消的任务')
    return
  }
  try {
    await ElMessageBox.confirm(`批量重试 ${failed.length} 个任务？`, '批量重试')
    const res = await adminFetch<{ success: number; total: number; errors: string[] }>(
      '/admin/jobs/batch-retry',
      {
        method: 'POST',
        body: JSON.stringify({ ids: failed.map((r) => r.id) }),
      },
    )
    ElMessage.success(`已提交 ${res.success}/${res.total} 个重试`)
    if (res.errors?.length) console.warn(res.errors)
    await load()
    await loadStats()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

function formatJson(raw?: string) {
  if (!raw) return '—'
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

function onSelectionChange(rows: AdminJob[]) {
  selected.value = rows
}

watch(detailVisible, () => syncDetailPoll())

onMounted(() => {
  const jobNo = route.query.jobNo
  if (typeof jobNo === 'string' && jobNo) query.jobNo = jobNo
  void load()
  void loadStats()
})

onUnmounted(() => {
  if (listPollTimer) clearTimeout(listPollTimer)
  if (detailPollTimer) clearTimeout(detailPollTimer)
})
</script>

<template>
  <div>
    <AdminPageHeader
      title="AI 生成任务"
      desc="监控用户 createJob + SSE 异步任务，支持筛选、统计、批量重试与取消"
    />

    <div v-if="stats" class="admin-stat-grid" style="margin-bottom: 16px">
      <div class="admin-stat-card">
        <div class="label">近 {{ stats.days }} 日任务</div>
        <div class="value">{{ stats.total }}</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">成功率</div>
        <div class="value small">{{ stats.successRate }}%</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">平均耗时</div>
        <div class="value small">{{ stats.avgDurationSec }}s</div>
      </div>
      <div class="admin-stat-card">
        <div class="label">失败 / 运行中</div>
        <div class="value small">{{ stats.failed }} / {{ stats.running }}</div>
      </div>
    </div>

    <div v-if="stats?.topErrors?.length" class="admin-card" style="margin-bottom: 16px">
      <h4 class="errors-title">失败原因 TOP</h4>
      <div v-for="(e, i) in stats.topErrors" :key="i" class="error-row">
        <span class="error-count">{{ e.count }}</span>
        <span class="error-reason">{{ e.reason }}</span>
      </div>
    </div>

    <div class="admin-card admin-toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="任务号">
          <el-input v-model="query.jobNo" clearable placeholder="J..." style="width: 130px" />
        </el-form-item>
        <el-form-item label="用户手机">
          <el-input v-model="query.userPhone" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" clearable style="width: 80px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 100px">
            <el-option v-for="s in JOB_STATUS" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="query.taskType" clearable placeholder="全部" style="width: 120px">
            <el-option v-for="t in TASK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="产品">
          <el-input v-model="query.productId" clearable placeholder="graduation" style="width: 110px" />
        </el-form-item>
        <el-form-item label="创建时间">
          <el-date-picker
            v-model="query.dateRange"
            type="daterange"
            value-format="YYYY-MM-DD"
            start-placeholder="开始"
            end-placeholder="结束"
            style="width: 240px"
          />
        </el-form-item>
        <el-form-item label="统计天数">
          <el-select v-model="statsDays" style="width: 88px" @change="loadStats">
            <el-option :value="7" label="7 天" />
            <el-option :value="14" label="14 天" />
            <el-option :value="30" label="30 天" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
          <el-button :disabled="!selected.length" @click="batchRetry">批量重试</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table
      v-loading="loading"
      :data="list"
      stripe
      class="data-table"
      @selection-change="onSelectionChange"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column prop="jobNo" label="任务号" width="148" />
      <el-table-column label="类型" width="108">
        <template #default="{ row }">
          <TaskTypeTag :type="row.taskType" />
        </template>
      </el-table-column>
      <el-table-column label="产品" min-width="110">
        <template #default="{ row }">
          <div>{{ row.productLabel || row.productId }}</div>
        </template>
      </el-table-column>
      <el-table-column label="用户" width="118">
        <template #default="{ row }">{{ row.userPhone || row.userId || '—' }}</template>
      </el-table-column>
      <el-table-column label="进度" min-width="168">
        <template #default="{ row }">
          <JobProgressBar :progress="row.progress ?? 0" :status="row.status" />
        </template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="jobStatusType(row.status)" size="small">
            {{ jobStatusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="168" />
      <el-table-column label="操作" width="168" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.status === 'failed' || row.status === 'cancelled'"
            link
            type="warning"
            @click="retry(row)"
          >
            重试
          </el-button>
          <el-button
            v-if="row.status === 'pending' || row.status === 'running'"
            link
            type="danger"
            @click="cancel(row)"
          >
            取消
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="onPageChange"
      @size-change="onSearch"
    />

    <el-drawer v-model="detailVisible" :title="`任务 ${detail?.jobNo}`" size="680px">
      <template v-if="detail">
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="任务类型">
            <TaskTypeTag :type="detail.taskType" />
          </el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="jobStatusType(detail.status)" size="small">
              {{ jobStatusLabel(detail.status) }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="产品">{{ detail.productLabel }} ({{ detail.productId }})</el-descriptions-item>
          <el-descriptions-item label="流程">{{ flowTypeLabel(detail.flowType || '') }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.userPhone || detail.userId }}</el-descriptions-item>
          <el-descriptions-item label="草稿 ID">{{ detail.paperId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="关联订单">
            <el-button
              v-if="detail.orderNo"
              link
              type="primary"
              @click="router.push({ path: '/orders', query: { orderNo: detail.orderNo } })"
            >
              {{ detail.orderNo }}
            </el-button>
            <span v-else-if="detail.orderId">ID {{ detail.orderId }}</span>
            <span v-else>—</span>
          </el-descriptions-item>
          <el-descriptions-item label="进度" :span="2">
            <JobProgressBar
              :progress="detail.progress ?? 0"
              :status="detail.status"
              large
            />
          </el-descriptions-item>
          <el-descriptions-item label="创建">{{ detail.createdAt }}</el-descriptions-item>
          <el-descriptions-item label="完成">{{ detail.finishedAt || '—' }}</el-descriptions-item>
        </el-descriptions>

        <el-tabs v-model="detailTab" style="margin-top: 16px">
          <el-tab-pane label="生成结果" name="result">
            <pre class="admin-json">{{ formatJson(detail.resultJson) }}</pre>
          </el-tab-pane>
          <el-tab-pane label="请求参数" name="payload">
            <pre class="admin-json">{{ formatJson(detail.payloadJson) }}</pre>
          </el-tab-pane>
          <el-tab-pane v-if="detail.errorMsg" label="错误信息" name="error">
            <el-alert type="error" :title="detail.errorMsg" :closable="false" />
          </el-tab-pane>
        </el-tabs>

        <div style="margin-top: 16px; display: flex; gap: 8px">
          <el-button
            v-if="detail.status === 'failed' || detail.status === 'cancelled'"
            type="warning"
            @click="retry(detail); detailVisible = false"
          >
            重新执行
          </el-button>
          <el-button
            v-if="detail.status === 'pending' || detail.status === 'running'"
            type="danger"
            @click="cancel(detail); detailVisible = false"
          >
            取消任务
          </el-button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.data-table {
  margin-top: 16px;
}
.errors-title {
  margin: 0 0 10px;
  font-size: 14px;
}
.error-row {
  display: flex;
  gap: 10px;
  font-size: 13px;
  padding: 6px 0;
  border-bottom: 1px solid var(--admin-border);
}
.error-count {
  flex-shrink: 0;
  width: 28px;
  font-weight: 600;
  color: #dc2626;
}
.error-reason {
  color: var(--admin-text);
  word-break: break-all;
}
</style>
