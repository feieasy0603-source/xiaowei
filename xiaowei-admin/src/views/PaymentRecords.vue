<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import type { PageResult } from '@/api/types'

interface PaymentRecordRow {
  id: number
  userId: number
  orderId?: number
  amount: number
  payMethod: string
  tradeNo?: string
  status: string
  createdAt?: string
}

const loading = ref(false)
const list = ref<PaymentRecordRow[]>([])
const total = ref(0)
const query = reactive({
  userId: '' as string | number,
  orderId: '' as string | number,
  payMethod: '',
  status: '',
  tradeNo: '',
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
    if (query.userId) params.set('userId', String(query.userId))
    if (query.orderId) params.set('orderId', String(query.orderId))
    if (query.payMethod) params.set('payMethod', query.payMethod)
    if (query.status) params.set('status', query.status)
    if (query.tradeNo) params.set('tradeNo', query.tradeNo)
    const res = await adminFetch<PageResult<PaymentRecordRow>>(`/admin/payment-records?${params}`)
    list.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function payMethodLabel(m: string) {
  const map: Record<string, string> = {
    wechat: '微信',
    balance: '余额',
    admin: '后台',
    vip_quota: 'VIP 额度',
    recharge: '余额充值',
    recharge_mock: '历史模拟充值',
  }
  return map[m] ?? m
}

function statusLabel(s: string) {
  if (s === 'success') return '成功'
  if (s === 'failed') return '失败'
  if (s === 'refunded') return '已退款'
  return s
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
      title="支付流水"
      desc="微信/余额等支付成功后的 payment_records 审计记录（只读）"
    />

    <div class="admin-card admin-toolbar">
      <el-form inline @submit.prevent="load">
        <el-form-item label="用户 ID">
          <el-input v-model="query.userId" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item label="订单 ID">
          <el-input v-model="query.orderId" clearable style="width: 100px" />
        </el-form-item>
        <el-form-item label="方式">
          <el-select v-model="query.payMethod" clearable placeholder="全部" style="width: 110px">
            <el-option label="微信" value="wechat" />
            <el-option label="余额" value="balance" />
            <el-option label="后台" value="admin" />
            <el-option label="VIP 额度" value="vip_quota" />
            <el-option label="余额充值" value="recharge" />
            <el-option label="历史模拟充值" value="recharge_mock" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 100px">
            <el-option label="成功" value="success" />
            <el-option label="失败" value="failed" />
            <el-option label="已退款" value="refunded" />
          </el-select>
        </el-form-item>
        <el-form-item label="交易号">
          <el-input v-model="query.tradeNo" clearable placeholder="WX..." style="width: 140px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column prop="userId" label="用户" width="72" />
      <el-table-column prop="orderId" label="订单 ID" width="88" />
      <el-table-column label="金额" width="96">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="方式" width="88">
        <template #default="{ row }">{{ payMethodLabel(row.payMethod) }}</template>
      </el-table-column>
      <el-table-column prop="tradeNo" label="交易号" min-width="160" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'success' ? 'success' : 'info'" size="small">
            {{ statusLabel(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="168">
        <template #default="{ row }">{{ row.createdAt?.slice(0, 19) ?? '—' }}</template>
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
