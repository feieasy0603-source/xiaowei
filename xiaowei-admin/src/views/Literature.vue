<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { ElMessageBox } from 'element-plus/es/components/message-box/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import type { PageResult } from '@/api/types'

interface LitRow {
  id?: number
  title: string
  authors?: string
  source?: string
  year?: number
  lang: string
  gbtCitation: string
  keywords?: string
  enabled?: boolean
}

const loading = ref(false)
const list = ref<LitRow[]>([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const keyword = ref('')
const enabledFilter = ref<'' | 'true' | 'false'>('')
const importVisible = ref(false)
const importText = ref('')
const dialogVisible = ref(false)
const form = ref<LitRow>({
  title: '',
  lang: 'zh',
  gbtCitation: '',
  enabled: true,
})

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: String(page.value),
      size: String(pageSize.value),
    })
    if (keyword.value) params.set('keyword', keyword.value)
    if (enabledFilter.value) params.set('enabled', enabledFilter.value)
    const res = await adminFetch<PageResult<LitRow>>(`/admin/literature?${params}`)
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

async function bulkImport() {
  try {
    const rows = JSON.parse(importText.value) as LitRow[]
    if (!Array.isArray(rows)) throw new Error('需为 JSON 数组')
    const payload = rows.map((row) => ({
      ...row,
      enabled: row.enabled !== false,
    }))
    const res = await adminFetch<{ imported: number; total: number }>('/admin/literature/batch', {
      method: 'POST',
      body: JSON.stringify(payload),
    })
    ElMessage.success(`已导入 ${res.imported}/${res.total} 条`)
    importVisible.value = false
    importText.value = ''
    await load()
  } catch (e) {
    ElMessage.error(e instanceof SyntaxError ? 'JSON 格式错误' : (e as Error).message)
  }
}

function openCreate() {
  form.value = { title: '', lang: 'zh', gbtCitation: '', enabled: true }
  dialogVisible.value = true
}

function openEdit(row: LitRow) {
  form.value = { ...row }
  dialogVisible.value = true
}

async function save() {
  try {
    await adminFetch('/admin/literature', {
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

async function remove(row: LitRow) {
  if (!row.id) return
  try {
    await ElMessageBox.confirm(`删除文献「${row.title}」？`, '确认')
    await adminFetch(`/admin/literature/${row.id}`, { method: 'DELETE' })
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
      title="文献库"
      desc="供用户端 /ai/literature/search 与向导「文献」步骤检索，影响 AI 写作推荐质量"
    >
      <template #actions>
        <el-button @click="importVisible = true">批量导入</el-button>
        <el-button type="primary" @click="openCreate">新增文献</el-button>
      </template>
    </AdminPageHeader>

    <div class="admin-card admin-toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="关键词">
          <el-input v-model="keyword" clearable placeholder="标题/作者/关键词" style="width: 200px" />
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="enabledFilter" clearable placeholder="全部" style="width: 100px">
            <el-option label="启用" value="true" />
            <el-option label="停用" value="false" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />
      <el-table-column prop="authors" label="作者" width="140" show-overflow-tooltip />
      <el-table-column prop="year" label="年份" width="72" />
      <el-table-column prop="lang" label="语言" width="64" />
      <el-table-column prop="keywords" label="关键词" width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '启用' : '停用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="140" fixed="right">
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

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑文献' : '新增文献'" width="560px">
      <el-form label-width="88px">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" />
        </el-form-item>
        <el-form-item label="作者">
          <el-input v-model="form.authors" />
        </el-form-item>
        <el-form-item label="来源">
          <el-input v-model="form.source" />
        </el-form-item>
        <el-form-item label="年份">
          <el-input-number v-model="form.year" :min="1900" :max="2100" />
        </el-form-item>
        <el-form-item label="语言">
          <el-select v-model="form.lang" style="width: 100%">
            <el-option label="中文" value="zh" />
            <el-option label="英文" value="en" />
          </el-select>
        </el-form-item>
        <el-form-item label="GBT 引用" required>
          <el-input v-model="form.gbtCitation" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="关键词">
          <el-input v-model="form.keywords" />
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

    <el-dialog v-model="importVisible" title="批量导入文献" width="560px">
      <p class="import-hint">粘贴 JSON 数组，每项含 title、authors、gbtCitation、lang 等字段</p>
      <el-input v-model="importText" type="textarea" :rows="12" placeholder='[{"title":"...","gbtCitation":"...","lang":"zh"}]' />
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" @click="bulkImport">导入</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.import-hint {
  font-size: 13px;
  color: var(--admin-muted);
  margin: 0 0 10px;
}
</style>
