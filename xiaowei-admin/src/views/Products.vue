<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import AdminPageHeader from '@/components/AdminPageHeader.vue'
import TaskTypeTag from '@/components/TaskTypeTag.vue'
import { adminFetch } from '@/api/http'
import { FLOW_TYPES, FORM_VARIANTS, PROCESS_VARIANTS, TASK_TYPES, flowTypeLabel } from '@/constants/ai'

interface Product {
  id: string
  label: string
  taskType: string
  flowType: string
  processVariant?: string
  formVariant?: string
  enabled: boolean
  banner?: string
  titleFieldLabel?: string
  titlePlaceholder?: string
  proLinkText?: string
  agreementText?: string
  submitLabel?: string
  showFaq?: boolean
  centerTitle?: boolean
  sortOrder?: number
  configJson?: string
}

interface PriceRow {
  id?: number
  productId: string
  degree?: string
  wordCount?: number
  modelType?: string
  price: number
}

const loading = ref(false)
const list = ref<Product[]>([])
const createVisible = ref(false)
const createForm = ref({
  id: '',
  label: '',
  taskType: 'paper_generate',
  flowType: 'both',
  processVariant: 'standard',
  formVariant: 'graduation',
  sortOrder: 99,
})
const editVisible = ref(false)
const editForm = ref<Partial<Product>>({})
const priceVisible = ref(false)
const priceProductId = ref('')
const priceList = ref<PriceRow[]>([])
const priceLoading = ref(false)
const priceFormVisible = ref(false)
const priceForm = ref<PriceRow>({
  productId: '',
  price: 29.9,
})

async function load() {
  loading.value = true
  try {
    list.value = await adminFetch('/admin/products')
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    loading.value = false
  }
}

async function createProduct() {
  if (!createForm.value.id.trim() || !createForm.value.label.trim()) {
    ElMessage.warning('请填写产品 ID 与名称')
    return
  }
  try {
    await adminFetch('/admin/products', {
      method: 'POST',
      body: JSON.stringify(createForm.value),
    })
    ElMessage.success('产品已创建')
    createVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

function openEdit(row: Product) {
  editForm.value = { ...row }
  editVisible.value = true
}

async function saveEdit() {
  try {
    await adminFetch(`/admin/products/${editForm.value.id}`, {
      method: 'PUT',
      body: JSON.stringify(editForm.value),
    })
    ElMessage.success('已保存')
    editVisible.value = false
    await load()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function toggle(row: Product) {
  await adminFetch(`/admin/products/${row.id}`, {
    method: 'PUT',
    body: JSON.stringify({ enabled: !row.enabled }),
  })
  ElMessage.success('已更新')
  await load()
}

async function openPrices(row: Product) {
  priceProductId.value = row.id
  priceVisible.value = true
  await loadPrices()
}

async function loadPrices() {
  priceLoading.value = true
  try {
    priceList.value = await adminFetch(
      `/admin/product-prices?productId=${encodeURIComponent(priceProductId.value)}`,
    )
  } catch (e) {
    ElMessage.error((e as Error).message)
  } finally {
    priceLoading.value = false
  }
}

function openPriceCreate() {
  priceForm.value = {
    productId: priceProductId.value,
    degree: '本科',
    wordCount: 12000,
    modelType: 'standard',
    price: 29.9,
  }
  priceFormVisible.value = true
}

function openPriceEdit(row: PriceRow) {
  priceForm.value = { ...row }
  priceFormVisible.value = true
}

async function savePrice() {
  try {
    await adminFetch('/admin/product-prices', {
      method: 'POST',
      body: JSON.stringify(priceForm.value),
    })
    ElMessage.success('已保存')
    priceFormVisible.value = false
    await loadPrices()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

async function removePrice(row: PriceRow) {
  if (!row.id) return
  try {
    await adminFetch(`/admin/product-prices/${row.id}`, { method: 'DELETE' })
    ElMessage.success('已删除')
    await loadPrices()
  } catch (e) {
    ElMessage.error((e as Error).message)
  }
}

onMounted(() => void load())
</script>

<template>
  <div>
    <AdminPageHeader
      title="AI 产品配置"
      desc="每个产品绑定 taskType（AI 能力）与 flowType（用户端流程），对应前台侧边栏 25+ 写作产品"
    >
      <template #actions>
        <el-button type="primary" @click="createVisible = true">新建产品</el-button>
      </template>
    </AdminPageHeader>

    <el-table v-loading="loading" :data="list" stripe style="margin-top: 16px">
      <el-table-column prop="id" label="ID" width="130" />
      <el-table-column prop="label" label="名称" min-width="140" />
      <el-table-column label="AI 任务" width="120">
        <template #default="{ row }">
          <TaskTypeTag :type="row.taskType" />
        </template>
      </el-table-column>
      <el-table-column label="用户流程" width="120">
        <template #default="{ row }">
          <el-tooltip :content="FLOW_TYPES.find((f) => f.value === row.flowType)?.desc">
            <span>{{ flowTypeLabel(row.flowType) }}</span>
          </el-tooltip>
        </template>
      </el-table-column>
      <el-table-column prop="formVariant" label="表单" width="100" />
      <el-table-column label="状态" width="88">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
            {{ row.enabled ? '上架' : '下架' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">配置</el-button>
          <el-button link type="primary" @click="openPrices(row)">定价</el-button>
          <el-button link @click="toggle(row)">{{ row.enabled ? '下架' : '上架' }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="createVisible" title="新建产品" width="560px">
      <el-form label-width="110px">
        <el-form-item label="产品 ID" required>
          <el-input v-model="createForm.id" placeholder="如 custom-paper" />
        </el-form-item>
        <el-form-item label="显示名称" required>
          <el-input v-model="createForm.label" />
        </el-form-item>
        <el-form-item label="AI 任务类型">
          <el-select v-model="createForm.taskType" style="width: 100%">
            <el-option v-for="t in TASK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户流程">
          <el-select v-model="createForm.flowType" style="width: 100%">
            <el-option v-for="f in FLOW_TYPES" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程变体">
          <el-select v-model="createForm.processVariant" style="width: 100%">
            <el-option v-for="p in PROCESS_VARIANTS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="表单变体">
          <el-select v-model="createForm.formVariant" style="width: 100%">
            <el-option v-for="f in FORM_VARIANTS" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="createForm.sortOrder" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" @click="createProduct">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="editVisible" title="产品配置" width="640px">
      <el-form v-if="editForm.id" label-width="110px">
        <el-form-item label="产品 ID">
          <el-input :model-value="editForm.id" disabled />
        </el-form-item>
        <el-form-item label="显示名称">
          <el-input v-model="editForm.label" />
        </el-form-item>
        <el-form-item label="AI 任务类型">
          <el-select v-model="editForm.taskType" style="width: 100%">
            <el-option v-for="t in TASK_TYPES" :key="t.value" :label="t.label" :value="t.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="用户流程">
          <el-select v-model="editForm.flowType" style="width: 100%">
            <el-option v-for="f in FLOW_TYPES" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="流程变体">
          <el-select v-model="editForm.processVariant" style="width: 100%">
            <el-option v-for="p in PROCESS_VARIANTS" :key="p.value" :label="p.label" :value="p.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="表单变体">
          <el-select v-model="editForm.formVariant" style="width: 100%">
            <el-option v-for="f in FORM_VARIANTS" :key="f.value" :label="f.label" :value="f.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标题字段名">
          <el-input v-model="editForm.titleFieldLabel" />
        </el-form-item>
        <el-form-item label="标题占位符">
          <el-input v-model="editForm.titlePlaceholder" />
        </el-form-item>
        <el-form-item label="专业版链接文案">
          <el-input v-model="editForm.proLinkText" />
        </el-form-item>
        <el-form-item label="横幅文案">
          <el-input v-model="editForm.banner" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="用户协议">
          <el-input v-model="editForm.agreementText" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="提交按钮">
          <el-input v-model="editForm.submitLabel" />
        </el-form-item>
        <el-form-item label="configJson">
          <el-input
            v-model="editForm.configJson"
            type="textarea"
            :rows="4"
            placeholder='{"wordCountOptions":[8000,12000],"defaultModel":"standard"}'
          />
        </el-form-item>
        <el-form-item label="显示 FAQ">
          <el-switch v-model="editForm.showFaq" />
        </el-form-item>
        <el-form-item label="标题居中">
          <el-switch v-model="editForm.centerTitle" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="editForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="上架">
          <el-switch v-model="editForm.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="saveEdit">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="priceVisible" :title="`定价规则 · ${priceProductId}`" size="640px">
      <div style="margin-bottom: 12px">
        <el-button type="primary" size="small" @click="openPriceCreate">新增规则</el-button>
      </div>
      <el-table v-loading="priceLoading" :data="priceList" stripe size="small">
        <el-table-column prop="degree" label="学位" width="80">
          <template #default="{ row }">{{ row.degree || '任意' }}</template>
        </el-table-column>
        <el-table-column prop="wordCount" label="字数档" width="90">
          <template #default="{ row }">{{ row.wordCount ?? '任意' }}</template>
        </el-table-column>
        <el-table-column prop="modelType" label="模型" width="90">
          <template #default="{ row }">{{ row.modelType || '任意' }}</template>
        </el-table-column>
        <el-table-column label="价格" width="90">
          <template #default="{ row }">¥{{ Number(row.price).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPriceEdit(row)">编辑</el-button>
            <el-button link type="danger" @click="removePrice(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="priceFormVisible" title="价格规则" width="440px">
      <el-form label-width="88px">
        <el-form-item label="学位">
          <el-input v-model="priceForm.degree" placeholder="留空表示任意" />
        </el-form-item>
        <el-form-item label="字数档">
          <el-input-number v-model="priceForm.wordCount" :min="1000" :step="1000" />
        </el-form-item>
        <el-form-item label="模型">
          <el-select v-model="priceForm.modelType" clearable style="width: 100%">
            <el-option label="标准" value="standard" />
            <el-option label="学术加强" value="academia" />
          </el-select>
        </el-form-item>
        <el-form-item label="价格">
          <el-input-number v-model="priceForm.price" :min="0" :precision="2" :step="1" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="priceFormVisible = false">取消</el-button>
        <el-button type="primary" @click="savePrice">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>
