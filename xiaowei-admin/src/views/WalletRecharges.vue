<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import type { PageResult } from '@/api/types'

interface RechargeRow {
  id: number
  orderNo: string
  userId: number
  amount: number
  payStatus: string
  paidAt?: string
  createdAt?: string
}

const loading = ref(false)
const actionLoading = ref<number | null>(null)
const list = ref<RechargeRow[]>([])
const total = ref(0)
const query = reactive({
  payStatus: '',
  orderNo: '',
  userId: '' as string | number,
  page: 1,
  size: 20,
})

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
    const res = await adminFetch<PageResult<RechargeRow>>(`/admin/wallet-recharges?${params}`)
    list.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function payStatusLabel(s: string) {
  if (s === 'paid') return '已支付'
  if (s === 'pending') return '待支付'
  if (s === 'cancelled') return '已取消'
  return s
}

async function confirmPaid(row: RechargeRow) {
  actionLoading.value = row.id
  try {
    await adminFetch(`/admin/wallet-recharges/${row.id}/confirm`, { method: 'POST' })
    ElMessage.success('已确认入账')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    actionLoading.value = null
  }
}

async function cancelOrder(row: RechargeRow) {
  actionLoading.value = row.id
  try {
    await adminFetch(`/admin/wallet-recharges/${row.id}/cancel`, { method: 'POST' })
    ElMessage.success('已取消')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    actionLoading.value = null
  }
}

function onSearch() {
  query.page = 1
  void load()
}

onMounted(() => void load())
</script>

<template>
  <div>
    <AdminPageHeader
      title="余额充值订单"
      desc="用户通过微信 Native 或模拟充值产生的 WR* 订单，便于排查 stuck pending"
    />

    <div class="admin-card admin-toolbar">
      <el-form inline @submit.prevent="load">
        <el-form-item label="状态">
          <el-select v-model="query.payStatus" clearable placeholder="全部" style="width: 100px">
            <el-option label="待支付" value="pending" />
            <el-option label="已支付" value="paid" />
            <el-option label="已取消" value="cancelled" />
          </el-select>
        </el-form-item>
        <el-form-item label="订单号">
          <el-input v-model="query.orderNo" clearable placeholder="WR..." style="width: 160px" />
        </el-form-item>
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column prop="userId" label="用户" width="80" />
      <el-table-column label="金额" width="96">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="row.payStatus === 'paid' ? 'success' : 'warning'" size="small">
            {{ payStatusLabel(row.payStatus) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="创建" width="168">
        <template #default="{ row }">{{ row.createdAt?.slice(0, 19) ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="支付" width="168">
        <template #default="{ row }">{{ row.paidAt?.slice(0, 19) ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <template v-if="row.payStatus === 'pending'">
            <el-button
              type="primary"
              link
              :loading="actionLoading === row.id"
              @click="confirmPaid(row)"
            >
              确认入账
            </el-button>
            <el-button
              type="danger"
              link
              :loading="actionLoading === row.id"
              @click="cancelOrder(row)"
            >
              取消
            </el-button>
          </template>
          <span v-else class="muted">—</span>
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
  </div>
</template>

<style scoped>
.muted {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}
</style>
