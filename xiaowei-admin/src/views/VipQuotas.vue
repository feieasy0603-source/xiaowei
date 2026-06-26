<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import { TASK_TYPES } from '@/constants/ai'

interface QuotaRow {
  id?: number
  vipLevel: number
  taskType: string
  dailyFree: number
  discountPercent: number
  enabled?: boolean
}

const loading = ref(false)
const list = ref<QuotaRow[]>([])
const dialogVisible = ref(false)
const form = ref<QuotaRow>({
  vipLevel: 0,
  taskType: 'paper_generate',
  dailyFree: 0,
  discountPercent: 0,
  enabled: true,
})

async function load() {
  loading.value = true
  try {
    list.value = await adminFetch('/admin/vip-quotas')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.value = {
    vipLevel: 0,
    taskType: 'paper_generate',
    dailyFree: 1,
    discountPercent: 10,
    enabled: true,
  }
  dialogVisible.value = true
}

function openEdit(row: QuotaRow) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  try {
    await adminFetch('/admin/vip-quotas', {
      method: 'POST',
      body: JSON.stringify(form.value),
    })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function remove(row: QuotaRow) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm('删除该配额规则？', '确认')
    await adminFetch(`/admin/vip-quotas/${row.id}`, { method: 'DELETE' })
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

function taskLabel(v: string) {
  return TASK_TYPES.find((t) => t.value === v)?.label ?? v
}

onMounted(() => void load())
</script>

<template>
  <div>
    <AdminPageHeader
      title="VIP 配额规则"
      desc="按 VIP 等级 × taskType 配置每日免费次数与超额折扣；支付或生成任务时自动扣减"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">新增规则</el-button>
      </template>
    </AdminPageHeader>

    <el-table v-loading="loading" :data="list" stripe>
      <el-table-column prop="vipLevel" label="VIP 等级" width="96" />
      <el-table-column label="任务类型" min-width="120">
        <template #default="{ row }">{{ taskLabel(row.taskType) }}</template>
      </el-table-column>
      <el-table-column prop="dailyFree" label="每日免费" width="96" />
      <el-table-column label="超额折扣" width="100">
        <template #default="{ row }">{{ row.discountPercent }}%</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑规则' : '新增规则'" width="480px">
      <el-form label-width="110px">
        <el-form-item label="VIP 等级">
          <el-input-number v-model="form.vipLevel" :min="0" :max="9" />
        </el-form-item>
        <el-form-item label="任务类型">
          <el-select v-model="form.taskType" style="width: 100%">
            <el-option v-for="t in TASK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="每日免费次数">
          <el-input-number v-model="form.dailyFree" :min="0" :max="99" />
        </el-form-item>
        <el-form-item label="超额折扣 %">
          <el-input-number v-model="form.discountPercent" :min="0" :max="100" />
          <p class="hint">免费次数用完后，订单按原价 × (1 - 折扣%) 计费</p>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.hint {
  font-size: 12px;
  color: var(--admin-muted);
  margin: 4px 0 0;
}
</style>
