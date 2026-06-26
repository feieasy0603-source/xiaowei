<script setup lang="ts">
import { computed } from 'vue'
import {
  clampProgress,
  jobProgressPhase,
  sectionProgressPercent,
} from '@/composables/jobProgressDisplay'

const props = withDefaults(
  defineProps<{
    percentage: number
    sectionDone?: number
    sectionTotal?: number
    active?: boolean
    status?: 'generating' | 'success' | 'exception'
  }>(),
  {
    active: true,
    status: 'generating',
  },
)

const pct = computed(() => clampProgress(props.percentage))

const phase = computed(() =>
  jobProgressPhase(pct.value, props.sectionDone, props.sectionTotal),
)

const sectionPct = computed(() =>
  props.sectionTotal != null && props.sectionDone != null
    ? sectionProgressPercent(props.sectionDone, props.sectionTotal)
    : 0,
)

const showSection = computed(
  () => props.sectionTotal != null && props.sectionTotal > 0,
)

const barStatus = computed(() => {
  if (props.status === 'success') return 'success'
  if (props.status === 'exception') return 'exception'
  return undefined
})

const isAnimating = computed(() => props.active && pct.value < 100 && props.status === 'generating')
</script>

<template>
  <div class="job-progress-panel" :class="{ done: pct >= 100 }">
    <div class="progress-head">
      <div class="phase-wrap">
        <span v-if="isAnimating" class="pulse-dot" />
        <span class="phase">{{ phase }}</span>
      </div>
      <span class="pct-num">{{ pct }}%</span>
    </div>

    <el-progress
      :percentage="pct"
      :stroke-width="14"
      :status="barStatus"
      :striped="isAnimating"
      striped-flow
      :duration="12"
      class="main-bar"
    />

    <div v-if="showSection" class="section-block">
      <div class="section-label">
        <span>章节进度</span>
        <span class="section-count">{{ sectionDone }} / {{ sectionTotal }}</span>
      </div>
      <el-progress
        :percentage="sectionPct"
        :stroke-width="8"
        :show-text="false"
        :striped="isAnimating && sectionPct < 100"
        striped-flow
        color="#6366f1"
        class="section-bar"
      />
    </div>
  </div>
</template>

<style scoped>
.job-progress-panel {
  padding: 16px 18px;
  border-radius: 12px;
  background: linear-gradient(135deg, #f8fafc 0%, #eef2ff 100%);
  border: 1px solid #e2e8f0;
}

.job-progress-panel.done {
  background: linear-gradient(135deg, #f0fdf4 0%, #ecfdf5 100%);
  border-color: #bbf7d0;
}

.progress-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
  gap: 12px;
}

.phase-wrap {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #6366f1;
  flex-shrink: 0;
  animation: pulse 1.2s ease-in-out infinite;
}

@keyframes pulse {
  0%,
  100% {
    opacity: 0.35;
    transform: scale(0.85);
  }
  50% {
    opacity: 1;
    transform: scale(1.1);
  }
}

.phase {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}

.pct-num {
  font-size: 22px;
  font-weight: 700;
  color: #4f46e5;
  font-variant-numeric: tabular-nums;
  flex-shrink: 0;
}

.main-bar :deep(.el-progress-bar__outer) {
  border-radius: 8px;
  background: #e2e8f0;
}

.main-bar :deep(.el-progress-bar__inner) {
  border-radius: 8px;
  transition: width 0.25s ease-out;
}

.section-block {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px dashed #cbd5e1;
}

.section-label {
  display: flex;
  justify-content: space-between;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 6px;
}

.section-count {
  font-weight: 600;
  color: #4f46e5;
  font-variant-numeric: tabular-nums;
}

.section-bar :deep(.el-progress-bar__outer) {
  border-radius: 6px;
}
</style>
