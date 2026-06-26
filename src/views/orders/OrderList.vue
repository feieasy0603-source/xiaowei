<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { Search } from '@element-plus/icons-vue'
import PayDialog from '@/components/iw/common/PayDialog.vue'
import {
  fetchOrders,
  lookupByNo,
  type OrderDelivery,
  type OrderDto,
} from '@/api/modules/orders'
import {
  downloadDelivery,
  downloadJobDelivery,
  downloadPublicDelivery,
  fetchJobDeliveries,
  fetchPaperDeliveries,
} from '@/api/modules/files'
import { useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'
import { payStatusLabel, payStatusTagType } from '@/utils/orderStatus'

const appStore = useAppStore()
const router = useRouter()
const route = useRoute()

const lookupInput = ref(
  typeof route.query.orderNo === 'string'
    ? route.query.orderNo
    : typeof route.query.jobNo === 'string'
      ? route.query.jobNo
      : '',
)
/** 游客查询时记住用户输入的单号，用于轮询刷新 */
const guestLookupNo = ref('')
const orders = ref<OrderDto[]>([])
const orderTotal = ref(0)
const orderPage = ref(1)
const orderPageSize = ref(20)
const guestOrder = ref<OrderDto | null>(null)
const guestMode = ref(false)
const loading = ref(false)
let pollFailCount = 0
const lookupLoading = ref(false)
const payVisible = ref(false)
const payOrderId = ref<number>()
let pollTimer: ReturnType<typeof setTimeout> | null = null

function goJobDetail(jobId: number) {
  void router.push({ name: 'jobDetail', params: { id: String(jobId) } })
}

function displayRows(): OrderDto[] {
  if (guestMode.value && guestOrder.value) return [guestOrder.value]
  return orders.value
}

function orderDeliveries(row: OrderDto): OrderDelivery[] {
  return row.deliveries ?? []
}

function publicDownloadRef(row: OrderDto): { orderNo?: string; jobNo?: string } {
  if (row.orderNo) return { orderNo: row.orderNo }
  if (row.jobNo) return { jobNo: row.jobNo }
  return {}
}

function displayOrderNo(row: OrderDto) {
  if (row.orderNo) return row.orderNo
  if (row.jobOnly && row.jobNo) return row.jobNo
  return '—'
}

async function loadMyOrders() {
  if (!useApiEnabled() || !appStore.isLoggedIn) {
    orders.value = []
    return
  }
  loading.value = true
  try {
    const res = await fetchOrders(orderPage.value, orderPageSize.value)
    orders.value = res.items
    orderTotal.value = res.total
    syncPoll()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function runLookup() {
  const no = lookupInput.value.trim()
  if (!no) {
    ElMessage.warning('请输入订单号或任务号')
    return
  }
  if (!useApiEnabled()) {
    ElMessage.warning('当前未连接后端，无法查询')
    return
  }
  lookupLoading.value = true
  try {
    guestOrder.value = await lookupByNo(no)
    guestLookupNo.value = no
    guestMode.value = true
    const q = { ...route.query }
    delete q.orderNo
    delete q.jobNo
    if (/^J/i.test(no)) q.jobNo = no
    else q.orderNo = no
    void router.replace({ query: q })
    syncPoll()
  } catch (e) {
    guestOrder.value = null
    guestMode.value = false
    guestLookupNo.value = ''
    ElMessage.error((e as Error).message)
  } finally {
    lookupLoading.value = false
  }
}

function clearGuestLookup() {
  guestOrder.value = null
  guestMode.value = false
  guestLookupNo.value = ''
  const q = { ...route.query }
  delete q.orderNo
  delete q.jobNo
  void router.replace({ query: q })
}

function syncPoll() {
  if (pollTimer) {
    clearTimeout(pollTimer)
    pollTimer = null
  }
  const rows = displayRows()
  const hasRunning = rows.some(
    (o) => o.jobStatus === 'pending' || o.jobStatus === 'running',
  )
  if (!hasRunning || !useApiEnabled()) return

  const stop = () => {
    if (pollTimer) clearTimeout(pollTimer)
    pollTimer = null
  }
  const hasActiveJob = (rowsToCheck: OrderDto[]) =>
    rowsToCheck.some((o) => o.jobStatus === 'pending' || o.jobStatus === 'running')

  const tick = async () => {
    try {
      pollFailCount = 0
      if (guestMode.value && guestLookupNo.value) {
        const updated = await lookupByNo(guestLookupNo.value)
        guestOrder.value = updated
        if (!hasActiveJob([updated])) {
          stop()
          return
        }
      } else if (appStore.isLoggedIn) {
        const res = await fetchOrders(orderPage.value, orderPageSize.value)
        orders.value = res.items
        if (!hasActiveJob(res.items)) {
          stop()
          return
        }
      } else {
        stop()
        return
      }
      pollTimer = setTimeout(() => void tick(), 3000)
    } catch {
      pollFailCount += 1
      if (pollFailCount >= 3) {
        ElMessage.warning('订单状态刷新失败，请手动刷新页面')
        stop()
        return
      }
      pollTimer = setTimeout(() => void tick(), 3000)
    }
  }

  pollTimer = setTimeout(() => void tick(), 3000)
}

function openPayDialog(row: OrderDto) {
  if (!appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  payOrderId.value = row.id ?? undefined
  payVisible.value = true
}

function onOrderPaid() {
  payVisible.value = false
  payOrderId.value = undefined
  if (guestMode.value && guestLookupNo.value) {
    void runLookup()
  } else {
    void loadMyOrders()
  }
}

function onOrderPageSizeChange() {
  orderPage.value = 1
  void loadMyOrders()
}

async function downloadFile(row: OrderDto, file: OrderDelivery) {
  try {
    if (file.downloadUrl?.includes('/files/public/download')) {
      await downloadPublicDelivery(publicDownloadRef(row), file.id, file.source, file.fileName)
      return
    }
    if (!appStore.isLoggedIn) {
      await downloadPublicDelivery(publicDownloadRef(row), file.id, file.source, file.fileName)
      return
    }
    if (file.source === 'job') {
      await downloadJobDelivery(file.id, file.fileName)
    } else {
      await downloadDelivery(file.id, file.fileName)
    }
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function download(row: OrderDto) {
  try {
    const embedded = orderDeliveries(row)
    if (embedded.length) {
      const pick =
        embedded.find((f) => f.fileType === 'docx') ??
        embedded.find((f) => f.fileType === 'txt') ??
        embedded[0]
      if (pick) {
        await downloadFile(row, pick)
        return
      }
    }
    if (!appStore.isLoggedIn) {
      ElMessage.warning('请使用订单号或任务号查询后下载，或登录账号')
      return
    }
    if (row.paperId) {
      const files = await fetchPaperDeliveries(row.paperId)
      if (!files.length) {
        ElMessage.warning('交付文件生成中，请稍后刷新')
        return
      }
      const pick =
        files.find((f) => f.fileType === 'docx') ??
        files.find((f) => f.fileType === 'txt') ??
        files[0]
      await downloadDelivery(pick!.id, pick!.fileName)
      return
    }
    if (row.jobId) {
      const files = await fetchJobDeliveries(row.jobId)
      if (!files.length) {
        ElMessage.warning('交付文件生成中，请稍后刷新')
        return
      }
      const pick =
        files.find((f) => f.fileType === 'docx') ??
        files.find((f) => f.fileType === 'txt') ??
        files[0]
      await downloadJobDelivery(pick!.id, pick!.fileName)
      return
    }
    ElMessage.warning('订单暂无可下载文件')
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function jobStatusText(row: OrderDto) {
  if (!row.jobId) return '—'
  const map: Record<string, string> = {
    pending: '排队',
    running: '生成中',
    success: '已完成',
    failed: '失败',
    cancelled: '已取消',
  }
  return `${row.jobNo} · ${map[row.jobStatus ?? ''] ?? row.jobStatus}`
}

function deliveryLabel(file: OrderDelivery) {
  if (file.fileType === 'docx') return 'Word'
  if (file.fileType === 'txt') return 'TXT'
  return file.fileName
}

watch(
  () => appStore.isLoggedIn,
  () => {
    if (!guestMode.value) void loadMyOrders()
  },
)

onMounted(async () => {
  if (lookupInput.value.trim()) {
    await runLookup()
  } else {
    await loadMyOrders()
  }
})

onUnmounted(() => {
  if (pollTimer) clearTimeout(pollTimer)
})
</script>

<template>
  <div class="order-list xw-card">
    <h2 class="page-title">查询结果</h2>
    <p class="page-desc">
      输入订单号（O 开头）或任务号（J 开头）即可查看进度与下载，无需登录；登录后可查看全部订单
    </p>

    <div class="search-bar">
      <el-input
        v-model="lookupInput"
        placeholder="订单号 O1730... 或 任务号 J1730..."
        clearable
        size="large"
        @keyup.enter="runLookup"
      >
        <template #append>
          <el-button :loading="lookupLoading" type="primary" @click="runLookup">
            <el-icon><Search /></el-icon>
            查询
          </el-button>
        </template>
      </el-input>
      <el-button v-if="guestMode" link type="primary" @click="clearGuestLookup">
        返回列表
      </el-button>
    </div>

    <el-alert
      v-if="!appStore.isLoggedIn && !guestMode"
      type="info"
      :closable="false"
      show-icon
      class="guest-tip"
      title="游客查询"
      description="输入支付后获得的订单号，或生成任务的任务号，即可查看进度与下载文件。"
    />

    <el-table
      v-loading="loading || lookupLoading"
      :data="displayRows()"
      stripe
      class="order-table"
    >
      <el-table-column label="订单号/任务号" width="168">
        <template #default="{ row }">{{ displayOrderNo(row) }}</template>
      </el-table-column>
      <el-table-column prop="productLabel" label="产品" min-width="120" />
      <el-table-column prop="amount" label="金额" width="100">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="payStatus" label="支付" width="88">
        <template #default="{ row }">
          <el-tag :type="payStatusTagType(row)" size="small">
            {{ payStatusLabel(row) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="生成任务" min-width="168">
        <template #default="{ row }">
          <el-button
            v-if="row.jobId && appStore.isLoggedIn"
            type="primary"
            link
            @click="goJobDetail(row.jobId)"
          >
            {{ jobStatusText(row) }}
          </el-button>
          <span v-else-if="row.jobId" class="muted">{{ jobStatusText(row) }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <template v-if="row.payStatus !== 'paid' && row.id">
            <el-button type="primary" link @click="openPayDialog(row)">去支付</el-button>
          </template>
          <template v-else-if="row.jobStatus === 'success'">
            <template v-if="orderDeliveries(row).length">
              <el-button
                v-for="file in orderDeliveries(row)"
                :key="`${file.source}-${file.id}`"
                type="primary"
                link
                @click="downloadFile(row, file)"
              >
                {{ deliveryLabel(file) }}
              </el-button>
            </template>
            <el-button v-else type="primary" link @click="download(row)">下载</el-button>
          </template>
          <template v-else-if="row.jobStatus === 'failed'">
            <el-button
              v-if="appStore.isLoggedIn && row.jobId"
              type="warning"
              link
              @click="goJobDetail(row.jobId)"
            >
              失败 · 重试
            </el-button>
            <span v-else class="muted">生成失败</span>
          </template>
          <template v-else-if="row.jobId">
            <span class="muted">生成中…</span>
          </template>
          <template v-else>
            <span class="muted">等待生成</span>
          </template>
        </template>
      </el-table-column>
    </el-table>

    <div
      v-loading="loading || lookupLoading"
      class="order-card-list"
      aria-label="订单列表"
    >
      <article v-for="row in displayRows()" :key="row.id ?? displayOrderNo(row)" class="order-card-item">
        <div class="order-card-head">
          <div class="order-card-title">
            <span class="order-no">{{ displayOrderNo(row) }}</span>
            <span class="product-name">{{ row.productLabel }}</span>
          </div>
          <el-tag :type="payStatusTagType(row)" size="small">
            {{ payStatusLabel(row) }}
          </el-tag>
        </div>

        <div class="order-card-meta">
          <span>金额 ¥{{ Number(row.amount).toFixed(2) }}</span>
          <span>{{ row.createdAt }}</span>
        </div>

        <div class="job-row">
          <span class="job-label">生成任务</span>
          <el-button
            v-if="row.jobId && appStore.isLoggedIn"
            type="primary"
            link
            @click="goJobDetail(row.jobId)"
          >
            {{ jobStatusText(row) }}
          </el-button>
          <span v-else-if="row.jobId" class="muted">{{ jobStatusText(row) }}</span>
          <span v-else class="muted">—</span>
        </div>

        <div class="order-card-actions">
          <template v-if="row.payStatus !== 'paid' && row.id">
            <el-button type="primary" @click="openPayDialog(row)">去支付</el-button>
          </template>
          <template v-else-if="row.jobStatus === 'success'">
            <template v-if="orderDeliveries(row).length">
              <el-button
                v-for="file in orderDeliveries(row)"
                :key="`${file.source}-${file.id}`"
                type="primary"
                plain
                @click="downloadFile(row, file)"
              >
                {{ deliveryLabel(file) }}
              </el-button>
            </template>
            <el-button v-else type="primary" plain @click="download(row)">下载</el-button>
          </template>
          <template v-else-if="row.jobStatus === 'failed'">
            <el-button
              v-if="appStore.isLoggedIn && row.jobId"
              type="warning"
              plain
              @click="goJobDetail(row.jobId)"
            >
              失败 · 重试
            </el-button>
            <span v-else class="muted">生成失败</span>
          </template>
          <span v-else-if="row.jobId" class="muted">生成中…</span>
          <span v-else class="muted">等待生成</span>
        </div>
      </article>
    </div>

    <el-pagination
      v-if="!guestMode && appStore.isLoggedIn && orderTotal > 0"
      v-model:current-page="orderPage"
      v-model:page-size="orderPageSize"
      :total="orderTotal"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="order-pager"
      @current-change="loadMyOrders"
      @size-change="onOrderPageSizeChange"
    />

    <el-empty
      v-if="!loading && !lookupLoading && !displayRows().length"
      :description="
        guestMode
          ? '未找到订单或任务'
          : appStore.isLoggedIn
            ? '暂无订单'
            : '请输入订单号或任务号查询'
      "
    />

    <PayDialog
      v-model:visible="payVisible"
      :existing-order-id="payOrderId"
      @paid="onOrderPaid"
    />
  </div>
</template>

<style scoped>
.order-list {
  padding: 20px 24px;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  margin-bottom: 6px;
}

.page-desc {
  font-size: 13px;
  color: var(--xw-muted);
  margin: 0 0 16px;
}

.search-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.search-bar .el-input {
  flex: 1;
  min-width: 260px;
  max-width: 480px;
}

.guest-tip {
  margin-bottom: 16px;
}

.muted {
  color: var(--xw-muted);
  font-size: 13px;
}

.order-pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.order-card-list {
  display: none;
}

.order-card-item {
  border: 1px solid var(--xw-border);
  border-radius: 10px;
  padding: 14px;
  background: #fff;
}

.order-card-item + .order-card-item {
  margin-top: 10px;
}

.order-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.order-card-title {
  min-width: 0;
}

.order-no {
  display: block;
  font-size: 13px;
  font-weight: 700;
  color: var(--xw-text);
  word-break: break-all;
}

.product-name {
  display: block;
  margin-top: 3px;
  font-size: 12px;
  color: var(--xw-text-secondary);
}

.order-card-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px 12px;
  margin-top: 10px;
  color: var(--xw-muted);
  font-size: 12px;
}

.job-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 10px;
  padding: 9px 10px;
  border-radius: 8px;
  background: #f8fafc;
  font-size: 13px;
}

.job-label {
  color: var(--xw-text-secondary);
  flex-shrink: 0;
}

.order-card-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
  min-height: 32px;
  margin-top: 12px;
}

@media (max-width: 760px) {
  .order-list {
    padding: 16px;
  }

  .page-title {
    font-size: 20px;
  }

  .page-desc {
    line-height: 1.6;
  }

  .search-bar {
    align-items: stretch;
  }

  .search-bar .el-input {
    min-width: 100%;
    max-width: none;
  }

  .order-table {
    display: none;
  }

  .order-card-list {
    display: block;
  }

  .order-pager {
    justify-content: center;
  }
}
</style>
