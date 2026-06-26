<script setup lang="ts">
import { taskTypeColor, taskTypeLabel } from '@/constants/ai'

defineProps<{ type: string }>()
</script>

<template>
  <span class="task-tag" :style="{ '--c': taskTypeColor(type) }">
    {{ taskTypeLabel(type) }}
  </span>
</template>

<style scoped>
.task-tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 500;
  color: var(--c);
  background: color-mix(in srgb, var(--c) 12%, white);
  border: 1px solid color-mix(in srgb, var(--c) 28%, white);
}
</style>
