<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import { adminFetch } from '@/api/http'
import type { AdminUser, PageResult, WalletLogRow } from '@/api/types'

const loading = ref(false)
const list = ref<AdminUser[]>([])
const total = ref(0)
const query = reactive({ phone: '', status: '', page: 1, size: 20 })

const editVisible = ref(false)
const editForm = reactive({ id: 0, nickname: '', status: 'active', vipLevel: 0 })

const balanceVisible = ref(false)
const balanceMode = ref<'recharge' | 'deduct'>('recharge')
const balanceUserId = ref(0)
const balanceForm = reactive({ amount: 100, remark: '' })

const logsVisible = ref(false)
const logsLoading = ref(false)
const logs = ref<WalletLogRow[]>([])
const logsUser = ref<AdminUser | null>(null)
const logsPage = ref(1)
const logsTotal = ref(0)

const quotaVisible = ref(false)
const quotaLoading = ref(false)
const quotaRows = ref<
  { taskType: string; dailyFree: number; usedToday: number; freeRemaining: number; discountPercent: number }[]
>([])
const quotaUser = ref<AdminUser | null>(null)

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: String(query.page),
      size: String(query.size),
    })
    if (query.phone) params.set('phone', query.phone)
    if (query.status) params.set('status', query.status)
    const res = await adminFetch<PageResult<AdminUser>>(`/admin/users?${params}`)
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

function openEdit(row: AdminUser) {
  editForm.id = row.id
  editForm.nickname = row.nickname ?? ''
  editForm.status = row.status
  editForm.vipLevel = row.vipLevel ?? 0
  editVisible.value = true
}

async function saveEdit() {
  try {
    await adminFetch(`/admin/users/${editForm.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        nickname: editForm.nickname,
        status: editForm.status,
        vipLevel: editForm.vipLevel,
      }),
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function openBalance(row: AdminUser, mode: 'recharge' | 'deduct') {
  balanceUserId.value = row.id
  balanceMode.value = mode
  balanceForm.amount = mode === 'recharge' ? 100 : 10
  balanceForm.remark = mode === 'recharge' ? '管理员充值' : '管理员扣款'
  balanceVisible.value = true
}

async function submitBalance() {
  const path =
    balanceMode.value === 'recharge'
      ? `/admin/users/${balanceUserId.value}/recharge`
      : `/admin/users/${balanceUserId.value}/deduct`
  try {
    await adminFetch(path, {
      method: 'POST',
      body: JSON.stringify({
        amount: balanceForm.amount,
        remark: balanceForm.remark,
      }),
    })
    ElMessage.success(balanceMode.value === 'recharge' ? '充值成功' : '扣款成功')
    balanceVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function toggleStatus(row: AdminUser) {
  const next = row.status === 'active' ? 'disabled' : 'active'
  const label = next === 'disabled' ? '禁用' : '启用'
  try {
    await ElMessageBox.confirm(`确定${label}用户 ${row.phone || row.id}？`, '确认')
    await adminFetch(`/admin/users/${row.id}`, {
      method: 'PUT',
      body: JSON.stringify({ status: next }),
    })
    ElMessage.success(`已${label}`)
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

async function openQuota(row: AdminUser) {
  quotaUser.value = row
  quotaVisible.value = true
  quotaLoading.value = true
  try {
    quotaRows.value = await adminFetch(`/admin/users/${row.id}/quota`)
  } catch (e) {
    ElMessage.error((e as Error).message)
    quotaRows.value = []
  } finally {
    quotaLoading.value = false
  }
}

async function resetQuota() {
  if (!quotaUser.value) return
  try {
    await ElMessageBox.confirm(
      `重置用户 ${quotaUser.value.phone || quotaUser.value.id} 今日全部 VIP 用量？`,
      '重置今日用量',
      { type: 'warning' },
    )
    const res = await adminFetch<{ message?: string }>(
      `/admin/users/${quotaUser.value.id}/reset-quota`,
      { method: 'POST', body: JSON.stringify({}) },
    )
    ElMessage.success(res.message ?? '已重置')
    await openQuota(quotaUser.value)
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

async function openLogs(row: AdminUser) {
  logsUser.value = row
  logsPage.value = 1
  logsVisible.value = true
  await loadLogs()
}

async function loadLogs() {
  if (!logsUser.value) return
  logsLoading.value = true
  try {
    const res = await adminFetch<PageResult<WalletLogRow>>(
      `/admin/users/${logsUser.value.id}/wallet-logs?page=${logsPage.value}&size=20`,
    )
    logs.value = res.items
    logsTotal.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    logsLoading.value = false
  }
}

function statusTag(status: string) {
  return status === 'active' ? 'success' : 'danger'
}

function logTypeLabel(type: string) {
  const map: Record<string, string> = {
    recharge: '充值',
    deduct: '扣款',
    adjust: '调账',
  }
  return map[type] ?? type
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <div class="page-head">
      <h2>用户管理</h2>
      <p class="desc">查询用户、充值/扣款、启用禁用、查看钱包流水</p>
    </div>

    <el-card shadow="never" class="toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="手机号">
          <el-input v-model="query.phone" placeholder="模糊搜索" clearable style="width: 160px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="query.status" clearable placeholder="全部" style="width: 120px">
            <el-option label="正常" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column prop="phone" label="手机" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="100" />
      <el-table-column label="余额" width="110">
        <template #default="{ row }">
          <span class="balance">¥{{ Number(row.balance).toFixed(2) }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="vipLevel" label="VIP" width="64" />
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="statusTag(row.status)" size="small">
            {{ row.status === 'active' ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="注册时间" width="180" />
      <el-table-column label="操作" width="320" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openBalance(row, 'recharge')">充值</el-button>
          <el-button link type="warning" @click="openBalance(row, 'deduct')">扣款</el-button>
          <el-button link @click="openQuota(row)">配额</el-button>
          <el-button link @click="openLogs(row)">流水</el-button>
          <el-button link @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 'active' ? 'danger' : 'success'" @click="toggleStatus(row)">
            {{ row.status === 'active' ? '禁用' : '启用' }}
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

    <el-dialog v-model="editVisible" title="编辑用户" width="420px">
      <el-form label-width="80px">
        <el-form-item label="昵称">
          <el-input v-model="editForm.nickname" />
        </el-form-item>
        <el-form-item label="VIP">
          <el-input-number v-model="editForm.vipLevel" :min="0" :max="9" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="正常" value="active" />
            <el-option label="禁用" value="disabled" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="balanceVisible"
      :title="balanceMode === 'recharge' ? '用户充值' : '用户扣款'"
      width="420px"
    >
      <el-form label-width="80px">
        <el-form-item label="金额">
          <el-input-number v-model="balanceForm.amount" :min="0.01" :precision="2" :step="10" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="balanceForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="balanceVisible = false">取消</el-button>
        <el-button :type="balanceMode === 'recharge' ? 'primary' : 'warning'" @click="submitBalance">
          确认{{ balanceMode === 'recharge' ? '充值' : '扣款' }}
        </el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="quotaVisible" :title="`今日 VIP 配额 · ${quotaUser?.phone || quotaUser?.id}`" size="480px">
      <div style="margin-bottom: 12px">
        <el-button type="warning" size="small" @click="resetQuota">重置今日用量</el-button>
      </div>
      <el-table v-loading="quotaLoading" :data="quotaRows" size="small">
        <el-table-column prop="taskType" label="任务类型" min-width="120" />
        <el-table-column prop="dailyFree" label="每日免费" width="88" />
        <el-table-column prop="usedToday" label="已用" width="64" />
        <el-table-column prop="freeRemaining" label="剩余" width="64" />
        <el-table-column label="超额折扣" width="88">
          <template #default="{ row }">{{ row.discountPercent }}%</template>
        </el-table-column>
      </el-table>
      <p v-if="!quotaLoading && !quotaRows.length" class="empty-hint">该 VIP 等级暂无启用中的配额规则</p>
    </el-drawer>

    <el-drawer v-model="logsVisible" :title="`钱包流水 · ${logsUser?.phone || logsUser?.id}`" size="640px">
      <el-table v-loading="logsLoading" :data="logs" size="small">
        <el-table-column prop="createdAt" label="时间" width="170" />
        <el-table-column label="类型" width="80">
          <template #default="{ row }">{{ logTypeLabel(row.type) }}</template>
        </el-table-column>
        <el-table-column label="金额" width="100">
          <template #default="{ row }">
            <span :class="row.type === 'recharge' ? 'amt-plus' : 'amt-minus'">
              {{ row.type === 'recharge' ? '+' : '-' }}{{ Number(row.amount).toFixed(2) }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="余额后" width="100">
          <template #default="{ row }">¥{{ Number(row.balanceAfter).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="120" show-overflow-tooltip />
        <el-table-column prop="refType" label="来源" width="100" />
      </el-table>
      <el-pagination
        v-model:current-page="logsPage"
        :total="logsTotal"
        :page-size="20"
        layout="prev, pager, next"
        style="margin-top: 12px"
        @current-change="loadLogs"
      />
    </el-drawer>
  </div>
</template>

<style scoped>
.page-head h2 {
  margin: 0;
  font-size: 20px;
}
.desc {
  margin: 6px 0 0;
  font-size: 13px;
  color: #64748b;
}
.balance {
  font-weight: 600;
  color: #059669;
}
.amt-plus {
  color: #059669;
}
.amt-minus {
  color: #dc2626;
}
.empty-hint {
  margin-top: 12px;
  font-size: 13px;
  color: #94a3b8;
}
</style>
