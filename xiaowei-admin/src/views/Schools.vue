<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'

interface SchoolRow {
  id: string
  name: string
  sortOrder: number
  enabled: boolean
  createdAt?: string
}

const list = ref<SchoolRow[]>([])
const loading = ref(false)
const form = ref({ id: '', name: '', sortOrder: 50 })
const editVisible = ref(false)
const editForm = ref({ id: '', name: '', sortOrder: 50 })

async function load() {
  loading.value = true
  try {
    list.value = await adminFetch<SchoolRow[]>('/admin/schools')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function create() {
  if (!form.value.name.trim()) {
    ElMessage.warning('请填写学校名称')
    return
  }
  try {
    await adminFetch('/admin/schools', {
      method: 'POST',
      body: JSON.stringify({
        id: form.value.id.trim() || undefined,
        name: form.value.name.trim(),
        sortOrder: form.value.sortOrder,
      }),
    })
    ElMessage.success('已添加')
    form.value = { id: '', name: '', sortOrder: 50 }
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function openEdit(row: SchoolRow) {
  editForm.value = { id: row.id, name: row.name, sortOrder: row.sortOrder }
  editVisible.value = true
}

async function saveEdit() {
  try {
    await adminFetch(`/admin/schools/${editForm.value.id}`, {
      method: 'PUT',
      body: JSON.stringify({
        name: editForm.value.name,
        sortOrder: editForm.value.sortOrder,
      }),
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function toggleEnabled(row: SchoolRow) {
  try {
    await adminFetch(`/admin/schools/${row.id}`, {
      method: 'PUT',
      body: JSON.stringify({ enabled: !row.enabled }),
    })
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function remove(row: SchoolRow) {
  try {
    await adminFetch(`/admin/schools/${row.id}`, { method: 'DELETE' })
    ElMessage.success('已停用')
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <AdminPageHeader
      title="学校格式模板"
      desc="用户端选题页可选学校；启用后 DOCX 交付会附带学校格式副标题"
    />
    <div class="admin-card toolbar">
      <el-input v-model="form.id" placeholder="ID（可选，如 pku）" style="width: 140px" />
      <el-input v-model="form.name" placeholder="学校名称" style="width: 220px" />
      <el-input-number v-model="form.sortOrder" :min="0" :max="999" />
      <el-button type="primary" @click="create">添加学校</el-button>
    </div>
    <el-table v-loading="loading" :data="list" stripe class="admin-card">
      <el-table-column prop="id" label="ID" width="120" />
      <el-table-column prop="name" label="名称" min-width="180" />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link type="primary" @click="toggleEnabled(row)">
            {{ row.enabled ? '停用' : '启用' }}
          </el-button>
          <el-button v-if="row.id !== 'other'" link type="danger" @click="remove(row)">
            下线
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="editVisible" title="编辑学校" width="420px">
      <el-form label-width="80px">
        <el-form-item label="ID">
          <el-input :model-value="editForm.id" disabled />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="editForm.name" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" :max="999" />
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
.toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  padding: 16px;
  margin-bottom: 16px;
}
</style>
