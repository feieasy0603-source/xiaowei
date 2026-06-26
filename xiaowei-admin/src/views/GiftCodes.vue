<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'

interface GiftCodeRow {
  id: number
  code: string
  amount: number
  usedBy?: number
  usedAt?: string
  expiresAt?: string
  createdAt: string
}

const list = ref<GiftCodeRow[]>([])
const loading = ref(false)
const amount = ref(10)
const expiresAt = ref<string | undefined>(undefined)

async function load() {
  loading.value = true
  try {
    list.value = await adminFetch<GiftCodeRow[]>('/admin/gift-codes')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function create() {
  try {
    const body: Record<string, unknown> = { amount: amount.value }
    if (expiresAt.value) {
      body.expiresAt = new Date(expiresAt.value).toISOString()
    }
    await adminFetch('/admin/gift-codes', {
      method: 'POST',
      body: JSON.stringify(body),
    })
    ElMessage.success('已生成礼包码')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function remove(id: number) {
  try {
    await adminFetch(`/admin/gift-codes/${id}`, { method: 'DELETE' })
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function isExpired(row: GiftCodeRow) {
  if (!row.expiresAt) return false
  return new Date(row.expiresAt).getTime() < Date.now()
}

onMounted(() => void load())
</script>

<template>
  <div>
    <AdminPageHeader
      title="礼包码管理"
      desc="生成一次性充值礼包码；可设置过期时间，过期后用户无法兑换"
    />
    <div class="admin-card toolbar">
      <el-input-number v-model="amount" :min="1" :max="9999" />
      <el-date-picker
        v-model="expiresAt"
        type="datetime"
        placeholder="过期时间（可选）"
        value-format="YYYY-MM-DDTHH:mm:ss.SSSZ"
        clearable
      />
      <el-button type="primary" @click="create">生成礼包码</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe class="admin-card">
      <el-table-column prop="code" label="礼包码" min-width="140" />
      <el-table-column prop="amount" label="面额" width="100">
        <template #default="{ row }">¥{{ Number(row.amount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column prop="usedBy" label="使用者" width="100">
        <template #default="{ row }">{{ row.usedBy ?? '—' }}</template>
      </el-table-column>
      <el-table-column label="过期时间" width="180">
        <template #default="{ row }">
          <template v-if="row.expiresAt">
            <el-tag v-if="isExpired(row)" type="info" size="small">已过期</el-tag>
            {{ row.expiresAt }}
          </template>
          <span v-else class="muted">永久</span>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" width="180" />
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="!row.usedBy" link type="danger" @click="remove(row.id)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  padding: 16px;
  margin-bottom: 16px;
  align-items: center;
}
.muted {
  color: #94a3b8;
  font-size: 12px;
}
</style>
