<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import type { AdminOrder, PageResult } from '@/api/types'
import { jobStatusLabel } from '@/constants/ai'

const router = useRouter()
const route = useRoute()

const loading = ref(false)
const list = ref<AdminOrder[]>([])
const total = ref(0)
const query = reactive({
  payStatus: '',
  orderNo: '',
  userId: '' as string | number,
  createdFrom: '',
  createdTo: '',
  page: 1,
  size: 20,
})
const detailVisible = ref(false)
const detail = ref<AdminOrder | null>(null)

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: String(query.page),
      size: String(query.size),
    })
    if (query.payStatus) params.set('payStatus', query.payStatus)
    if (query.orderNo) params.set('orderNo', query.orderNo)
    if (query.userId) params.set('userId', String(query.userId))
    if (query.createdFrom) params.set('createdFrom', query.createdFrom)
    if (query.createdTo) params.set('createdTo', query.createdTo)
    const res = await adminFetch<PageResult<AdminOrder>>(`/admin/orders?${params}`)
    list.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  void load()
}

function payStatusLabel(s: string) {
  const map: Record<string, string> = {
    unpaid: '待支付',
    paid: '已支付',
    refunded: '已退款',
  }
  return map[s] ?? s
}

function payStatusType(s: string) {
  if (s === 'paid') return 'success'
  if (s === 'refunded') return 'info'
  return 'warning'
}

async function openDetail(row: AdminOrder) {
  try {
    detail.value = await adminFetch<AdminOrder>(`/admin/orders/${row.id}`)
    detailVisible.value = true
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function goJob(row: AdminOrder) {
  if (!row.jobId) return
  router.push({ path: '/jobs', query: { jobNo: row.jobNo ?? String(row.jobId) } })
}

async function markPaid(row: AdminOrder) {
  try {
    await ElMessageBox.confirm(`将订单 ${row.orderNo} 标记为已支付？将自动创建生成任务。`, '确认')
    await adminFetch(`/admin/orders/${row.id}/mark-paid`, { method: 'POST' })
    ElMessage.success('已标记为已支付')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

async function refund(row: AdminOrder) {
  try {
    const hint =
      row.payMethod === 'wechat'
        ? '将调用微信退款 API，请确认商户配置已生效'
        : row.payMethod === 'balance'
          ? '余额支付将退回用户钱包'
          : '非余额订单仅更新状态，不涉及钱包'
    const { value } = await ElMessageBox.prompt(`退款备注（${hint}）`, '订单退款', {
      inputValue: `退款 ${row.orderNo}`,
    })
    await adminFetch(`/admin/orders/${row.id}/refund`, {
      method: 'POST',
      body: JSON.stringify({ remark: value }),
    })
    ElMessage.success('退款成功')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

onMounted(() => {
  const orderNo = route.query.orderNo
  if (typeof orderNo === 'string' && orderNo) {
    query.orderNo = orderNo
  }
  void load()
})
</script>

<template>
  <div>
    <AdminPageHeader
      title="订单管理"
      desc="支付成功后自动创建生成任务并关联订单；支持余额支付、微信回调与人工标记已付"
    />

    <div class="admin-card admin-toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="订单号">
          <el-input v-model="query.orderNo" placeholder="模糊搜索" clearable style="width: 180px" />
        </el-form-item>
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" placeholder="精确" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item label="支付状态">
          <el-select v-model="query.payStatus" clearable placeholder="全部" style="width: 120px">
            <el-option label="待支付" value="unpaid" />
            <el-option label="已支付" value="paid" />
            <el-option label="已退款" value="refunded" />
          </el-select>
        </el-form-item>
        <el-form-item label="创建起">
          <el-date-picker
            v-model="query.createdFrom"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="起"
            clearable
            style="width: 130px"
          />
        </el-form-item>
        <el-form-item label="创建止">
          <el-date-picker
            v-model="query.createdTo"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="止"
            clearable
            style="width: 130px"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="orderNo" label="订单号" width="160" />
      <el-table-column label="用户" min-width="140">
        <template #default="{ row }">
          <div>{{ row.userPhone || '-' }}</div>
          <div class="sub">{{ row.userNickname }}</div>
        </template>
      </el-table-column>
      <el-table-column label="产品" min-width="120">
        <template #default="{ row }">{{ row.productLabel || row.productId }}</template>
      </el-table-column>
      <el-table-column label="金额" width="100">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="payStatusType(row.payStatus)" size="small">
            {{ payStatusLabel(row.payStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="payMethod" label="支付方式" width="100" />
      <el-table-column label="关联任务" width="150">
        <template #default="{ row }">
          <template v-if="row.jobId">
            <el-button link type="primary" @click="goJob(row)">{{ row.jobNo }}</el-button>
            <div class="sub">{{ jobStatusLabel(row.jobStatus || '') }}</div>
          </template>
          <span v-else class="sub">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
          <el-button
            v-if="row.payStatus === 'unpaid'"
            link
            type="primary"
            @click="markPaid(row)"
          >
            标记已付
          </el-button>
          <el-button
            v-if="row.payStatus === 'paid'"
            link
            type="warning"
            @click="refund(row)"
          >
            退款
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
      @current-change="load"
      @size-change="onSearch"
    />

    <el-drawer v-model="detailVisible" :title="`订单 ${detail?.orderNo}`" size="480px">
      <template v-if="detail">
        <el-descriptions :column="1" border size="small">
          <el-descriptions-item label="订单号">{{ detail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="用户">
            {{ detail.userPhone }} (ID {{ detail.userId }})
          </el-descriptions-item>
          <el-descriptions-item label="产品">
            {{ detail.productLabel }} ({{ detail.productId }})
          </el-descriptions-item>
          <el-descriptions-item label="草稿 ID">{{ detail.paperId || '—' }}</el-descriptions-item>
          <el-descriptions-item label="渠道 ID">{{ detail.channelId ?? '—' }}</el-descriptions-item>
          <el-descriptions-item label="支付时间">{{ detail.paidAt || '—' }}</el-descriptions-item>
          <el-descriptions-item label="报价参数">
            {{ detail.quoteDegree || '—' }} /
            {{ detail.quoteWordCount ?? '—' }} 字 /
            {{ detail.quoteModelType || '—' }}
          </el-descriptions-item>
          <el-descriptions-item label="金额">¥{{ Number(detail.amount).toFixed(2) }}</el-descriptions-item>
          <el-descriptions-item label="支付状态">{{ payStatusLabel(detail.payStatus) }}</el-descriptions-item>
          <el-descriptions-item label="支付方式">{{ detail.payMethod || '—' }}</el-descriptions-item>
          <el-descriptions-item label="关联任务">
            <el-button v-if="detail.jobId" link type="primary" @click="goJob(detail)">
              {{ detail.jobNo }} ({{ jobStatusLabel(detail.jobStatus || '') }})
            </el-button>
            <span v-else>—</span>
          </el-descriptions-item>
        </el-descriptions>
      </template>
    </el-drawer>
  </div>
</template>

<style scoped>
.sub {
  font-size: 12px;
  color: #94a3b8;
}
</style>
