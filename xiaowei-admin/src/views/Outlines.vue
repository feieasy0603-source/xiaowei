<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import type { PageResult } from '@/api/types'

interface OutlineRow {
  id?: number
  title: string
  category?: string
  degree?: string
  depth?: number
  outlineJson: string
  enabled?: boolean
}

const loading = ref(false)
const list = ref<OutlineRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filterCategory = ref('')
const filterDegree = ref('')
const filterEnabled = ref<'' | 'true' | 'false'>('')
const dialogVisible = ref(false)
const form = ref<OutlineRow>({
  title: '',
  depth: 2,
  outlineJson: '[]',
  enabled: true,
})

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: String(page.value),
      size: String(pageSize.value),
    })
    if (filterCategory.value) params.set('category', filterCategory.value)
    if (filterDegree.value) params.set('degree', filterDegree.value)
    if (filterEnabled.value) params.set('enabled', filterEnabled.value)
    const res = await adminFetch<PageResult<OutlineRow>>(`/admin/outline-templates?${params}`)
    list.value = res.items
    total.value = res.total
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  void load()
}

function openCreate() {
  form.value = {
    title: '',
    category: '教育',
    degree: '本科',
    depth: 2,
    outlineJson: JSON.stringify(
      [{ title: '绪论', children: [{ title: '研究背景' }, { title: '研究意义' }] }],
      null,
      2,
    ),
    enabled: true,
  }
  dialogVisible.value = true
}

function openEdit(row: OutlineRow) {
  form.value = {
    ...row,
    outlineJson:
      typeof row.outlineJson === 'string'
        ? row.outlineJson
        : JSON.stringify(row.outlineJson, null, 2),
  }
  dialogVisible.value = true
}

async function save() {
  try {
    JSON.parse(form.value.outlineJson)
    await adminFetch('/admin/outline-templates', {
      method: 'POST',
      body: JSON.stringify(form.value),
    })
    ElMessage.success('已保存')
    dialogVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error(e instanceof SyntaxError ? '提纲 JSON 格式错误' : (e as Error).message)
  }
}

async function remove(row: OutlineRow) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`删除模板「${row.title}」？`, '确认')
    await adminFetch(`/admin/outline-templates/${row.id}`, { method: 'DELETE' })
    ElMessage.success('已删除')
    await load()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error((e as Error).message)
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <AdminPageHeader
      title="提纲模板"
      desc="供 /ai/outline/search 推荐，SCI 等向导产品的大纲步骤优先命中模板"
    >
      <template #actions>
        <el-button type="primary" @click="openCreate">新增模板</el-button>
      </template>
    </AdminPageHeader>

    <div class="admin-card admin-toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="学科">
          <el-input v-model="filterCategory" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item label="学位">
          <el-select v-model="filterDegree" clearable placeholder="全部" style="width: 100px">
            <el-option label="本科" value="本科" />
            <el-option label="硕士" value="硕士" />
            <el-option label="博士" value="博士" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="filterEnabled" clearable placeholder="全部" style="width: 100px">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">筛选</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="title" label="模板标题" min-width="180" />
      <el-table-column prop="category" label="学科" width="100" />
      <el-table-column prop="degree" label="学位" width="80" />
      <el-table-column prop="depth" label="层级" width="64" />
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

    <el-pagination
      v-model:current-page="page"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
      @size-change="onSearch"
    />

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑提纲' : '新增提纲'" width="640px">
      <el-form label-width="88px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="学科">
          <el-input v-model="form.category" />
        </el-form-item>
        <el-form-item label="学位">
          <el-select v-model="form.degree" style="width: 100%">
            <el-option label="本科" value="本科" />
            <el-option label="硕士" value="硕士" />
            <el-option label="博士" value="博士" />
          </el-select>
        </el-form-item>
        <el-form-item label="层级">
          <el-input-number v-model="form.depth" :min="1" :max="4" />
        </el-form-item>
        <el-form-item label="提纲 JSON" required>
          <el-input v-model="form.outlineJson" type="textarea" :rows="12" font-family="monospace" />
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
