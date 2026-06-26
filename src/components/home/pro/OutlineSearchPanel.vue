<script setup lang="ts">
import { ref } from 'vue'
import { Search } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import { searchOutline, generateOutline } from '@/api/modules/ai'
import { useApiEnabled } from '@/api/http'
import { useMockLoading } from '@/composables/useMockLoading'
import { usePaperStore } from '@/stores/paper'
import {
  mockOutlineSearchResults,
  type OutlineSearchItem,
} from '@/mocks/outlineSearchResults'
import { buildOutlineTextFromTitle } from '@/utils/outlineFromTitle'
import { outlineJsonToText } from '@/utils/outlineJson'

const emit = defineEmits<{ select: [text: string] }>()

const paperStore = usePaperStore()
const searchTitle = ref(paperStore.draft?.title ?? '')
const degree = ref('本科')
const searched = ref(false)
const searchResults = ref<OutlineSearchItem[]>([])
const applySeed = ref(0)
const { loading, run } = useMockLoading(1200)

const features = [
  { icon: '🔍', title: '智能搜索', desc: '按标题与学历匹配推荐提纲' },
  { icon: '📚', title: '模板提纲', desc: '优先展示后台配置的提纲模板' },
  { icon: '✨', title: 'AI 生成', desc: '无匹配时可一键生成 2/3 级提纲' },
]

function titleForGenerate(item?: OutlineSearchItem): string {
  const raw = (item?.title ?? searchTitle.value).trim()
  return raw.replace(/\s*[—-]\s*[^—-]+$/, '').trim() || searchTitle.value.trim()
}

async function onSearch() {
  const t = searchTitle.value.trim()
  if (t.length < 5) {
    ElMessage.warning('请输入 5-50 字完整论文标题')
    return
  }
  paperStore.setTitle(t)
  searchResults.value = []
  searched.value = false

  if (useApiEnabled()) {
    loading.value = true
    try {
      const list = await searchOutline(t, degree.value)
      searchResults.value = list.map((item, i) => ({
        id: (item.id as number | string) ?? i,
        title: String(item.title),
        degree: String(item.degree ?? degree.value),
        depth: (Number(item.depth) === 3 ? 3 : 2) as 2 | 3,
        outlineJson: item.outlineJson ? String(item.outlineJson) : undefined,
      }))
      searched.value = true
    } catch (e) {
      ElMessage.error((e as Error).message)
    } finally {
      loading.value = false
    }
    return
  }

  await run(async () => {})
  searchResults.value = mockOutlineSearchResults(t, degree.value)
  searched.value = true
}

async function applyOutline(item?: OutlineSearchItem, depthOverride?: 2 | 3) {
  const depth = (depthOverride ?? item?.depth ?? 2) as 2 | 3
  const title = titleForGenerate(item)
  if (title.length < 5) {
    ElMessage.warning('请先搜索有效论文标题')
    return
  }

  applySeed.value += 1
  paperStore.clearOutline()

  if (item?.outlineJson?.trim()) {
    const text = outlineJsonToText(item.outlineJson, title, depth)
    emit('select', text)
    ElMessage.success('已选用推荐提纲')
    return
  }

  if (useApiEnabled()) {
    try {
      const text = await generateOutline(title, depth)
      if (text.trim()) {
        emit('select', text.trim())
        ElMessage.success(`已选用 ${depth} 级提纲`)
        return
      }
      ElMessage.warning('未生成有效提纲')
      return
    } catch (e) {
      ElMessage.error((e as Error).message)
      return
    }
  }

  const text = buildOutlineTextFromTitle(title, depth, applySeed.value)
  emit('select', text)
  ElMessage.success(`已选用 ${depth} 级提纲`)
}
</script>

<template>
  <div class="search-panel xw-card">
    <div class="search-row">
      <el-input
        v-model="searchTitle"
        placeholder="根据标题搜索 | 输入完整论文标题（5-50字内或20个单词内）..."
        size="large"
        @keyup.enter="onSearch"
      >
        <template #append>
          <el-button :loading="loading" @click="onSearch">
            <el-icon><Search /></el-icon>
          </el-button>
        </template>
      </el-input>
      <div class="degree-pills">
        <span class="pill-label">学历</span>
        <button
          v-for="d in ['大专', '本科', '硕士']"
          :key="d"
          type="button"
          class="pill"
          :class="{ active: degree === d }"
          @click="degree = d"
        >
          {{ d }}
        </button>
      </div>
    </div>

    <div v-if="!searched" class="empty-state">
      <p class="emoji-hint">👆 在上方搜索提纲；或直接 粘贴贴心仪提纲</p>
    </div>

    <div v-else class="results">
      <p class="results-title">推荐提纲</p>
      <div v-if="searchResults.length" class="result-list">
        <div v-for="r in searchResults" :key="r.id" class="result-item">
          <span>{{ r.title }}</span>
          <el-button size="small" type="primary" plain @click="applyOutline(r)">
            选用
          </el-button>
        </div>
      </div>
      <div class="result-actions">
        <el-button type="primary" plain @click="applyOutline(undefined, 2)">
          选用 2 级提纲
        </el-button>
        <el-button type="primary" plain @click="applyOutline(undefined, 3)">
          选用 3 级提纲
        </el-button>
      </div>
    </div>

    <div class="feature-cards">
      <div v-for="f in features" :key="f.title" class="feature-card">
        <span class="f-icon">{{ f.icon }}</span>
        <strong>{{ f.title }}</strong>
        <p>{{ f.desc }}</p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.search-panel {
  padding: 20px 24px;
  min-height: 520px;
  display: flex;
  flex-direction: column;
}

.search-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  margin-bottom: 24px;
}

.search-row .el-input {
  flex: 1;
  min-width: 280px;
}

.degree-pills {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
}

.pill-label {
  font-size: 13px;
  color: #64748b;
  margin-right: 4px;
}

.pill {
  padding: 6px 14px;
  border: 1px solid #e2e8f0;
  border-radius: 20px;
  background: #fff;
  font-size: 13px;
  cursor: pointer;
  color: #475569;
}

.pill.active {
  border-color: #6366f1;
  background: #eef2ff;
  color: #4f46e5;
  font-weight: 600;
}

.empty-state {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 200px;
}

.emoji-hint {
  font-size: 15px;
  color: #64748b;
}

.results {
  flex: 1;
  margin-bottom: 20px;
}

.results-title {
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 12px;
}

.result-list {
  margin-bottom: 12px;
}

.result-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
  font-size: 14px;
}

.result-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.feature-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-top: auto;
  padding-top: 20px;
}

.feature-card {
  background: #f8fafc;
  border: 1px solid #e8eef4;
  border-radius: 12px;
  padding: 14px;
  text-align: center;
}

.f-icon {
  font-size: 24px;
  display: block;
  margin-bottom: 6px;
}

.feature-card strong {
  font-size: 13px;
  color: #334155;
  display: block;
  margin-bottom: 4px;
}

.feature-card p {
  font-size: 11px;
  color: #94a3b8;
  line-height: 1.4;
}

@media (max-width: 900px) {
  .feature-cards {
    grid-template-columns: 1fr;
  }
}
</style>
