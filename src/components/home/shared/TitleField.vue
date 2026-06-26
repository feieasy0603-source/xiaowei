<script setup lang="ts">
import { computed } from 'vue'
import { MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import TitleRecommendPanel from '@/components/home/shared/TitleRecommendPanel.vue'
import { useTitleRecommend } from '@/composables/useTitleRecommend'
import { useAppStore } from '@/stores/app'
import { usePaperStore } from '@/stores/paper'

defineProps<{
  label: string
  placeholder: string
  proLinkText?: string
}>()

const emit = defineEmits<{ pro: [] }>()

const paperStore = usePaperStore()
const appStore = useAppStore()
const {
  visible: recommendVisible,
  phase: recommendPhase,
  aiTitles: recommendAiTitles,
  activeCategoryId: recommendCategoryId,
  hotTitlesPage: recommendHotTitles,
  hotTitleCategories,
  open: openRecommend,
  close: closeRecommend,
  switchCategory,
  refreshHotBatch,
  analyze: analyzeTitles,
} = useTitleRecommend()

const keyword = computed(() => paperStore.draft?.title ?? '')
const titleLen = computed(() => keyword.value.length)

function onRecommend() {
  openRecommend()
  void analyzeTitles(keyword.value, appStore.activeMenuId)
}

function onPickHotTitle(title: string) {
  paperStore.setTitle(title)
  ElMessage.success('已选用热门标题')
}

function onPickAiTitle(title: string) {
  paperStore.setTitle(title)
  closeRecommend()
  ElMessage.success('已选用推荐标题')
}
</script>

<template>
  <div class="title-field">
    <div class="form-head">
      <span class="form-title"><span class="pipe">|</span> {{ label }}</span>
      <button v-if="proLinkText" type="button" class="pro-chip" @click="emit('pro')">
        <span class="pro-chip-label">{{ proLinkText }}</span>
        <span class="pro-chip-arrow" aria-hidden="true">→</span>
      </button>
    </div>

    <div class="title-box">
      <el-input
        :model-value="keyword"
        type="textarea"
        :rows="3"
        maxlength="50"
        :placeholder="placeholder"
        class="title-textarea"
        @update:model-value="paperStore.setTitle($event)"
      />
      <div class="title-toolbar">
        <button type="button" class="recommend-btn" @click="onRecommend">
          <el-icon class="recommend-icon"><MagicStick /></el-icon>
          推荐标题
        </button>
        <span class="char-count" :class="{ warn: titleLen >= 45 }">
          {{ titleLen }} / 50
        </span>
      </div>
    </div>

    <TitleRecommendPanel
      v-if="recommendVisible"
      :keyword="keyword"
      :phase="recommendPhase"
      :ai-titles="recommendAiTitles"
      :categories="hotTitleCategories"
      :active-category-id="recommendCategoryId"
      :hot-titles="recommendHotTitles"
      @update:active-category-id="switchCategory"
      @refresh="refreshHotBatch()"
      @pick-hot="onPickHotTitle"
      @pick-ai="onPickAiTitle"
      @close="closeRecommend()"
    />
  </div>
</template>

<style scoped>
.title-field {
  margin-bottom: 24px;
}

.form-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.form-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--xw-text);
  line-height: 1.4;
}

.pipe {
  color: var(--xw-primary);
  font-weight: 700;
  margin-right: 2px;
}

.pro-chip {
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: min(240px, 46vw);
  padding: 6px 12px;
  border-radius: 999px;
  border: 1px solid #bfdbfe;
  background: linear-gradient(180deg, #eff6ff, #dbeafe);
  color: var(--xw-primary-dark);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
  text-align: left;
}

.pro-chip:hover {
  border-color: #93c5fd;
  background: #eff6ff;
  box-shadow: 0 2px 8px rgb(59 130 246 / 12%);
}

.pro-chip-label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pro-chip-arrow {
  flex-shrink: 0;
  font-weight: 600;
}

.title-box {
  border: 2px solid var(--xw-border);
  border-radius: 12px;
  background: #fafbfc;
  overflow: hidden;
  transition: border-color 0.15s, box-shadow 0.15s, background 0.15s;
}

.title-box:focus-within {
  border-color: var(--xw-primary-light);
  background: #fff;
  box-shadow: 0 0 0 3px rgb(59 130 246 / 10%);
}

.title-textarea :deep(.el-textarea__inner) {
  border: none;
  box-shadow: none !important;
  border-radius: 0;
  padding: 14px 16px 10px;
  font-size: 15px;
  line-height: 1.65;
  background: transparent;
  resize: none;
  min-height: 88px;
}

.title-textarea :deep(.el-textarea__inner:focus) {
  box-shadow: none !important;
}

.title-textarea :deep(.el-input__count) {
  display: none;
}

.title-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px;
  background: #f1f5f9;
  border-top: 1px solid var(--xw-border);
}

.recommend-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px;
  border-radius: 999px;
  border: 1px solid #e2e8f0;
  background: #fff;
  color: var(--xw-text-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.15s;
}

.recommend-btn:hover {
  color: var(--xw-primary-dark);
  border-color: #93c5fd;
  background: #eff6ff;
}

.recommend-icon {
  font-size: 15px;
  color: var(--xw-primary);
}

.char-count {
  font-size: 12px;
  font-variant-numeric: tabular-nums;
  color: var(--xw-muted);
  flex-shrink: 0;
}

.char-count.warn {
  color: #ea580c;
  font-weight: 600;
}

@media (max-width: 520px) {
  .form-head {
    flex-direction: column;
    align-items: stretch;
  }

  .pro-chip {
    max-width: none;
    justify-content: center;
  }
}
</style>
