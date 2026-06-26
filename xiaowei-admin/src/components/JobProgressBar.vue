<script setup lang="ts">
import { computed } from 'vue'
import {
  clampProgress,
  isActiveJobStatus,
  jobStatusProgressLabel,
  progressBarStatus,
} from '@/utils/jobProgress'

const props = withDefaults(
  defineProps<{
    progress: number
    status: string
    large?: boolean
    showLabel?: boolean
  }>(),
  {
    large: false,
    showLabel: true,
  },
)

const pct = computed(() => clampProgress(props.progress))

const active = computed(() => isActiveJobStatus(props.status))

const label = computed(() => jobStatusProgressLabel(props.status, pct.value))

const barStatus = computed(() => progressBarStatus(props.status))

const displayPct = computed(() => {
  if (props.status === 'pending' && pct.value === 0) return 8
  return pct.value
})
</script>

<template>
  <div class="job-progress-bar" :class="{ large }">
    <el-progress
      :percentage="displayPct"
      :stroke-width="large ? 12 : 8"
      :status="barStatus"
      :striped="active"
      striped-flow
      :duration="10"
      class="bar"
    />
    <span v-if="showLabel" class="label" :title="`${pct}%`">{{ label }}</span>
  </div>
</template>

<style scoped>
.job-progress-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
}

.job-progress-bar.large {
  flex-direction: column;
  align-items: stretch;
  gap: 8px;
}

.bar {
  flex: 1;
  min-width: 0;
}

.job-progress-bar.large .bar {
  flex: none;
}

.label {
  flex-shrink: 0;
  font-size: 12px;
  color: #64748b;
  font-variant-numeric: tabular-nums;
  min-width: 4.5em;
  text-align: right;
}

.job-progress-bar.large .label {
  text-align: left;
  font-size: 13px;
  font-weight: 600;
  color: #334155;
}

.job-progress-bar :deep(.el-progress-bar__outer) {
  border-radius: 6px;
}

.job-progress-bar :deep(.el-progress-bar__inner) {
  transition: width 0.4s ease;
}
</style>
