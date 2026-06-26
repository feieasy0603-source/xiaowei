<script setup lang="ts">
import { computed } from 'vue'
import { Loading } from '@element-plus/icons-vue'
import type { RecommendPhase } from '@/composables/useTitleRecommend'
import type { HotTitleCategory } from '@/mocks/hotTitles'

const props = defineProps<{
  keyword: string
  phase: RecommendPhase
  aiTitles: string[]
  categories: HotTitleCategory[]
  activeCategoryId: string
  hotTitles: string[]
}>()

const emit = defineEmits<{
  'update:activeCategoryId': [id: string]
  refresh: []
  'pick-ai': [title: string]
  'pick-hot': [title: string]
  close: []
}>()

const displayKeyword = computed(() => props.keyword.trim() || '请输入选题关键词')
const keywordEmpty = computed(() => !props.keyword.trim())

const step2Active = computed(() => props.phase === 'analyzing')
const step3Ready = computed(() => props.phase === 'done' && props.aiTitles.length > 0)
</script>

<template>
  <div class="title-recommend-panel">
    <p class="panel-tip">
      <span class="tip-icon" aria-hidden="true">👆</span>
      输入内容后再点击推荐，结果更精准哦~
    </p>

    <div class="flow-row">
      <div class="flow-card">
        <div class="flow-card-title">输入内容</div>
        <div class="flow-mock-input" :class="{ placeholder: keywordEmpty }">
          {{ displayKeyword }}
        </div>
      </div>
      <span class="flow-arrow" aria-hidden="true">→</span>
      <div class="flow-card" :class="{ active: step2Active, done: step3Ready }">
        <div class="flow-card-title">AI深度分析</div>
        <div class="flow-sub">
          <template v-if="step2Active">
            <el-icon class="is-loading spin"><Loading /></el-icon>
            正在分析…
          </template>
          <template v-else>
            <div>趋势贴合度分析</div>
            <div class="muted">500亿文献对比</div>
          </template>
        </div>
      </div>
      <span class="flow-arrow" aria-hidden="true">→</span>
      <div class="flow-card" :class="{ active: step3Ready }">
        <div class="flow-card-title">获得标题</div>
        <div v-if="step3Ready" class="flow-results">
          <button
            v-for="(t, i) in aiTitles"
            :key="i"
            type="button"
            class="flow-result-item"
            :title="t"
            @click="emit('pick-ai', t)"
          >
            {{ t }}
          </button>
        </div>
        <div v-else-if="phase === 'analyzing'" class="flow-placeholder">生成中…</div>
        <div v-else class="flow-placeholder muted">输入关键词后生成</div>
      </div>
    </div>

    <div class="hot-section">
      <div class="hot-head">
        <span class="hot-icon" aria-hidden="true">🔥</span>
        <span class="hot-label">热门标题</span>
      </div>
      <div class="hot-tabs">
        <button
          v-for="cat in categories"
          :key="cat.id"
          type="button"
          class="hot-tab"
          :class="{ active: cat.id === activeCategoryId }"
          @click="emit('update:activeCategoryId', cat.id)"
        >
          {{ cat.label }}
        </button>
      </div>
      <div class="hot-grid">
        <button
          v-for="(t, i) in hotTitles"
          :key="i"
          type="button"
          class="hot-item"
          @click="emit('pick-hot', t)"
        >
          {{ t }}
        </button>
      </div>
      <div class="hot-foot">
        <button type="button" class="refresh-btn" @click="emit('refresh')">
          <span aria-hidden="true">🔄</span>
          换一批
        </button>
      </div>
    </div>

    <button type="button" class="panel-close" aria-label="关闭" @click="emit('close')">
      ×
    </button>
  </div>
</template>

<style scoped>
.title-recommend-panel {
  position: relative;
  margin: 12px 0 16px;
  padding: 16px 18px 14px;
  border-radius: 12px;
  background: linear-gradient(180deg, #f0f7ff 0%, #f8fbff 100%);
  border: 1px solid #dbeafe;
}

.panel-tip {
  text-align: center;
  font-size: 13px;
  color: #64748b;
  margin-bottom: 14px;
}

.tip-icon {
  margin-right: 4px;
}

.flow-row {
  display: flex;
  align-items: stretch;
  justify-content: center;
  gap: 8px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.flow-card {
  flex: 1;
  min-width: 140px;
  max-width: 200px;
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 10px 12px;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.flow-card.active {
  border-color: #93c5fd;
  box-shadow: 0 0 0 2px rgb(59 130 246 / 12%);
}

.flow-card.done {
  border-color: #86efac;
}

.flow-card-title {
  font-size: 12px;
  font-weight: 600;
  color: #334155;
  margin-bottom: 8px;
}

.flow-mock-input {
  font-size: 13px;
  color: #1e293b;
  padding: 6px 8px;
  border: 1px solid #e2e8f0;
  border-radius: 6px;
  background: #f8fafc;
  min-height: 32px;
  word-break: break-all;
}

.flow-mock-input.placeholder {
  color: #94a3b8;
}

.flow-sub {
  font-size: 12px;
  color: #475569;
  line-height: 1.5;
}

.flow-sub .muted,
.flow-placeholder.muted {
  color: #94a3b8;
  font-size: 11px;
  margin-top: 4px;
}

.flow-arrow {
  align-self: center;
  color: #94a3b8;
  font-size: 18px;
  flex-shrink: 0;
}

.flow-results {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.flow-result-item {
  border: none;
  background: #f1f5f9;
  border-radius: 6px;
  padding: 6px 8px;
  font-size: 11px;
  color: #334155;
  text-align: left;
  cursor: pointer;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.flow-result-item:hover {
  background: #e0e7ff;
  color: #1d4ed8;
}

.flow-placeholder {
  font-size: 12px;
  color: #64748b;
}

.spin {
  vertical-align: middle;
  margin-right: 4px;
}

.hot-section {
  background: #fff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  padding: 12px 14px;
}

.hot-head {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 10px;
}

.hot-label {
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}

.hot-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 4px 16px;
  margin-bottom: 12px;
  border-bottom: 1px solid #f1f5f9;
  padding-bottom: 8px;
}

.hot-tab {
  border: none;
  background: none;
  font-size: 13px;
  color: #64748b;
  padding: 4px 0;
  cursor: pointer;
  position: relative;
}

.hot-tab.active {
  color: var(--xw-primary);
  font-weight: 600;
}

.hot-tab.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -9px;
  height: 2px;
  background: var(--xw-primary);
  border-radius: 1px;
}

.hot-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px 16px;
}

.hot-item {
  border: none;
  background: none;
  text-align: left;
  font-size: 13px;
  color: #334155;
  line-height: 1.45;
  cursor: pointer;
  padding: 4px 0;
}

.hot-item:hover {
  color: var(--xw-primary);
}

.hot-foot {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}

.refresh-btn {
  border: none;
  background: none;
  font-size: 13px;
  color: var(--xw-primary);
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.refresh-btn:hover {
  text-decoration: underline;
}

.panel-close {
  position: absolute;
  top: 8px;
  right: 10px;
  width: 28px;
  height: 28px;
  border: none;
  background: rgb(255 255 255 / 80%);
  border-radius: 50%;
  font-size: 18px;
  line-height: 1;
  color: #94a3b8;
  cursor: pointer;
}

.panel-close:hover {
  color: #64748b;
  background: #fff;
}

@media (max-width: 640px) {
  .flow-row {
    flex-direction: column;
    align-items: center;
  }

  .flow-arrow {
    transform: rotate(90deg);
  }

  .flow-card {
    max-width: 100%;
    width: 100%;
  }

  .hot-grid {
    grid-template-columns: 1fr;
  }
}
</style>
