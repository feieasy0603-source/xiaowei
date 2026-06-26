<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'

interface ChannelRow {
  id: number
  dCode: string
  name: string
  commissionRate: number
  enabled: boolean
}

const loading = ref(false)
const list = ref<ChannelRow[]>([])
const commissionStats = ref<Record<string, unknown>[]>([])
const createVisible = ref(false)
const editVisible = ref(false)
const createForm = ref({ dCode: '', name: '', commissionRate: 0.1 })
const editForm = ref({ id: 0, dCode: '', name: '', commissionRate: 0.1 })

async function load() {
  loading.value = true
  try {
    list.value = await adminFetch('/admin/channels')
    commissionStats.value = await adminFetch('/admin/channels/commission-stats')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function openEdit(row: ChannelRow) {
  editForm.value = {
    id: row.id,
    dCode: row.dCode,
    name: row.name,
    commissionRate: Number(row.commissionRate),
  }
  editVisible.value = true
}

async function saveEdit() {
  try {
    await adminFetch(`/admin/channels/${editForm.value.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: editForm.value.name,
        commissionRate: editForm.value.commissionRate,
      }),
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function toggleEnabled(row: ChannelRow) {
  try {
    await adminFetch(`/admin/channels/${row.id}`, {
      method: 'PUT',
      body: JSON.stringify({ enabled: !row.enabled }),
    })
    ElMessage.success(row.enabled ? '已停用' : '已启用')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function createChannel() {
  try {
    await adminFetch('/admin/channels', {
      method: 'POST',
      body: JSON.stringify({
        dCode: createForm.value.dCode,
        name: createForm.value.name,
        commissionRate: createForm.value.commissionRate,
        enabled: true,
      }),
    })
    ElMessage.success('渠道已创建')
    createVisible.value = false
    createForm.value = { dCode: '', name: '', commissionRate: 0.1 }
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(() => void load())
</script>

<template>
  <div class="page">
    <AdminPageHeader title="渠道管理" desc="dCode 用于用户端推广链接；分成比例作用于已支付订单统计">
      <template #actions>
        <el-button type="primary" @click="createVisible = true">新建渠道</el-button>
      </template>
    </AdminPageHeader>

    <el-table v-loading="loading" :data="list" stripe class="admin-card" style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="72" />
      <el-table-column prop="dCode" label="dCode" width="120" />
      <el-table-column prop="name" label="名称" />
      <el-table-column label="分成比例" width="120">
        <template #default="{ row }">{{ (Number(row.commissionRate) * 100).toFixed(1) }}%</template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.enabled ? 'warning' : 'primary'" @click="toggleEnabled(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <h3 class="sub-title">渠道佣金结算（已支付订单）</h3>
    <el-table :data="commissionStats" stripe class="admin-card" style="margin-top: 12px">
      <el-table-column prop="channelName" label="渠道" />
      <el-table-column prop="dCode" label="dCode" width="120" />
      <el-table-column prop="orderCount" label="订单数" width="100" />
      <el-table-column label="成交额" width="120">
        <template #default="{ row }">¥{{ Number(row.totalAmount).toFixed(2) }}</template>
      </el-table-column>
      <el-table-column label="佣金" width="120">
        <template #default="{ row }">
          ¥{{ Number(row.commissionAmount ?? 0).toFixed(2) }}
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建渠道" width="420px">
      <el-form label-width="90px">
        <el-form-item label="dCode">
          <el-input v-model="createForm.dCode" placeholder="如 partner01" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="createForm.name" />
        </el-form-item>
        <el-form-item label="分成比例">
          <el-input-number v-model="createForm.commissionRate" :min="0" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createChannel">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="编辑渠道" width="420px">
      <el-form label-width="90px">
        <el-form-item label="dCode">
          <el-input :model-value="editForm.dCode" disabled />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="分成比例">
          <el-input-number v-model="editForm.commissionRate" :min="0" :max="1" :step="0.05" :precision="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.sub-title {
  margin: 24px 0 0;
  font-size: 16px;
}
</style>
