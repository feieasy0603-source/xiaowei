<script setup lang="ts">
export interface StepTabItem {
  step: number
  label: string
}

const props = withDefaults(
  defineProps<{
    current: number
    maxVisited: number
    tabs?: StepTabItem[]
  }>(),
  {
    tabs: () => [
      { step: 0, label: '标题' },
      { step: 1, label: '文献' },
      { step: 2, label: '大纲' },
      { step: 3, label: '预览/下载' },
    ],
  },
)

const emit = defineEmits<{
  change: [step: number]
}>()

function canClick(step: number) {
  return step <= props.maxVisited || step <= props.current
}

function onTabClick(step: number) {
  if (!canClick(step)) return
  emit('change', step)
}
</script>

<template>
  <nav class="step-tabs">
    <button
      v-for="(tab, idx) in props.tabs"
      :key="tab.step"
      type="button"
      class="step-tab"
      :class="{
        active: current === tab.step,
        done: tab.step < current,
        disabled: !canClick(tab.step),
      }"
      :disabled="!canClick(tab.step)"
      @click="onTabClick(tab.step)"
    >
      <span class="step-num">{{ idx + 1 }}</span>
      {{ tab.label }}
    </button>
  </nav>
</template>

<style scoped>
.step-tabs {
  display: flex;
  gap: 4px;
  flex-wrap: wrap;
}

.step-tab {
  flex: 1;
  min-width: 100px;
  padding: 10px 12px;
  border: none;
  background: rgb(255 255 255 / 15%);
  color: rgb(255 255 255 / 85%);
  border-radius: 8px;
  cursor: pointer;
  font-size: 14px;
  transition: background 0.2s;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

.step-tab:hover:not(.disabled) {
  background: rgb(255 255 255 / 25%);
}

.step-tab.active {
  background: #fff;
  color: var(--xw-primary);
  font-weight: 600;
}

.step-tab.done:not(.active) {
  background: rgb(255 255 255 / 30%);
}

.step-tab.disabled {
  opacity: 0.45;
  cursor: not-allowed;
}

.step-num {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: rgb(0 0 0 / 10%);
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.step-tab.active .step-num {
  background: var(--xw-primary);
  color: #fff;
}
</style>
