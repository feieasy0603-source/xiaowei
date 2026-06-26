<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import type { LiteratureItem } from '@/types/paper'
import { searchLiterature } from '@/api/modules/ai'
import { getToken, useApiEnabled } from '@/api/http'
import { useAppStore } from '@/stores/app'
import { mockLiteraturePool } from '@/mocks/literature'
import { mapLiteratureItem } from '@/utils/literature'

const props = defineProps<{
  modelValue: LiteratureItem[]
  /** 默认检索词（如论文标题） */
  defaultKeyword?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [v: LiteratureItem[]]
}>()

const appStore = useAppStore()
const libFilter = ref<'all' | 'zh' | 'en'>('all')
const yearFilter = ref<'all' | '3' | '5'>('all')
const keyword = ref(props.defaultKeyword ?? '')
const page = ref(1)
const pageSize = 8
const searching = ref(false)
const searchResults = ref<LiteratureItem[]>([])
const hasSearched = ref(false)

function filterItems(items: LiteratureItem[]) {
  const now = new Date().getFullYear()
  return items.filter((item) => {
    if (libFilter.value !== 'all' && item.lang !== libFilter.value) return false
    if (yearFilter.value === '3' && item.year > 0 && now - item.year > 3) return false
    if (yearFilter.value === '5' && item.year > 0 && now - item.year > 5) return false
    return true
  })
}

const pool = computed(() => {
  if (hasSearched.value) {
    return searchResults.value
  }
  if (useApiEnabled()) return []
  const k = keyword.value.trim().toLowerCase()
  const base = mockLiteraturePool
  if (!k) return base
  return base.filter(
    (item) =>
      item.title.toLowerCase().includes(k) ||
      item.authors.toLowerCase().includes(k),
  )
})

const filtered = computed(() => filterItems(pool.value))

const paged = computed(() => filtered.value.slice(0, page.value * pageSize))

const selectedIds = computed(() => new Set(props.modelValue.map((x) => x.id)))

async function toggle(item: LiteratureItem) {
  if (useApiEnabled() && !appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  const set = new Set(selectedIds.value)
  if (set.has(item.id)) {
    emit(
      'update:modelValue',
      props.modelValue.filter((x) => x.id !== item.id),
    )
  } else {
    emit('update:modelValue', [...props.modelValue, item])
  }
}

async function selectAll() {
  if (useApiEnabled() && !appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  const merged = new Map(props.modelValue.map((x) => [x.id, x]))
  paged.value.forEach((item) => merged.set(item.id, item))
  emit('update:modelValue', Array.from(merged.values()))
}

async function appendEnglish() {
  if (useApiEnabled() && !appStore.isLoggedIn) {
    appStore.openLogin()
    return
  }
  const en = filtered.value.filter((x) => x.lang === 'en')
  if (!en.length) {
    ElMessage.info('当前结果中暂无外文文献')
    return
  }
  const merged = new Map(props.modelValue.map((x) => [x.id, x]))
  en.forEach((item) => merged.set(item.id, item))
  emit('update:modelValue', Array.from(merged.values()))
}

function loadMore() {
  if (paged.value.length < filtered.value.length) page.value++
}

async function onSearch() {
  const k = keyword.value.trim()
  if (!k) {
    ElMessage.warning('请输入检索关键词')
    return
  }
  page.value = 1
  searching.value = true
  hasSearched.value = true

  try {
    if (useApiEnabled()) {
      const list = await searchLiterature(k)
      searchResults.value = list.map((item) => mapLiteratureItem(item))
      if (!searchResults.value.length) {
        ElMessage.info('未检索到文献，可尝试更换关键词')
      }
      return
    }
    await new Promise((r) => setTimeout(r, 600))
    searchResults.value = mockLiteraturePool.filter(
      (item) =>
        item.title.toLowerCase().includes(k.toLowerCase()) ||
        item.authors.toLowerCase().includes(k.toLowerCase()),
    )
  } catch (e) {
    ElMessage.error((e as Error).message)
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

onMounted(() => {
  void tryAutoSearch()
})

watch(
  () => props.defaultKeyword?.trim(),
  (k, prev) => {
    if (k && k !== prev) {
      keyword.value = k
      void tryAutoSearch()
    }
  },
)

async function tryAutoSearch() {
  const k = (props.defaultKeyword ?? keyword.value).trim()
  if (!k || k.length < 2) return
  if (useApiEnabled() && !getToken()) return
  keyword.value = k
  if (useApiEnabled()) {
    await onSearch()
    return
  }
  hasSearched.value = true
  page.value = 1
}
</script>

<template>
  <div class="lit-list">
    <div class="filters">
      <el-radio-group v-model="libFilter" size="small">
        <el-radio-button value="all">全部文库</el-radio-button>
        <el-radio-button value="zh">中文</el-radio-button>
        <el-radio-button value="en">外文</el-radio-button>
      </el-radio-group>
      <el-radio-group v-model="yearFilter" size="small">
        <el-radio-button value="all">全部年份</el-radio-button>
        <el-radio-button value="3">近3年</el-radio-button>
        <el-radio-button value="5">近5年</el-radio-button>
      </el-radio-group>
      <el-input
        v-model="keyword"
        placeholder="搜索文献（标题、作者）"
        clearable
        style="max-width: 240px"
        @keyup.enter="onSearch"
      />
      <el-button type="primary" size="small" :loading="searching" @click="onSearch">
        搜索
      </el-button>
    </div>

    <div class="actions">
      <el-button size="small" @click="appendEnglish">追加英文</el-button>
      <el-button size="small" @click="selectAll">全部选中</el-button>
      <span class="count">已选 {{ modelValue.length }} 条</span>
    </div>

    <el-empty v-if="hasSearched && !filtered.length && !searching" description="暂无匹配文献" />
    <el-empty
      v-else-if="!hasSearched && useApiEnabled()"
      description="请输入关键词并搜索文献"
    />

    <div v-for="item in paged" :key="item.id" class="lit-item xw-card">
      <el-checkbox
        :model-value="selectedIds.has(item.id)"
        @change="toggle(item)"
      />
      <div class="lit-body">
        <h4>
          {{ item.title }}
          <el-tag v-if="String(item.id).startsWith('oa:')" size="small" type="info" class="ext-tag">外部</el-tag>
        </h4>
        <p class="meta">
          {{ item.authors }} · {{ item.source }} · {{ item.year }} ·
          {{ item.lang === 'zh' ? '中文' : '外文' }}
        </p>
        <p class="cite">{{ item.gbtCitation }}</p>
      </div>
    </div>

    <el-button
      v-if="paged.length < filtered.length"
      text
      type="primary"
      @click="loadMore"
    >
      加载更多
    </el-button>
  </div>
</template>

<style scoped>
.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 16px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}

.count {
  margin-left: auto;
  font-size: 13px;
  color: var(--xw-muted);
}

.lit-item {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  padding: 14px 16px;
}

.lit-body h4 {
  font-size: 15px;
  margin-bottom: 6px;
}

.ext-tag {
  margin-left: 6px;
  vertical-align: middle;
}

.meta {
  font-size: 13px;
  color: var(--xw-muted);
  margin-bottom: 6px;
}

.cite {
  font-size: 12px;
  color: #9ca3af;
  line-height: 1.5;
}
</style>
