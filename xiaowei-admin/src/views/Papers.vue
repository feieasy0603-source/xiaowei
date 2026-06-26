<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import { adminFetch } from '@/api/http'
import { downloadAdminPaperFile } from '@/api/download'
import type { PageResult } from '@/api/types'

interface AdminPaper {
  id: string
  userId?: number
  userPhone?: string
  productId: string
  productLabel?: string
  title?: string
  maxVisitedStep: number
  hasPreview?: boolean
  updatedAt?: string
}

interface PaperFileRow {
  id: number
  fileName: string
  fileType: string
  sizeBytes?: number
  jobId?: number
  createdAt?: string
}

const loading = ref(false)
const list = ref<AdminPaper[]>([])
const total = ref(0)
const query = reactive({ title: '', productId: '', page: 1, size: 20 })
const detailVisible = ref(false)
const detailTab = ref('draft')
const detailJson = ref('')
const detailPaperId = ref('')
const detailFiles = ref<PaperFileRow[]>([])
const filesLoading = ref(false)

const stepLabels = ['标题', '文献', '提纲', '预览']

async function load() {
  loading.value = true
  try {
    const params = new URLSearchParams({
      page: String(query.page),
      size: String(query.size),
    })
    if (query.title) params.set('title', query.title)
    if (query.productId) params.set('productId', query.productId)
    const res = await adminFetch<PageResult<AdminPaper>>(`/admin/papers?${params}`)
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

function stepLabel(n: number) {
  return stepLabels[n] ?? `步骤${n}`
}

async function loadFiles(paperId: string) {
  filesLoading.value = true
  try {
    detailFiles.value = await adminFetch(`/admin/papers/${paperId}/files`)
  } catch (e) {
    ElMessage.error((e as Error).message)
    detailFiles.value = []
  } finally {
    filesLoading.value = false
  }
}

async function downloadFile(row: PaperFileRow) {
  try {
    await downloadAdminPaperFile(row.id, row.fileName)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function openDetail(row: AdminPaper) {
  try {
    detailPaperId.value = row.id
    detailTab.value = 'draft'
    const data = await adminFetch<Record<string, unknown>>(`/admin/papers/${row.id}`)
    detailJson.value = JSON.stringify(data, null, 2)
    detailVisible.value = true
    void loadFiles(row.id)
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <AdminPageHeader
      title="论文草稿"
      desc="用户端智能写作 lunwen 草稿，向导流程 maxVisitedStep 对应标题→文献→提纲→预览"
    />

    <div class="admin-card admin-toolbar">
      <el-form inline @submit.prevent="onSearch">
        <el-form-item label="标题">
          <el-input v-model="query.title" clearable placeholder="模糊搜索" style="width: 160px" />
        </el-form-item>
        <el-form-item label="产品 ID">
          <el-input v-model="query.productId" clearable style="width: 120px" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="onSearch">查询</el-button>
        </el-form-item>
      </el-form>
    </div>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="id" label="草稿 ID" width="120" />
      <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
      <el-table-column label="产品" min-width="120">
        <template #default="{ row }">{{ row.productLabel || row.productId }}</template>
      </el-table-column>
      <el-table-column label="用户" width="120">
        <template #default="{ row }">{{ row.userPhone || row.userId || '—' }}</template>
      </el-table-column>
      <el-table-column label="进度" width="100">
        <template #default="{ row }">
          <el-tag size="small" type="info">{{ stepLabel(row.maxVisitedStep) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="已生成预览" width="110">
        <template #default="{ row }">
          <el-tag :type="row.hasPreview ? 'success' : 'info'" size="small">
            {{ row.hasPreview ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170" />
      <el-table-column label="操作" width="88" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row)">详情</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-drawer v-model="detailVisible" :title="`草稿 ${detailPaperId}`" size="560px">
      <el-tabs v-model="detailTab">
        <el-tab-pane label="草稿 JSON" name="draft">
          <pre class="draft-json">{{ detailJson }}</pre>
        </el-tab-pane>
        <el-tab-pane label="交付文件" name="files">
          <el-table v-loading="filesLoading" :data="detailFiles" size="small" stripe>
            <el-table-column prop="fileName" label="文件名" />
            <el-table-column prop="fileType" label="类型" width="72" />
            <el-table-column prop="jobId" label="任务 ID" width="88" />
            <el-table-column label="大小" width="88">
              <template #default="{ row }">
                {{ row.sizeBytes ? `${Math.round(row.sizeBytes / 1024)} KB` : '—' }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="88">
              <template #default="{ row }">
                <el-button link type="primary" @click="downloadFile(row)">下载</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-if="!filesLoading && !detailFiles.length" description="暂无交付文件" />
        </el-tab-pane>
      </el-tabs>
    </el-drawer>

    <el-pagination
      v-model:current-page="query.page"
      v-model:page-size="query.size"
      :total="total"
      layout="total, prev, pager, next"
      style="margin-top: 16px; justify-content: flex-end"
      @current-change="load"
    />
  </div>
</template>

<style scoped>
.draft-json {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
