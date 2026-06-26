<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { MagicStick } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus/es/components/message/index.mjs'
import TitleRecommendPanel from '@/components/home/shared/TitleRecommendPanel.vue'
import { useTitleRecommend } from '@/composables/useTitleRecommend'
import { illustrationKeyFor, preloadIllustration } from '@/mocks/processIllustrations'
import { useAppStore } from '@/stores/app'
import { usePaperStore } from '@/stores/paper'
import { useProductsStore } from '@/stores/products'

const props = defineProps<{
  placeholder: string
}>()

const appStore = useAppStore()
const paperStore = usePaperStore()
const productsStore = useProductsStore()
const router = useRouter()
const route = useRoute()
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

/** 可在顶栏下拉切换的论文类产品 */
const paperOptions = computed(() =>
  productsStore.list.filter(
    (p) =>
      p.category === 'writing' &&
      !p.centerTitle &&
      ['graduation', 'journal', 'course', 'review', 'proposal', 'task', 'generic'].includes(
        p.formVariant,
      ),
  ),
)

const currentId = computed({
  get: () => appStore.activeMenuId,
  set: (id: string) => switchProduct(id),
})

function switchProduct(id: string) {
  const p = productsStore.getProduct(id)
  const key = illustrationKeyFor(p.processVariant, id)
  if (key) preloadIllustration(key)

  appStore.setActiveMenu(id)
  appStore.closeProEdition()
  appStore.closeWizard()
  const q = { ...route.query }
  delete q.pro
  delete q.wizard
  router.push({
    name: 'intelligentWriting',
    params: { step: '0' },
    query: q,
  })
}

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
  <div class="title-bar-block">
    <div class="title-bar">
      <el-select
        v-model="currentId"
        class="product-select"
        size="large"
        :teleported="false"
      >
        <el-option
          v-for="p in paperOptions"
          :key="p.id"
          :label="p.label"
          :value="p.id"
        />
      </el-select>
      <div class="title-input-wrap">
        <el-input
          :model-value="keyword"
          :placeholder="placeholder"
          maxlength="50"
          class="title-input"
          @update:model-value="paperStore.setTitle($event)"
        />
        <button type="button" class="recommend-btn" @click="onRecommend">
          <el-icon><MagicStick /></el-icon>
          推荐标题
        </button>
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
.title-bar-block {
  margin-bottom: 8px;
}

.title-bar {
  display: flex;
  align-items: stretch;
  gap: 10px;
}

.product-select {
  width: 132px;
  flex-shrink: 0;
}

.product-select :deep(.el-select__wrapper) {
  height: 48px;
  border-radius: 10px;
  box-shadow: none;
  border: 1px solid var(--xw-border);
  background: #fff;
}

.title-input-wrap {
  flex: 1;
  min-width: 0;
  position: relative;
  display: flex;
  align-items: center;
}

.title-input {
  width: 100%;
}

.title-input :deep(.el-input__wrapper) {
  height: 48px;
  padding-right: 108px;
  border-radius: 10px;
  box-shadow: none;
  border: 1px solid var(--xw-border);
  background: #fff;
  font-size: 14px;
}

.title-input :deep(.el-input__wrapper.is-focus) {
  border-color: var(--xw-primary-light);
  box-shadow: 0 0 0 2px rgb(59 130 246 / 12%);
}

.recommend-btn {
  position: absolute;
  right: 8px;
  top: 50%;
  transform: translateY(-50%);
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: none;
  border-radius: 8px;
  background: linear-gradient(135deg, #ede9fe, #e0e7ff);
  color: #5b21b6;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s, transform 0.12s;
  white-space: nowrap;
}

.recommend-btn:hover {
  opacity: 0.92;
  transform: translateY(-50%) scale(1.02);
}

@media (max-width: 560px) {
  .title-bar {
    flex-direction: column;
  }

  .product-select {
    width: 100%;
  }
}
</style>
