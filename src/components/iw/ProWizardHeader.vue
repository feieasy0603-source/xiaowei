<script setup lang="ts">
import { computed } from 'vue'
import StepTabs from '@/components/iw/StepTabs.vue'
import { usePaperStore } from '@/stores/paper'
import { usePaperRoute } from '@/composables/usePaperRoute'

const paperStore = usePaperStore()
const { step, goToStep } = usePaperRoute()

const maxVisited = computed(
  () => paperStore.draft?.maxVisitedStep ?? step.value,
)

const proTabs = [
  { step: 1, label: '文献' },
  { step: 2, label: '大纲' },
  { step: 3, label: '预览/下载' },
]

function backHome() {
  goToStep(0)
}
</script>

<template>
  <div class="pro-header">
    <button type="button" class="back-home" @click="backHome">← 返回快速生成</button>
    <StepTabs
      :current="step"
      :max-visited="maxVisited"
      :tabs="proTabs"
      @change="goToStep"
    />
  </div>
</template>

<style scoped>
.pro-header {
  background: #fff;
  border-radius: 12px;
  padding: 12px 16px;
  margin-bottom: 16px;
  border: 1px solid #e8eef4;
}

.back-home {
  border: none;
  background: none;
  color: #3b82f6;
  font-size: 13px;
  cursor: pointer;
  margin-bottom: 12px;
  padding: 0;
}

.back-home:hover {
  text-decoration: underline;
}

.pro-header :deep(.step-tabs) {
  gap: 8px;
}

.pro-header :deep(.step-tab) {
  background: #f1f5f9;
  color: #475569;
}

.pro-header :deep(.step-tab.active) {
  background: #3b82f6;
  color: #fff;
}
</style>
